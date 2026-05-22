# Boost ML + V5 Shadow Build — A Quick Read

*What this build of AndroidAPS does, why it does it, and what changes for the person actually wearing the pump.*

---

## The short version

This build takes Boost and adds two things on top: a small set of **machine-learning components that make hypos slightly less likely and meal-rises slightly less stressful**, and a **silent observer** called V5 that watches every cycle without changing what the pump does. Boost's DynISF formula, its tier ladder, and the way it sizes microboluses are unchanged in spirit. The additions sit alongside, contribute information, and occasionally apply a brake. Nothing about the underlying dose calculation has been replaced.

A user who flashes this APK and does nothing else will see two new fields in their Nightscout dashboard immediately (`mlHypoRisk`, `mlMealLikely`), seven more once the silent observer wakes up (`boostV5_*`), and gradually notice that the pump trims its corrections during moments when a low looks likely. Nothing else changes visually. There is no new setting to tune. There is no new mode to enable.

---

## Who this is for

People who already run Boost and want the safety benefit of probabilistic hypo prediction without changing the dosing logic they're already calibrated against. There is no medical claim here — these are improvements at the margins, not a redesign.

People who don't run Boost should stay on whatever they're using.

---

## What's different

Boost decides what to dose every five minutes by looking at the current blood glucose, the trend, the insulin on board, and a few derived quantities; picking one of eight tiers of response — from a small correction at the gentle end to a strong COB-triggered bolus at the aggressive end; and sizing the microbolus accordingly.

That logic stays the same in this build. What's added is a small layer of context that asks, before any dose is delivered: *given everything we know about the next four hours, is this dose the right size and shape?*

Concretely, four things are different:

### 1. A hypo-risk forecast every five minutes

A small machine-learning model — trained on a 28-user cohort contributing roughly three million decision cycles between them — looks at the eight inputs the algorithm has already calculated, and produces a single probability: the chance that blood glucose will dip below 70 mg/dL in the next four hours. The model was validated on five further users who weren't in the training set; the numbers came out essentially where the cross-validation said they would (more on this below).

The forecast is published to Nightscout in real time as `mlHypoRisk`, a number between 0 and 1. Watching it for a day or two builds intuition: it rises overnight as insulin on board accumulates, spikes after a meal correction, falls during exercise.

### 2. A meal-rise forecast every five minutes

The sister model. Same inputs, different question: what's the chance blood glucose will rise by at least 50 mg/dL in the next 90 minutes? This is what an unbolused (or under-bolused) meal usually looks like on a CGM. The model fires when a meal-shaped climb is starting — whether the carbs were entered or not. Published to Nightscout as `mlMealLikely`.

The meal model is the more reliable of the two. Across the five out-of-cohort validation users, it scored above the published cross-validation baseline on every single one. The meal signal turns out to be more universal than the hypo signal — a rising glucose curve with the right IOB shape looks the same across most physiologies.

### 3. Two brakes that act on those forecasts

This is where the dose actually changes. When `mlHypoRisk` rises above 0.3, every microbolus the algorithm wants to deliver is multiplied by a scaling factor that ramps from 1.0 down to 0.0 as the risk approaches 1.0. The size of the brake is published as `mlRiskScale` so it can be inspected after the fact.

When `mlHypoRisk` exceeds 0.6, a second brake kicks in: Boost's aggressive tiers (the higher tiers responsible for the larger correction doses, including the strong-acceleration tiers and the percent-scale tier) are skipped, and the algorithm falls through to one of the two conservative tiers (Enhanced oref1 or Regular oref1). The pump still doses if dosing is warranted — it just won't fire one of the larger response modes when the hypo forecast is loud.

Both brakes are strictly downward. Neither makes any cycle more aggressive than Boost would have been by itself. They can only soften.

### 4. A pre-meal-tier hold that delays a small set of marginal doses

When blood glucose starts climbing from near target with no logged carbs — the exact pattern of an unannounced meal whose carbs haven't been entered yet — the algorithm now holds the gentler tiers (Percent Scale, Acceleration, Enhanced oref1, Regular oref1) for one to three cycles, waiting for one of three signals to release the hold:

