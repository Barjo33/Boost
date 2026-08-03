#!/usr/bin/env python3
"""Pricing a re-anchored confirmedCap (2026-08-03).

The anchor studies say `confirmedCap = max(p90 manual bolus, p95 all SMBs)` is
mis-specified three ways over. This prices the replacement before anyone builds it —
the relative half of the two-test bar. No counterfactual BG is claimed anywhere.

METHOD. The dose chain is logged stage by stage (commit a78bf7bb95):

    raw = budget x actionMult x velocityFactor  ->  min(raw, stateCap) = doseAfterCaps
      ->  x brake stack = doseAfterBrakes  ->  composed floor = finalDose

so a different cap can be propagated faithfully through the *observed* brake behaviour:

    newAfterCaps = min(raw, capNew);  brakeFactor = doseAfterBrakes / doseAfterCaps
    newFinal     = newAfterCaps x brakeFactor      (floor uplift left untouched)

TWO POLICIES, because whether this ADDS insulin or only MOVES it depends on what happens
to the rolling-60-min budget:

    LEVEL CHANGE  cumulativeCap60 rises with the per-shot cap (today's apply layer
                  recomputes it as confirmedCap + 2 x committedCap), so the extra at the
                  confirm is genuinely new insulin.
    SHAPE CHANGE  cumulativeCap60 is HELD at its current value, so a cycle may only take
                  what is left in the hour after what was actually delivered. The confirm
                  shot grows; the hour's total cannot.

Both are reported. The distinction is not cosmetic: the early-dosing audit priced MOVED
insulin as harm-neutral and NEW insulin at +15pp, and the episode-total result (V6 costs
0.88x V1 per meal) says a meal's insulin is roughly conserved across the architectures.

Usage:  python3 reanchor_pricing.py
"""
from __future__ import annotations

import os
import sys

import numpy as np
import pandas as pd
import psycopg2

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import boost_autoconfig as ac  # noqa: E402
from redrive_replay import DSN, USERS, LowLookup, boot_ci, md  # noqa: E402

EPISODE_H = 2.0
VARIANTS = {"TDD/12": 12.0, "TDD/10": 10.0, "TDD/8": 8.0}


def load():
    conn = psycopg2.connect(DSN)
    dec = pd.read_sql("""
        SELECT DISTINCT ON (user_id, floor(ts_epoch/300.0))
          user_id, ts_utc, ts_epoch, tdd, boostv5_state AS state, boostv5_active AS active,
          boostv5_finaldose AS fd, boostv5_confirmedcap AS fcap, boostv5_committedcap AS ccap,
          boostv5_doseaftercaps AS dac, boostv5_doseafterbrakes AS dab,
          boostv5_cumulativecapu AS cap60, boostv5_smbvol60min AS vol60,
          boostv5_budget * boostv5_actionmult * boostv5_velocityfactor AS raw
        FROM boost_decisions
        WHERE boostv5_state IS NOT NULL AND user_id = ANY(%s)
        ORDER BY user_id, floor(ts_epoch/300.0), ts_epoch DESC""", conn, params=(USERS,))
    cgm = pd.read_sql("""
        SELECT user_id, ts_utc, cgm_mgdl FROM boost_cgm
        WHERE cgm_mgdl > 1 AND user_id = ANY(%s)""", conn, params=(USERS,))
    conn.close()
    for d in (dec, cgm):
        d["ts"] = pd.to_datetime(d.ts_utc, utc=True)
    return dec, cgm


def episode_id(g):
    """Label contiguous dosing runs (>=45 min gap starts a new episode)."""
    ts = g.ts_epoch.values
    return np.concatenate([[0], np.cumsum(np.diff(ts) >= 45 * 60)])


