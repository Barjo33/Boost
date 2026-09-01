# What a glucose trace tells a loop about a meal

## Summary

A closed loop that does not ask people to announce carbohydrate has to work out what it can from
glucose alone. It can tell that food arrived, quickly and for everybody, though at a useful
sensitivity it cries wolf about as often as it is right. It cannot tell how much food arrived, at
any horizon where a dose would be sized, and the models that appear to manage it are reading the
person and the hour rather than the meal. There is a third question it can answer, which is whether
the rise in front of it is going somewhere that matters, and almost all of that answer is carried by
two numbers the loop already holds: the glucose the rise started from, and the time of day. The
loop's own forward projection is at chance on that question. Sampling the sensor faster adds
nothing, because there is nothing in the signal below five minutes to find.

Measured on 1.6 million announced meals and 2 million rise onsets from 1,807 participants across
seven studies, held out by participant throughout.

## One rise, and what can be known about it

Take a rise that begins at breakfast time from a glucose of 138 mg/dL. Ten minutes in, the loop has
two or three new readings and must decide what to do.

It can be fairly confident food arrived. A detector using glucose, its short-window delta and the
curvature of the trace, all quantities the loop already computes every cycle, separates a declared
meal from an undeclared rise at this point about as well as it ever will.

It has no idea how big the meal was. If that breakfast was 60 g rather than 20 g, the extra 40 g
has lifted the ten-minute rise by less than one milligram per decilitre. The spread of ten-minute
rises across meals is more than ten times that. The information is not merely weak, it is an order
of magnitude below the noise it would have to be read out of.

It can, however, say something useful about where this is going. A rise that starts at 138 at
breakfast time is more likely than not to exceed 180 mg/dL, and that judgement comes almost entirely
from the two facts already stated: where it started, and when. Nothing about the shape of the first
ten minutes adds much to it.

The rest of this describes how firmly each of those three statements holds.

## Telling that food arrived

This is settled and is not where the difficulty lies.

Ten minutes after a rise begins, a declared meal separates from an undeclared rise at an area under
the curve of 0.843, with an interval of 0.841 to 0.846. It improves by about three points over the
following twenty minutes and then falls back. Glucose value and its delta alone reach 0.809 of that,
and adding curvature reaches 0.821, so a detector needs nothing the loop does not already have.

Two properties make it usable. It works for everybody: scored within each of 815 participants, the
tenth centile is 0.778 and no participant falls below 0.60, which is unusual in this programme. And
it transfers: fitted on one corpus and scored on a second of different therapy and era without
refitting, it loses about a hundredth of a point.

What an area under the curve does not say is how often the thing fires when nobody ate, and that is
where the practical limit sits. Meals occur about 0.55 times per participant-day and undeclared
rises about 1.74 times, so the class a detector must reject outnumbers the one it wants by three to
one.

| sensitivity at ten minutes | false alarms per day | true detections per day |
|---|---|---|
| 70% | 0.35 | 0.39 |
| 80% | 0.51 | 0.44 |
| 90% | 0.77 | 0.50 |

At 70 per cent sensitivity the detector is right about half the times it fires. Push it to 90 and it
produces more false alarms than true detections. Waiting until thirty minutes improves the trade to
roughly two true detections per false alarm, at the cost of twenty minutes.

One thing runs the other way and cannot be resolved here. Some undeclared rises are meals somebody
forgot to log, and every one of those is counted as a false alarm, so the real operating point is
better than the table by an unknown margin.

## Telling how much

This one is closed, and the cleanest way to see it needs no statistics at all.

Predicting that a person will eat their own median meal, every time, and looking at no glucose
whatsoever, gives a mean absolute error of 13.02 g. A model given the whole trajectory, the clock,
the participant's scale and their entire history of announcements gives 13.12 g at ten minutes and
13.01 g at sixty. The trajectory on its own is worse than guessing the population median.

Discrimination tells the same story. Against a baseline holding the same information with the
glucose trace removed, the trace is worth 0.002 at ten minutes and 0.008 at sixty. The
pre-registered margin was 0.05 by twenty minutes.

The diagnosis is in how those figures move with time. An arm carrying participant information sits
at 0.833 at ten minutes and 0.838 at sixty, barely shifting as a full hour of glucose arrives, while
an arm with only the trajectory climbs from 0.519 to 0.608 because it has nothing else to work with.
Information that does not improve as the excursion unfolds did not come from the excursion. A model
scoring 0.833 is reading the diner.

The underlying relationship is real and correctly signed. Within a participant, comparing their own
unbolused meals against their own bolused ones, carbohydrate is associated with a rising trace when
no insulin was given and a falling one when it was. In unbolused meals the slope is about 0.02 mg/dL
per gram at ten minutes. That is what makes 40 g worth 0.83 mg/dL against a between-meal spread of
9.71, roughly a twelfth of the noise, reaching a quarter of it only by the hour mark.

So sizing a dose to an inferred meal is not a hard problem awaiting a better model. At the horizons
where a dose is sized, the quantity is not in the trace.

## Telling whether it matters

A loop at the moment it must act needs neither of the previous two answers. It needs to know whether
the rise in front of it is going somewhere worth treating.

That question has a property the others lack: the answer is written in the trace afterwards, so no
announcement is needed and every corpus becomes usable, including five studies that record no
carbohydrate at all.

Start with how little variation there is to explain. Once a rise has cleared 25 mg/dL in half an
hour, the share going on to reach 40 mg/dL above baseline is between 0.833 and 0.859 in all seven
studies, which differ in therapy, era and age. Five in six declared rises are consequential on that
definition, whoever is wearing the sensor.

