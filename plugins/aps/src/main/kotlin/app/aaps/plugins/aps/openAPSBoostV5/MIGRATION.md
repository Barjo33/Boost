# Boost V4 → V5 migration map

Canonical reference for "where did X go in V5?". Read this before chasing a V4
mechanism through V5 source — most things didn't move, they got subsumed.

V5 is a **parallel plugin**, not an in-place rewrite. V3MLG3 (V4.4.x) continues
to exist alongside V5 throughout shadow mode, alpha, and at least the first
post-stable release. **No V4 source files are deleted by V5's introduction.**

Status legend:
- **subsumed** — V4 mechanism replaced by a V5 mechanism that does the same job differently
- **inherited** — V4 mechanism reused unchanged through `baseInsulinReq` or shared service
- **dropped** — V4 mechanism removed entirely (rare; each entry justified)
- **deferred** — decision held until backtest output drives a yes/no

Cross-references:
- V4 source: `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSBoostV3MLG3/`
  (`OpenAPSBoostV3MLG3Plugin.kt` ~1.5k lines, `DetermineBasalBoostV3MLG3.kt` ~1.6k lines)
- V5 source: `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSBoostV5/`
- V5 design proposal: `boost_v5_redesign_proposal.md` in claude memory


## Architectural summary

| V4 component | V5 home |
|---|---|
| 8-tier if-else ladder (`DetermineBasalBoostV3MLG3.kt:1315–1450`) | Phase 2 single rule + Phase 1 mealHypothesis state machine |
| Multiplicative brake stack (postSmbScale × mlRiskScale × fastCarbScale × Activity-mode target compression) | AggressionBudget — TWO multipliers (`mlHypoRiskScale × postExerciseRecoveryModifier`) with hard floor `0.30 × baseInsulinReq` |
| Pre-tier modulators (G3 hold, fast-carb rebound) | Folded into continuous `meal_signal_score` (Phase 1) |
| Post-tier brakes (mlRiskScale, postSmbScale) | Phase 3 ordered safety gates |
| Tier 7 IOB cap (V3 reinstated, V2 deletion regression) | `iobHeadroomBrake` — first soft gate in Phase 3, graduated curve |
| Tier 8 spike override | `dynamicSpikeCap` — applies on every cycle (V4 was Tier-8 only) |


## Phase 1 — State estimation

### MealHypothesis state machine (NEW in V5)

V4 had no equivalent — meal recognition was implicit in the tier ladder
selecting Tier 3/4 (UAM_BOOST / UAM_HIGH_BOOST). V5 makes meal hypothesis
a first-class persisted state with 5 states (IDLE, OBSERVING, CONFIRMED,
COMMITTED, RECOVERING) and explicit transitions.

State persistence: 2 new RT fields (`mealHypothesis: String?`,
`mealHypothesisAge: Int?`). Reset paths: reboot, pump disconnect, loop
suspend, profile switch, time jump > 30 min — all force IDLE.

### meal_signal_score weighted components

| V4 mechanism | V4 location | V5 home | Status |
|---|---|---|---|
| G3 Pre-UAM uncertainty hold (binary on `delta ≥ 5 AND shortAvgDelta ≥ 3 AND COB < 1 AND recentLowBG ≥ 70`) | DetermineBasalBoostV3MLG3.kt:1140–1188 | Continuous `delta` weight (0.30) + `delta_accl` weight (0.20) + `notRecentlyLow` weight (0.15) | **subsumed** — binary cliff replaced by continuous score |
| Meal model release (mlMealLikely > 0.65) | DetermineBasalBoostV3MLG3.kt:1107–1182 | `mlMealLikely` weight (0.20) in score | **subsumed** — ML model is now a graded signal, not a binary gate release |
| Fast-carb rebound protection (graduated 0.3–1.0 scale on Tiers 3/5/6 when `recentLowBG < 100 OR reversalScore > 30`) | DetermineBasalBoostV3MLG3.kt:1232–1288 | `notRecentlyLow` continuous penalty (linear 1.0 at recentLowBG ≥ 100, 0.0 at 70) | **subsumed** — replaces binary "low triggered" / reversalScore heuristics with one continuous score component |
| UAM eligibility (uamBoost1 > 1.2 AND uamBoost2 > 2.0) just-misses (5/5 incident: 1.15, 1.96) | DetermineBasalBoostV3MLG3.kt:1335 | Continuous `delta` + `delta_accl` weights — no binary cliff | **subsumed** |
| `mealTimeOfDay` likelihood signal | not in V4 | 0.10 weight in score (NEW; smooth bumps at 8/13/19) | **NEW (V5)** — small weight, captures meal-hour prior; not a dose amplifier |
| Exercise-active suppression of meal detection | not explicit in V4 | 0.05 weight in score (`1.0 - exerciseActive`) | **NEW (V5)** — suppresses false meal detection during walks |