def price_user(u, dec_u, cgm_u):
    """One row per variant for user u, plus the engagement/TBR context."""
    d = dec_u[dec_u.raw.notna() & dec_u.dac.notna() & dec_u.dab.notna()].copy()
    if d.empty:
        return []
    d = d.sort_values("ts_epoch")
    conf = d.state.eq("CONFIRMED")
    tdd = float(d.tdd[d.tdd > 0].median()) if (d.tdd > 0).any() else np.nan
    if not np.isfinite(tdd):
        return []
    cap_old = d.fcap.where(conf)                     # per-cycle operative confirm cap
    brake = np.where(d.dac > 0, d.dab / d.dac, 1.0)  # observed brake attenuation

    bg = cgm_u[(cgm_u.ts >= d.ts.min()) & (cgm_u.ts <= d.ts.max() + pd.Timedelta(hours=4))]
    low = LowLookup(bg.sort_values("ts"))
    dosed = d.fd > 0
    base_pre = low.any_low(d.ts.values[dosed].astype("datetime64[ns]").astype("int64"))
    baseline = float(d.fd.values[dosed][base_pre].sum() / d.fd.values[dosed].sum())

    n = len(bg)
    tbr70 = 100.0 * float((bg.cgm_mgdl < 70).sum()) / n if n else np.nan
    sev54 = 100.0 * float((bg.cgm_mgdl < 54).sum()) / n if n else np.nan

    # episode structure, for the full-offset bound
    d = d.assign(ep=episode_id(d), brake=brake)
    out = []
    for name, div in VARIANTS.items():
        cap_new = float(np.clip(tdd / div, 1.5, 7.5))
        new_after_caps = np.where(conf, np.minimum(d.raw, cap_new), d.dac)
        new_final = np.where(conf, new_after_caps * d.brake, d.fd)
        delta = np.maximum(new_final - d.fd.fillna(0).values, 0.0)   # raises only
        added_upper = float(delta.sum())

        # SHAPE-ONLY variant: raise the per-shot cap but HOLD the rolling-60-min budget at
        # its current value. The extra a cycle may take is whatever is left in the hour after
        # what was actually delivered — so the confirm shot gets bigger while the hour's total
        # cannot. cap60 == 0 means the cumulative cap is disabled for that cycle (no limit).
        cap60 = d.cap60.fillna(0.0).values
        vol60 = d.vol60.fillna(0.0).values
        headroom = np.where(cap60 > 0,
                            np.maximum(cap60 - vol60 - d.fd.fillna(0).values, 0.0),
                            np.inf)
        delta_held = np.minimum(delta, headroom)
        added_held = float(delta_held.sum())

        hit = delta > 1e-9
        pre = low.any_low(d.ts.values[hit].astype("datetime64[ns]").astype("int64")) if hit.any() \
            else np.array([], bool)
        pre_share = float(delta[hit][pre].sum() / delta[hit].sum()) if hit.any() else np.nan
        days = (d.ts.max() - d.ts.min()).total_seconds() / 86400
        out.append(dict(
            user=u, variant=name, tdd=tdd, cap_old=float(cap_old.median()), cap_new=cap_new,
            days=days, n_confirm=int(conf.sum()), n_raised=int(hit.sum()),
            clip_rate_old=float((d.dac[conf] >= 0.98 * cap_old[conf]).mean()),
            added_U_upper=added_upper, added_U_per_day=added_upper / days if days else np.nan,
            added_pct_tdd=100.0 * added_upper / (tdd * days) if days else np.nan,
            added_U_held=added_held, held_U_per_day=added_held / days if days else np.nan,
            held_pct_tdd=100.0 * added_held / (tdd * days) if days else np.nan,
            shape_only_share=added_held / added_upper if added_upper > 1e-9 else np.nan,
            prelow_share_added=pre_share, prelow_share_baseline=baseline,
            prelow_delta=pre_share - baseline if np.isfinite(pre_share) else np.nan,
            tbr70=tbr70, sev54=sev54,
            raise_guard=("BLOCKED" if ac.raise_guard_tripped(tbr70, sev54) else "allowed"),
            direction=("raise" if cap_new > float(cap_old.median()) else "lower")))
    return out


def main():
    dec, cgm = load()
    rows = []
    for u in USERS:
        rows += price_user(u, dec[dec.user_id == u], cgm[cgm.user_id == u])
    df = pd.DataFrame(rows)

    L = []
    P = L.append
    P("# Pricing a re-anchored confirmedCap\n")
    P("Dose-chain propagation through the observed brake behaviour; window is the "
      "`velocityFactor` telemetry era (from 2026-07-10). Raises only — the arithmetic never "
      "reduces a delivered dose.\n")

    for name in VARIANTS:
        g = df[df.variant == name]
        P(f"\n## {name}\n")
        P(md(g[["user", "tdd", "cap_old", "cap_new", "direction", "days", "n_confirm",
                "n_raised", "clip_rate_old", "added_U_per_day", "added_pct_tdd",
                "held_U_per_day", "held_pct_tdd", "prelow_share_added",
                "prelow_share_baseline", "tbr70", "sev54", "raise_guard"]], 2))
        allowed = g[g.raise_guard.eq("allowed") & g.direction.eq("raise")]
        if len(allowed):
            lo, hi = boot_ci(allowed.prelow_delta.dropna().values)
            P(f"\nAmong users the raise-guard would actually let through (n={len(allowed)}): "
              f"added insulin {allowed.added_U_per_day.mean():.2f} U/day "
              f"({allowed.added_pct_tdd.mean():.1f}% of TDD); the added insulin's pre-low share "
              f"minus baseline = {allowed.prelow_delta.mean():+.3f} "
              f"[{lo:+.3f}, {hi:+.3f}]. Positive = the extra insulin lands disproportionately "
              f"before real lows.\n")
        held = g[g.direction.eq("raise")]
        if len(held):
            P(f"\nHolding the rolling-60-min budget at its CURRENT value instead of letting it "
              f"rise with the cap turns the change into a pure redistribution: added insulin drops "
              f"from {held.added_U_per_day.mean():.2f} to {held.held_U_per_day.mean():.2f} U/day "
              f"across the raise users ({held.held_pct_tdd.mean():.1f}% of TDD), i.e. "
              f"{100 * (1 - held.held_U_per_day.sum() / max(held.added_U_per_day.sum(), 1e-9)):.0f}% "
              f"of the extra is absorbed by the existing hourly budget.\n")
        else:
            P("\n_No user is both a raise and past the raise-guard._\n")

    P("\n## Who the change actually reaches\n")
    piv = df.pivot_table(index="user", columns="variant", values="cap_new")
    piv["cap_old"] = df.groupby("user").cap_old.first()
    piv["raise_guard"] = df.groupby("user").raise_guard.first()
    piv["clip_rate_old"] = df.groupby("user").clip_rate_old.first()
    P(md(piv.reset_index(), 2))
    P("\n`clip_rate_old` is how often the confirm shot is currently pinned at the cap — the "
      "engagement rate. A re-anchor does nothing for a user whose cap never binds.\n")

    here = os.path.dirname(os.path.abspath(__file__))
    open(os.path.join(here, "REANCHOR_PRICING.md"), "w").write("\n".join(L))
    df.to_csv(os.path.join(here, "reanchor_pricing.csv"), index=False)
    print("wrote REANCHOR_PRICING.md")


if __name__ == "__main__":
    main()
