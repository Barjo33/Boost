# The two learned components that dose

## Hypothesis

Everything else in the shipping controller is deterministic. Two things are not: a pair of
gradient-boosted tree models that run on every cycle and whose outputs reach the dose. They are the
only place in the programme where a statistical object is allowed to change what the pump does, so
what they are, how they were validated, and whether they still work are the questions with the most
direct consequence of anything in this series.

The hypothesis that produced them was specific rather than general. The algorithm inherited a
binary safety gate: if the minimum projected glucose fell below a threshold, dosing was suspended.
A gate of that shape is a single-feature classifier with a hand-placed cut, and an analysis of its
behaviour found it separating dangerous cycles from safe ones at an area under the curve of 0.62,
suspending unnecessarily on 66 per cent of the occasions it fired, and missing a third of the
events it existed to catch. A small learned model over features the algorithm had already computed
reached 0.80 on the same data. The proposition was not that machine learning would improve dosing
in general. It was that this particular gate was a weak classifier and could be replaced by a
better one at no cost in inputs.

A sister model followed from the same reasoning applied to the opposite tail. If a rise of at least
50 mg/dL within 90 minutes is what an unannounced meal looks like on a sensor, then the probability
of that rise is a quantity the algorithm could use to decide whether a climb is a meal or noise.

## Investigation

Four questions had to be settled before either model could be built, and they were settled in an
order that mattered.

The first was model class. A factorial comparison trained eight models over 1,491,790 decision
points from 21 users: two targets, hypoglycaemia and hyperglycaemia within four hours; two
architectures, gradient-boosted trees and logistic regression; and two subgroups split on whether
dynamic sensitivity was active. All eight used the same 28 features, the same folds and the same
cost-sensitive weighting, so the only thing varying was the thing under test. Shapley values gave
per-feature attribution, computed exactly for the trees and by linear decomposition for the
regression.

The second was how many features to use. The full feature set reached 0.80 on the hypo target.
An eight-feature subset was proposed on the grounds that all eight were already present as
parameters of the dosing function and needed no additional data fetch, so the model would add no
coupling between itself and the rest of the algorithm's state. The expected cost was two to five
points of area under the curve.

The third was deployment. Three options were compared: the LightGBM C library through the Java
native interface, which gives exact parity with training at the price of a two megabyte native
library per architecture; conversion to a portable runtime, which adds a conversion step; and
exporting the trees as JSON and walking them in Kotlin, which is about fifty lines of code, has no
native dependency, and costs roughly five milliseconds per inference against a five-minute cycle
budget.

The fourth was validation discipline, and it was settled by a failure rather than by an argument.
An early attempt to tune the hypo model's hyperparameters with sequential optimisation reported a
gain of fourteen percentage points. The folds had been stratified rather than grouped, so the same
user appeared in training and test, and the model was being rewarded for recognising people. Under
leave-one-user-out the honest gain was 0.7 points. The tuned model was not shipped, and every
subsequent model in the programme has been scored with the user as the grouping variable.

With those settled, the models were trained, deployed, and then subjected to two validations. The
first, in May 2026, took six users monitored over a 72-day window and roughly 110,000 evaluation
cycles, one inside the training cohort and five almost certainly outside it, and asked whether the
cross-validated numbers survived contact with new people. It also compared the in-cohort user's
performance before and after the deployment date, to check that closing the loop around the model
had not degraded it.

The second is new, and is the reason this document is not simply a record. The models have been
running on ten users for months and had never been scored against those users' own telemetry. That
audit is reported below.

## Methods

The factorial architecture comparison is recorded under the stratified-models analysis; the design
plan, the feature-set reasoning and the deployment options under the on-device risk model plan;
the six-user validation under the transfer test of 2026-05-12.

The field audit is `backtesting/scripts/2026-08-ml-field-audit/`. It takes one decision row per
user per five-minute bucket across all available history for the ten Boost users, scores each
model against the target recorded in its own metadata asset, and reports the area under the curve
per user and pooled, with intervals from a bootstrap that resamples users rather than
observations. Forward outcomes come from the sensor record rather than from the decision record,
so the label does not depend on the loop having run. Hypoglycaemia spans are measured in
wall-clock time rather than in readings, so the definition means the same thing on a one-minute
feed as on a five-minute one.

