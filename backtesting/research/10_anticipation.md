# Anticipation: acting before the event rather than after

## Hypothesis

Every lever examined up to this point is reactive: the algorithm sees glucose move and responds. The
identification work had shown that the reactive problem is close to exhausted, in that glucose
trajectory and dose magnitude carry essentially all the short-horizon signal available. If further
improvement exists, it lies in acting before the event, which requires predicting the event rather
than the glucose.

The hypothesis was that meals and exercise are habitual enough to be anticipated from a person's own
history, and that a weak, early signal could be acted on safely if the action were retractable.

## Investigation

Anticipation was investigated separately for exercise and for meals, and separately per participant
and across participants, because the two events have different regularity and the transferability
question has a different answer for each.

The safety question was investigated by designing and shadowing a state machine that arms on a weak
signal, then either confirms it or unwinds, so that a false positive costs a retraction rather than a
dose.

## Methods

Recorded under `backtesting/scripts/2026-07-peruser-anticipation/` and
`2026-07-anticipation-backout/`. Prediction was scored temporally, so a model is tested on a
participant's later data having been fitted on their earlier data, and cross-participant models were
scored with participants held out.

## Results

Exercise is anticipable and the signal is idiosyncratic. Per-participant temporal prediction of
exercise onset at forty five minutes reaches an area under the curve of 0.78, with all eight
participants between 0.72 and 0.83. The cross-participant equivalent reaches 0.67. Per-participant is
decisively better, which follows from exercise timing being personal.

Meals are the other way round. Cross-participant prediction of meal onset reaches 0.72 against 0.68
per participant, because meal times are semi-universal. Per-participant wins only for participants
with a lot of data and collapses where data is thin.

Habitual structure is strong enough to arm on. Time of day and day of week predict activity at 0.73
to 0.85, with about 30 per cent of activity falling in a participant's top three hours. A habit prior
pre-arms 55 per cent of episodes about 55 minutes ahead, at an area under the curve of 0.85 and
precision of 0.63.

The retractable design validates. On the crux participant, confirmation after arming reaches 0.83 to
0.87, with a false back-out rate of about 11 per cent that is benign by construction, since backing
out withdraws insulin that had only just been committed.

Meal-time anticipation in the aggregate, as distinct from the per-participant and hybrid forms, is
close to chance, with onsets roughly uniform. Learned bedtime is too variable to lead sleep
detection, at an onset standard deviation of about 92 minutes.

## Discussion

The central result is that the earlier conclusion of no signal, reached from cross-participant
reactive analysis, does not bound the anticipation question. Those are different questions asked of
different data, and the per-participant temporal answer for exercise is clearly positive where the
cross-participant reactive answer was negative. Conflating them would have closed a line that is
open.

The design consequence is that exercise anticipation should be per participant and meal anticipation
should be hybrid, with a cross-participant prior adapted per person. That is not a modelling
preference; it follows directly from where the transferability sits for each event.

The more important consequence is about safety, and it changes what accuracy is needed. A detector at
0.63 precision commits insulin wrongly more than a third of the time, which is unusable if the
insulin cannot be recovered. The same detector is usable if arming commits a small retractable
amount, confirmation is required within a bounded window from an independent signal, and failure to
confirm unwinds it. Safety then comes from retractability rather than from accuracy, and the
accuracy bar falls to something the data can actually meet.

This is why the shipped components in this area are shadow-logging rather than dosing. The detection
is validated; the dosing benefit is not, and the register records it as needing the shadow log before
any claim. Nothing here has yet been shown to improve an outcome.