- Acceleration becomes deterministic (`delta_accl > 10`).
- Glucose climbs above 160 and is still rising (the safety backstop).
- The meal model says a meal is likely (`mlMealLikely > 0.5`).

If Boost's strong-acceleration tiers (the ones that fire for genuine meal-shaped climbs) become eligible during the hold, they fire normally — those aren't gated. The hold only affects the gentler tiers, which would otherwise fire small SMBs into what might turn out to be a fast-carb rebound rather than a real meal climb.

This was the most carefully-validated piece of the upstream work: 0 strong-tier doses were blocked across 20 real-meal events, and 80% of fast-carb pre-rescue over-doses were suppressed.

### 5. A small set of additional tweaks

The build also includes a refinement to the high-BG aggressive tier so that it keeps firing during sustained climbs into the 200s+ even after acceleration has plateaued, and a more responsive fast-carb release so that a genuine spike isn't held back by the rebound-protection logic for too long. These are the kind of small refinements that came out of months of incident reviews on the development branch.

---

## What V5 is — and why it's silent

V5 is a clean-slate redesign of the Boost decision logic, sitting on a different architectural principle: a three-phase **Observe → Confirm → Commit** state machine rather than the existing tier ladder. It's been running in production-shadow form on the developer's pump for several weeks. It is *not* used to dose insulin in this build.

What V5 actually does on this branch is **watch**. Every five-minute cycle, V5 sees the same inputs the active algorithm sees, computes what it would have done, and publishes seven fields to Nightscout describing that hypothetical decision:

| Field | What it means |
|---|---|
| `boostV5_score` | A confidence number between 0 and 1: how strongly V5 thinks a meal-like event is happening |
| `boostV5_state` | The state machine's current state — IDLE, OBSERVING, CONFIRMED, COMMITTED, or RECOVERING |
| `boostV5_age` | How many cycles V5 has been in the current state |
| `boostV5_budget` | V5's accumulated "aggression budget" in units of insulin |
| `boostV5_actionMult` | The action multiplier V5 would have applied for the current state |
| `boostV5_finalDose` | The microbolus V5 would have delivered (in units) — a direct comparison against the actual delivery |
| `boostV5_gateReduction` | Which safety gates fired, if any |

The point of running V5 silently is straightforward: **calibration data**. V5 has already had a first round of calibration based on a week of shadow data from a single pump — refining the conditions under which it confirms a meal, sharpening its safety gate, raising its post-hypo floor, and adding a detection mechanism for slower meal-shaped climbs that the original formula missed. The details of those four refinements are described in the next section. The next stage is multi-user shadow observation — collecting score distributions and confirmed-state behaviour across several users so the remaining calibration can be retuned against a broader population before V5 is ever asked to drive a pump.

If something goes wrong inside V5 — a bug, a corner case, a thread it doesn't expect — the error is caught and logged. The active algorithm carries on as if V5 wasn't there. V5 cannot affect dosing; the path through which dosing decisions are made does not consult V5.

V5 also does not appear in the AndroidAPS plugin list. Users cannot enable or disable it. It runs because the active Boost plugin calls it at the end of every cycle.

---

## V5 calibration so far — what shadow data taught us

The first version of V5 ran in shadow form for eight days and emitted enough Nightscout data to see, in retrospect, where its judgement was off. Four refinements came out of that week. None of them changes how the pump doses — they only change how V5's silent observer reaches its conclusions. The reason they appear in this build is that the multi-user shadow data this build is meant to gather will only be useful if V5 itself is making sensible decisions.

### 1. Confirming a meal on the peak score, not the current score

V5's state machine moves through stages: it watches a developing rise (`OBSERVING`), and if the meal-likelihood score crosses a threshold while in that state, it commits to a meal (`CONFIRMED`). The original implementation required the score to be above the threshold on the exact cycle the transition was evaluated. The trouble is that real meal scores wobble — they peak briefly, dip on a single-cycle noise spike, and recover. A user could see the score touch 0.66 for one cycle, drop to 0.61 on the next, then climb back, and V5 would never confirm because no single cycle had everything aligned.

