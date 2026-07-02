---
name: boost-mlhyporisk-usage-2026-07-02
description: "How mlHypoRiskModel is wired in dev Boost (V1 engine + V6/V5), and the key insight that it's a HYPO-directional damper that cannot prevent over-dosing during a rise — plus the claude/sleep-in-function-review-4zs33i fix branch."
metadata: 
  node_type: memory
  type: project
  originSessionId: d9e18601-4d49-42cd-9289-f1b24cf4e999
---

**mlHypoRiskModel wiring in dev Boost (reviewed 2026-07-02):**
- Asset `app/src/main/assets/boost/hypo_risk_model.json` = v12, **53 features** (17 static + 36 windowed lag0..5), GBM 100 trees, sigmoid over summed leaf margins. Loaded lazily by `BoostRiskModel` (injected into `OpenAPSBoostPlugin` as `boostRiskModel`, passed to `DetermineBasalBoost` at the `riskModel=` call). Feature vector built by `BoostMlFeatureBuilder` + a persisted 6-cycle ring buffer (`StringKey.ApsBoostMlRingBuffer`). Legacy 8-feature path kept for rollback.
- **V1 engine (`DetermineBasalBoost`)** computes `mlHypoRisk` and APPLIES it two ways: (a) `riskScale` = linear damper 1.0→0.0 across risk 0.3→1.0, multiplies `microBolus`; (b) `mlTierDowngrade` at risk>0.6 gates out aggressive tiers T3–T6. NOTE: the in-code "Layer A — observability only / dosing unchanged" comments are **stale/misleading** — Layer B IS wired and live.
- **V6/V5 active path** reads `mlHypoRisk = rT.mlHypoRisk` (from V1's RT) in `OpenAPSBoostV5Plugin`. When V6 is ACTIVE it REPLACES V1's `microBolus` with `v5decision.finalDose`, so V1's riskScale/tierDowngrade are DISCARDED. V6's own damper = `mlHypoRiskScale` in `AggressionBudget.kt` (floor 0.50/hypoCautionKnob), multiplied into the budget. So **no double-braking**: V5 scales `baseInsulinReq` (= rT.insulinReq, un-scaled) while V1's scale hit the discarded microBolus.
- **AggressionBudget floor:** `budget = max(BUDGET_FLOOR_FRACTION×baseInsulinReq, baseInsulinReq×mlScale×...)`. So mlHypoRiskScale is a **soft** meal-aggression damper (can't zero the dose); real hypo cutoff comes from minGuardBG / SafetyGates, not this.

**KEY INSIGHT:** the model predicts **impending HYPO**, so during a meal/dawn RISE its output is ~low → `mlScale ≈ 1.0` → **no damping**. It therefore does NOT and cannot prevent over-dosing during a rise — neither the 2026-07-01 overnight 1.55U-while-asleep incident nor the eventualBG→372 runaway. Those are context/hyper problems; the correct fixes were the sleep-in/boostActive gate and the confirm dose-adequacy gate, NOT the ML model.

**Fix branch `claude/sleep-in-function-review-4zs33i`** (2 commits on top of dev @ 9740d4c431):
1. `c883864df9` — gate the V6/V5 SMB override on `activityResult.boostActive`; surface a step-based `sleepInActive` (independent of boostActive) → cached → makes `isNightModeActiveImpl()` also enable night mode during a lie-in. Night mode's `bgCurrent < profileTarget+bgOffset` guard means a genuine high is still corrected. One-cycle lag on the cache is documented + safe.
2. `803704d1a9` (Option A) — gate OBSERVING→CONFIRMED on `confirmDoseAdequate = baseInsulinReq >= CONFIRM_MIN_INSULINREQ_FRAC_OF_MAXIOB(0.10)×maxIob` so the single per-session CONFIRMED token isn't spent on a trivial pre-meal upswing. Fast-carb fast-path exempt. Has a unit test (`MealHypothesisDoseGateTest`).

Watch-items: (a) V6 now silent OUTSIDE the boost time window (confirm window covers intended dosing hours); (b) 0.10×maxIob ties the confirm bar to a SAFETY ceiling, not meal size — high-maxIob users get a harder confirm (author flags "recalibrate"); (c) no unit test for commit 1's plugin changes. See [[boost-v6-experimental-state-2026-06-27]], [[feedback-boost-v6-branch-workflow]].
