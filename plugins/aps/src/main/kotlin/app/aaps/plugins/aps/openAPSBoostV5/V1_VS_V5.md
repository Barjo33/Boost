# Boost V1 vs Boost V5

A comparison of the original Boost (V1) and the new V5 redesign.

> **Status (2026-07):** this document was written during V5's development. V5 has since
> graduated to production as the user-facing **"Boost V6"** plugin — see
> [Currently — what V5 is and isn't](#currently--what-v5-is-and-isnt-updated-2026-07)
> and the main [README](../../../../../../../../../../README.md) for the current picture.
> The design rationale below is unchanged and still authoritative.

V1 reference: `~/StudioProjects/AndroidAPS/master/openAPSBoost/` (the canonical
V1 source). V5 reference: `~/StudioProjects/Boost-AAPS-core/openAPSBoostV5/`.


## TL;DR

- **V5 adds 3 headline settings (plus an Advanced screen of dose caps);
  most of V1's settings still apply.** V5
  replaces the dose-sizing dials inside `determineBasal` (boost_scale,
  insulin_req_pct, percent_scale_factor, bolus_cap, etc.) with hardcoded
  calibrated values. The upstream environment settings (sleep-in,
  activity %, post-exercise, dynISF velocity, etc.) keep working unchanged
  because they act before `determineBasal` runs.
- **V5 is more decisive about meals.** Where V1 has 8 different dosing rules
  it picks between, V5 has one explicit "is this a meal?" check and one
  dosing decision based on it.
- **V5 brings ML safety V1 doesn't have.** V5 uses a hypo-risk machine-learning
  model to dampen doses when a low is forecast — V1 has no equivalent.
- **V5 has a dose floor.** V1 can drive a dose to near-zero when several
  brakes stack; V5 guarantees at least 30% of the calculated insulin gets
  delivered (assuming safety checks pass).
- **The insulin-sensitivity calculation is the same.** V5 doesn't change how
  Boost figures out your sensitivity — it inherits all that from V1's
  existing pipeline. So your TDD-based ISF, autosens, hour-of-day basal
  rates, etc. all still apply.
- **V5 ships as the "Boost V6" plugin.** Selecting "Boost V6" makes the
  state machine drive the SMB; selecting plain "Boost" runs V1 dosing with
  the V5/V6 decision in shadow (logged to Nightscout, never dosed).
  Shadow-first is the supported path for anyone but the developer.


## Why V5 was built

After releasing the current Boost (4.1.5), I spent a lot of time trying
to take it forward. Each step added another layer of overlay onto the
existing eight-tier ladder — more sophisticated meal detection, extra
safety gates, ML-backed signals, additional brake mechanisms. Each one
was a sensible local fix in isolation, but together they complicated the
decision path, made the code harder to reason about, and made each next
addition harder to fit in safely.

A few specific patterns kept appearing in that process:

- **Binary thresholds occasionally just-miss real meals.** A tier
  condition expects, say, `uamBoost1 > 1.20` and `uamBoost2 > 2.0`; a
  real meal arrives at 1.15 and 1.96 and the algorithm falls through to
  a slower tier. BG climbs unchecked for tens of minutes before a
  fallback path catches up. The tier ladder requires exact matches; real
  meal patterns vary.
- **Brakes stack multiplicatively with no overall floor.** When two or
  three damping multipliers fire on the same cycle, they multiply
  together. Under stacked high-risk the dose can drop to a single-digit
  percentage of what oref calculated as needed. In post-hypo-recovery
  scenarios this leaves real meals dramatically under-dosed.
- **Tier flicker on noisy signals.** When BG delta dips momentarily
  mid-rise (e.g. CGM noise), the tier conditions can fall out of
  UAM_BOOST or ACCELERATION and back into PERCENT_SCALE or
  ENHANCED_OREF1 — different dose formulas on what is functionally the
  same meal. Each cycle starts from scratch with no memory of "we were
  already tracking this rise."
- **Activity-mode persistence into a meal.** Walk, stop walking, eat
  breakfast. The walking-detection lookback decays slowly, keeping
  Activity-mode profile percentage and target compression in place
  during the meal climb. Slower dosing during the rise; BG overshoots.
  The mode logic doesn't know "the user is now eating."
- **The settings burden.** The preferences screen has 60+ entries, and
  roughly 30 of them are Boost-specific knobs the user is expected to
  tune per-individual. Most users end up with sub-optimal settings they
  don't know to change.

At a certain point I decided to step back. Instead of yet another layer
on top, I went back to basics and started from scratch with what I'd
learned. V5 keeps the parts of the existing Boost that genuinely work
(the sensitivity stack, the exercise classifier, the post-exercise
recovery detection, the calibration block, all the user's settings
upstream of the dosing decision) and replaces the dosing decision itself
with a single coherent design.

### The goals

V5 was designed to:

1. **Eliminate binary cliffs in meal detection.** Replace tier conditions
   with a continuous 0–1 score, so just-miss patterns can still reach the
   right state through accumulated signal weight rather than needing
   exact threshold matches.
2. **Make the meal hypothesis a first-class state.** Encode "test then
   commit" as an explicit state machine — IDLE → OBSERVING → CONFIRMED
   → COMMITTED → RECOVERING — with state persisted across cycles. The
   algorithm reasons about which phase a meal is in instead of
   re-evaluating from scratch each cycle.
3. **Bound the safety composition.** Cap the brake stack with a hard
   minimum (30% of oref's calculated need); collapse multiple brakes
   on the same hypo-risk metric into a single graduated curve.
4. **Reduce the settings burden.** Move the dose-sizing dials to
   hardcoded values calibrated once at release; expose only knobs where
   users genuinely have a basis to choose a value.
5. **Make decisions reconstructable.** Six NS fields fully describe any
   V5 cycle's reasoning so behaviour can be analysed after the fact
   without grepping logs.

### What V5 explicitly is not trying to do

- **Not faster dosing on average.** V5's post-exercise modifier and ML
  hypo-risk damping make it more conservative in many situations.
  The goal is *correct* dosing, not maximum dosing.
- **Not a clinical superiority claim.** The evidence is one developer's
  ~5 months of active use plus a small shadow cohort — real-world
  experience, not a demonstrated TIR / TBR improvement vs the current
  Boost (see the README's Testing & evidence section).
- **Not a replacement for sensitivity calibration.** DynISF, autosens,
  hour-of-day basal / ISF are all preserved unchanged. V5 trusts the
  user's existing sensitivity setup.
- **Not a "solve every Boost problem" claim.** Basal-side logic remains
  where it was. V5 redesigns the dosing **decision**, not the
  surrounding basal handling.


## What you'll notice as a user

### Settings

Most of V1's settings stay exactly as they are. V5 only replaces the small
group of dials that controlled V1's per-tier dose sizing.

**You keep:** sleep-in window, inactivity scaling, activity thresholds + %,
post-exercise recovery hours/scale/target, dynISF velocity, max IOB, max
basal, autosens, profile, ISF, CR, target ranges, TempTargets, HR zones,
calibration-block window — all unchanged. These act in the Boost plugin
upstream of the dosing decision; V5 inherits them automatically.

**You stop using** (V5 replaces these with its own logic):
`boost_insulin_req_pct` (divisor), `boost_scale`, `boost_percent_scale_factor`
(sliding scale BG 108→180), `boost_bolus_cap` and the per-tier toggles. V5
has its own action multipliers per state and its own dynamic dose cap.

**You gain three new dials**, and the third is reserved for future use:

| Setting | Default | Range | What it does |
|---|---|---|---|
| **Aggression** | 1.0 | 0.7–1.3 | Scales V5's catch-up dose at the moment it commits to "this is a meal." Lower is gentler, higher is more aggressive. |
| **Hypo Caution** | 1.0 | 1.0–2.0 | Strengthens the brake when the ML model thinks a hypo is likely. Raise it if you have hypo unawareness or recent severe lows. |
| **Sensitivity** | 1.0 | 0.8–1.2 | Reserved — currently has no effect. May ship in a future release if backtesting justifies. |

V5's internal numbers (score weights, state-machine thresholds, IOB-headroom
curve points) are hardcoded — calibrated once by the developers from
backtesting on a 19-user cohort. You don't tune them.

### Behaviour during a meal

The biggest behavioural difference. Picture a typical post-meal rise:

**V1's flow:** every 5 minutes, V1 checks 8 conditions in order. The first one
that matches picks the dosing formula. So a meal might start as "Tier 5
PERCENT_SCALE", then if it accelerates more becomes "Tier 6 ACCELERATION", then
falls back to Tier 7 if delta drops mid-rise, etc. The dose can change
discontinuously as you cross between tiers.

**V5's flow:** V5 watches the meal develop across cycles. The first time it
sees rising BG with positive acceleration it enters **OBSERVING** and
delivers a small **test dose** (30% of normal). If the rise continues for
2 cycles AND the projected BG goes well above target, V5 transitions to
**CONFIRMED** and delivers a **catch-up dose** (180% of normal — making up for
the test cycles). After that it goes to **COMMITTED** and doses normally
until BG starts falling, when it transitions to **RECOVERING** (40% of normal,
backing off as IOB takes effect).

In short: V1 reacts to each cycle in isolation; V5 has memory and follows the
meal through its phases.

### When V5 catches a meal V1 missed

V1's tiers have hard thresholds. If a real meal's signals just barely miss a
threshold (e.g., BG-rise velocity at 1.15 when the threshold is 1.20), V1
falls through to a slow tier and might dose 41 minutes late. This actually
happened on 2026-05-05 — V1 didn't deliver SMB for 41 minutes despite a real
meal climbing.

V5 doesn't have that binary cliff. The same input pattern accumulates "meal
likelihood score" over time, and once that score crosses a threshold V5
commits. Real meals don't usually need a binary trigger — they need a
sustained pattern, which is what V5 watches for.


## The design shift in plain language

V1 is a **decision tree**: at each cycle, walk through 8 if-else conditions,
take the first one that matches.

V5 is a **state machine + safety layer**:

1. **Watch.** Combine 6 signals (BG delta, acceleration, ML meal-likelihood,
   recent-low penalty, time of day, exercise state) into a single
   "is this a meal?" score from 0 to 1.
2. **Track.** Carry that score across cycles. Move through states:
   IDLE → OBSERVING (testing) → CONFIRMED (catch-up) → COMMITTED (sustain)
   → RECOVERING (back off) → IDLE.
3. **Calculate.** Take what oref calculated as needed insulin, apply two
   safety multipliers (ML hypo risk and post-exercise damping), enforce a
   minimum 30% floor.
4. **Decide.** Multiply by the state's action factor (0.3× for OBSERVING,
   1.8× for CONFIRMED, etc.). One number out.
5. **Check.** Pass the dose through ordered safety gates (low-BG predicted
   → zero, IOB high → damp, deceleration with high IOB → damp, etc.). The
   gates can only reduce the dose, never increase it.

The upshot: V5 makes one explicit decision per cycle based on a continuous
signal, with explicit safety damping. V1 makes 8 different decisions and
picks one.


## What stays the same

V5 redesigns the **dosing-decision layer** — what `determineBasal` does each
cycle. It does NOT touch the **environment layer** (the Boost plugin code
that prepares inputs before `determineBasal` runs). Most of V1's behaviour
lives in that environment layer and continues to operate identically under
V5.

**Inherited unchanged:**

- **Insulin sensitivity calculation.** DynISF V1 formula
  (`1800 / (TDD × ln(...))`), 7-day TDD with 8-hour pull-down, EMA-smoothed
  sensitivity ratio, autosens, hour-of-day basal rates, hour-of-day ISF.
  V5 reads the resulting `baseInsulinReq`; the user's `boost_dynisf_velocity`
  setting still applies.
- **TempTargets** — V5 reads them via the existing target pipeline.
- **The `delta_accl` formula** including its `max(|shortAvgDelta|, 2.0)`
  denominator floor.
- **Sleep-in window.** V1's `boost_sleep_in_hrs` + `boost_sleep_in_steps`
  set `boostActive = false` during the sleep window. That flag flows into
  `baseInsulinReq` exactly as before; V5 doesn't override it.
- **Inactivity scaling.** `boost_inactivity_pct` adjusts profile percentage
  during inactive periods (V1 plugin line 577). The adjusted profile feeds
  `baseInsulinReq`; V5 inherits the result.
- **Activity (exercise) profile/target compression.** `boost_activity_pct`
  and the activity step thresholds (5/15/30/60 min) modify profile% and
  target during exercise. Same upstream pathway.
- **Post-exercise recovery window detection.** V5 reads the existing
  Boost recovery-window flag (`v5_inPostExerciseWindow` on
  `OapsProfileBoost`). V5 doesn't reimplement HR-zone classification or
  step-fusion logic.
- **Boost active time window.** `boost_start_time` / `boost_end_time`
  produce a `boostActive` flag in V1's plugin. V5 inherits it the same way.
- **Hypo-rebound TempTarget auto-cancel.** V1's behaviour where a recovery
  TT gets cancelled when BG bounces back from a low. Operates outside V5's
  dose pipeline.
- **Calibration block** — 15-minute zero after a CGM calibration event.
- **HR integration** (`hrMaxBpm`, `hrRestingBpm`, etc.) — feeds exercise
  classification, which feeds the upstream pathway.

The mental model: V1's plugin shapes the inputs (profile%, target,
boostActive, recovery window). V5's algorithm core makes the dosing
decision. V5 changes the second part. The first part — and all the user
settings that drive it — continues to operate.


