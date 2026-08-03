#!/usr/bin/env python3
"""Post-rescue rebound guard — is the BG ramp opening too fast? (2026-08-03)

THE GUARD (DetermineBasalBoost.postRescueReboundScale, shipped 51e7663a36 after the
2026-07-23 user-H double-crash):

    bg < 120        -> scale 0.30
    120 <= bg < 170 -> 0.30 + 0.70 * (bg - 120) / 50      (linear 0.30 -> 1.00)
    bg >= 170       -> guard does not apply at all (the block is gated on bg < 170)

The scale is a function of BG ALONE — not of how long the window has been open, not of
how fast BG is climbing. The design reasoning is that a higher BG makes a genuine meal
more likely, so protection should relax. The concern this study tests: on a rescue-carb
rebound BG traverses 120 -> 170 in a few cycles, so the guard relaxes from full
protection to none precisely while the rescue carbs are peaking.

Observed live, tim 2026-08-02 (BG 116 -> 143 -> 180 in 15 minutes):
    18:16  BG 116  scale 30%   SMB 1.05 -> 0.30
    18:21  BG 143  scale 62%   SMB 1.65 -> 1.00
    18:31  BG 180  guard OFF   SMB 1.00 delivered unscaled
    20:26  BG 44.6

MEASURES (cohort, V6 era; no counterfactual BG is claimed anywhere):
  1. TRAVERSE   how long a post-rescue window spends in each scale band, and how quickly
                BG crosses 120 and 170 once the window opens.
  2. EXPOSURE   insulin delivered inside post-rescue windows, split by the band in force.
  3. PRICING    for each band, the share of that insulin delivered within 3 h of a real
                <70 — the established harm-pricing method — against the user's own
                baseline share.
  4. POLICIES   three candidate ramps, each priced by how much insulin it removes and how
                much of what it removes sits ahead of a real low.

Usage:  python3 ramp_study.py
"""
from __future__ import annotations

import os
import re
import sys

import numpy as np
import pandas as pd
import psycopg2

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "2026-08-autoconfig-redrive"))
from redrive_replay import LowLookup, boot_ci, md  # noqa: E402

DSN = "dbname=oref host=127.0.0.1 port=5432"
USERS = ["tim", "A", "B", "C", "D", "E", "F", "H"]
GAP_MIN = 12          # a break longer than this starts a new post-rescue window
RNG = np.random.default_rng(20260803)


def shipped_scale(bg):
    """The scale actually in force today (None = the guard does not apply)."""
    if bg < 120.0:
        return 0.30
    if bg < 170.0:
        return 0.30 + 0.70 * (bg - 120.0) / 50.0
    return None                                    # block gated on bg < 170


def band(bg):
    if bg < 120.0:
        return "a. <120 (scale .30)"
    if bg < 145.0:
        return "b. 120-145 (.30-.65)"
    if bg < 170.0:
        return "c. 145-170 (.65-1.0)"
    return "d. >=170 (guard OFF)"


def load():
    conn = psycopg2.connect(DSN)
    d = pd.read_sql("""
        SELECT DISTINCT ON (user_id, floor(ts_epoch/300.0))
          user_id, ts_utc, ts_epoch, cgm_mgdl AS bg, boostv5_state AS state,
          boostv5_active AS active, boostv5_finaldose AS fd, v1_units,
          boostv5_postrescuewindow AS prw, iob_iob AS iob, sug_cob AS cob,
          reason_text
        FROM boost_decisions
        WHERE boostv5_state IS NOT NULL AND user_id = ANY(%s)
        ORDER BY user_id, floor(ts_epoch/300.0), ts_epoch DESC""", conn, params=(USERS,))
    cgm = pd.read_sql("""
        SELECT user_id, ts_utc, cgm_mgdl FROM boost_cgm
        WHERE cgm_mgdl > 1 AND user_id = ANY(%s)""", conn, params=(USERS,))
    conn.close()
    for f in (d, cgm):
        f["ts"] = pd.to_datetime(f.ts_utc, utc=True)
    # the scale the engine actually printed, when it applied
    d["scale_applied"] = [
        float(m.group(1)) / 100.0 if (m := re.search(r"Post-rescue rebound scale ([\d.]+)%", t or ""))
        else np.nan for t in d.reason_text]
    d["pre_smb"] = [
        float(m.group(1)) if (m := re.search(r"rebound scale [\d.]+%: SMB ([\d.]+) →", t or ""))
        else np.nan for t in d.reason_text]
    # delivered dose: V6's when it was the active doser, else V1's
    d["dose"] = np.where(d.active.fillna(False), d.fd.fillna(0.0), d.v1_units.fillna(0.0))
    return d.sort_values(["user_id", "ts_epoch"]).reset_index(drop=True), cgm


