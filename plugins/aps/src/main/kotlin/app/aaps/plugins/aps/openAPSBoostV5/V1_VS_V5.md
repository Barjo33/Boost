# Boost V1 vs Boost V5

A side-by-side reference for users and maintainers comparing the original Boost
algorithm (V1, plugin `openAPSBoost`, internal short name `BOOST`) and the
clean-slate V5 redesign (plugin `openAPSBoostV5`, short name `V5`).

This document is an **architectural** comparison. V5 is a complete rethink of
how Boost makes a dosing decision, not an incremental fork. V1, V2, V3, V3ML,
V3MLG3 (V4.4.1) all share the 8-tier ladder; V5 is the first algorithm in the
Boost family to abandon that structure entirely.

For the V4.4.1 → V5 mapping (every V4-era mechanism's V5 home, including the
G3 hold and meal-likelihood model that V4.4.1 added on top of V1), see
`MIGRATION.md` in this directory. For the full architectural rationale, see
`boost_v5_redesign_proposal.md` in claude memory.

**Reference comparator**: V1 facts in this document are taken from the V1
plugin in `~/StudioProjects/AndroidAPS/master`'s `openAPSBoost/` directory —
the canonical V1 reference. (The `openAPSBoost/` directory in
`Boost-AAPS-core` carries some additional ML-risk integration that was
back-ported during V3ML development; that is not the canonical V1.)

V1 here HAS: the 8-tier ladder, the same `1800/(TDD·ln)` ISF formula V4.4.1
uses, the `delta_accl` denominator floor, the Tier 7 IOB cap, the Tier 8
spike override, fast-carb rebound brake, hypo-rebound TT auto-cancel,
post-exercise recovery window, and `dynISFvelocity` as a user-tunable knob.

V1 specifically does NOT have: any ML model integration (no `mlHypoRisk`,
no meal-likelihood model), the `mlTierDowngrade` brake, the `mlRiskScale`
brake, the G3 pre-UAM hold, or the post-SMB risk gate. Those are all V3ML+
additions.


## At a glance