**Model-load failure handling.** If `mlMealLikely` is null for ≥3 consecutive
cycles, V5 drops the 0.20 weight and renormalizes the remaining 5 weights to
sum to 1.0. Resets to standard formula on first non-null cycle. This addresses
the V3ML lazy-load bug noted in `boost_v3ml_production_validation.md`.

### AggressionBudget — the brake-stack collapse

V4 had a multiplicative chain with no overall floor: `microBolus × postSmbScale
× mlRiskScale × fastCarbScale` could drive doses to <5% of baseline under
stacked high-risk. V5 enforces a hard floor and removes redundant multipliers.

```
V4: tier_dose × postSmbScale × mlRiskScale × fastCarbScale  (no floor)
V5: max(0.30 × baseInsulinReq, baseInsulinReq × mlHypoRiskScale × postExerciseRecoveryModifier)
```

| V4 mechanism | V4 location | V5 home | Status |
|---|---|---|---|
| mlRiskScale (graduated 0–1.0 on mlHypoRisk > 0.3) | DetermineBasalBoostV3MLG3.kt:1505–1513 | `mlHypoRiskScale()` in AggressionBudget | **subsumed** |
| mlPostSmbScale (post-SMB risk re-projection) | DetermineBasalBoostV3MLG3.kt:1473–1503 | `postActionRiskCheck()` in Phase 3 (re-runs at projected IOB) | **subsumed** — moved from Phase 1 brake to Phase 3 gate where the projection makes sense |
| mlTierDowngrade (binary skip Tiers 3-6 if mlHypoRisk > 0.6) | DetermineBasalBoostV3MLG3.kt:1135 | Subsumed by graduated `mlHypoRiskScale` (no separate tier-skip needed) | **subsumed** — eliminates double-braking on the same metric |
| Activity-mode profile/target compression | OpenAPSBoostV3MLG3Plugin.kt:600–803 | Inherited via `baseInsulinReq` (target shift + profile% feed oref calc) | **inherited** |
| Post-exercise recovery (`boost_bolus *= postExerciseRecoveryScale`) | OpenAPSBoostV3MLG3Plugin.kt:734–779 | `postExerciseRecoveryModifier()` in AggressionBudget — reads V4's existing detection state | **subsumed** — V5 reuses V4's window-tracking state, applies the dampening at AggressionBudget level |
| timeOfDayModifier (proposed dawn 1.1) | (proposal only — never built in V4 or V5) | **dropped** — dawn coverage is profile responsibility (hour-of-day basal rates) | **dropped** by design |
| bgRangeModifier (proposed high 1.2 / low 0.5) | (proposal only — never built) | **dropped** — `(eventualBG - target)` already scales with BG; minGuardBG handles low; CONFIRMED 1.8 + dynamicSpikeCap handle high | **dropped** by design |


## Phase 2 — Single decision rule

V4's 8-tier if-else replaced by `aggression_budget × meal_action_multiplier(mealHypothesis)`.