def windows(g):
    """Label contiguous runs of postRescueWindow=true."""
    p = g[g.prw.fillna(False)]
    if p.empty:
        return []
    brk = (p.ts_epoch.diff() > GAP_MIN * 60).cumsum()
    return [x for _, x in p.groupby(brk)]


def main():
    d, cgm = load()
    win_rows, cyc_rows = [], []
    lows = {u: LowLookup(cgm[cgm.user_id == u].sort_values("ts")) for u in USERS}

    for u in USERS:
        g = d[d.user_id == u]
        for w in windows(g):
            w = w.sort_values("ts_epoch")
            t0 = w.ts_epoch.iloc[0]
            cross120 = w.ts_epoch[w.bg >= 120].min()
            cross170 = w.ts_epoch[w.bg >= 170].min()
            win_rows.append(dict(
                user=u, start=w.ts.iloc[0], mins=(w.ts_epoch.iloc[-1] - t0) / 60 + 5,
                bg0=w.bg.iloc[0], bg_max=w.bg.max(), bg_min=w.bg.min(),
                insulin=float(w.dose.sum()),
                mins_to_120=(cross120 - t0) / 60 if np.isfinite(cross120) else np.nan,
                mins_to_170=(cross170 - t0) / 60 if np.isfinite(cross170) else np.nan,
                pct_cycles_over170=float((w.bg >= 170).mean())))
            for _, r in w.iterrows():
                cyc_rows.append(dict(user=u, ts=r.ts, bg=r.bg, dose=r.dose,
                                     band=band(r.bg), scale=shipped_scale(r.bg),
                                     scale_applied=r.scale_applied, pre_smb=r.pre_smb,
                                     cob=r.cob, state=r.state))
    W = pd.DataFrame(win_rows)
    C = pd.DataFrame(cyc_rows)
    C["pre_low"] = np.concatenate([
        lows[u].any_low(gg.ts.values.astype("datetime64[ns]").astype("int64"))
        for u, gg in C.groupby("user", sort=False)]) if len(C) else []

    # user baseline: share of ALL delivered insulin that precedes a real low
    base = {}
    for u in USERS:
        g = d[(d.user_id == u) & (d.dose > 0)]
        if g.empty:
            continue
        pre = lows[u].any_low(g.ts.values.astype("datetime64[ns]").astype("int64"))
        base[u] = float(g.dose.values[pre].sum() / g.dose.sum())

    L = []
    P = L.append
    P("# Post-rescue rebound guard — is the BG ramp opening too fast?\n")
    P(f"Cohort {', '.join(USERS)}; V6 era; {len(W)} post-rescue windows, {len(C)} cycles.\n")
    P("\nShipped ramp: `<120 → 0.30`, `120–170 → linear 0.30→1.00`, `≥170 → guard does not "
      "apply`. The scale reads BG only — not window age, not rate of rise.\n")

    P("\n## 1. Traverse — how long does protection last?\n")
    t = W.groupby("user").agg(windows=("mins", "size"), med_len_min=("mins", "median"),
                              med_min_to_120=("mins_to_120", "median"),
                              med_min_to_170=("mins_to_170", "median"),
                              pct_reaching_170=("mins_to_170", lambda s: float(s.notna().mean())),
                              insulin=("insulin", "sum")).reset_index()
    P(md(t.round(2)))
    lo, hi = boot_ci(W.mins_to_170.dropna().values)
    P(f"\nAcross all {len(W)} windows: BG reaches 120 a median "
      f"{W.mins_to_120.median():.0f} min after the window opens and 170 a median "
      f"{W.mins_to_170.median():.0f} min after (mean 95% CI [{lo:.0f}, {hi:.0f}]). "
      f"{100 * W.mins_to_170.notna().mean():.0f}% of windows reach 170 at all.\n")

    P("\n## 2 & 3. Exposure and pricing by band\n")
    rows = []
    for b, g in C.groupby("band"):
        dosed = g[g.dose > 0]
        u_tot = dosed.dose.sum()
        rows.append(dict(
            band=b, cycles=len(g), dosed_cycles=len(dosed), insulin_U=u_tot,
            pct_of_window_insulin=100.0 * u_tot / C.dose.sum() if C.dose.sum() else np.nan,
            mean_dose=dosed.dose.mean() if len(dosed) else np.nan,
            prelow_share=float(dosed.dose[dosed.pre_low].sum() / u_tot) if u_tot else np.nan))
    R = pd.DataFrame(rows).sort_values("band")
    R["baseline"] = np.mean(list(base.values()))
    R["vs_baseline"] = R.prelow_share - R.baseline
    P(md(R))
    P("\n`prelow_share` = share of that band's insulin delivered within 3 h of a real <70; "
      "`baseline` is the cohort mean over ALL delivered insulin. Positive `vs_baseline` means "
      "insulin in that band lands ahead of a low more often than the user's average unit.\n")

    # per-user, so the pooled figure is not carried by one person
    pu = []
    for u, g in C.groupby("user"):
        for b, gg in g.groupby("band"):
            dd = gg[gg.dose > 0]
            if dd.dose.sum() < 0.5:
                continue
            pu.append(dict(user=u, band=b, U=dd.dose.sum(),
                           prelow=float(dd.dose[dd.pre_low].sum() / dd.dose.sum()),
                           delta=float(dd.dose[dd.pre_low].sum() / dd.dose.sum()) - base.get(u, np.nan)))
    PU = pd.DataFrame(pu)
    P("\n### Per-user, so no single person carries the pooled figure\n")
    P(md(PU.pivot_table(index="user", columns="band", values="delta").reset_index()))
    P("\nEach cell is that user's band pre-low share minus their own baseline.\n")
    for b, g in PU.groupby("band"):
        lo, hi = boot_ci(g.delta.values)
        P(f"- **{b}**: mean {g.delta.mean():+.3f} over {len(g)} users, 95% CI [{lo:+.3f}, {hi:+.3f}]")

    P("\n\n## 4. Candidate ramps\n")
    P("Each priced by the insulin it removes relative to the shipped ramp, and by how much of "
      "that removed insulin sat ahead of a real low. `pre_smb` (the dose before scaling) is "
      "only logged when the guard applied, so removal above 170 is computed from the delivered "
      "dose, which is what the guard would have scaled.\n")

    def policy_scale(bg, mins_open):
        return {
            "shipped": shipped_scale(bg) if shipped_scale(bg) is not None else 1.0,
            "flat 0.30 whole window": 0.30,
            "cap ramp at 0.60": min(shipped_scale(bg) if shipped_scale(bg) is not None else 1.0, 0.60),
            "extend ramp to 220": (0.30 if bg < 120 else
                                   min(1.0, 0.30 + 0.70 * (bg - 120.0) / 100.0)),
        }

    prows, ppu = [], []
    for name in ("flat 0.30 whole window", "cap ramp at 0.60", "extend ramp to 220"):
        # pre-scale dose: use the engine's own logged pre_smb where the guard applied
        # (exact); elsewhere the guard did not scale, so the delivered dose IS the
        # pre-scale dose. NB where V6 was the active doser its caps/brakes act after the
        # V1 guard, so removals are an approximation of what the guard alone would do.
        cur = C.bg.map(lambda b: shipped_scale(b) if shipped_scale(b) is not None else 1.0)
        pre = np.where(C.pre_smb.notna(), C.pre_smb.fillna(0.0), C.dose.values)
        newscale = C.bg.map(lambda b: policy_scale(b, 0)[name])
        removed = np.maximum(0.0, pre * cur.values - pre * newscale.values)
        removed = np.where(C.dose.values > 0, removed, 0.0)
        C["_removed"] = removed
        tot = float(removed.sum())
        pl = float(C._removed[C.pre_low].sum() / tot) if tot > 0 else np.nan
        # cluster bootstrap over users
        per = []
        for u, gg in C.groupby("user"):
            t_u = gg._removed.sum()
            if t_u > 0.5:
                per.append(float(gg._removed[gg.pre_low].sum() / t_u) - base.get(u, np.nan))
                ppu.append(dict(policy=name, user=u, U=t_u, delta=per[-1]))
        lo, hi = boot_ci(np.array(per))
        prows.append(dict(policy=name, U_removed=tot,
                          pct_of_window_insulin=100.0 * tot / C.dose.sum(),
                          removed_prelow_share=pl,
                          per_user_vs_baseline=np.mean(per) if per else np.nan,
                          ci_lo=lo, ci_hi=hi, n_users=len(per),
                          verdict=("targeted" if lo > 0 else "unproven")))
    P(md(pd.DataFrame(prows)))
    P("\n`per_user_vs_baseline` is the mean over users of (removed insulin's pre-low share "
      "minus that user's own baseline share), with a cluster bootstrap 95% CI over users. "
      "A CI overlapping zero means the policy is **unproven** — it is not demonstrably "
      "removing the units that were about to cause a low rather than units at random.\n")
    P("\n### Per-user\n")
    P(md(pd.DataFrame(ppu).pivot_table(index="user", columns="policy", values="delta").reset_index()))

    open(os.path.join(HERE, "RAMP_REPORT.md"), "w").write("\n".join(L))
    W.to_csv(os.path.join(HERE, "windows.csv"), index=False)
    C.drop(columns=["_removed"], errors="ignore").to_csv(os.path.join(HERE, "cycles.csv"), index=False)
    print("wrote RAMP_REPORT.md")


if __name__ == "__main__":
    main()
