# The case for retiring exponential smoothing in AAPS and replacing it with an Unscented Kalman Filter

**Thesis.** AAPS's exponential smoother is a fixed-weight heuristic that is dominated by an Unscented Kalman Filter (UKF) on every axis a smoother is meant to serve — accuracy against a known signal, one-step prediction, lag, jitter, and outlier rejection — and on real-world one-step prediction it is no better than doing no smoothing at all. Two independent UKF implementations have already appeared in the AAPS ecosystem, including one built by the exponential smoother's own authors. The recommendation is to deprecate exponential smoothing and adopt the more advanced of those UKFs.

Crucially, **the evidence here is reproducible by anyone, with no access to private data.** A committed, seeded benchmark ships a synthetic-CGM generator with *known ground truth*, so a reader can run one command and reproduce the ranking below. That is the standard this argument is built to meet.

This is a **sensing-layer** argument. It makes no claim about time-in-range or any dosing outcome — those are not identifiable from retrospective data without a glucodynamic simulator. It argues only that the UKF is a strictly better estimator of the glucose signal (and its rate of change) than the exponential smoother.

---

## 0. Reproduce it yourself

```
cd backtesting/scripts/2026-07-ukf-smoothing/repeatable
pip install -r requirements.txt      # numpy only for the default mode
python benchmark.py                  # synthetic, known ground truth, seeds 0..19, deterministic
```

`benchmark.py` first runs a 9-case parity self-test (the ported UKF must reproduce the shipped Kotlin unit-test behaviours, or it aborts), then scores four smoothers — persistence (no smoothing), exponential (AAPS today), and the two UKFs — against a synthetic signal whose truth is known. A `--mode real --csv <file>` path lets anyone repeat it on their own CGM export; `--db` uses a local TimescaleDB. The numbers below come straight from that script.

## 1. What AAPS ships today

The `ExponentialSmoothingPlugin` is a weighted blend of first- and second-order exponential smoothing — the code is labelled "TSUNAMI DATA SMOOTHING CORE" — governed by four hard-coded constants (`o1_a=0.5`, `o2_a=0.4`, `o2_b=1.0`, `o1_weight=0.4`). By construction it has:

- **no noise model** — the same smoothing whether the sensor is quiet or noisy;
- **no state** — it estimates a level, not a level *and* a rate; it emits `trendArrow = NONE`, discarding the trend entirely;
- **no outlier, compression-artifact, or gap handling** — a spike or dropout is smoothed like any other point;
- **inherent, fixed lag** — second-order exponential smoothing trails every real move.

These are structural, not tuning, limits. A fixed-weight level smoother cannot both track a fast meal rise and reject sensor jitter, and it cannot report a trend it never estimates.

## 2. The evidence — two reproducible tests, agreeing

### 2a. Synthetic, known ground truth (anyone can run; the primary statement)

Because the true signal is known, we can measure the thing you can never measure on real CGM: **RMSE of each smoother's output against the truth.** 20 seeds × 3 days, realistic glucose dynamics + calibrated sensor noise + injected compression artifacts. Lower is better.

| smoother | RMSE vs **truth** | one-step RMSE | artifact absorbed¹ | lag² | stable-window jitter |
|---|---|---|---|---|---|
| persistence (no smoothing) | 8.64 | 8.68 | 1.00 | +0.61 | 45.4 |
| exponential (AAPS today) | 8.13 | 9.73 | 0.90 | **+5.42** | 31.3 |
| tsunami UKF | 9.00 | 9.75 | 1.11 | +1.65 | 47.1 |
| **v4 UKF (RTS + chi²)** | **6.12** | 9.42 | **0.71** | **+0.58** | **13.3** |

¹ fraction of an injected compression dip that passes into the output (lower = better rejection; >1 = amplified). ² signed tracking offset on fast transitions, mg/dL (higher = more lag).

The **v4 UKF recovers the true signal best by a wide margin** — 25% better RMSE-vs-truth than exponential (6.12 vs 8.13) and 32% better than the tsunami UKF. It also **rejects** compression artifacts (absorbs 0.71) while exponential passes most through and the tsunami filter *amplifies* them. And it does this with the **least lag and least jitter of all four**. Exponential's defining failure is visible here: its lag (+5.42) is roughly **nine times** the v4 UKF's, from second-order ringing.

### 2b. Real cohort (corroboration)

Nine closed-loop users' raw CGM, ~356k one-step samples. No ground truth here, so the metric is **one-step-ahead predictive RMSE** — a two-sided penalty that punishes both lag and noise-chasing.

| smoother | one-step RMSE | vs persistence | lag | jitter |
|---|---|---|---|---|
| persistence (no smoothing) | 5.88 | — | +0.00 | 13.4 |
| exponential — **shipped (level-only)** | ~8.4 | **~43% WORSE** | +4.36 | 17.4 |
| exponential — best-case trend variant | 6.15 | −4.7% WORSE | +4.36 | 17.4 |
| tsunami UKF | 5.57 | +5.2% better | +1.25 | 14.1 |
| **v4 UKF** | 5.71 | +2.8% better | **−0.09** | **9.4** |

The exponential smoother as **shipped** (level output, `trendArrow = NONE`) predicts the next reading ~43% *worse* than doing nothing. Even if you generously hand it a best-case trend forecast it never ships, it is still ~5% worse than persistence. **A smoothing stage whose output predicts worse than its own raw input is not extracting signal — it is adding lag.** Both UKFs beat persistence; the v4 UKF does so with the lowest lag and lowest jitter of anything tested.

