#!/usr/bin/env python3
"""What should size confirmedCap for a HANDS-FREE user? (2026-08-03)

Boost's target end-state is zero manual bolusing, yet `confirmedCap` is anchored on
`p90(manual boluses)` with `p95(all SMBs)` as the fallback. For a user who already runs
hands-free — an older-Boost migrant, or anyone not announcing — the manual term is
absent by design and the fallback takes over. Two problems with that fallback:

  (a) WRONG POPULATION. p95 is taken over EVERY SMB, and ~85% of SMBs are IDLE /
      OBSERVING / COMMITTED micro-doses. The 95th percentile of that mixture sits inside
      the micro-dose mass, not among the confirm shots the cap is supposed to bound.
  (b) CENSORED. Delivered shots are clipped at the cap being derived.

This scores the candidate anchors against three reference quantities that do NOT depend
on the user announcing anything:

  R1  p90 of the UNCAPPED desired CONFIRMED shot (budget x actionMult x velocityFactor
      on CONFIRMED cycles) — what the engine wanted to give in one shot. Uncensored.
  R2  p90 of meal-episode TOTAL insulin (all doses in the 2 h from a CONFIRMED entry) —
      what a meal actually cost. A per-shot cap should sit below this, since Boost
      deliberately splits a meal across a confirm shot plus holds.
  R3  the operative confirmedCap the user is actually running (revealed preference).

It also asks whether `p90(manual bolus)` — the current anchor — is even the right target
for a system that splits a meal dose across a sequence.

Usage:  python3 anchor_study.py [--window 28]
"""
from __future__ import annotations

import argparse
import os
import sys

import numpy as np
import pandas as pd
import psycopg2

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import boost_autoconfig as ac  # noqa: E402
from redrive_replay import DSN, USERS, CLIP_TOL, boot_ci, md  # noqa: E402

EPISODE_H = 2.0


def load():
    conn = psycopg2.connect(DSN)
    tre = pd.read_sql("""
        SELECT user_id, ts_utc, insulin, is_smb FROM boost_treatments
        WHERE insulin > 0 AND bolus_type IS NOT NULL AND user_id = ANY(%s)""",
                      conn, params=(USERS,))
    dec = pd.read_sql("""
        SELECT DISTINCT ON (user_id, floor(ts_epoch/300.0))
          user_id, ts_utc, ts_epoch, tdd, boostv5_state AS state,
          boostv5_finaldose AS fd, boostv5_confirmedcap AS fcap,
          boostv5_budget AS budget, boostv5_actionmult AS amult, boostv5_velocityfactor AS vf
        FROM boost_decisions
        WHERE boostv5_state IS NOT NULL AND user_id = ANY(%s)
        ORDER BY user_id, floor(ts_epoch/300.0), ts_epoch DESC""", conn, params=(USERS,))
    conn.close()
    for d in (tre, dec):
        d["ts"] = pd.to_datetime(d.ts_utc, utc=True)
    dec["desired"] = dec.budget * dec.amult * dec.vf
    return tre, dec


def episode_totals(g):
    """Total delivered insulin in the 2 h following each entry into CONFIRMED."""
    g = g.sort_values("ts_epoch")
    st = g.state.values
    ts = g.ts_epoch.values
    fd = np.nan_to_num(g.fd.values)
    entries = np.flatnonzero((st[1:] == "CONFIRMED") & (st[:-1] != "CONFIRMED")) + 1
    out = []
    for i in entries:
        j = np.searchsorted(ts, ts[i] + EPISODE_H * 3600, "right")
        out.append(float(fd[i:j].sum()))
    return [x for x in out if x > 0]