Two things were added because without them the result could not be interpreted. Trivial predictors
were scored on identical rows against identical labels, so that a model's figure could be read
against what the algorithm already knew rather than against chance. And the same scores were
re-scored at horizons from 30 minutes to four hours, to separate a weak model from a horizon at
which nothing is predictable.

The audit is measured on policy. A high hypo score reduces the dose, which suppresses some of the
events being predicted, and the direction of that bias is toward zero. Rather than concede the
point and move on, the calibration table carries the damper the engine would have applied at each
score, which allows the confound to be priced.

## Results

The architecture comparison was decisive and is the reason the programme uses trees. Gradient
boosting beat logistic regression by 10 to 21 points of area under the curve in every one of the
four strata, the largest gap being on the hypoglycaemia target with static sensitivity, where 0.910
against 0.701 says the signal there is substantially non-linear and a linear model cannot represent
it. The same comparison found outcomes markedly more predictable when sensitivity was static than
when it was dynamic, 0.910 against 0.840 for hypoglycaemia, which is consistent with a dynamic
adjustment introducing variation that a single decision point cannot see.

The eight-feature models were trained on roughly three million cycles from 28 users. The hypo model
reached 0.7011 under grouped folds and 0.6796 leave-one-user-out; the meal model 0.7342 and 0.7375.
The pure-Kotlin tree walker was chosen and has never been the limiting factor.

The transfer test held. Out-of-cohort mean area under the curve was 0.679 for the hypo model
against a leave-one-user-out baseline of 0.680, and 0.771 for the meal model against 0.738, so the
hypo model generalised exactly as predicted and the meal model slightly better than predicted. The
before-and-after comparison on the in-cohort user gave 0.642 against 0.633, which is no meaningful
drift from closing the loop around the model. On that basis the models shipped without retraining.

The hypo model was then revised twice in June 2026. The horizon moved from four hours to 90 minutes,
and the label from any two consecutive readings below 70 to a run below 70 sustained for at least
fifteen minutes. The feature vector grew from 8 to 53, being 17 instantaneous features and 36
formed by carrying six of them back over six cycles through a persisted ring buffer. Trained on
3,007,589 cycles from 32 users, that model reported 0.8391 under grouped folds and 0.8317
leave-one-user-out, and four new users scored between 0.78 and 0.89.

The field audit, on ten users and their own data, gives a different picture for each model.

The meal model replicates. Pooled area under the curve is 0.728 with an interval from 0.702 to
0.760, against a training figure of 0.7375 and a transfer-test figure of 0.771. Every user is above
0.6, the range being 0.619 to 0.846. Calibration is monotone across deciles, from an observed 2.8
per cent in the lowest to 40.8 per cent in the highest. Against the strongest trivial baseline
available on the same rows, the algorithm's own eventual glucose figure, it is ahead by 0.143 with
an interval from 0.076 to 0.218.

The hypo model does not. Scored against the target in its own metadata it reaches 0.582 with an
interval from 0.517 to 0.643, against a training figure of 0.832. Four of the ten users sit at or
below 0.5. Scored instead against the target its documentation claims, four hours and two
consecutive readings, it reaches 0.521 and is not distinguishable from chance.

The baseline comparison is what settles its status. On identical rows and labels, the negated
current glucose reaches 0.594. The model minus that baseline is −0.010 with an interval from −0.067
to +0.050, so a 53-feature model carrying six cycles of history does not outrank the single number
that is its own first feature. The horizon sweep is worse for it: at 30 minutes the model reaches
0.663 against 0.836 for current glucose, a deficit of 0.154 with an interval from −0.229 to −0.068,
and at 60 minutes it is still behind. It draws level only at 90 minutes and beyond, by which point
neither predictor carries much.

