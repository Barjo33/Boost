#!/usr/bin/env python3
"""Every knob under the chosen 28-day / 7-day re-derivation (2026-08-03).

WHY THIS EXISTS. The first pass excluded hypoCaution and aggression from periodic
re-derivation because their five-month drift did not beat their measurement noise. That
test was the wrong instrument, for two reasons raised in review:

  1. QUANTISATION. aggression takes exactly three values (0.85 / 0.92 / 1.00) and
     hypoCaution is rounded to 0.1 and clipped to [1.0, 2.0]. For a knob that coarse, both
     the "drift" and the "noise" in a drift-to-noise ratio are dominated by whether a
     threshold happened to be crossed. The ratio measures the quantiser, not whether the
     knob is tracking anything.
  2. DIRECTION. hypoCaution rising on deteriorating time-below-range is a TIGHTENING, the
     direction the design already applies unconditionally. Requiring it to clear the same
     evidential bar as a dose-cap RAISE contradicts the asymmetry used everywhere else.

So this asks a better question, per knob: **does the derived value track its own driver?**
If hypoCaution moves when and only when TBR moves, it is following signal regardless of any
drift-to-noise ratio. Drivers are structural, from the formula itself:

    aggression, hypoCaution   <- TBR<70 and time<54
    committedCap              <- TDD/40
    confirmedCap              <- manual-bolus p90 (what actually binds, 32/37 windows)
    cumulative60, primerCap   <- the caps they are computed from

Also reported: direction split (tightening vs loosening), and how each knob interacts with
the deadband it would be given — including where a deadband is WIDER than the knob's own
quantum, which would silently freeze it.

Usage:  python3 allknobs_28_7.py
"""
from __future__ import annotations

import os
import sys

import numpy as np
import pandas as pd

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import redrive_replay as rr  # noqa: E402

WINDOW, STEP = 28, 7

# measured 28-day bootstrap half-widths (REDRIVE_REPORT.md §4b)
DEADBAND = dict(aggression=0.025, hypoCaution=0.16, confirmedCap=0.47,
                committedCap=0.067, cumulative60=0.54, primerCap=0.056)
# smallest possible non-zero move, from the formula's own rounding/quantisation
QUANTUM = dict(aggression=0.07,        # 0.85 -> 0.92
               hypoCaution=0.1,        # round1
               confirmedCap=0.01, committedCap=0.01, cumulative60=0.1, primerCap=0.01)
# the column in the trajectory that drives each knob
DRIVER = dict(aggression="tbr70", hypoCaution="tbr70", committedCap="tdd40",
              confirmedCap="manual_p90", cumulative60="committedCap", primerCap="committedCap")
# expected sign of (knob change) vs (driver change). aggression is EASED DOWN as time-below-range
# rises, so its correct behaviour is anticorrelation — scoring it as +1 agreement would mark
# correct tracking as a total failure.
DRIVER_SIGN = dict(aggression=-1, hypoCaution=+1, committedCap=+1,
                   confirmedCap=+1, cumulative60=+1, primerCap=+1)
# is a DECREASE in this knob a tightening (less insulin) or a loosening?
TIGHTEN_ON_DECREASE = dict(aggression=True, confirmedCap=True, committedCap=True,
                           cumulative60=True, primerCap=True, hypoCaution=False)