The refinement: V5 now remembers the highest score it saw during the `OBSERVING` window and uses *that* peak — rather than the current cycle's instantaneous score — for the transition decision. The CONFIRMED threshold was also relaxed from 0.66 to 0.55, reflecting that the underlying score distribution turned out to be lower than the original tuning anticipated.

The practical effect: V5's `boostV5_state` will reach `CONFIRMED` several times per day during normal meal patterns, rather than the once-every-other-day rate the original tuning produced.

### 2. Looking 30 minutes ahead, not four hours, for the safety floor

V5 has a hard safety gate that refuses to commit a dose if the minimum predicted blood glucose value is below a safety floor. The original wiring read the minimum over the full four-hour prediction horizon, which is also what the active algorithm displays. The problem is that the four-hour-tail of the IOB-only forecast routinely dips into the 30s and 40s even when the next 30 minutes is comfortably in range — that's an artefact of how oref projects forward, not an actionable risk. Reading it caused V5's hard gate to fire on roughly half of all cycles.

The refinement: V5 now takes the minimum over the next 30 minutes only — across all four prediction series (IOB, UAM, ZT, COB). That's the window in which a basal cutoff issued now could actually prevent a hypo. The longer-tail forecast remains visible elsewhere; V5 just doesn't gate against it any more.

The practical effect: V5's `boostV5_gateReduction` will report `HARD:min_guard_bg` far less often. It still fires when blood glucose is genuinely projected low over the next 30 minutes — that's the point.

### 3. Staying responsive to meals after a recent hypo

The score formula includes a "recently low" penalty that down-weights the score when blood glucose has been below 70 in the last hour. The original penalty took the score's recent-low contribution to zero for roughly four hours following any hypo episode — meaning if a meal arrived during that window, V5 was structurally blocked from confirming it regardless of how strong the meal signal was.

The refinement: the penalty is still present (a recent hypo should make V5 more cautious), but it now floors at 0.4 rather than 0.0. The recent-low signal is still meaningfully down-weighted, but it no longer wholly extinguishes the meal-detection ceiling. The hypo damper from the ML hypo-risk model and the 30-minute safety gate provide the right counterweights when an actual meal does need to be flagged post-hypo.

The practical effect: users whose time-below-70 sits in the typical 3–7% range will see V5 continue to track meals across the post-hypo windows that used to be a deaf zone.

### 4. Catching the slow meal that the velocity signal misses

V5's score is sensitive to two velocity signals: the current cycle's blood-glucose delta, and the percentage acceleration of that delta. Both saturate at fairly high values, calibrated for the sharp climb shape of a fast-carb meal — perhaps 4 mg/dL per minute. A slower meal, climbing at maybe 1.5–2 mg/dL per minute (mixed-carb, fat-stacked, or grazing), can rise 100 mg/dL over two hours without triggering either velocity-based score component strongly enough to confirm.

The refinement: V5 now also tracks the **cumulative rise over the last 30 minutes**. A cumulative rise of 60 mg/dL or more saturates this new component fully; 20 mg/dL contributes nothing; the contribution is linear in between. This term is **sustained** — it doesn't dip on a single-cycle delta wobble the way the velocity components do — which means it lifts the peak score V5 sees during the meal window and makes the OBSERVING → CONFIRMED transition reachable for slow climbs that the original formula missed.

The practical effect: V5 will now confirm meal events for the kind of meal shape (mixed carbs, slow climb) that real-world meals frequently produce — not just the textbook fast-carb spike.

---

## Recap — what these four refinements together mean

None of the four changes how the pump doses. They change which patterns V5 picks out as meal-shaped events, how often it confirms them, and how it phrases its safety hesitation. The data published to Nightscout by V5 will become a richer record of real-world meal and hypo patterns across the users running this build — which is exactly the data needed to push V5 further along its path from silent observer to alpha-stage active algorithm.

---

## A second silent observer — the ISF shadow

Boost has a long history of debating one specific question: should the sensitivity ratio (the number that says "the user is currently 10% more sensitive than their 7-day baseline, so dose accordingly") be the **instantaneous** value, or should it be **smoothed** to dampen short-term noise? The publicly-released Boost takes the instantaneous value. A later development branch settled on a smoothed-with-a-3-hour-exponential-average approach. Both have plausible arguments behind them. Neither has been A/B tested cleanly because changing the sensitivity calculation changes everything downstream and no two days are comparable.

