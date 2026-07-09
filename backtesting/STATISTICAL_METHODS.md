# Statistical methods — Boost algorithm development

_Reference for the statistical / ML methods used across the Boost backtesting and analysis work. Written for a technical audience (quant / statistician). No patient data here — methods only._

## The central constraint (read first — it shapes every choice)

We have **no structural glucodynamic simulator**. For any *dosing-policy* change we therefore cannot generate the counterfactual BG trajectory, so a clean "simulate policy A vs B" backtest is impossible. The whole methodology is built around this:

- **Prediction / detection** questions (does a pattern exist? does it forecast an event?) are validated cleanly **out-of-sample** — no counterfactual needed.
- **Policy** questions (does changing a dosing knob help?) are handled by **pricing the change against observed outcomes**, not simulating trajectories, plus **within-subject and matched-baseline designs** to approach identification.
- We are explicit that an observational effect size is **associational** unless a within-user or randomised design supports it.

This — identification, not model sophistication — is the genuine bottleneck.

## Where these sit — lab vs loop (read this before the method list)

**The inference is in the lab, not the loop.** What actually doses is deterministic; all the statistical machinery below is offline decision-support that decides *what gets built*. Do not read the method list as runtime behaviour.

| Method | Where | Role |
|---|---|---|
| State machine, multipliers, caps, composed brake-floor | **Runtime (loop)** | The dosing logic — deterministic |
| Rule-based sleep detector (HR + steps + clock) | **Runtime (loop)** | Night-mode gating — thresholds, not a model |
| Auto-config per-user knob derivation (from own TBR/dosing history) | **Runtime (loop)** | Sets hypoCaution/caps/aggression — deterministic formula, once/periodic (not online, not Bayesian) |
| `mlHypoRisk`, `mlMealLikely` (pre-trained models) | **Runtime (loop)** | The only *learned* components live — fixed functions at inference |
| — hard constraint — | | **No training / online inference in the dose path** |
| LightGBM + grouped-by-user CV | **Offline (lab)** | Does a signal exist / forecast, leakage-safe |
| Empirical-Bayes Beta-Binomial, asymmetric-loss lower-bound gating, hierarchical partial pooling | **Offline (lab)** | The "Bayesian decision layer" — analysing recurring structure |
| Policy-replay pricing, permutation tests, OLS confound-adjustment, regime decomposition, matched-window hazard | **Offline (lab)** | The "inference piece" — GO/NO-GO on proposed levers |
| Exercise-prep Beta-lower-bound gate | **Specced, not built** | Would move Bayesian decision *into* runtime — shadow-log first |
| Night-mode mixed-effects A/B | **Pre-registered, not run** | Needs instrumentation first |
| V7 residual-tracker / sens-frozen innovation | **Shadow** | Computes, doesn't dose |

The shipping controller is deterministic (state machine + caps + a deterministic per-user auto-config derivation) with two pre-trained ML models at inference; every Bayesian/inferential method below is offline tooling; the two places we'd move inference into the loop are gated behind shadow-logging or a pre-registered RCT first.

## 0. Learned runtime & shadow components — their statistical derivation

The runtime *does* contain learned quantities (HR baselines, sleep timing) and the V7 shadow contains a distributional model. **None are parametric/posterior Bayesian models** — they are **robust order statistics, circular statistics, and asymmetric-loss decision theory** — chosen deliberately to be simple and hard to break in a safety-critical loop.

| Component | Derivation | Tier |
|---|---|---|
| V7 sizer "p10/p90" | empirical windowed quantiles of regime-conditioned forecast residuals → minimum-expected-asymmetric-loss dose | **Shadow** |
| HR learning (resting / daytime baseline) | per-session p10, median across ≥7 sessions → personalises Karvonen HRR | **Runtime** |
| Sleep learning (bedtime / wake) | circular mean of onset/wake clock-minutes | **Runtime** |

### V7 distributional sizer — the "p10/p90" model (shadow only)

A **windowed empirical predictive distribution + a decision rule**, not a fitted distribution:

- **Substrate (`V7ResidualTracker`):** each cycle records the IOB-only forecast `projBG(t+h) = bg + BGI5·(h/5)`, `BGI5 = −iob_activity·variable_sens·5`. On horizon maturation it pools the **residual = observed − projected**, keyed by **regime × horizon**. Regimes {QUIET_FLAT, MEAL, NIGHT} (V5 state + CGM flatness + hour) are a *debiasing* split — unannounced-carb absorption otherwise biases the residual +12–38 mg/dL.
- **The quantiles:** each pool is a ~21-day windowed, size-capped sample (oldest-evicted) exposing **5 empirical percentiles (5/25/50/75/95) via linear interpolation**. Cold pools (<60 samples) return null → abstain. So "p10/p90" = **empirical order statistics of the recent regime-conditioned forecast error**, not a parametric fit.
- **The decision:** for a candidate dose it forms a predictive BG distribution (point projection + residual quantiles) as a **piecewise-linear inverse CDF through the 5 knots, discretised at 19 equal-probability points (5–95%)**, and picks the dose minimising an **asymmetric linear loss** `E[R·max(0,70−BG) + max(0,BG−140)]`, cost-ratio R ∈ {4,7,10}; grid search, first-minimum, hard cap/budget envelope.
- **In stats terms:** a **minimum-expected-asymmetric-loss (Bayes-risk) point decision under an empirical predictive distribution** — the most decision-theoretic object in the codebase, and it **doses nothing** (logs R4/R7/R10 to test formulation sensitivity to R).

### HR learning (runtime) — robust order statistics personalising a fixed formula

- **Learned resting HR** = `median` over sessions of each session's **sleep-period p10** (10th percentile), once ≥7 sessions accrue. The p10 is the quiescent floor (robust to movement spikes); the median-across-sessions is robust to outlier nights. **Learned daytime baseline** = same on the awake-period p10.
- These personalise the deterministic **Karvonen HRR**: `HRR% = (HR − HRrest)/(HRmax − HRrest)·100` → fixed zone thresholds (<30/30–40/40–60/60–80/>80). The only *learned* input is the personalised `HRrest`/daytime baseline — robust percentile estimation, not inference.

### Sleep learning (runtime) — circular (directional) statistics

- Learned bedtime and wake are the **circular mean** of clock-minute-of-day values (each minute → angle on the unit circle, vector-sum, `atan2` back), **because clock times wrap at midnight** — the mean of 22:00 and 02:00 must be 00:00, not 12:00; a naive arithmetic mean is wrong. Requires ≥ min sessions; HR baselines within the same tracker use the median-of-p10s above.

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
