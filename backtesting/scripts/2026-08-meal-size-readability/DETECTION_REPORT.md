# Telling that food arrived: detection on 850 participants (2026-08-25)

*Reproduce: `detection.py` and `detection_diagnostics.py` against the `studies` schema of the local
TimescaleDB, sharing the extraction built by `extract_meals.py`. 492,440 announced meals from 839
participants against 562,564 undeclared rises from 850, spanning 323,965 participant-days, with a
second corpus of 71,761 meals and 92,050 rises from 189 participants on different therapy.
Participants are held out as folds and intervals come from resampling participants. Protocol:
`backtesting/protocols/2026-08_meal_size_readability_PREREG.md`, secondary analysis.*

## What is being measured

A controller without carbohydrate announcement has to decide, from glucose alone, that food has
arrived. The comparison that answers this is a declared meal against a rise nobody declared. The
undeclared class is built as rises of at least 25 mg/dL within thirty minutes, above the
hypoglycaemia threshold, with no carbohydrate entered within two hours either side.

That rule admits an undeclared rise only when it is substantial, while admitting a meal however
flat its trace. The two classes are then separated partly by the inclusion criterion rather than by
physiology, and the effect grows with horizon, because a meal that never rises becomes steadily
easier to distinguish from a rise that had to reach 25 mg/dL. Holding both classes to the same bar
removes it.

| Horizon after onset | Both classes held to the same bar | 95% CI | Meals admitted without the bar |
|---|---|---|---|
| 10 min | 0.843 | 0.841 to 0.846 | 0.833 |
| 15 min | 0.851 | 0.848 to 0.853 | 0.855 |
| 20 min | 0.865 | 0.862 to 0.867 | 0.896 |
| 30 min | 0.873 | 0.871 to 0.875 | 0.952 |
| 45 min | 0.865 | 0.863 to 0.867 | 0.930 |
| 60 min | 0.861 | 0.859 to 0.864 | 0.918 |

The left column is the one to quote. Detection is available at ten minutes and gains about three
points over the following twenty, then falls back. The second corpus gives 0.818 at ten minutes and
0.863 at thirty on the matched comparison, close enough across a different therapy and era to treat
the figure as a property of glucose traces rather than of one population.

## What carries it

| Horizon | Value and delta | With curvature | All twelve shape features |
|---|---|---|---|
| 10 min | 0.809 | 0.821 | 0.843 |
| 30 min | 0.815 | 0.851 | 0.873 |

Three features reach 0.809 of an eventual 0.843 at ten minutes, and five reach 0.821. This agrees
with the programme's standing finding that glucose value, delta and curvature carry essentially all
of the short-horizon information, and it means a detector needs nothing the loop does not already
compute every cycle.

## Whether it works for everybody

Scored within each participant who contributes at least twenty of each class, at ten minutes the
tenth centile is 0.778, the median 0.839 and the ninetieth 0.887, across 815 participants. None
falls below 0.60. At thirty minutes the same figures are 0.823, 0.873 and 0.907. There is no
subgroup for whom detection fails, which is unusual in this programme and is the strongest practical
property the measurement has.

## Whether it transfers

Fitted on the larger corpus and scored on the second without any refitting, detection gives 0.807
at ten minutes and 0.855 at thirty. Fitted and scored within the second corpus it gives 0.818 and
0.863. Crossing therapy, era and de-identification scheme costs about a hundredth of a point, so a
detector trained on one population is very nearly as good on another as one trained on that
population.

## What it would cost to run

An area under the curve is silent about how often a detector fires when nothing was eaten. Meals
meeting the matched bar occur 0.55 times per participant-day and undeclared rises 1.74 times, so
the negative class outnumbers the positive by about three to one and the operating point is where
the practical answer lives.

| Horizon | Sensitivity | False positive rate | False alarms per day | True detections per day |
|---|---|---|---|---|
| 10 min | 70% | 0.202 | 0.35 | 0.39 |
| 10 min | 80% | 0.294 | 0.51 | 0.44 |
| 10 min | 90% | 0.442 | 0.77 | 0.50 |
| 30 min | 70% | 0.126 | 0.22 | 0.39 |
| 30 min | 80% | 0.221 | 0.38 | 0.44 |
| 30 min | 90% | 0.409 | 0.71 | 0.50 |

At ten minutes and 70 per cent sensitivity the detector is right about half the times it fires. Push
it to 90 per cent and it produces more false alarms than true detections. Waiting to thirty minutes
improves the economics, to roughly two true detections for every one false alarm at 70 per cent, at
the cost of twenty minutes.

One caveat runs the other way and cannot be resolved from these data. Some undeclared rises are
meals somebody forgot to log, and every one of those is counted as a false alarm here. The false
alarm figures are therefore an upper bound, and the true operating point is somewhere better than
the table says by an unknown margin.

## What this supports

Detection is not the constraint on an unannounced-meal controller. It is available within ten
minutes at 0.843, it needs three to five features the loop already has, it works for every
participant measured, and it transfers between populations at a cost of about 0.01. Against that,
size is not readable from the same traces: 0.519 at ten minutes on the trajectory alone, and 0.007
above a model that knows only who is eating and at what hour.

The honest bar for the accelerometer meal shadow is 0.843 at ten minutes and 0.873 at thirty.

The operating point is the part that bears on whether a better detector would help. A false
detection commits insulin into a rise that no food caused, and the high-IOB tail is where this
programme's lows repeatedly originate. Moving along this curve towards higher sensitivity buys
detections at a rate of roughly one false alarm each, so the question a controller faces is not how
to detect better but what to do about the fact that at any useful sensitivity a material share of
detections are wrong.

Confidence: SOLID. Out of sample with participants held out, intervals from resampling
participants, replicated in an independent corpus, and the headline is quoted from the construction
that removes the inclusion asymmetry rather than the one that flatters it.

## Limitations

A declared meal is one the participant chose to declare, and the undeclared class contains dawn
phenomenon, stress responses and rebounds, which differ in shape for reasons other than
carbohydrate. The comparison bounds what a detector can achieve rather than isolating carbohydrate.
Onset is inferred from the trace rather than observed. These participants are not users of this
fork, so what transfers is a statement about the information in a glucose trace and not about any
controller's response to it. Nothing here measures the detector this fork currently ships, because
its users do not announce carbohydrate and no ground truth exists on their records.