This build adds a way to test it without changing dosing. Every cycle, the algorithm now computes **both** the instantaneous sensitivity ratio it actually uses and the smoothed ratio it would have used under the alternative, and publishes both to Nightscout for direct comparison. Like V5, the shadow path is observation-only; the dose the pump delivers is determined by the instantaneous value as before.

Seven fields appear in Nightscout per cycle:

| Field | What it means |
|---|---|
| `isfShadow_ratioRaw` | The instantaneous tdd_24h / tdd_7d ratio — the same number Boost actually uses today |
| `isfShadow_ratioEma` | The 3-hour exponentially-smoothed version of the same ratio |
| `isfShadow_warmup` | A 0.0–1.0 number that climbs to 1.0 over the first five days of EMA history. While warming up, the shadow leans toward "no smoothing" so the user isn't comparing against an unsettled smoother. |
| `isfShadow_variableSens` | What `variable_sens` would have been if the smoothed ratio had been used (mg/dL/U) |
| `isfShadow_insulinReq` | What this cycle's `insulinReq` would have been |
| `isfShadow_microBolus` | What the delivered microbolus would have been |
| `isfShadow_deltaPct` | A single-number summary: `(shadow / actual − 1) × 100`. Negative means the smoothed version would have given more insulin this cycle; positive means it would have given less. |

The interesting field to watch is `deltaPct`. In steady-state — TDD this week broadly matches TDD this month — it sits near 0%. When TDD is rising fast (heavy day, illness, bigger meals) the instantaneous ratio responds immediately and the smoothed ratio lags — so `deltaPct` goes mildly **negative**, meaning the smoothed approach would have delivered slightly more insulin. When TDD is falling fast (active day, smaller meals, fasting) the opposite holds and `deltaPct` goes mildly **positive**. The two ratios diverge most during the first few hours of a TDD shift; over a 24-hour window they converge.

Over a week of observation, plotting `isfShadow_deltaPct` against actual outcomes (time-in-range, time-below-70, peak-after-meal) will tell whether the smoothing approach would have improved the user's day or simply produced different doses with similar results. If `deltaPct` ranges within ±5% and outcomes look similar, the instantaneous approach is a perfectly reasonable choice. If `deltaPct` swings to ±15-25% and bigger swings correlate with hypos or highs, the smoothing approach has a real argument behind it.

Note that this is a single-cycle counterfactual, not a full simulation. The shadow says "for this cycle, with everything else held equal, what would the smoothed approach have produced?" It does not re-simulate the entire day under the alternative — that would require running a parallel pump in parallel time, which obviously isn't possible. Differences accumulate over a day in ways the shadow can't see, but in practice over a 24-hour window they remain small (a percent or two) because the ratio difference is small at most points in time.

Like V5 shadow, the ISF shadow does not appear as a setting. It runs invisibly. The fields just show up in Nightscout.

---

## What the user sees and doesn't see

