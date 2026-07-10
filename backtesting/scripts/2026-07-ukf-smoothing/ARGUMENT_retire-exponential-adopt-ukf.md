# The case for retiring exponential smoothing in AAPS and replacing it with an Unscented Kalman Filter

**Thesis.** AAPS's exponential smoother is a fixed-weight heuristic whose forward signal is measurably *worse than doing no smoothing at all*. An Unscented Kalman Filter (UKF) dominates it on every axis that matters — one-step predictive accuracy, lag, trend estimation, and robustness to outliers and gaps — and two independent UKF implementations have already appeared in the AAPS ecosystem, including one that the original smoothing authors built to replace their own exponential code. The recommendation is to deprecate exponential smoothing and adopt a UKF as the smoothing implementation.

This is a **sensing-layer** argument. It makes no claim about time-in-range or any dosing outcome — those are not identifiable from retrospective data without a glucodynamic simulator. It argues only that the UKF is a strictly better estimator of the glucose signal (and its rate of change) than the exponential smoother, which is the job a smoothing plugin exists to do.

---

## 1. What AAPS ships today

The `ExponentialSmoothingPlugin` is a weighted blend of first- and second-order exponential smoothing — the code is labelled "TSUNAMI DATA SMOOTHING CORE". Its behaviour is governed by four hard-coded constants (`o1_a=0.5`, `o2_a=0.4`, `o2_b=1.0`, `o1_weight=0.4`). It has, by construction:

- **no noise model** — the same smoothing is applied whether the sensor is quiet or noisy;
- **no state** — it estimates a level, not a level *and* a rate; it emits `trendArrow = NONE`, discarding the trend entirely;
- **no outlier, compression-artifact, or gap handling** — a spike or a sensor dropout is smoothed like any other point;
- **inherent lag** — second-order exponential smoothing trails any real move, and its lag is fixed, not adaptive.

These are not tuning problems; they are structural. A fixed-weight level smoother cannot both track a fast meal rise and reject sensor jitter, and it cannot report a trend it never estimates.

## 2. The empirical case: the exponential smoother predicts worse than raw

We backtested a faithful UKF against the shipped exponential smoother on **real CGM from 9 closed-loop users, ~356,000 one-step samples, February–July 2026** (raw sensor stream, 5-minute cadence). The primary metric is **one-step-ahead predictive RMSE**: from each smoother's state at time *t*, predict the *raw* reading at *t+1*, and score against what actually arrived. This is ground-truth-free and, crucially, it is a two-sided penalty — it punishes noise-chasing *and* lag. A smoother that tracks the signal beats it; a smoother that distorts the signal loses to it.

| smoother | pooled one-step RMSE (mg/dL) | vs raw persistence |
|---|---|---|
| naive persistence (no smoothing) | 5.87 | — |
| **exponential (AAPS today)** | **8.39** | **43% WORSE** |
| UKF | 5.56 | 5% better |

The headline is not that the UKF wins by 5% over doing nothing. **It is that the exponential smoother loses to doing nothing — by 43%, on every one of the 9 users** (its RMSE is worse than persistence for all of self, A–H). A smoothing stage whose output predicts the next reading *worse than its own raw input* is not extracting signal from noise; it is injecting lag and distortion into the loop.

Against the exponential smoother directly, the UKF improves one-step RMSE by **+27% to +47% per user** (pooled **+34%**), and it trails fast transitions **~3.5× less** (signed tracking offset +1.25 mg/dL vs +4.39 mg/dL on windows steeper than 2 mg/dL/min).

On stable-window jitter, neither filter is primarily a denoiser — but the exponential smoother is the worse of the two here as well: its second-order term *increases* stable-window variance by ~36% through ringing/overshoot (it buys fewer direction-reversals at the cost of amplitude). So the one thing a smoother is naively assumed to do — quieten a flat trace — it does poorly.

## 3. Exponential is dominated on every axis

| property | exponential (today) | UKF |
|---|---|---|
| one-step predictive accuracy | **worse than raw** | better than raw, +34% over exponential |
| lag on transitions | ~3.5× the UKF's | small (velocity state + rapid-rise handling) |
| trend / rate output | none (`trendArrow = NONE`) | principled velocity estimate → trend arrow |
| adaptivity to sensor noise | none (fixed weights) | adaptive measurement noise, learned online |
| outlier / spike rejection | none | chi-squared gating (99.99%) + absolute limit |
| sensor-change / gap handling | none | event-based learning reset; gap segmentation |
| uncertainty estimate | none | full state covariance |