Calibration locates the failure precisely. The lowest six deciles track the observed rate closely,
from 0.015 predicted against 0.017 observed up to 0.087 against 0.066. The top four inverted:
predicted rates of 0.253, 0.339, 0.417 and 0.541 against observed rates of 0.034, 0.027, 0.030 and
0.059, on a base rate of 0.034. The model's confident region is where it is most wrong, and that
region is exactly where the dosing consumption operates.

The on-policy confound does not account for this. Between the fifth and sixth deciles the observed
rate halves while the mean score in the sixth is 0.253, below the 0.30 threshold at which the
damper engages, so no insulin was withheld on account of the model in either decile. Above that the
damper does engage, but at reductions of 3, 8 and 17 per cent of the budget, which cannot take a
genuine 40 per cent event rate down to 3 per cent. The confound is real and biases the figure
downward; it is not large enough to explain a fall from 0.83 to 0.58 and does not explain the
inversion at all.

Two incidental findings came out of the audit. The scores populate between 7.7 and 25 per cent of
decision rows per user, the remainder being eras before the models were wired. And the damper
engages on between 0.6 and 51 per cent of scored cycles depending on the user, which is a wider
spread than anyone had assumed.

## Discussion

The meal model is the programme's cleanest positive result. A model trained on a foreign cohort in
early 2026 predicts unannounced meal rises on a different set of people months later at the
accuracy its cross-validation advertised, having been validated out of cohort twice by different
methods. It earns its place, and the appropriate action is to leave it alone.

The hypo model is a different matter, and the honest statement is narrow. What is established is
that as consumed today it carries no more information than the current glucose reading, and less at
the horizons where acting is still possible. That result is robust to the label definition, to the
horizon, and to the choice of baseline, which makes it solid. What is not established is why, and
three explanations remain open.

The first is that the on-device feature vector does not reproduce the training-time one. The 53
features include 36 built from a persisted six-cycle ring buffer, which is state the training
pipeline constructed from a table and the device constructs from its own history across process
restarts, sensor gaps and engine changes. A discrepancy there would degrade the 53-feature model
while leaving the 8-feature meal model untouched, and that asymmetry is what the data shows. This
explanation is checkable, and checking it is the obvious next step: log the assembled vector, score
it offline through the training-time library with the same exported model, and compare against the
value the engine published. If they disagree, the model was never the problem, and the same check
would have caught it in June 2026 when the model first failed to load on a device at all.

The second is that this cohort differs from the 32-user training cohort in a way leave-one-user-out
did not capture. That estimate holds out one user at a time from a population, which bounds
transfer within that population and says less about a different one. The May transfer test is
evidence against this explanation, but it tested the 8-feature model, not the one now shipping.

The third is that sustained hypoglycaemia is simply rarer and less predictable here. The base rate
under the v12 label is 3.4 per cent against 17 per cent under the older one, and a rarer event is
harder.

Separately from all three, the documentation is wrong in a way that matters. The consuming code's
own comment, the reader document and the branch readme all describe the model as predicting
hypoglycaemia within four hours from two consecutive low readings. That was the model retired in
June 2026. Anyone reasoning about where to place the 0.30 and 0.60 thresholds from those documents
is reasoning about a different quantity from the one the model emits, and the thresholds themselves
were calibrated against the earlier model and were never revisited when the target changed
underneath them. Recalibrating a consumption threshold against a model whose output distribution
has moved is a mechanical exercise, and it has not been done.

The wider lesson concerns validation timing rather than any of these models. Both were validated
thoroughly before deployment, by cross-validation, by an out-of-cohort transfer test and by a
before-and-after check for closed-loop drift, which is more than most components in this programme
received. None of that was repeated afterwards. The meal model came through the intervening year
unchanged and the hypo model did not, and the only reason anyone knows which is which is that the
question was finally asked. A learned component in a control loop has a shelf life that a
deterministic one does not, because the population, the sensor and the policy all move underneath
it, and the discipline that follows is that anything learned which reaches the dose needs a
scheduled re-audit against live outcomes rather than a validation at birth.
