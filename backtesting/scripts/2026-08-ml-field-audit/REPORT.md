# Field audit of the two shipped LightGBM models (2026-08-13)

*Reproduce: `ml_field_audit.py` against the local TimescaleDB refreshed to t=now. Ten Boost
users, all available history, one decision row per user per five-minute bucket. Intervals are
cluster bootstrap over users, 2000 resamples.*

## Why this exists

Two pre-trained LightGBM models ship inside the engine and are consumed on the dose path. Both
were trained in early 2026 on a foreign Nightscout cohort and validated at training time. Neither
has been scored against the telemetry of the people now running them. The models are the only
learned components in the shipping controller, so their field behaviour is the one ML question
that has direct dosing consequence.

## What the models actually are

Read from the metadata assets rather than from the documentation, which is stale in three places.

| | hypo risk (v12) | meal likelihood |
|---|---|---|
| trees / depth | 100 / 5 | 50 / 4 |
| features | 53 (17 static + 36 windowed lag0..5) | 8 |
| target | CGM < 70 sustained ≥ 15 min | peak ≥ current + 50 mg/dL |
| horizon | 90 min | 90 min |
| training rows / users | 3,007,589 / 32 | 2,978,062 / 28 |
| GroupKFold AUC | 0.8391 | 0.7342 |
| LOUO AUC | 0.8317 | 0.7375 |

The hypo model's KDoc, the V3ML reader document and the ML branch README all state the output is
"P(hypo event in next 4h)" with the event defined as two consecutive readings below 70. That was
the target of the model shipped on 2026-04-10 and retired on 2026-06-06, when the horizon moved to
90 minutes and the label became a sustained-15-minute one. The consuming code and its comments
were not updated. Anyone reasoning about the 0.30 and 0.60 thresholds from the documentation is
reasoning about the wrong quantity.

## Coverage and how often the thresholds bite

Scores populate 7.7 to 25.0 per cent of decision rows per user, the remainder being cycles from
engine eras before the models were wired or where the model had not loaded.

| user | cycles | scored | risk > 0.30 | risk > 0.60 | meal > 0.50 |
|---|---|---|---|---|---|
| A | 103,828 | 8,857 | 33.7% | 0.58% | 18.0% |
| B | 60,347 | 11,068 | 37.4% | 1.36% | 23.3% |
| C | 26,571 | 6,462 | 47.2% | 2.14% | 42.6% |
| D | 95,695 | 7,389 | 50.7% | 5.26% | 30.5% |
| E | 60,935 | 10,976 | 44.7% | 1.42% | 23.9% |
| F | 93,383 | 9,609 | 27.0% | 0.52% | 23.5% |
| G | 33,493 | 8,375 | 5.7% | 2.26% | 24.5% |
| H | 26,324 | 2,799 | 0.93% | 0.07% | 15.9% |
| I | 5,715 | 1,230 | 0.57% | 0.00% | 13.7% |
| tim | 103,352 | 13,147 | 20.6% | 1.12% | 15.9% |

The damper engages on between 0.6 and 51 per cent of scored cycles depending on the user, which is
a wide enough spread to matter. The tier downgrade is rare everywhere.

## Discrimination in the field

Scored against each model's own target, per user, pooled with a cluster bootstrap over users.

| | pooled AUC | 95% CI | training LOUO |
|---|---|---|---|
| hypo risk, sustained ≥15 min within 90 min | 0.582 | [0.517, 0.643] | 0.8317 |
| hypo risk, against the target the KDoc claims | 0.521 | [0.472, 0.569] | — |
| meal likelihood | 0.728 | [0.702, 0.760] | 0.7375 |

The meal model replicates. Its field AUC is within a point of its leave-one-user-out figure and
within four points of the six-user transfer test run in May 2026, which is about as clean a
replication as this programme has produced. Per-user values run 0.619 to 0.846 and every user is
above 0.6.

The hypo model does not. It clears chance, but at 0.582 against a training figure of 0.832 the
gap is a quarter of the available range, and per-user values run from 0.442 to 0.725 with four of
ten users at or below 0.5.

## The baseline that decides it

The same rows and the same label, scored with trivial predictors.

| | AUC | 95% CI |
|---|---|---|
| hypo model | 0.582 | [0.517, 0.643] |
| current glucose, negated | 0.594 | [0.524, 0.640] |
| eventualBG, negated | 0.515 | [0.426, 0.611] |
| IOB | 0.484 | [0.426, 0.558] |
| model minus current glucose | −0.010 | [−0.067, +0.050] |

A 53-feature gradient-boosted model, given six cycles of history, does not outrank the single
number that is its own first feature. Against the meal model the same comparison goes the other
way: 0.728 against 0.586 for eventualBG, a difference of +0.143 with an interval of [+0.076,
+0.218], so that model is doing real work.

## Horizon