## What V5 brings that V1 doesn't have

These are genuinely new — V1 has no equivalent:

- **ML hypo-risk damping.** V5 uses a machine-learning model that estimates
  the probability of a hypo in the next 4 hours, and dampens the dose as
  that probability rises. The damping is graduated (smooth) rather than a
  binary skip. V1 has no ML at all.
- **ML meal-likelihood signal.** A second ML model contributes 20% weight
  to V5's meal-detection score. V1 has no equivalent.
- **Explicit meal hypothesis state.** V5 carries "what stage of a meal are
  we in?" across cycles. V1 is stateless — every cycle starts from scratch.
- **AggressionBudget hard floor (30%).** V5 guarantees that, before action
  multipliers and safety gates, the dose can't fall below 30% of what oref
  calculated. V1's brake stack can drive doses near zero when fast-carb
  protection fires.
- **Dynamic spike cap on every cycle.** V5 caps the dose at 2.5× the
  calculated need on every cycle. V1 has spike-override logic that fires
  only on Tier 8.
- **Graduated IOB headroom brake.** As IOB approaches your max, V5 smoothly
  reduces the dose (85% → 60% → 40% as IOB-fraction crosses 0.5/0.7/0.85).
  V1 has a hard cap on Tier 7 only.
- **6 NS observability fields.** Anyone reading your Nightscout deviceStatus
  sees V5's score, hypothesis state, age, budget, action multiplier, and
  any safety reductions for every cycle. V1 emits a tier name and the
  modulators are buried in console logs.


