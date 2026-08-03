#!/usr/bin/env python3
"""Faithful Python port of BoostV5AutoConfig.compute() (Kotlin, origin/dev).

Ported verbatim from
  plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSBoostV5/BoostV5AutoConfig.kt
including the rounding (Kotlin `Math.round` is half-up, NOT Python's banker's
rounding), the percentile definition (linear interpolation over the positive,
finite values only) and every clamp. Any divergence here would silently
invalidate the re-derivation replay, so `--selftest` checks the arithmetic
against hand-worked cases.

Run:  python3 boost_autoconfig.py --selftest
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

# ── constants (mirrored from the Kotlin object) ─────────────────────────────────
MIN_DAYS = 7
MIN_BG_READINGS = 1500
TBR70_TARGET = 4.0
SEV54_TARGET = 1.0
SEV54_HYPO_PRONE = 1.5
TBR70_HYPO_PRONE = 6.0
LOOKBACK_DAYS = 14
MIN_MANUAL_BOLUS_SAMPLES = 10
CUMULATIVE_CAP_MAX_U = 10.0
WELL_CONTROLLED_MAX_TBR70 = 1.5
WELL_CONTROLLED_MAX_SEV54 = 0.3

# Raise-guard thresholds (BoostV5AutoConfigApply).
TBR_RAISE_GUARD_PCT = 4.0
TBR54_RAISE_GUARD_PCT = 1.0


def _round(x: float, dp: int) -> float:
    """Kotlin Math.round semantics: half-UP for positive values."""
    f = 10.0 ** dp
    return math.floor(x * f + 0.5) / f


def percentile(values, p: float) -> float:
    """Kotlin BoostV5AutoConfig.percentile — positive finite values, linear interp."""
    v = sorted(x for x in values if math.isfinite(x) and x > 0.0)
    if not v:
        return 0.0
    if len(v) == 1:
        return v[0]
    rank = (p / 100.0) * (len(v) - 1)
    lo = int(rank)
    hi = min(lo + 1, len(v) - 1)
    frac = rank - lo
    return v[lo] + (v[hi] - v[lo]) * frac


def _clamp(x, lo, hi):
    return max(lo, min(hi, x))


@dataclass
class V1Profile:
    daysWithData: int
    bgReadingCount: int
    tddMedianU: float
    manualBolusesU: list
    smbAmountsU: list
    tbrBelow70Pct: float
    timeBelow54Pct: float
    meanGlucoseMgdl: float = 0.0
    currentMaxIobU: float = 6.0
    currentMaxBolusU: float = 2.5


@dataclass
class V5Suggestion:
    aggression: float
    hypoCaution: float
    confirmedCapU: float
    committedCapU: float
    cumulativeSmbCap60MinU: float
    maxIobU: float
    bolusCapU: float
    fastCarbConfirm: bool
    aggressiveEarlyConfirm: bool
    velocityBudgetFloor: bool
    primerCapU: float
    primerTbrFallback: bool
    hypoProne: bool = False
    wellControlled: bool = False
    rationale: list = field(default_factory=list)

    def knobs(self) -> dict:
        return dict(aggression=self.aggression, hypoCaution=self.hypoCaution,
                    confirmedCap=self.confirmedCapU, committedCap=self.committedCapU,
                    cumulative60=self.cumulativeSmbCap60MinU, primerCap=self.primerCapU,
                    fastCarbConfirm=float(self.fastCarbConfirm),
                    aggressiveEarlyConfirm=float(self.aggressiveEarlyConfirm),
                    velocityBudgetFloor=float(self.velocityBudgetFloor))


def cumulative_cap_60min(confirmed: float, committed: float) -> float:
    return _round(_clamp(confirmed + 2.0 * committed, 1.0, CUMULATIVE_CAP_MAX_U), 1)


def compute(p: V1Profile):
    """Returns a V5Suggestion, or None when there isn't enough data."""
    if p.daysWithData < MIN_DAYS or p.bgReadingCount < MIN_BG_READINGS:
        return None

    hypo_prone = p.timeBelow54Pct > SEV54_HYPO_PRONE or p.tbrBelow70Pct > TBR70_HYPO_PRONE

    caution_raw = (1.0
                   + max(0.0, p.tbrBelow70Pct - TBR70_TARGET) / 4.0
                   + max(0.0, p.timeBelow54Pct - SEV54_TARGET) * 0.5)
    hypo_caution = _round(_clamp(caution_raw, 1.0, 2.0), 1)

    if hypo_prone:
        aggression = 0.85
    elif p.tbrBelow70Pct > TBR70_TARGET:
        aggression = 0.92
    else:
        aggression = 1.0
    aggression = _round(aggression, 2)

    manual_p90 = (percentile(p.manualBolusesU, 90.0)
                  if len(p.manualBolusesU) >= MIN_MANUAL_BOLUS_SAMPLES else 0.0)
    confirmed = _round(_clamp(max(manual_p90, percentile(p.smbAmountsU, 95.0)), 1.5, 7.5), 2)
    committed = _round(_clamp(max(percentile(p.smbAmountsU, 75.0), p.tddMedianU / 40.0), 0.25, 2.5), 2)
    cumulative = cumulative_cap_60min(confirmed, committed)

    max_iob = _round(_clamp(p.currentMaxIobU, 0.1, 12.0), 1)
    bolus_cap = _round(_clamp(p.currentMaxBolusU, 0.1, 10.0), 1)

    fast_carb = not hypo_prone
    well_controlled = (p.tbrBelow70Pct < WELL_CONTROLLED_MAX_TBR70
                       and p.timeBelow54Pct < WELL_CONTROLLED_MAX_SEV54)
    primer_frac = 0.25 if hypo_prone else (0.5 if well_controlled else 0.4)
    primer_cap = _round(_clamp(committed * primer_frac, 0.0, 0.6), 2)

    return V5Suggestion(
        aggression=aggression, hypoCaution=hypo_caution,
        confirmedCapU=confirmed, committedCapU=committed,
        cumulativeSmbCap60MinU=cumulative, maxIobU=max_iob, bolusCapU=bolus_cap,
        fastCarbConfirm=fast_carb, aggressiveEarlyConfirm=well_controlled,
        velocityBudgetFloor=well_controlled, primerCapU=primer_cap,
        primerTbrFallback=not well_controlled,
        hypoProne=hypo_prone, wellControlled=well_controlled)


