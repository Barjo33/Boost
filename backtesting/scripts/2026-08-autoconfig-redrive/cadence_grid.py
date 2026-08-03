#!/usr/bin/env python3
"""Auto-config re-derivation cadence — window width x step, on real history (2026-08-03).

Proposal on the table: re-derive **every 7 days on a 14-day lookback**.

Those are two separable choices and the replay so far only covered the diagonal (14/14 and
28/28). Cadence buys RESPONSIVENESS; window width buys PRECISION; and with overlapping
windows (step < width) consecutive derivations share data, so they are correlated rather
than independent.

Effective lag of a trailing-W window sampled every S days is about W/2 + S/2 — the mean age
of the information a knob is acting on. That is the cost of a wide window and the benefit of
a short step, and it is arithmetic, not something to measure:

    14/14 -> ~14 d      14/7 -> ~10.5 d      28/7 -> ~17.5 d      28/28 -> ~28 d

What has to be measured is the noise cost. Metrics per grid cell:

  changes_per_6mo   how often a knob would move
  median_abs_delta  typical size of a move
  path_over_net     total distance travelled / net distance covered. 1.0 = every move was
                    progress; large = the knob wanders and comes back. This is the direct
                    measure of "is more frequent re-derivation just jitter?"
  material_share    share of moves bigger than that window's own day-block bootstrap band
  after_deadband    changes surviving a fixed deadband set from the measured noise floor

Usage:  python3 cadence_grid.py
"""
from __future__ import annotations

import os
import sys

import numpy as np
import pandas as pd

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import redrive_replay as rr  # noqa: E402

GRID = [(14, 14), (14, 7), (21, 7), (28, 7), (28, 14), (28, 28)]
CAPS = ("committedCap", "confirmedCap", "cumulative60")
# deadbands from the 28-day bootstrap half-widths measured in REDRIVE_REPORT.md §4b
DEADBAND = dict(aggression=0.025, hypoCaution=0.16, confirmedCap=0.47,
                committedCap=0.067, cumulative60=0.54, primerCap=0.056)


def cell(tre, cgm, dec, width, step, boot=200):
    rows, bands = [], []
    for u in rr.USERS:
        tre_u = tre[tre.user_id == u].sort_values("ts")
        cgm_u = cgm[cgm.user_id == u].sort_values("ts")
        dec_u = dec[dec.user_id == u].sort_values("ts")
        if cgm_u.empty or tre_u.empty:
            continue
        t = rr.trajectory(u, tre_u, cgm_u, dec_u, width, step)
        t = t[t.derived].sort_values("t0").reset_index(drop=True)
        if len(t) < 3:
            continue
        span_days = (t.t1.iloc[-1] - t.t0.iloc[0]).days
        for _, w in t.iterrows():
            bd = rr.bootstrap_band(w.t0, w.t1, tre_u, cgm_u, dec_u, boot)
            if bd:
                bands.append(dict(user=u, t0=w.t0,
                                  **{f"{k}_half": (v[1] - v[0]) / 2.0 for k, v in bd.items()}))
        B = pd.DataFrame(bands)
        for knob in rr.KNOBS:
            v = t[knob].values
            d = np.diff(v)
            moved = np.abs(d) > 1e-9
            path = np.abs(d).sum()
            net = abs(v[-1] - v[0])
            # material: beats the later window's own sampling band
            mat = 0
            bu = B[B.user == u].set_index("t0") if len(B) else None
            for i in np.flatnonzero(moved):
                t0 = t.t0[i + 1]
                if bu is not None and t0 in bu.index and np.isfinite(bu.loc[t0, f"{knob}_half"]):
                    if abs(d[i]) > bu.loc[t0, f"{knob}_half"]:
                        mat += 1
            rows.append(dict(
                user=u, knob=knob, windows=len(t), span_days=span_days,
                changes=int(moved.sum()),
                changes_per_6mo=moved.sum() / span_days * 182.5 if span_days else np.nan,
                median_abs_delta=float(np.median(np.abs(d[moved]))) if moved.any() else 0.0,
                path=path, net=net,
                path_over_net=path / net if net > 1e-9 else np.nan,
                material=mat,
                after_deadband=int((np.abs(d) > DEADBAND[knob]).sum()),
                after_deadband_per_6mo=(np.abs(d) > DEADBAND[knob]).sum() / span_days * 182.5
                if span_days else np.nan))
    return pd.DataFrame(rows)


def main():
    tre, cgm, dec = rr.load()
    all_rows = []
    for (w, s) in GRID:
        c = cell(tre, cgm, dec, w, s)
        c["window"], c["step"] = w, s
        all_rows.append(c)
        print(f"  {w}/{s}: {len(c)} user-knob rows")
    A = pd.concat(all_rows, ignore_index=True)
    A["lag_days"] = A.window / 2 + A.step / 2

    L = []
    P = L.append
    P("# Auto-config re-derivation cadence — window x step grid\n")
    P("Real history, 8 users. Effective lag = W/2 + S/2 (arithmetic). Everything else measured.\n")

    P("\n## The caps (committedCap, confirmedCap, cumulative60) — the knobs with real drift\n")
    caps = A[A.knob.isin(CAPS)]
    g = caps.groupby(["window", "step"]).agg(
        lag_days=("lag_days", "first"),
        changes_per_6mo=("changes_per_6mo", "mean"),
        median_abs_delta=("median_abs_delta", "mean"),
        path_over_net=("path_over_net", "median"),
        material_share=("material", lambda s: np.nan),
        after_deadband_per_6mo=("after_deadband_per_6mo", "mean")).reset_index()
    ms = caps.groupby(["window", "step"]).apply(
        lambda x: x.material.sum() / x.changes.sum() if x.changes.sum() else np.nan,
        include_groups=False)
    g["material_share"] = g.set_index(["window", "step"]).index.map(ms)
    g = g.sort_values(["window", "step"])
    P(rr.md(g))
    P("\n`changes_per_6mo` and `after_deadband_per_6mo` are per knob per user. "
      "`path_over_net` is the median over user-knobs of (total distance travelled ÷ net distance "
      "covered): 1.0 would mean every move was progress.\n")

    P("\n## Per knob, at the proposed 14/7 against the alternatives\n")
    for knob in rr.KNOBS:
        k = A[A.knob == knob]
        gg = k.groupby(["window", "step"]).agg(
            changes_per_6mo=("changes_per_6mo", "mean"),
            path_over_net=("path_over_net", "median"),
            after_deadband_per_6mo=("after_deadband_per_6mo", "mean")).reset_index().sort_values(
            ["window", "step"])
        P(f"\n### {knob} (deadband {DEADBAND[knob]})\n")
        P(rr.md(gg))

    P("\n## Bootstrap CIs on the headline contrast (caps only)\n")
    for metric in ("changes_per_6mo", "path_over_net", "after_deadband_per_6mo"):
        P(f"\n**{metric}**")
        for (w, s) in GRID:
            v = caps[(caps.window == w) & (caps.step == s)][metric].dropna().values
            lo, hi = rr.boot_ci(v)
            P(f"- {w}/{s}: mean {np.mean(v):.2f} [{lo:.2f}, {hi:.2f}] (n={len(v)} user-knobs)")

    open(os.path.join(HERE, "CADENCE_GRID.md"), "w").write("\n".join(L))
    A.to_csv(os.path.join(HERE, "cadence_grid.csv"), index=False)
    print("wrote CADENCE_GRID.md")


if __name__ == "__main__":
    main()