Where the rise started carries most of what remains. Glucose at the onset, one number, reaches 0.812
for whether the excursion will pass 180 mg/dL. Adding the hour of the clock, which is free, gives
0.829.

The shape of the trajectory does add to that, and the addition is real but small and late.

| what it adds, over onset glucose and the clock | at 10 min | at 20 min | at 30 min |
|---|---|---|---|
| for a peak rise of 60 mg/dL or more | +0.014 | +0.032 | +0.049 to +0.082 |
| for exceeding 180 mg/dL | +0.014 | +0.027 | +0.049 to +0.082 |

Every one of those intervals excludes zero, so the gains are not noise. They are also the wrong
shape to use. The size result was flat across horizons, which is what told us the information never
came from the excursion at all. Here it grows steadily, which means information genuinely is
arriving from the trajectory. It simply arrives after the point at which a controller has to commit:
the pre-registered margin of 0.05 is cleared at thirty minutes and not before.

## What the loop already has, and does not use

The two quantities that carry this are available to any controller for nothing. Joining an engine
record to outcomes gives 27,619 rise onsets on which what the loop actually computed can be scored
against what happened.

| what is scored | area under the curve |
|---|---|
| the base rate | 0.544 |
| the loop's forward projection | 0.544 |
| onset glucose and the clock | 0.625 |
| the whole loop record added to those two | 0.625 |

The forward projection, the quantity every dosing decision rests on, is at chance for whether the
excursion it is projecting will matter. Two numbers the loop holds at the same instant reach 0.625,
and adding the entire engine record to them contributes 0.001.

This is not new signal. It is an unused reading of signal already in hand, and it is the only lever
in this programme that survived a control built to kill it.

## Whether a faster sensor helps

If the useful information arrives at around thirty minutes, the obvious remedy is to sample more
often. It does not work, and the reason is a property of glucose rather than of any sensor.

One participant wore a five-minute sensor for 83 days and then a one-minute sensor for 61. Compared
through a measure that places both cadences on one axis without resampling either, the two records
differ by a single scale factor, flat to within 7 per cent across a twenty-four-fold range of lag,
with no bend at the short end and matching slopes in the bands they share. Below five minutes, where
only the faster sensor can see, the slope contains the value from above. The same power law runs
from one minute to sixty, and neither record shows the flattening that measurement noise would
impose.

There is nothing under five minutes for a detector to find. What a faster feed does buy is
scheduling: a loop running every minute waits less for its next chance to act. Four controller
instances run in parallel on one person, three sharing a single sensor, put that at 1.8 minutes to
the first microbolus on a rise and about 2.6 minutes on a basal suspension. Both are the size of the
sampling interval they came from, which is what a scheduling effect looks like and is not what new
information looks like.

## What a controller should do with this

Detection is solved, so effort spent making it better is spent in the wrong place. Sizing a dose to
an inferred meal is closed and should not be attempted from the trace. Richer inference from the
shape of an excursion is unlikely to repay the work, because the information that exists arrives
after the decision.

Two things are available now. The first is to price consequence at the onset, from the glucose the
rise started at and the hour, which the loop holds and does not currently combine, and which beats
its own forward projection on that question by 0.08. The second is to shorten the delay between
information and action, since what the loop will eventually act on is largely determined by the time
it acts. A faster decision cycle buys one to three minutes of that, and buys nothing else.

Neither is a claim that acting differently would improve an outcome. No observational corpus can
settle that, and the programme's bar for a dosing change is a within-participant randomised
comparison.

## How this was measured

The size and detection work uses two corpora of announced meals: 492,440 meals from 839 participants
on an open-source automated system, and 71,761 from 189 participants on sensor-augmented pump
therapy in an earlier era, the second serving as an independent replication. Rescue carbohydrate and
entries below 8 g are excluded, and meal onset is inferred from the trace.

The consequence work uses rise onsets rather than announcements, which frees it from anyone having
logged anything, and therefore draws on all seven studies: 1,986,123 onsets from 1,807 participants.
A rise onset is a climb of at least 25 mg/dL within thirty minutes beginning above the hypoglycaemia
threshold, which is approximately the set of events a detector fires on.

Participants are held out as folds throughout and every interval comes from resampling participants.
The analysis plan, including the decision margin of 0.05 in area under the curve at twenty minutes
or less, was fixed before these measurements were made.

Comparisons between two arms scoring the same events are reported as a paired difference, because
two areas under the curve each carrying their own interval say nothing about whether they differ
when their errors move together.

## Limitations

Announced carbohydrate is a person's own estimate, and its error puts a ceiling on measured accuracy
that this design cannot separate from the ceiling physiology imposes. Meal onset is inferred rather
than observed, so a meal announced well after the eating is anchored imprecisely.

The undeclared class contains dawn phenomenon, stress responses and rebounds, which differ in shape
for reasons that have nothing to do with carbohydrate, so the detection figures bound what a
detector can achieve rather than isolating food.

Outcomes in the consequence work are read from traces produced under active insulin therapy, so what
is predicted is the excursion that occurred given the treatment given. The modelling uses 200 of the
1,807 available participants; intervals are narrow and effect sizes stable across the sweep, but a
full-corpus run has not been done.

The cadence comparison rests on one participant, across two sensor eras that are not glycaemically
matched, and on four parallel instances of which three commanded a virtual pump. Its magnitudes
describe what controllers propose rather than what they would achieve.

None of these participants use the system this programme develops. What transfers is a statement
about the information in a glucose trace, not about any controller's response to it.