def raise_guard_tripped(tbr70: float, sev54: float) -> bool:
    """BoostV5AutoConfigApply: dose-cap RAISES are held above these lines."""
    return tbr70 > TBR_RAISE_GUARD_PCT or sev54 >= TBR54_RAISE_GUARD_PCT


# Knobs the raise-guard governs (primerCap is deliberately NOT one — routing is its safety).
DOSE_CAP_KNOBS = ("confirmedCap", "committedCap", "cumulative60")


def _selftest():
    # percentile: Kotlin drops non-positive values, interpolates linearly
    assert percentile([1, 2, 3, 4], 50.0) == 2.5
    assert percentile([], 90.0) == 0.0
    assert percentile([0.0, -1.0, 2.0], 50.0) == 2.0     # single positive survivor
    assert abs(percentile([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 90.0) - 9.1) < 1e-9

    # half-up rounding (Python's round() would give 0.8 here)
    assert _round(0.85, 1) == 0.9
    assert _round(2.675, 2) == 2.68

    # insufficient data -> None
    assert compute(V1Profile(6, 5000, 40, [], [0.5], 2.0, 0.2)) is None
    assert compute(V1Profile(14, 1000, 40, [], [0.5], 2.0, 0.2)) is None

    # well-controlled user: neutral aggression, extras ON, primer at 0.5x committed
    s = compute(V1Profile(14, 4000, 40.0, [], [0.4] * 40, 1.0, 0.1))
    assert s.aggression == 1.0 and s.hypoCaution == 1.0
    assert s.wellControlled and s.aggressiveEarlyConfirm and s.velocityBudgetFloor
    assert s.committedCapU == 1.0                      # max(p75=0.4, 40/40=1.0)
    assert s.confirmedCapU == 1.5                      # p95=0.4 -> clamped up to floor
    assert s.cumulativeSmbCap60MinU == 3.5             # 1.5 + 2*1.0
    assert s.primerCapU == 0.5                         # 1.0 * 0.5
    assert s.fastCarbConfirm and not s.primerTbrFallback

    # hypo-prone user: eased aggression, caution up, extras OFF, primer 0.25x
    s = compute(V1Profile(14, 4000, 40.0, [], [0.4] * 40, 8.0, 2.0))
    assert s.aggression == 0.85 and s.hypoProne
    assert s.hypoCaution == 2.0                        # 1 + 4/4 + 1.0*0.5 -> clamped
    assert not s.fastCarbConfirm and not s.aggressiveEarlyConfirm
    assert s.primerCapU == 0.25 and s.primerTbrFallback

    # mid user: 0.92 band, extras off, primer 0.4x
    s = compute(V1Profile(14, 4000, 40.0, [], [0.4] * 40, 5.0, 0.5))
    assert s.aggression == 0.92 and not s.hypoProne and not s.wellControlled
    assert s.hypoCaution == 1.3 and s.primerCapU == 0.4   # 1 + (5-4)/4 = 1.25, half-UP -> 1.3

    # manual-bolus p90 only participates with n >= 10
    few = compute(V1Profile(14, 4000, 40.0, [8.0] * 4, [0.4] * 40, 1.0, 0.1))
    many = compute(V1Profile(14, 4000, 40.0, [8.0] * 10, [0.4] * 40, 1.0, 0.1))
    assert few.confirmedCapU == 1.5 and many.confirmedCapU == 7.5

    assert raise_guard_tripped(4.5, 0.0) and raise_guard_tripped(1.0, 1.0)
    assert not raise_guard_tripped(4.0, 0.99)
    print("boost_autoconfig selftest OK")


if __name__ == "__main__":
    import sys
    if "--selftest" in sys.argv:
        _selftest()