| V4 tier | V4 location | V5 mapping |
|---|---|---|
| Tier 1: COB_PRIMARY (lastCarbAge < 25, COB > 0) | DetermineBasalBoostV3MLG3.kt:1316 | mealHypothesis goes straight to COMMITTED on the cycle COB appears; baseInsulinReq already accounts for COB |
| Tier 2: COB_SECONDARY (lastCarbAge < 40, delta > 5) | DetermineBasalBoostV3MLG3.kt:1325 | same as Tier 1 |
| Tier 3: UAM_BOOST | DetermineBasalBoostV3MLG3.kt:1335 | mealHypothesis CONFIRMED → COMMITTED |
| Tier 4: UAM_HIGH_BOOST | DetermineBasalBoostV3MLG3.kt:1363 | same as Tier 3 (single state for all UAM cycles) |
| Tier 5: PERCENT_SCALE | DetermineBasalBoostV3MLG3.kt:1381 | OBSERVING (test dose 0.3×) → CONFIRMED → COMMITTED depending on score |
| Tier 6: ACCELERATION | DetermineBasalBoostV3MLG3.kt:1408 | same as Tier 5 — `delta_accl` is a continuous score component, not a tier discriminator |
| Tier 7: ENHANCED_OREF1 | DetermineBasalBoostV3MLG3.kt:1429 | mealHypothesis IDLE / RECOVERING → action_multiplier 1.0× / 0.4× |
| Tier 8: REGULAR_OREF1 | DetermineBasalBoostV3MLG3.kt:1439 | same as Tier 7 |

Action multipliers: IDLE 1.0, OBSERVING 0.3, CONFIRMED 1.8, COMMITTED 1.0, RECOVERING 0.4.
The user-facing **Aggression** knob (one of ≤3) scales the CONFIRMED multiplier only.


## Phase 3 — Ordered safety gates

| V4 gate | V4 location | V5 home | Status |
|---|---|---|---|
| `enableSMB` checks (microBolusAllowed, BG > threshold, profile checks) | DetermineBasalBoostV3MLG3.kt:884–892 | `enableSmbPreChecks()` — full V4 chain, hard gate | **inherited** |
| minGuardBG hard gate | DetermineBasalBoostV3MLG3.kt:884–886 | minGuardBG hard gate | **inherited** |
| maxDelta hard gate (`maxDelta > 0.30 × bg`) | DetermineBasalBoostV3MLG3.kt:888–892 | maxDelta hard gate | **inherited** |
| maxIOB clamp | DetermineBasalBoostV3MLG3.kt | maxIOB hard gate | **inherited** |
| Tier 7 IOB cap (V1→V2 deletion → 5.5× hypo regression → V3 reinstatement) | DetermineBasalBoostV3MLG3.kt:1429 | `iobHeadroomBrake()` graduated curve, FIRST soft gate; fires regardless of delta_accl direction | **subsumed** — see V3 architecture memo for the empirical justification of this safety mechanism's necessity |
| postSmbScale (re-runs risk model at projected IOB) | DetermineBasalBoostV3MLG3.kt:1473–1503 | `postActionRiskCheck()` — second soft gate | **subsumed** |
| (none in V4) | — | `decelerationBrake()` — third soft gate, fires on `delta_accl < -10 AND IOB > 0.5×max` | **NEW (V5)** — captures "the IOB you have is starting to bite" |
| Sensor quality dampening | (not implemented in V4) | `sensorQualityCheck()` — fourth soft gate | **NEW (V5)** — placeholder; must be defined or dropped before alpha |
| Spike override (raises Tier 8 cap when BG > 180 + delta > 5) | DetermineBasalBoostV3MLG3.kt:1453–1471 | `dynamicSpikeCap()` — applies on **every** cycle, not just one tier | **subsumed and improved** |
| Final round to roundSMBTo + max(0) | DetermineBasalBoostV3MLG3.kt | same | **inherited** |

**Critical: gate ordering is load-bearing.** `iobHeadroomBrake` runs first so
that `postActionRiskCheck` projects against the already-damped dose, not the
raw Phase 2 output. Reordering will silently weaken the safety stack.


## Sensitivity / ISF stack — INHERITED ENTIRELY

V5 contains zero sensitivity logic. The whole stack is read through `baseInsulinReq`
and trusted as-is.

| Mechanism | V4 location | V5 treatment |
|---|---|---|
| DynISF V1 formula `1800 / (TDD × ln(target/insulinDivisor + 1))` | OpenAPSBoostV3MLG3Plugin.kt | inherited |
| 7D-only TDD with W8H pull-down rule | OpenAPSBoostV3MLG3Plugin.kt + V3 architecture | inherited |
| TDD-anchored EMA sensitivity (ratio = EMA τ=3h on tdd_24h/tdd_7d) | OpenAPSBoostV3MLG3Plugin.kt; see `boost_tdd_ema_sensitivity.md` | inherited |
| Autosens | AAPS standard | inherited |
| Hour-of-day basal rates | profile | inherited |
| Hour-of-day ISF (if user has it) | profile | inherited |
| TempTargets | AAPS | inherited via `target` term |
| `delta_accl` denominator floor `max(abs(shortAvgDelta), 2.0)` | V3 input preprocessing | inherited verbatim |

