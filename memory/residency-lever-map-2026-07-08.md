---
name: residency-lever-map-2026-07-08
description: "TIR-loss attribution + brake audit → the lever map. Brake is DIRECTIONALLY right (don't loosen) but the '90%' is pooled/self-dominated (13% proven + 76% assumed); real levers = activity/rescue LOWS (per-user rescue>activity) + sizing/timing HIGHS. Cause-shares are pooled — see 2026-07-10 audit."
metadata: 
  node_type: memory
  type: project
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**Settled 2026-07-08 (two backtests): where Boost's TIR loss comes from, and which levers matter.**

**Residency attribution** (segment high>180 / low<70 episodes, attribute each onset to a proximate mechanism; V6 boostv5_* data, self+A–H, ~87k cycles). Cohort:
- HIGH-time: BRAKE_SUPPRESS 34%, LATE_CONFIRM 16%, CAP_CLIP 15%, RECOVERING_HOLD 11%, UNCOVERABLE 10%, UNDERSIZED 9%, NO_MEAL_HIGH 5%.
- LOW-time: activity + rescue dominate (dosing/stacking only 16%) — **but the ranking is AGGREGATION-DEPENDENT (2026-07-10 audit): pooled = ACTIVITY 47% > RESCUE 37%, per-user-median = RESCUE 44% > ACTIVITY 36%** (pooled is driven by 2 high-activity users = 53% of low-minutes). So treat rescue-overshoot and activity as CO-EQUAL top low levers, NOT activity-first. STACKING 16%; BASAL_DRIFT 1%.
- LGBM foreseeability (grouped-by-user OOF): forward-high AUC 0.83, forward-low AUC 0.78. Foreseeable (≥1.5× base): BRAKE, NO_MEAL_HIGH. NOT foreseeable: CAP_CLIP, UNDERSIZED, UNCOVERABLE (sudden meal hits).

**Brake-correctness audit** (price the 34% by OUTCOME — oref insulinReq>0.05 & budget<0.10 & bg>170): 675min strict set (135 cycles). **"90% right" splits into 13% RIGHT_SAVEDLOW (OUTCOME-proven, a low followed) + 76% RIGHT_RESTRAINT (correct-by-ASSUMPTION = high-IOB, no forward low) + 3% (20min/6wk) WRONG_RECOVERABLE + 7% harmless.** ⚠️ 2026-07-10 audit: the 135 cycles are POOLED and SELF-DOMINATED (1 user = 51%), so "90%" is not a hard cohort number — only ~13% is outcome-proven. **DIRECTION holds (DON'T loosen the brake — most firing is defensible high-IOB restraint, some saved lows, only 3% recoverable)** but don't quote "90%." Composed floor's target is the tiny 3% — "bounded defect-fix, not a lever." Still confirms the residency's 34% was proximate over-attribution ([[recovering-highs-smb-rejected-2026-07-03]]).

**THE LEVER MAP (what to work on):**
- **LOWS (bigger addressable loss, NOT a dosing-brake problem):** activity + rescue dominate (stacking/over-dose only 16%). Ranking aggregation-dependent (pooled activity 47>rescue 37; per-user rescue 44>activity 36) → treat the Garmin HR/steps ingest + exercise protections AND rescue-overshoot handling as CO-EQUAL top low levers ([[garmin-watchface-port-2026-07-08]], [[hr-steps-review-2026-07-06]]).
- **HIGHS = SIZING + TIMING, not the brake:** CAP_CLIP 15% + UNDERSIZED 9% are NOT foreseeable (sudden meals) → per-user cap sizing + V7 distributional sizing ([[v7-design-2026-07-07]]); LATE_CONFIRM 16% IS foreseeable → confirm age-gate / score-ready ([[early-dosing-audit-2026-07-03]]).

**Cohort comparison + regime decomposition** (Boost-dosing cohort AAPS self+A–H, V1+ generation — NB **V1 IS Boost**, not oref — vs oref/Trio reference cohort U0xx, BG-level only since Trio emits no budget/steps/HR): raw TIR +2.9pp (Boost 88.1 vs oref 85.2), attenuates to +1.2pp after case-difficulty adjustment (CV/meanBG), permutation p=0.27 NS in 9-vs-21 → suggestive, underpowered, population-level. **BUT the regime decomposition is the real story: the edge is ENTIRELY overnight** — overnight (00–06 local) TIR gap **+13.3pp** (fewer lows −4.4 AND fewer highs −9.1); daytime +0.3pp. Two cohorts run anti-phase: Boost best overnight (7/9 users), oref worst overnight (13/21 — unhandled dawn); **oref BEATS Boost post-breakfast ~09–13 (−4 to −7pp)** = Boost's meal-handling deficit. Time-specific structure ⇒ mechanism signature, not selection. **CONFIRMS the lever map: overnight machinery (night mode) is Boost's validated STRENGTH; post-breakfast meal sizing/timing (LATE_CONFIRM+UNDERSIZED) is the daytime lever where Boost trails oref.** Artifacts: cohort_bglevel.py/cohort_dig.py/cohort_regime.py + reports/PNGs in backtesting/2026-07-residency/.

**Caveats:** proximate≠causal (the brake audit exists BECAUSE of this); decision-tree ordering inflates upstream causes; `boostv5_actionmult` is only the per-state multiplier (0.3/0.4/1.0/1.8, not the brake); `boostv5_floorwouldadd` is 100% NULL in historical rows (floor logged only post-ship — can't price the floor directly from history).

Sits under [[two-test-bar-2026-07-06]]. Artifacts (both branches, experimental + v7-shadow): `backtesting/scripts/2026-07-residency/` — residency_attribution.py, residency_ml.py, residency_chart.py (+RESIDENCY_REPORT.md + PNG), brake_audit.py (+BRAKE_AUDIT_REPORT.md).
