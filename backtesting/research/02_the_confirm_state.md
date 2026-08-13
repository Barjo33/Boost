# The confirm state and the crash that follows it

## Hypothesis

Entering the confirmed state is the moment the algorithm commits a substantial dose to a meal it
believes is real. A recurring clinical observation was that some confirms are followed by a fall into
hypoglycaemia a couple of hours later. Three hypotheses were entertained in turn.

The first was that the crash is foreseeable at the moment of confirm, so that the algorithm could
identify the dangerous confirms and restrain only those. The second was that continued acceleration
after a confirm marks a meal that will overshoot, so that a second confirm should be blocked. The
third, entertained most recently, was that a confirm arriving as acceleration decays is a late
confirm firing on a rise that has already peaked.

Underlying all three was an assumption never itself examined: that the post-confirm low rate is
elevated, rather than simply being what happens after any period of rising glucose.

## Investigation

The foreseeability question was investigated by attempting to predict the shape of the excursion from
the state available at the confirm. The second-confirm question was investigated by comparing meals
where acceleration continued after the confirm against those where it decayed, both in the record and
by driving the real engine through reconstructed scenarios. The decay question was investigated by
classifying every confirm by what acceleration did over the approaching cycles.

The assumption underneath was tested last, by matching each confirm to control windows that began
from the same state in the same participant without a confirm.

## Methods

Prediction used out-of-sample validation with participants held out as folds, on 2,117 meals, and is
recorded under `2026-07-postconfirm-accel/meal_shape.py`. The second-confirm work used 3,879 anchors
across nine participants with cluster bootstrap intervals and a real-engine scenario run. The decay
work used 1,268 confirms across twelve participants from 26 June to 12 August 2026, with intervals
from a bootstrap resampling participants, and is recorded under `2026-08-confirm-decay/`.

The matched-baseline work paired each confirm with control windows from the same participant, matched
on hour of day to within an hour, on starting glucose to within 15 mg/dL and on insulin on board to
within 0.5 U, requiring no confirm in the window or the hour preceding it, with a median of 43
controls per confirm. The outcome was the lowest glucose in the following three hours.

## Results

The crash is not foreseeable. Out of sample, the area under the curve was 0.518 with an interval from
0.485 to 0.549, which is chance. Tail shape, meaning under-recovery rather than overshoot, was weakly
predictable at 0.60 with an interval from 0.58 to 0.63, diffuse and partly explained by second-meal
clustering, which is too weak to gate on.

Continued acceleration after a confirm is a real signal for the size of the excursion, associated
with a peak some 23 mg/dL higher and distinguishable from noise. It carries no room to act, because
the accelerating group already crashes at about nineteen per cent with severe lows at 6.6 per cent,
which is not lower than the decelerating group and for two participants is higher.

Decaying acceleration does not predict the crash. Confirms approached with decaying acceleration were
followed by a low within three hours 24.0 per cent of the time against 22.1 for sustained or rising,
a difference of 1.9 points with an interval from minus 4.1 to plus 8.1. Eight further threshold
variants were tried, including acceleration near zero at the moment of confirm and a fall of twenty
points or more, and every interval spanned zero with two pointing the wrong way. Dose size did not
separate it either.

The assumption underneath the three hypotheses is correct, and is the finding of the topic. Against
matched controls, confirms were followed by glucose below 70 mg/dL on 22.8 per cent of occasions
against a control rate of 14.0, and below 54 mg/dL on 7.6 per cent against 1.9. The differences are
8.8 points with an interval from 3.9 to 14.0, and 5.7 points with an interval from 3.0 to 8.5, from
1,074 matched confirms across eleven participants. Nine of the eleven move in the same direction and
no single participant carries the result.

## Discussion

Confirming roughly doubles the chance of a subsequent low and quadruples the chance of a severe one,
from the same starting state in the same person at the same time of day. That is the substantive
result, and it went unmeasured for months while three attempts were made to identify which confirms
were dangerous.

The three attempts failing is not incidental to the fourth succeeding. If the crash cannot be
distinguished at the moment of commitment, then no gate conditioned on the state at that moment can
help, and the levers reduce to two: give less, or withdraw afterwards. The register records the
retractable back-out as vindicated by exactly this reasoning, and a within-participant randomised
trial on the confirm dose is registered under
`backtesting/protocols/2026-08_confirm_dose_PREREG.md`.

One methodological point is worth carrying forward. The decay hypothesis was entertained partly
because the acceleration metric appeared to contradict the confirm: it read 1.63 on the event that
prompted the enquiry, against 32 four cycles earlier. The metric is one hundred times the difference
between the current delta and its short average, divided by that average floored at two. On a steady
steep rise the delta converges on its own short average and the metric reads near zero by
construction. It is high at the onset of a rise and decays as the rise establishes itself, so a low
value means the rise is steady rather than that it is failing. Reading it as evidence against the
confirm was a misinterpretation, and the register now carries the definition alongside the null.

The nine threshold variants tested across these hypotheses are also a caution. One of them cleared
zero by a tenth of a point, weakened as the threshold tightened, and reversed sign. It is recorded as
noise rather than as a lead, because a discriminator hunted across enough cuts of the same events
will eventually produce one that clears.