**Hard requirement on future maintenance.** A maintainer reading the V5 source's
`baseInsulinReq` field must understand it is **NOT vanilla oref**. If anyone
swaps that to oref's standard ISF formula, V5 silently loses DynISF + 7D TDD +
EMA sensitivity — a regression invisible to unit tests but visible in production
hypos. The KDoc on `DetermineBasalBoostV5` and `AggressionBudget` calls this
out explicitly; do not soften that wording.


## Exercise mode classification — INHERITED ENTIRELY

V5 does not reimplement exercise detection. V4's HR-augmented classifier (Karvonen
zones + step fusion, 7 modes: VIGOROUS_AEROBIC, RESISTANCE, ACTIVE, STRESS,
INACTIVE, RESTING, POST-EXERCISE_RECOVERY) runs unchanged. V5 reads:

- The resulting `profile%` and `target_bg` (which feed `baseInsulinReq` —
  modes 1–6 are handled transparently)
- A boolean `in_post_exercise_window` (the one effect not in baseInsulinReq —
  drives `postExerciseRecoveryModifier`)

| V4 detection logic | V4 location | V5 use |
|---|---|---|
| HrActivityCalculator (Karvonen zones) | plugins/aps/.../openAPSBoost/HrActivityCalculator.kt | reused |
| StepService (step counting) | plugins/aps/.../openAPSBoost/StepService.kt | reused |
| Activity / Inactivity / Stress / VigorousAerobic / Resistance / Resting modes | OpenAPSBoostV3MLG3Plugin.kt:600–803 | reused |
| 5/6 incident BG-rising override (v4.5 Design 9) | OpenAPSBoostV3MLG3Plugin.kt | not needed in V5 — meal_signal_score's delta+accl components dominate when a meal climb starts; mode persistence in classification doesn't affect V5's dose pipeline |


## Things that V5 explicitly does NOT do

- **Doesn't add a dawn-phenomenon adjuster.** Dawn coverage = profile concern (hour-of-day basal rates / hour-of-day ISF). V5 dose pipeline contains zero "what hour is it" amplification.
- **Doesn't reimplement carb handling.** COB > 0 cycles flow through `baseInsulinReq` (which already accounts for COB) and trigger an immediate COMMITTED state.
- **Doesn't override TempTargets, autosens, or any sensitivity-related setting.**
- **Doesn't try to identify meal type** (carbs vs fat-protein, fast vs slow). Treats all meals the same; the back-off in RECOVERING is the universal mechanism for "BG is now under control."


## V4 mechanisms with no V5 home (dropped)

None at the time of V5 launch. Every V4 mechanism is either subsumed,
inherited, or held for backtest decision (deferred). If a future task drops
something, add it here with the dropping commit's hash and rationale.


## Per-user calibration policy

V4 had ~40+ user-facing settings affecting dosing. V5 launches with **at most 3**
new user-facing knobs (Aggression, Hypo Caution, optional Sensitivity). All
~14–15 internal constants (score weights, transition thresholds, action
multipliers, iobHeadroomBrake curve points) are HARDCODED at release, calibrated
once via the multi-user backtest harness with GroupKFold-by-user. Per-user
calibration is a deferred capability, not a launch feature.

If a maintainer wants to expose an internal constant as a user setting, the
question is "does per-user variation genuinely help, AND can the user reasonably
choose a value?" If either answer is no, leave it hardcoded.


## How to use this document

When you see V5 source and wonder "where did Tier 5 go?":
1. Search this doc for the V4 mechanism name.
2. Find the V5 home.
3. Read the V5 source at that location with this mapping in mind.

Do NOT scatter `// removed: Tier 5` comments through V5 source. The migration
context lives here; per-removal rationale lives in commit messages. V5 source
should describe V5, not remember V4.