## What V5 replaces (dose-sizing logic inside `determineBasal`)

These are V1's dose-sizing dials and per-tier formulas that live INSIDE
`determineBasal`. V5's decision rule replaces them; the user no longer
touches these knobs when running V5:

- **`boost_insulin_req_pct`** — V1's divisor turning insulinReq into SMB.
  V5's per-state action multiplier (0.3 / 1.0 / 1.8 / 1.0 / 0.4) replaces it.
- **`boost_scale`** — multiplier on Boost insulin requirement inside V1's
  tier formulas. V5 has the **Aggression** knob (CONFIRMED-only) instead.
- **`boost_percent_scale_factor`** — V1's sliding-scale BG 108→180 logic
  (PERCENT_SCALE tier). V5 has no PERCENT_SCALE tier; this knob has no
  V5 analogue.
- **`boost_bolus_cap`** — V1's flat per-cycle SMB cap. V5's
  `dynamicSpikeCap` derives from `baseInsulinReq` rather than a flat user
  value (2.5 × baseInsulinReq).
- **`enableBoostPercentScale`, `enableCircadianISF`,
  `allowBoost_with_high_TT`** — V1 toggles for behaviour that V5 either
  doesn't have at all (PERCENT_SCALE) or handles differently (high-TT
  handling flows through `baseInsulinReq`).