**Visible immediately in Nightscout**: `mlHypoRisk`, `mlMealLikely`, `mlRiskScale`, `mlMealG3Released`, `mlG3ReleaseSource`, plus the seven `boostV5_*` fields and the seven `isfShadow_*` fields. Useful for understanding what the algorithm is thinking; not useful for tuning anything (these aren't user-controllable knobs).

**Visible occasionally in the algorithm's "reason" text**: notes like `ML risk scale 65%: SMB 0.45 → 0.29`, or `pre-UAM uncertainty hold: gentler tiers suppressed`, or `Fast-carb conditions met but delta 12.4 > 10 override`. These appear when the brakes or the hold actually fire.

**New `boostTier = "NONE"` entries** during the pre-meal hold — distinct from baseline NONE cycles. When the hold is suppressing the gentler tiers, no SMB is delivered for that cycle, and the tier reads NONE with a hold-active explanation.

**Three new V5 settings** in `Boost V5 (PRE-ALPHA)` — Aggression, Hypo Caution, and Meal-detection Sensitivity. These are knobs for the shadow observer's calibration, not the active algorithm. Most users should leave them at their defaults (all 1.0). Changing them only affects what V5 would have done if it were dosing; it cannot change actual dosing.

**Not visible**: any change to the user's profile settings, basal rates, ISF, CR, or targets. The build doesn't touch profile calibration. Users who have spent time tuning their profile for Boost should find that work is preserved.

---

## Why this is built the way it is

The decision to keep the DynISF formula identical to Boost is deliberate. Users have spent time calibrating against that formula. Replacing it would require all those users to retune. The additions decide *what* the algorithm should do with the dose it has computed — not *how* the dose is computed in the first place.

The decision to run V5 silently rather than asking users to opt into a beta reflects the principle that calibration data should be gathered before active deployment, not during it. V5 will graduate to alpha — actively driving doses for at least one user — only after its score distribution and CONFIRMED threshold have been verified against multi-user data. That data only accumulates if V5 is observing.

The decision to validate everything against five real users before any code shipped reflects the principle that cross-validation numbers are estimates, not guarantees. The five out-of-cohort users came through the transfer test confirming that the ML models generalise as advertised — mean hypo AUC 0.679 against a cross-validation baseline of 0.680, mean meal AUC 0.771 against a baseline of 0.738. Those numbers said: ship it.

---

## What this build does not do

It does not change the user's basal rates, ISF, CR, target, max IOB, or max bolus. It does not change profile switching. It does not change exercise mode, sleep mode, or any of the time-of-day behaviours. It does not introduce new alerts or notifications. It does not change the way carbs are entered or boluses are confirmed.

It also does not replace the user's responsibility to enter meals as accurately as practical. The meal model is a backup, not a substitute. The hypo model is a brake, not a forecast the user should act on directly. The active algorithm still uses real glucose, real IOB, real time, and real announced carbs to make decisions.

---

## What to expect after flashing

In the first few hours, very little will look different. Nightscout will start showing the new fields, glucose will continue trending the way it normally does, and the algorithm will continue dosing the way it normally does. If the user happens to be at moderate hypo risk during that window, they may notice a slightly smaller-than-usual SMB and an `ML risk scale 75%` note in the reason text. If they happen to start a climb shortly after a meal they forgot to enter, they may notice a one-or-two-cycle delay before the algorithm starts firing — that's the pre-meal-tier hold engaging and then releasing once the meal-shaped climb confirms.

Over a few days, the cumulative effect is what's worth watching: time-below-70 should be modestly lower, peak-after-meal values should be roughly similar, time-in-range should be modestly higher. None of these effects is dramatic. They were never supposed to be. The algorithm was already doing the right thing most of the time. The additions described here are about the tails — the cycles where Boost would have over-dosed near a hypo, or under-dosed at the start of a real meal, or held back while a genuine spike was beginning.

V5's fields will populate from the first cycle. There will be no visible effect from V5 in dosing terms because V5 doesn't drive anything. Anyone watching it on a dashboard will see the state machine moving between IDLE, OBSERVING, and CONFIRMED — landing in CONFIRMED several times across a typical day, alongside real meal events. With the four refinements described above in place, the score will exhibit a recognisable shape: sustained rises during meals, transient bumps during noise that don't reach CONFIRMED, and cleaner behaviour around recent hypos.

The ISF shadow fields will also populate from the first cycle, but `isfShadow_warmup` will start near 0.0 and climb toward 1.0 over five days. During that period the shadow is leaning conservative and the `deltaPct` numbers will be artificially small. After day five they reflect the actual smoothing-vs-instantaneous difference and become the comparison worth looking at.

---

## In summary

Boost decides what to dose. This build adds a probabilistic check, a brake, a pre-meal hold, two small refinements for sustained climbs, a silent observer (V5) that's preparing the ground for the next generation of the algorithm — refined across four calibration adjustments based on real-world shadow data — and a second silent observer (the ISF shadow) that lets the smoothed-vs-instantaneous sensitivity-ratio debate be resolved with the user's own data. The user sees a small number of new dashboard fields and a slightly more cautious response near hypos. They do not see a different algorithm. The dose calculation is the same. The safety logic is the same. The change is in judgement at the margins — the kind of refinement that's hard to feel cycle-by-cycle but adds up across thousands of cycles a week.
