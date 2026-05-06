# Boost V1 vs Boost V5

A side-by-side reference for users and maintainers comparing the original Boost
algorithm (V1, plugin `openAPSBoost`, internal short name `BOOST`) and the
clean-slate V5 redesign (plugin `openAPSBoostV5`, short name `V5`).

V5 is a **complete architectural rethink**, not an incremental upgrade. V1, V2,
V3, V3ML, V3MLG3 (V4.4.1) are all variants of the same 8-tier ladder; V5 is the
first algorithm in the Boost family to abandon that structure entirely. This
document explains what changed, why, and what the user experience differs in.

For the V4.4.1 → V5 mapping (every V4 mechanism's V5 home), see `MIGRATION.md`
in this directory. For the full architectural rationale, see
`boost_v5_redesign_proposal.md` in claude memory.


## At a glance

|  | **V1 (Boost)** | **V5 (Boost V5)** |
|---|---|---|
| Architecture | 8-tier if-else ladder + 5–11 modulators | 3-phase pipeline (state → decision → safety) |
| Lines of Kotlin (algorithm core) | ~1,490 | ~620 across 5 files |
| Meal recognition | Implicit — emerges from which tier the if-else picks | Explicit — `MealHypothesis` state machine, persisted across cycles |
| Decision rule | 8 separate dose formulas, one per tier | One: `aggression_budget × meal_action_multiplier(state)` |
| Safety brakes | Multiplicative stack with no overall floor | One AggressionBudget chain (2 multipliers, 30% hard floor); ordered Phase 3 gates |
| User-facing settings | ~67 preference entries (Boost-specific knobs alone ~30+) | **3** (Aggression, Hypo Caution, Sensitivity-reserved) |
| Internal hardcoded constants | Distributed across the if-else thresholds; per-user calibration left to users | ~14–15, calibrated once via backtest at release; frozen |
| State persisted across cycles | None (each cycle starts fresh) | `mealHypothesis`, `mealHypothesisAge`, `mlMealLikelyNullStreak` |
| ISF / sensitivity calculation | Uses Boost-flavoured DynISF + EMA sensitivity (same as V4.4.1) | **Inherits V4.4.1's `baseInsulinReq`** — V5 contains zero sensitivity logic of its own |
| ML risk integration | `mlTierDowngrade` (binary at 0.6) + `mlRiskScale` (graduated) + `mlPostSmbScale` (post-SMB, V4 addition) | Single graduated `mlHypoRiskScale` damper inside AggressionBudget |
| Meal-likelihood ML | Used in V3MLG3+ as binary G3-hold release; V1 doesn't use it directly | 0.20-weight component of continuous `meal_signal_score` |
| Exercise handling | Profile% + target compression; post-exercise window; HR + step fusion | **Pass-through via `baseInsulinReq`** for modes 1–6; explicit `postExerciseRecoveryModifier` (the one effect not in baseInsulinReq) |


## Architecture

### V1 — 8-tier if-else ladder

For each cycle, V1 evaluates conditions for 8 tiers in order. The first matching
tier sets the SMB amount. Tiers (V1 source `DetermineBasalBoost.kt:1225+`):

```
Tier 1  COB_PRIMARY      lastCarbAge < 25 min, COB > 0
Tier 2  COB_SECONDARY    lastCarbAge < 40 min, delta > 5
Tier 3  UAM_BOOST        delta ≥ 5, shortAvgDelta ≥ 3, uamBoost1 > 1.2, uamBoost2 > 2
Tier 4  UAM_HIGH_BOOST   delta_accl > 5, BG > 180
Tier 5  PERCENT_SCALE    BG 110–180, delta > 3, delta_accl > 0
Tier 6  ACCELERATION     delta_accl > 25, delta > 4, BG > 110
Tier 7  ENHANCED_OREF1   delta > 0, delta_accl ≥ 0.5  (with IOB cap — V1 safety mechanism)
Tier 8  REGULAR_OREF1    fallback
```

After tier selection, the chosen SMB goes through several **modulators**:

- `mlTierDowngrade` — binary skip of Tiers 3–6 if `mlHypoRisk > 0.6`
- `mlRiskScale` — graduated 0–1.0 multiplier on the SMB
- `fastCarbScale` — graduated 0.3–1.0 brake on Tiers 3/5/6 when recent low BG
- `g3HoldActive` — zeroes Tiers 5–8 SMB when "BG rising from near-target with no COB"
- Spike Override — raises Tier 8's cap when BG > 180 + delta > 5 + insulinReq high

The result: dose comes from one of 8 formulas, **then** multiplied by anything
from 1 to 3 brakes, with no floor on the multiplicative stack.

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
| What if a rise just-misses the binary thresholds? | Falls through to Tier 7 or 8 — the slow-acting fallback. Real example: 2026-05-05 incident, uamBoost1=1.15 (need 1.2), no SMB for 41 minutes | Score is continuous; no binary cliff. Same input pattern reaches CONFIRMED through accumulated score weight |
| Recovery / back-off | Implicit — when delta drops, Tier 5/6 conditions fail and dose drops naturally. Can flicker if delta dips mid-rise | Explicit `COMMITTED → RECOVERING` transition requires `delta_accl < -5 AND deltaDeclining(2)`. State is sticky — single-cycle delta noise doesn't flicker |
| Tim's "test then commit" intent | Not encoded — V1 dose at OBSERVING-equivalent cycles is the same as at CONFIRMED-equivalent cycles | OBSERVING multiplier `0.3×` (test dose) → CONFIRMED `1.8×` (commit) → COMMITTED `1.0×` (sustain) |


## Sensitivity (ISF / DynISF / TDD / EMA)

Both V1 and V5 use the same Boost-flavoured ISF stack. **V5 does not
reimplement it.**

| Component | V1 | V5 |
|---|---|---|
| DynISF formula | `1800 / (TDD × ln(target/insulinDivisor + 1))` | **same** — inherited via `baseInsulinReq` |
| TDD source | 7-day total daily dose with 8-hour pull-down rule | **same** — inherited |
| EMA sensitivity ratio | `EMA τ=3h on tdd_24h/tdd_7d` (a multiplier on sensNormalTarget) | **same** — inherited |
| Autosens | AAPS standard, applied per-cycle | **same** — inherited |
| Hour-of-day basal rates | Profile-driven | **same** — inherited |
| TempTargets | Standard AAPS | **same** — inherited via target term |
| `dynISFvelocity` setting | **User-configurable** (default ~0.5–0.8) — controls how aggressively ISF tracks BG | **Hardcoded 1.0** in V4.4.1, inherited by V5 (the V2 → V3 reversion) |
| `delta_accl` denominator floor | `max(|shortAvgDelta|, 2.0)` (V3+ addition) | **same** — inherited verbatim |

**V5 contains zero sensitivity logic of its own.** Sensitivity is owned by the
existing layer (which V4.4.1 has refined over multiple versions); V5 trusts the
result and applies meal/risk multipliers on top. This is enforced by the
sidecar architecture — V5 reads V4.4.1's computed `insulinReq` directly and
uses it as `baseInsulinReq`.


## User-facing settings

V1 exposes ~67 preference entries (~30+ Boost-specific). V5 exposes **3**.

### V1 settings the user can tune (selected, not exhaustive)

- `boost_bolus_cap` (max single SMB)
- `boost_max_iob`
- `boost_insulin_req_pct` (divisor for insulinReq → SMB)
- `boost_scale` (multiplier on Boost insulin requirement)
- `boost_percent_scale_factor` (sliding scale BG 108→180)
- `boost_dynisf_velocity` (ISF tracking velocity)
- `boost_start_time` / `boost_end_time` (active time window)
- `boost_sleep_in_hrs`, `boost_inactivity_pct`, `boost_activity_pct`
- `boost_post_exercise_recovery_hours`, `boost_post_exercise_recovery_scale`
- Activity steps thresholds (5/15/30/60 min)
- HR integration: `hrMaxBpm`, `hrRestingBpm`, etc.
- Plus all standard AAPS settings (max basal, max IOB, autosens etc.)

The user is responsible for selecting reasonable values for all of these. V1
provides defaults but most are user-tuned per-individual.

### V5 settings — the entire list

```
Aggression           default 1.0    range 0.7–1.3    scales the CONFIRMED commit multiplier
Hypo Caution         default 1.0    range 1.0–2.0    multiplier on the mlHypoRiskScale floor
Sensitivity          default 1.0    range 0.8–1.2    reserved (currently inert; may ship if backtest justifies)
```

That's it. **All other behaviour is hardcoded** at release time, calibrated by
backtest on a 19-user cohort with GroupKFold-by-user. Internal weights, state
machine thresholds, action multipliers, iobHeadroomBrake curve points — none of
these are user-visible.

The design tenet: a user has no basis to choose the value of "delta weight in
meal_signal_score." Forcing them to is forcing them to misconfigure.


## Safety mechanisms

| Mechanism | V1 location / form | V5 location / form |
|---|---|---|
| `enableSMB` pre-checks | Per-cycle, inline in determineBasal | Phase 3 hard gate (full V4 chain inherited) |
| `minGuardBG` predicted-low gate | Hard disable inline (line 884–886) | Phase 3 hard gate |
| `maxDelta > 0.30 × bg` | Hard disable inline | Phase 3 hard gate (retained) |
| `maxIOB` clamp | Distributed across tiers | Phase 3 hard gate (single clamp) |
| Tier 7 IOB cap (V3 reinstated) | Tier-7-specific: `if insulinReq > maxIOB - iob, clamp` | `iobHeadroomBrake` graduated curve, fires regardless of delta_accl direction. **First soft gate** so risk projection runs against damped dose |
| `mlPostSmbScale` (post-SMB risk re-projection) | Multiplier on final SMB (V4 addition) | `postActionRiskCheck` in Phase 3 (re-runs at projected IOB) |
| `mlRiskScale` (graduated 0–1.0) | Multiplier on final SMB | `mlHypoRiskScale` in AggressionBudget (Phase 1) |
| `mlTierDowngrade` (binary at 0.6) | Skips Tiers 3–6 | **Subsumed by graduated mlHypoRiskScale** — no double-braking |
| Deceleration response | Implicit — when delta drops, lower tier fires | `decelerationBrake` (NEW in V5) — fires only when both `delta_accl < -10` AND `IOB > 0.5×max` |
| Spike override (raise Tier 8 cap) | Tier-8-only, when BG > 180 + delta > 5 + insulinReq > 3×maxBolus | `dynamicSpikeCap` applies on **every** cycle |
| Sensor data quality | `flatBGsDetected` inline check | `sensorQualityCheck` soft gate (Phase 3) |
| Calibration block | 15-min hard zero after CGM calibration event | **Same** — inherited via V4.4.1 (V5 reads V4.4.1's `enableSmbPreChecks`) |

**Critical V5 invariant:** Phase 3 gates run in a load-bearing order. Hard
gates short-circuit; soft gates damp; final clamp rounds + applies spike cap +
floors at 0. `iobHeadroomBrake` MUST run first in soft gates so that
`postActionRiskCheck` projects against the already-damped dose, not the raw
Phase 2 output. Re-shuffling silently weakens the safety stack.


## Brake stacking — composition behaviour

This is the single most consequential V5 change for safety.

### V1: multiplicative stack with no floor

```
final_smb = tier_dose × postSmbScale × mlRiskScale × fastCarbScale
```

Under high-risk conditions, three brakes can stack to drive the dose to <5% of
the tier-selected baseline. There is **no overall floor** on the product.
Recorded incident: `mlRiskScale = 0.357 × postSmbScale = 0.3` → `0.107×` of
intended dose. Per the V4.4.1 dosing pipeline map (`boost_dosing_pipeline_map_2026-05-02.md`),
this is one of the architectural concerns that motivated V5.

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

- `exerciseStateModifier` — V4 exercise modes act through profile% / target shift, both of which already feed `baseInsulinReq`. Multiplying again would double-count.
- `timeOfDayModifier` (dawn 1.1×) — AAPS profiles already have hour-of-day basal rates and optional hour-of-day ISF; dawn coverage flows through `baseInsulinReq`. **V5 has no dawn-phenomenon adjuster.**
- `bgRangeModifier` — `(eventualBG - target) / sens` already scales with BG; minGuardBG handles low; CONFIRMED 1.8× and dynamicSpikeCap handle high.


## State persistence

| Property | V1 | V5 |
|---|---|---|
| State persisted across cycles | None | `mealHypothesis: String?`, `mealHypothesisAge: Int?`, `mlMealLikelyNullStreak: Int` |
| Storage mechanism | n/a | `StringKey.ApsBoostV5State` — atomic JSON blob in AAPS Preferences |
| Reset paths | n/a | Explicit: reboot, pump disconnect, loop suspend, profile switch, time jump > 30 min |
| Risk class | Stateless tier rules — no carry-forward bugs | State persists ⇒ a state-machine bug persists. Mitigated by explicit reset paths and 99 unit tests covering every transition + reset |


## Observability (NS RT fields)

| Field | V1 emits | V5 emits |
|---|---|---|
| Boost tier identifier | `boostTier: String` (which of the 8 tiers fired) | n/a — there are no tiers |
| `mlHypoRisk` | Yes (V3+ only) | Yes — same value, computed by the inherited V4.4.1 ML model |
| `mlMealLikely` | Yes (V3MLG3+ only) | Yes — same value |
| `insulinReq` | Yes — V1's computed insulinReq | Yes — V5 logs the inherited V4.4.1 insulinReq as `baseInsulinReq` |
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


## Backwards compatibility

V5 is a **parallel plugin**, not an in-place rewrite. V1 (`openAPSBoost`),
V2 (`openAPSBoostV2`), V3 (`openAPSBoostV3`), V3ML (`openAPSBoostV3ML`),
V3MLG3 / V4.4.1 (`openAPSBoostV3MLG3`), and V5 (`openAPSBoostV5`) all coexist
in the codebase. Users select which one is the active APS algorithm via the
plugin selector.

Currently V5 is **hidden** from the plugin list (`showInList { false }`,
`isEnabled() = false`) — it cannot be selected for active dosing. V5 runs
**only as a sidecar to V4.4.1** during shadow mode, reading V4.4.1's gathered
inputs and result, running its own decide() in parallel, and logging a JSON
RT blob via `aapsLogger.info` with prefix `BoostV5_RT:`. V5 will only be
made user-selectable after the test plan's Layer 1–3 acceptance gates pass
on real shadow-mode data.


## When to choose V5 over V1 (when V5 is alpha-released)

V5 is designed for users who want:

- **Earlier dosing on real meals** without entering carbs (CONFIRMED commit at delta+accl threshold, around BG=110–130 typically)
- **Fewer surprises** from interactions between brakes and tier eligibility
- **Far fewer settings** to tune — three knobs vs ~30+
- **Better observability** — full decision reconstructable from 6 NS fields

V1 may still be preferred by users who:

- Have already tuned V1's many knobs and are happy with the result
- Want explicit per-tier control (boost_scale, boost_percent_scale, etc.)
- Have specific dosing patterns better captured by particular tier conditions

There is no clinical superiority claim of V5 over V1 yet — V5 is PRE-ALPHA and
shadow-mode only at the time of writing.


## Related documents

- `MIGRATION.md` (this directory) — full V4.4.1 → V5 mechanism mapping
- `boost_v5_redesign_proposal.md` (claude memory) — the architectural rationale
- `boost_v3_architecture.md` (claude memory) — V1/V2/V3 specifics and the V2 → V3 safety regression
- `boost_dosing_pipeline_map_2026-05-02.md` (claude memory) — the 11-modulator inventory of V4 that motivated the V5 redesign
- `boost_v5_constants_calibration.md` (claude memory) — the calibrated values + sweep results