- **The 8 tier formulas themselves.** V5 has one rule, not eight.

### Things V5 specifically *doesn't add*, by design

- **Time-of-day dose amplification** (a dawn-phenomenon adjuster). V5 has
  no such adjuster. Dawn coverage belongs in your AAPS profile (hour-of-day
  basal rates / hour-of-day ISF), not in the dosing algorithm. The user's
  existing dawn-adjusted profile flows through `baseInsulinReq` and V5
  doesn't multiply on top.
- **BG-range dose modifier** (a "dose more at high BG" multiplier). Already
  covered by `(eventualBG - target) / sens` in oref's standard
  calculation; explicit modifier would double-count.
- **Per-meal-type recognition** (carbs vs fat-protein, fast vs slow).
  V5 treats all meals the same; the RECOVERING state handles "BG is now
  under control" universally.


## Calibration philosophy

V1 expects the user to be a calibrator. You tune `boost_insulin_req_pct`,
`boost_scale`, the percent-scale factor, the dynISF velocity, etc. to match
your own physiology. The defaults are starting points; getting good results
generally requires per-user tuning informed by observed glycemic outcomes.

V5 inverts that: the developers calibrate once, and ship a single set of
hardcoded values. Calibration is done via a backtest harness with ±20%
sensitivity sweeps, run with GroupKFold-by-user (each user appears in either
train or test, never both) across 19 cohort users. Constants that don't
materially affect outcomes get challenged. The user gets a 3-knob settings
screen.