|  | **V1 (Boost)** | **V5 (Boost V5)** |
|---|---|---|
| Architecture | 8-tier if-else ladder + modulators (fast-carb scale, spike override, Tier 7 IOB cap) | 3-phase pipeline (state estimation → single decision → ordered safety gates) |
| Lines of Kotlin (algorithm core) | ~1,330 | ~620 across 5 files |
| Meal recognition | Implicit — emerges from which tier the if-else picks | Explicit — `MealHypothesis` state machine, persisted across cycles |
| Decision rule | 8 separate dose formulas, one per tier | One: `aggression_budget × meal_action_multiplier(state)` |
| Safety brakes | Multiplicative (`tier_dose × fastCarbScale` on Tiers 3/5/6) — no overall floor | One AggressionBudget chain (2 multipliers, 30% hard floor); ordered Phase 3 gates |
| User-facing settings (Boost-specific knobs) | ~30+ user-tunable parameters (62 preference entries in the V1 plugin's PreferenceScreen) | **3** (Aggression, Hypo Caution, Sensitivity-reserved) |
| Internal hardcoded constants | Distributed across the if-else thresholds; per-user calibration left to users | ~14–15, calibrated once via backtest at release; frozen |
| State persisted across cycles | None (with one exception: `recoveryWindowEnd` and `wasExerciseActive` for post-exercise tracking — no per-cycle algorithm state) | `mealHypothesis`, `mealHypothesisAge`, `mlMealLikelyNullStreak` |
| ISF / sensitivity formula | `1800 / (TDD × ln(target/insulinDivisor + 1))` — DynISF V1 | **Same calculation** (V5 reads `baseInsulinReq` from the Boost-flavoured oref pipeline; no own sensitivity logic) |
| `dynISFvelocity` | **User-configurable** in V1 (default ~0.5–0.8) — affects ISF responsiveness to BG | Inherited from whatever the user has set; V5 doesn't override |
| ML hypo-risk integration | **None** — V1 doesn't run any ML model | Single graduated `mlHypoRiskScale` damper in AggressionBudget. V5 reads `mlHypoRisk` predictions from V4.4.1 in shadow mode (when V4.4.1 is the active APS); when V5 graduates to alpha it would inject and run `BoostRiskModel` itself |
| Meal-likelihood ML | **None** in V1 | 0.20-weight component of continuous `meal_signal_score` (read from V4.4.1's `mlMealLikely` in shadow mode; V5 needs `BoostMealModel` injected when active) |
| Exercise handling | Profile% + target compression + post-exercise recovery window + HR/step fusion | Modes 1–6 pass through `baseInsulinReq` (target / profile% feed it); explicit `postExerciseRecoveryModifier` for the boost-bolus reduction effect that doesn't go through `baseInsulinReq` |


## Architecture

### V1 — 8-tier if-else ladder

For each cycle, V1 evaluates conditions for 8 tiers in order. The first matching
tier sets the SMB amount. Tiers (V1 source `DetermineBasalBoost.kt:1192+`):

```
Tier 1  COB_PRIMARY      lastCarbAge < 25 min, COB > 0
Tier 2  COB_SECONDARY    lastCarbAge < 40 min, delta > 5
Tier 3  UAM_BOOST        delta ≥ 5, shortAvgDelta ≥ 3, uamBoost1 > 1.2, uamBoost2 > 2
Tier 4  UAM_HIGH_BOOST   delta_accl > 5, BG > 180
Tier 5  PERCENT_SCALE    BG 110–180, delta > 3, delta_accl > 0
Tier 6  ACCELERATION     delta_accl > 25, delta > 4, BG > 110
Tier 7  ENHANCED_OREF1   delta > 0, delta_accl ≥ 0.5  (with IOB cap: insulinReq capped at maxIOB - currentIOB)
Tier 8  REGULAR_OREF1    fallback (with spike override: raises cap when BG > 180 + delta > 5 + high insulinReq)
```

After tier selection, V1 runs **modulators**:

- `fastCarbScale` — graduated 0.3–1.0 brake on Tiers 3/5/6 when `recentLowBG < 100` or post-hypo reversal score positive
- Spike override — raises Tier 8's cap on confirmed spikes
- Hypo-rebound TT auto-cancel — cancels recovery TempTarget when BG climbs back from a low

The result: dose comes from one of 8 formulas; on Tiers 3/5/6 it may be
multiplied by `fastCarbScale` (0.3–1.0). On Tier 7 the IOB cap clamps before
the formula. On Tier 8 the spike override may raise the cap.

### V5 — 3-phase pipeline

Each cycle goes through three phases in strict order:

```
PHASE 1 — STATE ESTIMATION (no commitment)
  meal_signal_score(...)                — continuous 0–1 weighted combination of 6 signals
  mealHypothesis state machine step()   — 5 states: IDLE / OBSERVING / CONFIRMED / COMMITTED / RECOVERING
  aggressionBudget(...)                 — baseInsulinReq × mlHypoRiskScale × postExerciseRecoveryModifier
                                          floored at 0.30 × baseInsulinReq

PHASE 2 — DECISION (single rule)
  insulin_to_deliver = aggression_budget × meal_action_multiplier(state)

PHASE 3 — SAFETY GATES (ordered)
  HARD GATES (binary, short-circuit to 0):
    enableSmbPreChecks → minGuardBG → maxDelta → maxIOB clamp
  SOFT GATES (multiplicative, ordered):
    iobHeadroomBrake → postActionRiskCheck → decelerationBrake → sensorQualityCheck
  FINAL CLAMP:
    round(roundSMBTo) → dynamicSpikeCap → max(0)
```

Each function is pure (no side effects, no globals); the only state carried
across cycles is the persisted `mealHypothesis` + age + null-streak counter.


## Meal recognition

| | **V1** | **V5** |
|---|---|---|
| When does the algorithm "know it's a meal"? | Never explicitly — implied by Tiers 1–6 selecting | Explicit transition to `CONFIRMED` state requires score ≥ 0.66 + eventualBG > target+50 + 2 cycles in OBSERVING |
| What if a rise just-misses the binary thresholds? | Falls through to Tier 7 or 8 — slower-acting fallback | Score is continuous; no binary cliff. Same input pattern reaches CONFIRMED through accumulated score weight |
| Recovery / back-off | Implicit — when delta drops, Tier 5/6 conditions fail and dose drops naturally. Can flicker if delta dips mid-rise | Explicit `COMMITTED → RECOVERING` transition requires `delta_accl < -5 AND deltaDeclining(2)`. State is sticky — single-cycle delta noise doesn't flicker |
| Tim's "test then commit" intent | Not encoded — V1 dose at OBSERVING-equivalent cycles is the same as at CONFIRMED-equivalent cycles | OBSERVING multiplier `0.3×` (test dose) → CONFIRMED `1.8×` (commit) → COMMITTED `1.0×` (sustain) |
| Use of ML for meal recognition | None — V1 has no ML | Continuous `mlMealLikely` (0.20-weight component of meal_signal_score); when V5 is shadowing V4.4.1, this is V4.4.1's prediction; when V5 graduates to alpha it runs its own `BoostMealModel` |


## Sensitivity (ISF / DynISF / TDD / EMA)

V1 and V5 use the same Boost-flavoured ISF stack — V5 doesn't reimplement it.

| Component | V1 | V5 |
|---|---|---|
| DynISF formula | `1800 / (TDD × ln(target/insulinDivisor + 1))` | **same** — read through `baseInsulinReq` |
| TDD source | 7-day total daily dose with 8-hour pull-down rule | **same** |
| EMA sensitivity ratio | `EMA τ=3h on tdd_24h/tdd_7d` (multiplier on sensNormalTarget) | **same** |
| Autosens | AAPS standard, applied per-cycle | **same** |
| Hour-of-day basal rates | Profile-driven | **same** |
| TempTargets | Standard AAPS | **same** — feeds the target term in `(eventualBG - target) / sens` |
| `dynISFvelocity` setting | **User-configurable in V1** (default ~0.5–0.8) — controls ISF responsiveness to BG changes | Inherited from the user's V1 / V4.4.1 setting; V5 doesn't override |
| `delta_accl` denominator floor | `max(|shortAvgDelta|, 2.0)` (V1 line 257) | **same** — inherited verbatim |

**V5 contains zero sensitivity logic of its own.** Sensitivity is owned by the
existing Boost pipeline (which V1 already runs). V5 reads the resulting
`baseInsulinReq` and applies meal/risk multipliers on top.


## User-facing settings

V1 exposes ~30+ Boost-specific user-tunable knobs (62 preference entries total
in the V1 plugin's PreferenceScreen). V5 exposes **3**.

### V1 settings the user can tune (selected, not exhaustive)

- `boost_bolus_cap` (max single SMB)
- `boost_max_iob`
- `boost_insulin_req_pct` (divisor for insulinReq → SMB)
- `boost_scale` (multiplier on Boost insulin requirement)
- `boost_percent_scale_factor` (sliding scale BG 108→180)
- `boost_dynisf_velocity` (ISF responsiveness)
- `boost_start_time` / `boost_end_time` (active time window)
- `boost_sleep_in_hrs`, `boost_inactivity_pct`, `boost_activity_pct`
- `boost_post_exercise_recovery_hours`, `boost_post_exercise_recovery_scale`, `boost_post_exercise_recovery_target`
- Activity step thresholds (5/15/30/60 min)
- HR integration: `hrMaxBpm`, `hrRestingBpm`, etc.
- Plus all standard AAPS settings (max basal, max IOB, autosens, etc.)

The user is responsible for selecting reasonable values for all of these. V1
provides defaults but most are user-tuned per-individual.

### V5 settings — the entire list

```
Aggression           default 1.0    range 0.7–1.3    scales the CONFIRMED commit multiplier
Hypo Caution         default 1.0    range 1.0–2.0    multiplier on the mlHypoRiskScale floor
Sensitivity          default 1.0    range 0.8–1.2    reserved (currently inert; may ship if backtest justifies)
```

That's it. **All other behaviour is hardcoded** at release time, calibrated
by backtest on a 19-user cohort with GroupKFold-by-user. Internal weights,
state machine thresholds, action multipliers, iobHeadroomBrake curve points
— none of these are user-visible.

The design tenet: a user has no basis to choose the value of "delta weight in
meal_signal_score." Forcing them to is forcing them to misconfigure.


## Safety mechanisms

| Mechanism | V1 form | V5 form |
|---|---|---|
| `enableSMB` pre-checks | Per-cycle, inline in determineBasal | Phase 3 hard gate (full V4 chain inherited via V4.4.1 in shadow mode) |
| `minGuardBG` predicted-low gate | Hard disable inline | Phase 3 hard gate (V5 reads V4.4.1's smart-selected `minGuardBG` value to match exactly) |
| `maxDelta > 0.30 × bg` | Hard disable inline | Phase 3 hard gate (retained) |
| `maxIOB` clamp | Distributed across tiers | Phase 3 hard gate (single clamp) |
| Tier 7 IOB cap (`if insulinReq > maxIOB − iob, clamp`) | Tier-7-specific in V1 — caps insulinReq before the dose formula | `iobHeadroomBrake` graduated curve, fires regardless of delta_accl direction. **First soft gate** so risk projection runs against damped dose |
| ML hypo-risk damping | **None in V1** | `mlHypoRiskScale` in AggressionBudget (Phase 1) — graduated 1.0→0.5 floor as risk climbs from 0.30 to 1.0 |
| ML tier-skip | **None in V1** | Subsumed by graduated `mlHypoRiskScale` (no double-braking) |
| `fastCarbScale` (post-hypo brake on Tiers 3/5/6) | Graduated 0.3–1.0 multiplier when `recentLowBG < 100` | Folded into the continuous `notRecentlyLow` weight in `meal_signal_score` |
| Spike override (raise Tier 8 cap) | Tier-8-only, when BG > 180 + delta > 5 + insulinReq > 3×maxBolus | `dynamicSpikeCap` applies on **every** cycle |
| Deceleration response | Implicit — when delta drops, lower tier fires | `decelerationBrake` (NEW in V5) — fires only when both `delta_accl < -10` AND `IOB > 0.5×max` |
| Sensor data quality | `flatBGsDetected` inline check | `sensorQualityCheck` soft gate (Phase 3) |
| Calibration block | 15-min hard zero after CGM calibration event | **Same** — V5 inherits the `enableSmbPreChecks` decision from whichever variant is active |
| Hypo-rebound TT auto-cancel | Cancels recovery TempTarget when BG climbs back from a low | Inherited unchanged — operates outside V5's dose pipeline |

**Critical V5 invariant:** Phase 3 gates run in a load-bearing order. Hard
gates short-circuit; soft gates damp; final clamp rounds + applies spike cap +
floors at 0. `iobHeadroomBrake` MUST run first in soft gates so that
`postActionRiskCheck` projects against the already-damped dose, not the raw
Phase 2 output. Re-shuffling silently weakens the safety stack.


## Brake stacking — composition behaviour

V1's brake stack is shorter than V4.4.1's (V4.4.1 added two more brakes:
`mlPostSmbScale` and `mlRiskScale`). Even so, V1 has the same fundamental
structural concern: the brake is a multiplicative factor with no overall floor.

### V1: multiplicative (smaller chain than V4)

```
final_smb = tier_dose × fastCarbScale       # only on Tiers 3/5/6, only when recentLowBG < 100
```

`fastCarbScale` ranges 0.3–1.0. There is **no floor** on the final dose: if
`fastCarbScale = 0.3` and the tier's selected dose was already small,
the multiplied result could be near zero. (V4.4.1 then added `mlRiskScale` and
`mlPostSmbScale` which can stack multiplicatively with `fastCarbScale`,
producing the unbounded composition concern documented in
`boost_dosing_pipeline_map_2026-05-02.md`.)

### V5: bounded composition with hard floor

```
aggression_budget = max(
  0.30 × baseInsulinReq,                                                # hard floor — never below 30%
  baseInsulinReq × mlHypoRiskScale() × postExerciseRecoveryModifier()   # the chain
)

insulin_to_deliver = aggression_budget × meal_action_multiplier(state)
```

The chain has only **two** multipliers, both safety reducers; neither
amplifies. The `0.30 × baseInsulinReq` floor guarantees a non-zero dose
foundation (Phase 3 can still zero the dose via hard gates if a safety check
fails, but the AggressionBudget itself never drives below 30%).

V5 explicitly **dropped** three multipliers from earlier proposals:

- `exerciseStateModifier` — V1's exercise modes act through profile% / target shift, both of which already feed `baseInsulinReq`. Multiplying again would double-count.
- `timeOfDayModifier` (dawn 1.1×) — AAPS profiles already have hour-of-day basal rates and optional hour-of-day ISF; dawn coverage flows through `baseInsulinReq`. **V5 has no dawn-phenomenon adjuster.**
- `bgRangeModifier` — `(eventualBG - target) / sens` already scales with BG; minGuardBG handles low; CONFIRMED 1.8× and dynamicSpikeCap handle high.


## State persistence

| Property | V1 | V5 |
|---|---|---|
| State persisted across cycles | None per-cycle algorithm state. (V1 keeps `recoveryWindowEnd` and `wasExerciseActive` as plugin-level state for post-exercise tracking, but the tier evaluation each cycle is stateless.) | `mealHypothesis: String?`, `mealHypothesisAge: Int?`, `mlMealLikelyNullStreak: Int` |
| Storage mechanism | n/a | `StringKey.ApsBoostV5State` — atomic JSON blob in AAPS Preferences |
| Reset paths | n/a | Explicit: reboot, pump disconnect, loop suspend, profile switch, time jump > 30 min |
| Risk class | Stateless tier rules — no carry-forward bugs | State persists ⇒ a state-machine bug persists. Mitigated by explicit reset paths and 99 unit tests covering every transition + reset |


## Observability (NS RT fields)

| Field | V1 emits | V5 emits |
|---|---|---|
| Boost tier identifier | `boostTier: String` (which of the 8 tiers fired) | n/a — there are no tiers |
| `mlHypoRisk` | **No** — V1 doesn't run an ML model | Yes — V5 reads from the active Boost variant's RT (V4.4.1 in shadow mode) |
| `mlMealLikely` | **No** — V1 doesn't run the meal-likelihood model | Yes — same source as `mlHypoRisk` |
| `insulinReq` | Yes — V1's computed insulinReq | Yes — V5 logs its own `boostV5_baseInsulinReq` (same value V1 computed) |
| `meal_signal_score` | n/a | **NEW** — `boostV5_score` |
| `mealHypothesis` state | n/a | **NEW** — `boostV5_state` (IDLE/OBSERVING/CONFIRMED/COMMITTED/RECOVERING) |
| `mealHypothesisAge` | n/a | **NEW** — `boostV5_age` (cycles in current state) |
| `aggression_budget` | n/a | **NEW** — `boostV5_budget` |
| `action_multiplier` | n/a | **NEW** — `boostV5_actionMult` |
| Gate reductions | Distributed across log messages | **NEW** — `boostV5_gateReduction` (compact summary string) |
| Reconstructable from RT alone? | Partially (tier + dose visible; modulator stack approximated from log text) | **Yes** — observability test verifies 20/20 random cycles fully reconstructable from the 6 fields |


## Calibration approach

| | **V1** | **V5** |
|---|---|---|
| Per-user tuning expectation | High — most user-facing knobs need user-specific values | None at launch — defaults derived from population backtest |
| Calibration mechanism | User adjusts settings based on observed glycemic outcomes | Backtest harness with ±20% sensitivity sweeps × 5-fold GroupKFold-by-user; constants chosen once, frozen at release |
| Output of calibration process | Recommended setting values per individual | A single set of hardcoded constants in source code |
| Re-calibration cadence | Continuous (user-initiated) | At each Boost release, by the developer |


## What V1 has that V5 doesn't reimplement

V5 doesn't replace these — it inherits them via the Boost-flavoured
`baseInsulinReq` calculation (which V1 already runs):

- DynISF V1 formula and the entire sensitivity stack
- Post-exercise recovery window detection (V5 reads the boolean state)
- HR-augmented exercise classification (Karvonen zones + step fusion)
- Hypo-rebound TT auto-cancel
- Calibration block (15-min hard zero after CGM calibration event)

V5's contribution is the architectural REPLACEMENT of:
- The 8-tier ladder → state machine + single rule
- The (modest) brake stack → AggressionBudget with hard floor
- Implicit meal recognition → explicit MealHypothesis state machine
- ~30+ user knobs → 3 user knobs


## What V5 brings that V1 didn't have at all

These are NEW capabilities V5 introduces (sourced via the inherited
`baseInsulinReq` pipeline / external ML models):

- **ML hypo-risk integration** — V5's `mlHypoRiskScale` damper. V1 has no ML; V5 brings hypo-risk-aware dose damping. (V3ML+ is the first Boost variant to add ML; V5 keeps it but folds the V3ML two-brake design into a single graduated curve.)
- **ML meal-likelihood signal** — 0.20-weight component of `meal_signal_score`. V1 has no equivalent; V5 brings continuous meal probability into the meal-detection signal.
- **Explicit meal hypothesis state** — IDLE → OBSERVING → CONFIRMED → COMMITTED → RECOVERING. V1's meal recognition is implicit.
- **Aggression Budget hard floor (30%)** — bounded composition for safety. V1's `fastCarbScale` could drive doses near zero on Tiers 3/5/6 without a floor.
- **Dynamic spike cap on every cycle** — V1 had spike override only on Tier 8.
- **iobHeadroomBrake graduated curve** — V1 has a hard Tier 7 IOB cap; V5 generalises this into a graduated brake that fires regardless of which decision path produced the dose.


## Backwards compatibility

V5 is a **parallel plugin**, not an in-place rewrite. V1 (`openAPSBoost`),
V2 (`openAPSBoostV2`), V3 (`openAPSBoostV3`), V3ML (`openAPSBoostV3ML`),
V3MLG3 / V4.4.1 (`openAPSBoostV3MLG3`), and V5 (`openAPSBoostV5`) all coexist
in the codebase. Users select which one is the active APS algorithm via the
plugin selector.

Currently V5 is **hidden** from the plugin list (`showInList { false }`,
`isEnabled() = false`) — it cannot be selected for active dosing. V5 runs
**only as a sidecar to V4.4.1** during shadow mode, reading V4.4.1's
gathered inputs and result, running its own decide() in parallel, and
logging a JSON RT blob via `aapsLogger.info` with prefix `BoostV5_RT:`. V5
will only be made user-selectable after the test plan's Layer 1–3
acceptance gates pass on real shadow-mode data.


## When to choose V5 over V1 (when V5 is alpha-released)

V5 is designed for users who want:

- **Earlier dosing on real meals** without entering carbs (CONFIRMED commit at delta+accl threshold, around BG=110–130 typically)
- **ML-aware safety** — hypo risk damping V1 doesn't have at all
- **Fewer surprises** from interactions between brakes and tier eligibility
- **Far fewer settings** to tune — three knobs vs ~30+
- **Better observability** — full decision reconstructable from 6 NS fields

V1 may still be preferred by users who:

- Have already tuned V1's many knobs and are happy with the result
- Want explicit per-tier control (boost_scale, boost_percent_scale, etc.)
- Have specific dosing patterns better captured by particular tier conditions
- Don't want ML in their dosing loop

There is no clinical superiority claim of V5 over V1 yet — V5 is PRE-ALPHA
and shadow-mode only at the time of writing.


## Related documents

- `MIGRATION.md` (this directory) — full V4.4.1 → V5 mechanism mapping
- `boost_v5_redesign_proposal.md` (claude memory) — the architectural rationale
- `boost_v3_architecture.md` (claude memory) — V1/V2/V3 specifics and the V2 → V3 safety regression
- `boost_dosing_pipeline_map_2026-05-02.md` (claude memory) — the 11-modulator inventory of V4 that motivated the V5 redesign
- `boost_v5_constants_calibration.md` (claude memory) — the calibrated values + sweep results
