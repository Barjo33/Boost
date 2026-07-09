# Statistical methods — Boost algorithm development

_Reference for the statistical / ML methods used across the Boost backtesting and analysis work. Written for a technical audience (quant / statistician). No patient data here — methods only._

## The central constraint (read first — it shapes every choice)

We have **no structural glucodynamic simulator**. For any *dosing-policy* change we therefore cannot generate the counterfactual BG trajectory, so a clean "simulate policy A vs B" backtest is impossible. The whole methodology is built around this:

- **Prediction / detection** questions (does a pattern exist? does it forecast an event?) are validated cleanly **out-of-sample** — no counterfactual needed.
- **Policy** questions (does changing a dosing knob help?) are handled by **pricing the change against observed outcomes**, not simulating trajectories, plus **within-subject and matched-baseline designs** to approach identification.
- We are explicit that an observational effect size is **associational** unless a within-user or randomised design supports it.

This — identification, not model sophistication — is the genuine bottleneck.

## 1. Supervised prediction — gradient-boosted trees

- **LightGBM** binary classifiers: forward events (BG > 180 or < 70 at +60 min) and habitual-activity prediction. Config ≈ 350–400 trees, lr 0.03, num_leaves 15–31, min_child_samples 50, subsample/colsample 0.8.
- **Leakage control (load-bearing):** `GroupKFold` with the **user** as the group → no subject in both train and test. This is what makes "does feature block X add value" honest: cross-user *generalisation*, not per-person memorisation. Habit models additionally use a **temporal split** (train first 60% / test last 40%) to respect time ordering.
- Reported: out-of-sample AUC (forward-high 0.83, forward-low 0.78, activity habit 0.85), gain importance, and the **incremental OOS AUC** of adding a feature block over a baseline block. (This is how we showed the activity→hypo signal is *per-user*: it lifted in-sample and in gain rank but **not** in grouped-OOS AUC → it does not transfer across subjects → per-user thresholds, not a global model.)

## 2. Bayesian decision layer

"Bayesian" earns its place in the **action-under-uncertainty** layer, not in prediction (GBMs already predict well).

- **Empirical-Bayes / Beta-Binomial** for habitual-event rates: `P(event | weekday, time-bin)` as a Beta posterior per cell, shrunk toward the subject's base rate with a pseudo-count (α₀=β₀=1, shrink strength ≈ 20). Gives sensible rates for sparse (weekday × time) cells instead of 0/1 noise.
- **Decision under asymmetric loss:** anticipatory actions gate on the posterior **lower credible bound** (~90%), not the mean, because the loss is asymmetric (missed exercise → hypo ≫ false prep → mild high). The action fires only when the pattern is *reliably* present.
- **Hierarchical partial pooling** (James–Stein-style shrinkage) for per-(user, weekday) estimates such as bedtime onset: `μ = (k·weekday_mean + m·global_mean) / (k + m)` — borrows strength across a subject's weekdays when a cell is sparse.

## 3. Causal / inferential methods

- **Policy replay with observational pricing** (cap-stepper, slider-controller): deterministically walk a proposed control policy over the real per-cycle telemetry. Where a lever is a *known multiplier* on the dose (the sliders), compute the exact counterfactual **dose** — but never the counterfactual BG — and **price the insulin delta against observed** forward-lows/highs. Reported: revert-rate, good-vs-wrong insulin ratios, priced pre-low units. The honesty is in what is *not* claimed (no BG simulation).
- **Permutation testing:** cross-cohort comparison used a 5,000-draw permutation null on the platform coefficient → non-parametric p-value (p ≈ 0.27, NS).
- **OLS confound adjustment:** `TIR ~ platform + CV + meanBG` to strip case difficulty from a raw cross-cohort gap (+2.9 pp raw → +1.2 pp adjusted).
- **Subgroup / regime decomposition:** splitting an aggregate by time-of-day exposed that a flat +0.3 pp *daytime* average hid a **+13 pp overnight** advantage against a compensating post-breakfast deficit (anti-phase). Time-specific structure is far harder to explain by selection than a flat offset → it upgrades the causal read.
- **Mixed-effects, pre-registered (not yet run):** the night-mode isolation A/B plan is `overnight_TIR ~ arm + weekday + (1 | user)` on a **within-user night-randomised crossover**, sample size powered from the measured night-to-night TIR SD (~13 pp) → ~5–6 pp MDE at n ≈ 6–8 users × 4 wk. Pre-registered because it is the only design that isolates the mechanism from selection/basal-tuning confounds.
- **Matched-baseline / per-hour hazard analysis:** for time-to-event tails (post-exercise hypo), compare a **per-hour hazard to a matched-window baseline**, not a cumulative window to a fixed baseline. This caught a "delayed 2× ramp" that was purely a window-length artifact (true effect ≈ 1.2×, flat).

## 4. Methodological discipline

- **Out-of-sample everything**; grouped-by-subject CV to kill leakage.
- **Effect-size skepticism via matched baselines.** Multiple striking surface findings dissolved under a proper baseline: an attribution share that was proximate over-counting (a "34%" mechanism that was 90%-correct on audit); a cross-cohort TIR gap that was mostly selection; a post-exercise recovery "2×" that was window-length. Any un-baselined effect size is treated as provisional.
- **Within-subject > between-subject** wherever possible — the population is small (single-digit to ~30 users) and self-selected, so most cross-user results are hypothesis-generating, not confirmatory.
- **Absolute safety gates sit *underneath* every statistical decision.** Hard time-below-range thresholds (kill-switches) can only tighten; the statistics rank options, they never override a safety floor.

## Summary for a skeptical reader

The modelling is deliberately modest (GBMs + empirical Bayes + standard linear/mixed models + permutation tests) because the binding constraint is **identification, not fitting power**. Absent a simulator, we (a) validate prediction cleanly OOS, (b) price policy changes against observed outcomes with explicit counterfactual caveats, (c) demand matched baselines before believing an effect size, and (d) route anything that would actually change dosing through a pre-registered within-user randomised design before it ships.