This isn't dogma — V5's design tenet is "users shouldn't have to set
hundreds of internal parameters to use Boost." If a parameter genuinely
needs per-user variation AND the user has a basis to choose a value, it's a
candidate for becoming a knob. If either condition fails, it stays
hardcoded.


## Currently — what V5 is and isn't (updated 2026-07)

V5 **graduated to production as the "Boost V6" plugin** after the shadow
acceptance gates passed. Two selectable plugins share one engine:

- **"Boost"** — V1 dosing, with the V5/V6 decision computed as a
  **sidecar shadow** every cycle: the same inputs (glucose status, IOB,
  ML predictions where present, sensitivity values, exercise state) are
  handed to V5, which logs its decision to `aapsLogger` with prefix
  `BoostV5_RT:` and to Nightscout deviceStatus under `boostV5_*` fields —
  without affecting what was dosed.
- **"Boost V6"** — the state machine drives the SMB. The override is
  gated: suppressed while asleep, capped at V1's would-dose outside
  CONFIRMED/COMMITTED, re-checked against the cumulative 60-minute SMB
  cap, and clamped to the system max-IOB. On first activation an
  auto-config seeds the knobs from the user's own 14-day history
  (suggestion-only — see the README, §4).

Shadow-first remains the supported onboarding for anyone but the
developer: run "Boost", watch the paired telemetry in Nightscout for a
couple of weeks, then decide.


---


## Deeper dive — for developers and curious users

The rest of this document is the technical detail. The summary above
captures the gist; the tables below are for people who want to look at the
specifics.

### V1's 8-tier ladder

```
Tier 1  COB_PRIMARY      lastCarbAge < 25 min, COB > 0
Tier 2  COB_SECONDARY    lastCarbAge < 40 min, delta > 5
Tier 3  UAM_BOOST        delta ≥ 5, shortAvgDelta ≥ 3, uamBoost1 > 1.2, uamBoost2 > 2
Tier 4  UAM_HIGH_BOOST   delta_accl > 5, BG > 180
Tier 5  PERCENT_SCALE    BG 110–180, delta > 3, delta_accl > 0
Tier 6  ACCELERATION     delta_accl > 25, delta > 4, BG > 110
Tier 7  ENHANCED_OREF1   delta > 0, delta_accl ≥ 0.5  (with IOB cap before formula)
Tier 8  REGULAR_OREF1    fallback (with spike override)
```

Modulators applied after tier selection in V1:

- `fastCarbScale` (0.3–1.0) — graduated brake on Tiers 3/5/6 when
  `recentLowBG < 100` or post-hypo reversal score positive.
- Spike override — raises Tier 8's cap on confirmed spikes.
- Hypo-rebound TT auto-cancel — cancels recovery TempTarget when BG climbs
  back from a low.

### V5's 3-phase pipeline

```
PHASE 1 — STATE ESTIMATION (no commitment)
  meal_signal_score(...)                — continuous 0–1 weighted sum of 6 signals
  mealHypothesis state machine step()   — 5 states with explicit transitions
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
    round(roundSMBTo) → dynamicSpikeCap (2.5×) → max(0)
```

### The 6 components of `meal_signal_score`

Each weighted; weights are hardcoded.

| Signal | Weight | What it represents |
|---|---|---|
| `delta` | 0.30 | Current 5-min BG rise |
| `delta_accl` | 0.16 | Acceleration of the rise |
| `mlMealLikely` | 0.20 | ML model's probability of a meal-driven peak |
| `notRecentlyLow` | 0.12 | Continuous penalty (0 at recentLowBG=70, 1 at ≥100) — replaces V1's binary fast-carb-rebound trigger |
| `mealTimeOfDay` | 0.10 | Smooth bumps near typical meal hours (8/13/19) |
| `notExercising` | 0.04 | Suppresses score when an exercise mode is active |

If `mlMealLikely` is null for ≥3 consecutive cycles (model load failure),
V5 drops its weight and renormalises the remaining 5 to compensate, so
the score ceiling stays consistent.