def main():
    tre, cgm, dec = rr.load()
    rows, per_user = [], []
    for u in rr.USERS:
        tre_u = tre[tre.user_id == u].sort_values("ts")
        cgm_u = cgm[cgm.user_id == u].sort_values("ts")
        dec_u = dec[dec.user_id == u].sort_values("ts")
        if cgm_u.empty or tre_u.empty:
            continue
        t = rr.trajectory(u, tre_u, cgm_u, dec_u, WINDOW, STEP)
        t = t[t.derived].sort_values("t0").reset_index(drop=True)
        if len(t) < 4:
            continue
        span = (t.t1.iloc[-1] - t.t0.iloc[0]).days
        for knob in rr.KNOBS:
            v = t[knob].values.astype(float)
            d = np.diff(v)
            moved = np.abs(d) > 1e-9
            drv = t[DRIVER[knob]].values.astype(float)
            dd = np.diff(drv)
            # does the knob move WITH its driver?
            ok = np.isfinite(d) & np.isfinite(dd)
            r = np.corrcoef(d[ok], dd[ok])[0, 1] if ok.sum() > 2 and np.std(d[ok]) > 0 and np.std(dd[ok]) > 0 else np.nan
            # of the moves that happen, how many go the same way as the driver?
            both = moved & (np.abs(dd) > 1e-12)
            agree = float((np.sign(d[both]) == DRIVER_SIGN[knob] * np.sign(dd[both])).mean()) if both.any() else np.nan
            tighten = (d < 0) if TIGHTEN_ON_DECREASE[knob] else (d > 0)
            per_user.append(dict(
                user=u, knob=knob, span_days=span, changes=int(moved.sum()),
                changes_per_6mo=moved.sum() / span * 182.5 if span else np.nan,
                driver_corr=r, driver_agreement=agree,
                tightenings=int((moved & tighten).sum()),
                loosenings=int((moved & ~tighten).sum()),
                past_deadband=int((np.abs(d) > DEADBAND[knob]).sum()),
                past_deadband_per_6mo=(np.abs(d) > DEADBAND[knob]).sum() / span * 182.5 if span else np.nan,
                median_move=float(np.median(np.abs(d[moved]))) if moved.any() else 0.0))
    P_ = pd.DataFrame(per_user)

    L = []
    A = L.append
    A("# Every knob under 28-day window / 7-day cadence\n")
    A(f"{P_.user.nunique()} users, {WINDOW}d window, {STEP}d step. "
      "Supersedes the drift-to-noise screen, which was the wrong test for quantised knobs.\n")

    A("\n## Does each knob track its own driver?\n")
    rows = []
    for knob in rr.KNOBS:
        k = P_[P_.knob == knob]
        lo, hi = rr.boot_ci(k.driver_agreement.dropna().values)
        clo, chi = rr.boot_ci(k.driver_corr.dropna().values)
        rows.append(dict(
            knob=knob, driver=DRIVER[knob],
            changes_per_6mo=k.changes_per_6mo.mean(),
            expected_sign=DRIVER_SIGN[knob],
            driver_corr=k.driver_corr.mean(), corr_ci=f"[{clo:.2f}, {chi:.2f}]",
            moves_agreeing_with_driver=k.driver_agreement.mean(),
            agree_ci=f"[{lo:.2f}, {hi:.2f}]"))
    A(rr.md(pd.DataFrame(rows)))
    A("\n`driver_corr` = correlation between the knob's change and its driver's change across "
      "consecutive windows. `moves_agreeing_with_driver` = of the moves that happen, the share "
      "going the same direction as the driver — 0.5 is a coin flip, 1.0 is perfect tracking.\n")

    A("\n## Direction — is re-derivation tightening or loosening?\n")
    rows = []
    for knob in rr.KNOBS:
        k = P_[P_.knob == knob]
        tot = k.tightenings.sum() + k.loosenings.sum()
        rows.append(dict(knob=knob, tightenings=int(k.tightenings.sum()),
                         loosenings=int(k.loosenings.sum()),
                         tighten_share=k.tightenings.sum() / tot if tot else np.nan))
    A(rr.md(pd.DataFrame(rows)))
    A("\nTightenings apply unconditionally under the existing design; loosenings of a dose cap "
      "go through the TBR/<54 raise-guard. A knob that mostly tightens is mostly running on the "
      "safe side of that asymmetry.\n")

    A("\n## Deadband interaction — and where a deadband would freeze a knob\n")
    rows = []
    for knob in rr.KNOBS:
        k = P_[P_.knob == knob]
        rows.append(dict(
            knob=knob, deadband=DEADBAND[knob], quantum=QUANTUM[knob],
            deadband_exceeds_quantum=DEADBAND[knob] >= QUANTUM[knob],
            median_move=k.median_move.mean(),
            changes_per_6mo=k.changes_per_6mo.mean(),
            past_deadband_per_6mo=k.past_deadband_per_6mo.mean(),
            share_surviving=k.past_deadband.sum() / k.changes.sum() if k.changes.sum() else np.nan))
    A(rr.md(pd.DataFrame(rows)))
    A("\n**`deadband_exceeds_quantum = True` is a defect**: the deadband is wider than the "
      "smallest move the formula can make, so single-step changes can never be written and the "
      "knob is silently frozen at anything below a double step.\n")

    A("\n## Per user\n")
    A(rr.md(P_.pivot_table(index="user", columns="knob", values="changes_per_6mo").reset_index()))

    open(os.path.join(HERE, "ALLKNOBS_28_7.md"), "w").write("\n".join(L))
    P_.to_csv(os.path.join(HERE, "allknobs_28_7.csv"), index=False)
    print("wrote ALLKNOBS_28_7.md")


if __name__ == "__main__":
    main()