def per_user(u, tre_u, dec_u):
    smb = tre_u[tre_u.is_smb].insulin.tolist()
    man = tre_u[~tre_u.is_smb].insulin.tolist()
    conf = dec_u[dec_u.state.eq("CONFIRMED") & (dec_u.fd > 0)]
    conf_d = conf.fd.tolist()
    des = conf.desired.dropna()
    des = des[des > 0].tolist()
    med = float(np.median(smb)) if smb else 0.0
    large = [x for x in smb if x > med]                 # "obviously large", type-blind
    allb = smb + man
    tdd = float(dec_u.tdd[dec_u.tdd > 0].median()) if (dec_u.tdd > 0).any() else np.nan
    opcap = float(conf.fcap.median()) if conf.fcap.notna().any() else np.nan

    # censoring of each candidate's contributing doses, against the live cap
    def clipped(vals):
        if not vals or not np.isfinite(opcap):
            return np.nan
        return float(np.mean([v >= CLIP_TOL * opcap for v in vals]))

    return dict(
        user=u, n_manual=len(man), n_smb=len(smb), n_conf=len(conf_d), tdd=tdd, op_cap=opcap,
        # references (no announcing required)
        R1_desired_conf_p90=ac.percentile(des, 90.0) if des else np.nan,
        R2_episode_p90=ac.percentile(episode_totals(dec_u), 90.0),
        R3_op_cap=opcap,
        # candidates
        cur_manual_p90=ac.percentile(man, 90.0) if len(man) >= ac.MIN_MANUAL_BOLUS_SAMPLES else np.nan,
        cur_p95_all_smb=ac.percentile(smb, 95.0),
        conf_p90=ac.percentile(conf_d, 90.0),
        conf_p95=ac.percentile(conf_d, 95.0),
        large_p90=ac.percentile(large, 90.0),
        allbolus_p90=ac.percentile(allb, 90.0),
        tdd_over_8=tdd / 8.0, tdd_over_10=tdd / 10.0,
        # censoring
        clip_conf_p90=clipped([v for v in conf_d if v >= ac.percentile(conf_d, 85.0)]),
        clip_p95_smb=clipped([v for v in smb if v >= ac.percentile(smb, 90.0)]))


CANDIDATES = ["cur_manual_p90", "cur_p95_all_smb", "conf_p90", "conf_p95",
              "large_p90", "allbolus_p90", "tdd_over_8", "tdd_over_10"]
REFS = ["R1_desired_conf_p90", "R2_episode_p90", "R3_op_cap"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="ANCHOR_REPORT.md")
    a = ap.parse_args()
    tre, dec = load()
    rows = [per_user(u, tre[tre.user_id == u], dec[dec.user_id == u]) for u in USERS]
    df = pd.DataFrame(rows)

    L = []
    P = L.append
    P("# Sizing confirmedCap without manual boluses — anchor study\n")
    P("Per user, over the whole `boostv5_state` era (shadow + live). All quantities in U.\n")
    P("\n## Candidates and references\n")
    P(md(df[["user", "n_manual", "n_smb", "n_conf", "tdd", "op_cap"] + REFS + CANDIDATES], 2))
    P("\n`cur_*` are what auto-config uses today. `R1` is the uncapped shot the engine wanted; "
      "`R2` is what a whole meal cost; `R3` is the cap the user actually runs.\n")

    P("\n## How each candidate compares to the references (ratio, pooled across users)\n")
    rr = []
    for c in CANDIDATES:
        row = dict(candidate=c)
        for r in REFS:
            v = (df[c] / df[r]).replace([np.inf, -np.inf], np.nan).dropna()
            lo, hi = boot_ci(v.values)
            row[r.split("_")[0]] = f"{v.median():.2f} [{lo:.2f}, {hi:.2f}]"
        rr.append(row)
    P(md(pd.DataFrame(rr)))
    P("\nRatio 1.00 = the candidate lands on that reference. Bracketed range is a bootstrap 95% CI "
      "of the mean over users (n=8, so it is wide by construction).\n")

    P("\n## Censoring — is the candidate reading its own cap back?\n")
    P(md(df[["user", "clip_conf_p90", "clip_p95_smb"]], 3))
    P("\nShare of each candidate's top contributing doses sitting at (>=98% of) the live cap.\n")

    P("\n## The hands-free case\n")
    hf = df.sort_values("n_manual").head(2)
    P(md(hf[["user", "n_manual", "cur_manual_p90", "cur_p95_all_smb", "conf_p90",
             "R1_desired_conf_p90", "R2_episode_p90", "R3_op_cap"]], 2))
    P("\nThe users with the fewest manual boluses are where the current anchor has to fall back to "
      "`p95(all SMBs)`. Compare that column to what the engine actually delivers at a confirm.\n")

    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), a.out)
    open(out, "w").write("\n".join(L))
    df.to_csv(os.path.join(os.path.dirname(os.path.abspath(__file__)), "anchor_candidates.csv"),
              index=False)
    print(f"wrote {a.out}")


if __name__ == "__main__":
    main()