### State machine transitions

| From | To | Trigger |
|---|---|---|
| IDLE | OBSERVING | score ≥ 0.44 |
| OBSERVING | CONFIRMED | age ≥ 2 cycles AND score ≥ 0.66 AND eventualBG > target+50 |
| OBSERVING | IDLE | score < 0.36 for ≥ 2 cycles (hysteresis) |
| CONFIRMED | COMMITTED | after 1 cycle |
| COMMITTED | RECOVERING | delta_accl < -5 AND delta declining over last 2 cycles |
| RECOVERING | IDLE | delta < 0 OR score < 0.18 |

Reset to IDLE on: reboot, pump disconnect, loop suspend, profile switch,
time jump > 30 min.

### Action multipliers per state

| State | Multiplier | Notes |
|---|---|---|
| IDLE | 1.0 | Standard oref dose |
| OBSERVING | 0.3 | Test dose — encodes "test before commit" |
| CONFIRMED | 1.8 | Catch-up dose (×Aggression knob 0.7–1.3) |
| COMMITTED | 1.0 | Sustained meal dosing at baseline |
| RECOVERING | 0.4 | Backing off as IOB bites |

### Safety gate constants

| Gate | Trigger / curve |
|---|---|
| `iobHeadroomBrake` | iob_fraction < 0.5 → 1.0 (no brake); 0.5–0.7 → 0.85; 0.7–0.85 → 0.6; ≥ 0.85 → 0.4 |
| `decelerationBrake` | If `delta_accl < -10` AND `IOB > 0.5×max`: scale 0.5 |
| `postActionRiskCheck` | Re-runs ML hypo-risk model at projected (iob + dose); damps if projection > current + 0.15 AND > 0.40, floor 0.30 |
| `sensorQualityCheck` | 0.7 if sensor data flagged bad; otherwise 1.0 |
| `dynamicSpikeCap` | Final dose capped at 2.5 × baseInsulinReq |

### V1's brake stack vs V5's

V1:
```
final_smb = tier_dose × fastCarbScale       # only on Tiers 3/5/6, only when recentLowBG < 100
```

V5:
```
aggression_budget = max(
  0.30 × baseInsulinReq,                                                # hard floor — never below 30%
  baseInsulinReq × mlHypoRiskScale() × postExerciseRecoveryModifier()   # the chain
)
insulin_to_deliver = aggression_budget × meal_action_multiplier(state)
```

V5 explicitly **dropped** three multipliers from earlier proposals:

- `exerciseStateModifier` — would double-count V1's profile/target effects already in `baseInsulinReq`.
- `timeOfDayModifier` (dawn 1.1×) — dawn coverage belongs in profile basals, not the algorithm.
- `bgRangeModifier` — `(eventualBG - target) / sens` already scales with BG; minGuardBG handles low; CONFIRMED 1.8× and dynamicSpikeCap handle high.

### Observability — fields V5 emits to NS

```
boostV5_score        meal_signal_score (0–1)
boostV5_state        IDLE | OBSERVING | CONFIRMED | COMMITTED | RECOVERING
boostV5_age          cycles in current state
boostV5_budget       aggression_budget (U)
boostV5_actionMult   meal_action_multiplier
boostV5_gateReduction comma-separated summary of which Phase 3 gates fired
```

The observability test verifies that any V5 cycle is fully reconstructable
from these 6 fields plus the per-cycle inputs (within 0.05U dose tolerance
due to the SMB rounding step).

### Plugin status

| Aspect | V1 | V5 |
|---|---|---|
| Plugin class | `OpenAPSBoostPlugin` | `OpenAPSBoostV5Plugin` |
| Plugin folder | `openAPSBoost/` | `openAPSBoostV5/` |
| User-selectable as APS? | Yes | **No** — `showInList { false }`, `isEnabled() = false` |
| Dosing? | Yes (when selected) | **No** — sidecar shadow only |
| Currently runs? | Only if user selects it as APS | During development testing, alongside the active Boost variant — V5 is invoked at the end of the active variant's cycle |


## Related documents

- `boost_v5_redesign_proposal.md` (claude memory) — full architectural rationale
- `boost_dosing_pipeline_map_2026-05-02.md` (claude memory) — pre-V5 dosing-pipeline analysis (developer reference)
- `boost_v5_constants_calibration.md` (claude memory) — calibration sweep results
- `MIGRATION.md` (this directory) — source-level mechanism mapping for plugin maintainers
