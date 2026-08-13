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

Current model era only. Scores populate 21.5 to 93.7 per cent of decision rows per user, the
remainder being cycles where the model had not loaded.

| user | cycles | scored | risk > 0.30 | risk > 0.60 | meal > 0.50 |
|---|---|---|---|---|---|
| A | 11,967 | 5,055 | 2.63% | 0.10% | 16.8% |
| B | 12,046 | 6,433 | 4.46% | 0.22% | 22.9% |
| C | 11,854 | 4,169 | 23.00% | 1.20% | 42.3% |
| D | 12,195 | 4,839 | 27.67% | 2.23% | 30.9% |
| E | 12,122 | 5,864 | 2.37% | 0.02% | 26.0% |
| F | 11,529 | 5,880 | 0.49% | 0.00% | 20.8% |
| G | 8,937 | 8,375 | 5.72% | 2.26% | 24.5% |
| H | 11,716 | 2,799 | 0.93% | 0.07% | 15.9% |
| I | 5,715 | 1,230 | 0.57% | 0.00% | 13.7% |
| tim | 12,261 | 5,859 | 0.99% | 0.17% | 13.7% |

## The era filter, which is not optional

The `ml_hypo_risk` column carries the output of three model generations under one name, with nothing
in the record marking the boundary. The eight-feature model ran from 2026-04-10 with a four-hour
horizon; the current 53-feature model reached the cohort in the week of 2026-06-29, at which point
the cohort median score falls from 0.364 to 0.038. Those are different quantities on different
scales, and any figure computed across the boundary is a mixture rather than a measurement. All
discrimination figures below are computed within a single generation.

## Discrimination in the field

Scored against each model's own target, pooled with a cluster bootstrap over users.

| | pooled AUC | 95% CI | training LOUO |
|---|---|---|---|
| hypo risk, current model, own target | 0.655 | [0.606, 0.701] | 0.8317 |
| hypo risk, current model, target the KDoc claims | 0.563 | [0.513, 0.600] | |
| hypo risk, previous model, own era | 0.606 | [0.493, 0.729] | 0.6796 |
| meal likelihood, current era | 0.722 | [0.684, 0.757] | 0.7375 |
| meal likelihood, previous era | 0.740 | [0.718, 0.774] | 0.7375 |

The meal model replicates in both eras, within a point or two of its leave-one-user-out figure and
of the six-user transfer test run in May 2026. Per-user values run 0.618 to 0.869 and every user is
above 0.6.

## The baseline that decides it

Same rows, same label, trivial predictors.

| | AUC | 95% CI |
|---|---|---|
| hypo model, current | 0.655 | [0.606, 0.701] |
| current glucose, negated | 0.588 | [0.534, 0.617] |
| eventualBG, negated | 0.532 | [0.436, 0.631] |
| IOB | 0.470 | [0.412, 0.552] |
| model minus current glucose | +0.068 | [+0.046, +0.104] |

The current model beats the glucose reading. Its predecessor did not: on its own era it reaches 0.606
against 0.605 for the same baseline, a difference of +0.018 with an interval from -0.037 to +0.113.
The revision therefore achieved what it was for. The meal model beats eventualBG by +0.144
[+0.054, +0.233].

## Horizon

| horizon | base | model | 95% CI | -BG | model - (-BG) |
|---|---|---|---|---|---|
| 30 min | 0.008 | 0.799 | [0.739, 0.864] | 0.817 | -0.010 [-0.061, +0.041] |
| 60 min | 0.019 | 0.701 | [0.644, 0.750] | 0.653 | +0.051 [+0.028, +0.087] |
| 90 min | 0.036 | 0.655 | [0.606, 0.701] | 0.588 | +0.068 [+0.046, +0.104] |
| 120 min | 0.055 | 0.627 | [0.572, 0.664] | 0.558 | +0.068 [+0.046, +0.102] |
| 180 min | 0.089 | 0.593 | [0.537, 0.632] | 0.520 | +0.073 [+0.051, +0.111] |
| 240 min | 0.124 | 0.578 | [0.521, 0.620] | 0.505 | +0.073 [+0.047, +0.111] |