The same scores against nearer and further horizons, with the glucose baseline at each.

| horizon | base rate | model | 95% CI | −BG | model − (−BG) |
|---|---|---|---|---|---|
| 30 min | 0.005 | 0.663 | [0.606, 0.767] | 0.836 | −0.154 [−0.229, −0.068] |
| 60 min | 0.017 | 0.607 | [0.552, 0.667] | 0.665 | −0.054 [−0.117, −0.002] |
| 90 min | 0.034 | 0.582 | [0.517, 0.643] | 0.594 | −0.010 [−0.067, +0.050] |
| 120 min | 0.052 | 0.562 | [0.504, 0.612] | 0.559 | +0.005 [−0.045, +0.070] |
| 180 min | 0.086 | 0.545 | [0.490, 0.590] | 0.512 | +0.034 [−0.017, +0.106] |
| 240 min | 0.121 | 0.541 | [0.488, 0.587] | 0.493 | +0.049 [−0.004, +0.116] |

Absolute discrimination falls with horizon for both, as expected. What does not behave as hoped is
the comparison: at the horizons where a hypo forecast could still be acted on, the model is
significantly worse than reading the current glucose, and it only draws level once the horizon is
long enough that neither predictor carries much.

## Calibration, and the on-policy confound

Observed event rate by predicted decile, with the damper the engine would have applied at that
score. `mlHypoRiskScale` is 1.0 below risk 0.30 and falls linearly to 0.50 at risk 1.0.

| decile | n | predicted | observed | damper |
|---|---|---|---|---|
| 0 | 9,102 | 0.015 | 0.017 | 1.000 |
| 1 | 8,115 | 0.020 | 0.021 | 1.000 |
| 2 | 7,655 | 0.026 | 0.018 | 1.000 |
| 3 | 7,459 | 0.033 | 0.031 | 1.000 |
| 4 | 7,854 | 0.047 | 0.042 | 1.000 |
| 5 | 7,770 | 0.087 | 0.066 | 1.000 |
| 6 | 8,001 | 0.253 | 0.034 | 1.000 |
| 7 | 7,984 | 0.339 | 0.027 | 0.972 |
| 8 | 8,065 | 0.417 | 0.030 | 0.916 |
| 9 | 7,907 | 0.541 | 0.059 | 0.828 |

The lower six deciles are well calibrated. The top four are not: the model predicts 25 to 54 per
cent and observes 3 to 6 per cent, against a base rate of 3.4 per cent. Its confident region is
where it is most wrong, and that region is exactly where the dosing consumption lives.

The obvious objection is that the model caused this by suppressing the events it predicted, which
would deflate both the calibration and the AUC. The damper column prices that objection. Between
decile 5 and decile 6 the observed rate halves, from 0.066 to 0.034, while the mean predicted score
in decile 6 is 0.253, below the 0.30 threshold, so the damper was 1.0 in both and no insulin was
withheld on account of the model in either. A treatment that did not occur cannot explain the
step. At deciles 7 to 9 the damper does engage, but at 3, 8 and 17 per cent reductions in the
budget, which is not enough to take a genuine 40 per cent event rate down to 3.

The confound is real and it biases the field AUC downward. It is not large enough to account for
the collapse from 0.83 to 0.58, and it cannot account for the inversion at the top of the range at
all.

## Verdict

The meal model is doing what it was built to do and the field figures support leaving it alone.
Confidence SOLID: replicated out of cohort twice, monotone calibration, beats its baseline with an
interval clear of zero.

The hypo model, as consumed today, is not distinguishable from reading the current glucose, and is
worse than reading the current glucose at the horizons where acting is still possible. Its top
four deciles are anti-calibrated. Confidence SOLID for the negative on discrimination-over-baseline,
which is robust to the label definition, the horizon and the choice of baseline. PROVISIONAL on the
cause.

Three explanations remain open and the audit does not separate them. The feature vector assembled
on device by `BoostMlFeatureBuilder` and its persisted six-cycle ring buffer may not reproduce the
training-time features, which would degrade a 53-feature model while leaving the 8-feature meal
model untouched, and that asymmetry fits what is observed. The training cohort may differ from this
one in a way the leave-one-user-out estimate did not capture. Or the v12 label, sustained
hypoglycaemia rather than any excursion, may simply be rarer and harder here.

The first of those is checkable and is the obvious next step: log the on-device feature vector for
a period, score it offline with the same JSON model through the Python LightGBM path, and compare
against the value the engine published. If the two disagree, the model was never the problem.

## What not to conclude

This does not say the damper is unsafe. It reduces insulin, never adds it, is floored at half the
budget, and sits under the composed floor at 30 per cent of baseline. The finding is that a
component believed to be discriminating is behaving like a noisy function of glucose, so the
restraint it applies is being applied for the wrong reason. That is a reason to fix the model or
retire the component, not a reason to expect harm from having run it.