There is no metric on which the exponential smoother is preferable. The usual defence of a simple filter — "it is cheap and predictable" — does not apply, because its predictable behaviour is to lag, and its output is worse than the input it was given.

## 4. The field has already converged on the UKF

This is not one team's preference. Two **independent** UKF implementations for AAPS smoothing now exist, and the convergence includes the exponential smoother's own lineage:

1. **The tsunami project's adaptive UKF** (`AdaptiveSmoothingPlugin`, branch `devTsuV35_1`). Tsunami *is* the origin of the exponential "smoothing core" AAPS ships. The same project moved on to a 2-state UKF (glucose + rate) with adaptive measurement noise and rapid-rise/hypo safety logic. **The authors of the current smoother built a UKF to supersede it.**

2. **An independent, unit-tested UKF plugin in an AAPS-3.4 fork** — a more mature implementation still: an **adaptive UKF with RTS (Rauch–Tung–Striebel) backward smoothing** (the optimal two-pass smoother, not merely a forward filter), **chi-squared outlier detection** (threshold 15.13, 99.99% confidence, plus a 65 mg/dL absolute limit), event-based reset on sensor change, timestamp-corruption handling, and a **9-case unit-test suite** (determinism, outlier dampening, gap segmentation, rising-trend, error-code flooring). ~1,300 lines, production-shaped.

Two independent groups, arriving at the same state-space answer from different starting points, with the incumbent's own authors among them, is strong external corroboration that the exponential smoother is a legacy choice rather than a considered one.

## 5. What the UKF actually gives AAPS

A UKF is the textbook fit for this problem: a noisy scalar measurement (CGM) of a smoothly-evolving latent state (true glucose and its rate). It provides, in one pass, what AAPS currently has to approximate with separate, cruder machinery:

- **A denoised level *and* a velocity/acceleration estimate** — a principled trend the exponential smoother throws away, which downstream logic (rise/fall detection, trend arrows, prediction) can consume directly.
- **Adaptive noise handling** — it tightens on a quiet sensor and loosens on a noisy one, instead of one fixed compromise.
- **Explicit outlier and artifact rejection** — chi-squared gating and compression heuristics keep a single bad reading or a compression low from propagating, with uncertainty (not a guess) driving the decision.
- **Optimal smoothing where latency allows** — the RTS backward pass gives the best estimate of past points for display/analysis, while the forward filter serves the real-time path.

## 6. Honest limitations (what this argument does *not* claim)

Per the project's own methodology discipline, the boundaries matter:

- **This is UKF-vs-exponential, not smooth-vs-raw.** The UKF beats raw persistence only modestly (+5% pooled) and is not primarily a denoiser. But that does not rescue the exponential smoother — it *loses* to raw. If anything, the honest fallback for a user who does not adopt the UKF is **no smoothing**, not exponential.
- **No dosing or TIR claim.** Retrospective data cannot yield the counterfactual glucose trajectory under a changed input signal. The case rests entirely on estimator quality, which *is* identifiable out-of-sample.
- **Absolute numbers owe a parity check.** Our backtest UKF is a Python mirror of a Kotlin port; bit-exact JVM↔CPython parity has not been unit-tested (the *relative* ranking is robust to sub-ULP drift — every predictor sees the identical stream). The independent fork UKF *is* unit-tested but has not been benchmarked on this cohort. Both gaps are closable before any default flip.
- **Changing the default changes behaviour.** Users currently on exponential would get a different input signal. The transition should be staged: ship the UKF as a selectable option, validate on cohort data with the parity test in place, then move the recommended default.

## 7. Recommendation

1. **Deprecate exponential smoothing** as a recommended option. It is dominated on every axis and its forward signal is worse than no smoothing; keeping it as the "middle" choice misleads users into a worse-than-raw filter.
2. **Adopt a UKF as the smoothing implementation.** The RTS variant (adaptive R, chi-squared outliers, backward smoothing, unit-tested) is the stronger candidate for the real-time-plus-display path; the lighter forward-only variant is adequate where simplicity is preferred.
3. **Stage the rollout:** UKF selectable and off by default → Kotlin↔Python parity/golden-vector test + within-cohort benchmark → move the recommended default from exponential to UKF, with a migration note. Retain **no-smoothing** as the honest simple fallback.

---

*Evidence: `backtesting/scripts/2026-07-ukf-smoothing/` (backtest script, per-user tables, event overlays). Method and identification constraints per `CLAUDE.md` and `backtesting/STATISTICAL_METHODS.md`.*