**The two tests agree** on the ranking (v4 UKF best; exponential worst on lag and no better than raw on prediction). They differ on one point, stated plainly: on the *smooth synthetic* truth, persistence is a very strong one-step baseline that none of the smoothers beat — which is exactly why RMSE-vs-truth, not one-step, is the primary synthetic statement. The generator was **not** tuned to force agreement.

## 3. Exponential is dominated on every axis

| property | exponential (today) | v4 UKF |
|---|---|---|
| accuracy vs known truth (synthetic) | 8.13 | **6.12 (+25%)** |
| one-step prediction (real) | ≤ persistence (worse than raw) | better than raw, lowest jitter |
| lag on transitions | ~4–9× the UKF's | ~0 |
| trend / rate output | none (`trendArrow = NONE`) | principled velocity estimate → trend arrow |
| adaptivity to sensor noise | none (fixed weights) | adaptive measurement noise, learned online |
| outlier / artifact rejection | none (passes ~90%) | chi-squared gating + absolute limit (passes ~70%) |
| sensor-change / gap handling | none | event-based reset; gap segmentation |
| uncertainty estimate | none | full state covariance |

There is no metric on which the exponential smoother is preferable. The usual defence of a simple filter — "cheap and predictable" — fails, because its predictable behaviour is to lag, and its output is no better than the input it was given.

## 4. The field has already converged on the UKF

This is not one team's preference. Two **independent** UKF implementations for AAPS smoothing now exist, and the convergence includes the exponential smoother's own lineage:

1. **The tsunami project's adaptive UKF** (`AdaptiveSmoothingPlugin`, introduced 2026-04-29). Tsunami *is* the origin of the exponential "smoothing core" AAPS ships — and the same project moved on to a 2-state UKF. **The authors of the current smoother built a UKF to supersede it.**

2. **A more advanced UKF in an AAPS-3.4 fork** (`UnscentedKalmanFilterPlugin`, 2026-05-12 — two weeks newer, ~1,300 lines): an **adaptive UKF with RTS (Rauch–Tung–Striebel) backward smoothing** (the optimal two-pass smoother), **chi-squared outlier detection** (threshold 15.13, 99.99%, + a 65 mg/dL absolute limit), event-based sensor-change reset, timestamp-corruption handling, and a **unit-test suite**. The head-to-head above shows this is not just feature-richer but **measurably better** than the tsunami UKF — +32% RMSE-vs-truth, better artifact rejection, less lag, far less jitter.

Two independent groups arriving at the same state-space answer — with the incumbent's own authors among them — is strong corroboration that exponential is a legacy choice, not a considered one. Note that **exponential still ships in both lines**; nobody has removed it. This paper argues it is time to.

## 5. What the UKF gives AAPS

A UKF is the textbook fit: a noisy scalar measurement (CGM) of a smoothly-evolving latent state (true glucose and its rate). In one pass it provides what AAPS now approximates with cruder machinery:

- **A denoised level *and* a velocity/trend estimate** the exponential smoother throws away — directly usable by rise/fall detection, trend arrows, and prediction.
- **Adaptive noise handling** — tightens on a quiet sensor, loosens on a noisy one.
- **Explicit outlier/artifact rejection** — chi-squared gating keeps a single bad reading or a compression low from propagating, with uncertainty (not a guess) deciding.
- **Optimal smoothing where latency allows** — the RTS backward pass gives the best estimate of past points for display/analysis, while the forward filter serves the real-time path.

## 6. Honest limitations (what this argument does *not* claim)

- **No dosing or TIR claim.** Retrospective data cannot yield the counterfactual glucose trajectory under a changed input signal. The case rests on estimator quality, which *is* identifiable — out-of-sample on real data, and against ground truth in simulation.
- **Smooth-vs-raw is a separate question.** On real one-step prediction the UKF's edge over raw is modest (+3–5%); its clearest wins are lag, jitter, artifact rejection, and — in simulation — truth-recovery. But none of that rescues exponential, which *loses* to raw. The honest fallback for anyone not adopting the UKF is **no smoothing**, not exponential.
- **One-step vs truth diverge on smooth data.** Stated in §2b: where the underlying signal is smooth, persistence is hard to beat at one-step. This is why RMSE-vs-truth is the primary synthetic metric.
- **Parity of the benchmark port.** The Python UKF used for scoring mirrors the Kotlin operation-for-operation and passes the 9-case shipped unit-test behaviours as a parity oracle; exact JVM↔CPython float parity is not separately asserted. The *relative* ranking is robust — every smoother sees the identical stream.
- **Changing the default changes behaviour.** Users on exponential would get a different input signal; stage the transition (below).

## 7. Recommendation

1. **Deprecate exponential smoothing** as a recommended option. It is dominated on every axis and its shipped output predicts no better than no smoothing — keeping it as the "middle" choice steers users into a worse-than-raw filter.
2. **Adopt the v4 UKF** (adaptive R, chi-squared outliers, RTS backward smoothing, unit-tested) as the smoothing implementation. It is the validated winner of the head-to-head, not merely the feature-richest.
3. **Stage the rollout:** ship the UKF selectable and off by default → run the committed reproducible benchmark (and a golden-vector Kotlin↔Python parity test) in review → move the recommended default from exponential to the UKF, with a migration note. Retain **no-smoothing** as the honest simple fallback.

---

*Reproducible evidence: `backtesting/scripts/2026-07-ukf-smoothing/repeatable/` (seeded four-way benchmark, synthetic + real, `benchmark.py`, `results.md`). Original cohort backtest and event overlays: `backtesting/scripts/2026-07-ukf-smoothing/`. Method and identification constraints per `CLAUDE.md` and `backtesting/STATISTICAL_METHODS.md`.*
