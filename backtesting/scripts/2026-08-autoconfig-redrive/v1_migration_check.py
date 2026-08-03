#!/usr/bin/env python3
"""What does auto-config actually inherit from the user's PREVIOUS Boost? (2026-08-03)

Correction to the main study's framing. Every user in this cohort was already running
Boost before V6 — V1 IS Boost, not oref. So the "pre-migration" windows are not an
exogenous baseline: those SMBs are Boost's own output, clipped by V1's own dose ceilings.
The censoring concern spans the whole record, not just the V6 era.

That raises the question this script answers: the meal doses V1 created ARE in the
telemetry (tiered: UAM_BOOST / UAM_HIGH_BOOST / PERCENT_SCALE / ACCELERATION vs plain
REGULAR_OREF1). How much of that does auto-config's `p95(all SMBs)` actually see, and is
a V1-era per-shot distribution even the right thing to size a V6 per-shot cap from?

The architectures differ: V1 spreads a meal response across many moderate SMBs; V6
concentrates it into one CONFIRMED shot plus holds. So the migration hands V6 a cap sized
for distributed dosing and applies it to concentrated dosing.

Usage:  python3 v1_migration_check.py
"""
from __future__ import annotations

import os
import sys

import numpy as np
import pandas as pd
import psycopg2

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import boost_autoconfig as ac  # noqa: E402
from redrive_replay import DSN, USERS, boot_ci, md  # noqa: E402

# V1's meal-response tiers (everything above plain oref1 SMB behaviour).
BOOSTED_TIERS = ("UAM_BOOST", "UAM_HIGH_BOOST", "PERCENT_SCALE", "ACCELERATION")


def load():
    conn = psycopg2.connect(DSN)
    v1 = pd.read_sql("""
        SELECT DISTINCT ON (user_id, floor(ts_epoch/300.0))
          user_id, ts_utc, ts_epoch, v1_units AS u, boost_tier_top AS tier, tdd
        FROM boost_decisions
        WHERE boostv5_state IS NULL AND v1_units > 0 AND user_id = ANY(%s)
        ORDER BY user_id, floor(ts_epoch/300.0), ts_epoch DESC""", conn, params=(USERS,))
    v6 = pd.read_sql("""
        SELECT DISTINCT ON (user_id, floor(ts_epoch/300.0))
          user_id, ts_epoch, boostv5_state AS state, boostv5_finaldose AS fd,
          boostv5_confirmedcap AS fcap,
          boostv5_budget * boostv5_actionmult * boostv5_velocityfactor AS desired
        FROM boost_decisions
        WHERE boostv5_state IS NOT NULL AND user_id = ANY(%s)
        ORDER BY user_id, floor(ts_epoch/300.0), ts_epoch DESC""", conn, params=(USERS,))
    conn.close()
    return v1, v6


def meal_episodes(g, dose_col, gap_min=45):
    """Total dose per contiguous dosing episode (a gap >= gap_min starts a new one).

    V1 has no CONFIRMED state to key on, so a meal response is recovered as a run of
    doses. The same rule is applied to V6 so the two are compared like for like.
    """
    g = g.sort_values("ts_epoch")
    ts = g.ts_epoch.values
    u = g[dose_col].values
    if len(ts) == 0:
        return []
    brk = np.flatnonzero(np.diff(ts) >= gap_min * 60) + 1
    return [float(x.sum()) for x in np.split(u, brk) if x.sum() > 0]


def main():
    v1, v6 = load()
    rows = []
    for u in USERS:
        a = v1[v1.user_id == u]
        b = v6[v6.user_id == u]
        if a.empty or b.empty:
            continue
        boosted = a.u[a.tier.isin(BOOSTED_TIERS)].tolist()
        conf = b[b.state.eq("CONFIRMED") & (b.fd > 0)]
        des = conf.desired.dropna()
        des = des[des > 0].tolist()
        tdd = float(a.tdd[a.tdd > 0].median()) if (a.tdd > 0).any() else np.nan
        rows.append(dict(
            user=u, n_v1=len(a), n_boosted=len(boosted),
            v1_p95_all=ac.percentile(a.u.tolist(), 95.0),
            v1_p90_boosted=ac.percentile(boosted, 90.0) if boosted else np.nan,
            v1_episode_p90=ac.percentile(meal_episodes(a, "u"), 90.0),
            v6_conf_p90=ac.percentile(conf.fd.tolist(), 90.0),
            v6_desired_p90=ac.percentile(des, 90.0) if des else np.nan,
            v6_episode_p90=ac.percentile(meal_episodes(b[b.fd > 0], "fd"), 90.0),
            tdd_over_10=tdd / 10.0 if np.isfinite(tdd) else np.nan))
    df = pd.DataFrame(rows)
    df["tier_visibility"] = df.v1_p90_boosted / df.v1_p95_all
    df["concentration_delivered"] = df.v6_conf_p90 / df.v1_p95_all
    df["concentration_uncapped"] = df.v6_desired_p90 / df.v1_p95_all
    df["episode_ratio"] = df.v6_episode_p90 / df.v1_episode_p90
    df["tdd10_over_desired"] = df.tdd_over_10 / df.v6_desired_p90

    L = []
    P = L.append
    P("# What auto-config inherits from the user's previous Boost\n")
    P("Every cohort user ran Boost before V6 (V1 IS Boost). V1's meal responses are tiered in "
      f"telemetry: {', '.join(BOOSTED_TIERS)} vs plain REGULAR_OREF1.\n")
    P("\n## Per-shot and per-episode sizing, V1 era vs V6 era\n")
    P(md(df[["user", "n_v1", "n_boosted", "v1_p95_all", "v1_p90_boosted", "v1_episode_p90",
             "v6_conf_p90", "v6_desired_p90", "v6_episode_p90", "tdd_over_10"]], 2))

    P("\n## Ratios\n")
    for lbl, col, note in (
        ("tier visibility", "tier_visibility",
         "p90(V1 meal-tier shots) / p95(all V1 shots) — how much bigger V1's meal shots are "
         "than the blind percentile auto-config takes"),
        ("concentration (delivered)", "concentration_delivered",
         "p90(V6 CONFIRMED shot) / p95(all V1 shots) — V6 is censored by its own cap, so this "
         "is a LOWER bound"),
        ("concentration (uncapped)", "concentration_uncapped",
         "p90(V6 desired confirm shot) / p95(all V1 shots) — the honest figure"),
        ("episode totals", "episode_ratio",
         "p90(V6 meal-episode total) / p90(V1 meal-episode total) — does a meal cost the same "
         "under both architectures?"),
        ("TDD/10 vs need", "tdd10_over_desired",
         "TDD/10 / p90(V6 desired confirm shot) — cross-user stability of the TDD anchor"),
    ):
        v = df[col].replace([np.inf, -np.inf], np.nan).dropna()
        lo, hi = boot_ci(v.values)
        P(f"- **{lbl}**: median {v.median():.2f}, range {v.min():.2f}–{v.max():.2f}, "
          f"mean 95% CI [{lo:.2f}, {hi:.2f}]  \n  _{note}_")

    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "V1_MIGRATION_REPORT.md")
    open(out, "w").write("\n".join(L))
    df.to_csv(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                           "v1_migration.csv"), index=False)
    print("wrote V1_MIGRATION_REPORT.md")


if __name__ == "__main__":
    main()