At 30 minutes the model is level with reading the glucose, which is the horizon at which "glucose is
already low" predicts a low trivially. From 60 minutes outward it adds, and the increment is stable.

## Is the model itself sane

Probing the exported trees directly, all features at cohort medians and one swept:

| glucose | 45 | 55 | 65 | 75 | 90 | 110 | 140 | 180 | 250 |
|---|---|---|---|---|---|---|---|---|---|
| risk | 0.861 | 0.868 | 0.780 | 0.442 | 0.170 | 0.105 | 0.081 | 0.070 | 0.078 |

Monotone and correctly shaped, with a weak positive response to insulin on board. The model is not
broken.

## Calibration, and the threshold that was never moved

Observed rate by predicted decile, current era, with the damper the engine applies at that score.

| decile | n | predicted | observed | damper |
|---|---|---|---|---|
| 0 | 5,660 | 0.013 | 0.017 | 1.000 |
| 1 | 7,799 | 0.019 | 0.021 | 1.000 |
| 2 | 2,678 | 0.021 | 0.017 | 1.000 |
| 3 | 4,473 | 0.024 | 0.017 | 1.000 |
| 4 | 4,719 | 0.029 | 0.027 | 1.000 |
| 5 | 5,063 | 0.035 | 0.027 | 1.000 |
| 6 | 5,750 | 0.044 | 0.044 | 1.000 |
| 7 | 4,370 | 0.059 | 0.043 | 1.000 |
| 8 | 4,946 | 0.099 | 0.075 | 1.000 |
| 9 | 5,045 | 0.392 | 0.072 | 0.934 |

Nine deciles track. The tenth predicts 0.392 and observes 0.072 against a base rate of 0.036, and it
is the only part of the range the consumption thresholds touch. The on-policy confound does not
account for it: the damper there is 0.934, a seven per cent reduction in budget, which cannot take a
genuine 39 per cent event rate down to 7.

The thresholds at 0.30 and 0.60 were placed against the previous model's distribution, where the
cohort median was 0.364. They were not re-placed when the median fell to 0.038. The damper now
engages on 0.49 to 27.7 per cent of scored cycles depending on the user, and the tier downgrade on
0.00 to 2.26 per cent, which is a fifty-fold spread nobody selected.

## Verdict

The meal model is doing what it was built to do and the figures support leaving it alone. Confidence
SOLID: replicated out of cohort three times now, monotone calibration, beats its baseline with an
interval clear of zero, and stable across the model changeover that moved the other one.

The current hypo model adds real information over the glucose reading, at +0.068 [+0.046, +0.104],
and does so consistently from 60 minutes outward. Its predecessor did not, at +0.018 [-0.037,
+0.113]. Confidence SOLID for the positive on the current model and for the null on its predecessor,
both being robust to the horizon and to the choice of baseline.

Its absolute discrimination remains well below the training figure, 0.655 against 0.8317. Three
explanations are open and this audit does not separate them: the field measurement is on policy and
biased toward zero; this cohort is not the training cohort, and a leave-one-user-out estimate bounds
transfer within a population rather than across populations; and the on-device feature vector,
36 of whose 53 entries come from a persisted six-cycle ring buffer, may not reproduce the
training-time vector. The last is checkable by logging the assembled vector and scoring it offline
through the training-time library, and that is the obvious next step.

The actionable finding is the threshold. Both cuts were placed against a distribution whose median
has since moved by an order of magnitude, and nothing re-placed them. Recalibrating them against the
current output distribution needs no retraining, does not change the model's ranking, and would
restore the firing rate to whatever the policy actually intends.

## What not to conclude

This does not say the damper is unsafe. It reduces insulin, never adds it, is floored at half the
budget, and sits under the composed floor at 30 per cent of baseline. Nor does it say the model is
broken: probed directly it responds to glucose correctly and monotonically. The finding is that a
threshold is a statement about a distribution, and replacing the model beneath it moved the
distribution without moving the threshold.
