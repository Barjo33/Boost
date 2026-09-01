# What a glucose trace tells a loop about a meal

## Summary

A loop that does not ask people to announce carbohydrate has to read the glucose instead. We
measured what it can get from that, on 1.6 million announced meals and 2 million rises from 1,807
people across seven studies.

It can tell that food arrived. It cannot tell how much. Both answers are firm and neither improves
with a better model.

There is a third question, and it is the useful one. Will this rise go somewhere that matters? That
is answerable at the moment the loop has to act. Almost all of the answer comes from the glucose the
rise started at and the time of day, both of which the loop already holds. Its own forecast is at
chance on the same question.

A faster sensor does not help. There is nothing in glucose below five minutes to find.

## One rise

A rise starts at breakfast time, from 138 mg/dL. Ten minutes later the loop has two or three new
readings and has to decide.

Can it tell food arrived? Yes. Glucose, its delta and the curvature of the trace do that about as
well at ten minutes as they ever will.

Can it tell how big the meal was? No. Suppose it was 60 g rather than 20 g. Those extra 40 grams
have lifted the ten-minute rise by less than 1 mg/dL. Ten-minute rises vary by about 10 mg/dL from
meal to meal. The signal sits an order of magnitude under the noise.

Will it matter? Probably. A rise starting at 138 at breakfast is more likely than not to pass
180 mg/dL. That comes from where it started and what time it is. The shape of the first ten minutes
barely adds to it.

## Telling that food arrived

Detection works, and it works for everyone.

Ten minutes in, a declared meal separates from an undeclared rise at 0.843 (0.841 to 0.846). It
gains about three points over the next twenty minutes, then falls back. Glucose and delta alone
reach 0.809 of that. Add curvature and you get 0.821. A detector needs nothing the loop does not
already compute.

Scored inside each of 815 people, the tenth centile is 0.778. Nobody falls below 0.60. Fit it on one
corpus and score it on another of different therapy and era, without refitting, and it loses about a
hundredth of a point.

The catch is how often it fires when nobody ate. Meals happen about 0.55 times a day. Undeclared
rises happen 1.74 times. The class you want is outnumbered three to one.

| sensitivity at ten minutes | false alarms per day | true detections per day |
|---|---|---|
| 70% | 0.35 | 0.39 |
| 80% | 0.51 | 0.44 |
| 90% | 0.77 | 0.50 |

At 70 per cent the detector is right about half the times it fires. At 90 per cent it is wrong more
often than right. Waiting until thirty minutes gets you two true detections per false alarm, and
costs twenty minutes.

Some undeclared rises are meals people forgot to log, and we count every one as a false alarm. The
real operating point is better than the table by an unknown margin.

## Telling how much

Predict that someone eats their median meal every time. Look at no glucose at all. That gives a mean
absolute error of 13.02 g.

Now give a model the whole trajectory, the clock, the person's scale and their entire history of
announcements. It gives 13.12 g at ten minutes, and 13.01 g at sixty.

The trajectory on its own is worse than guessing the population median.

Discrimination agrees. Hold the person and the clock constant, then add the glucose trace: it is
worth 0.002 at ten minutes and 0.008 at sixty. We had pre-registered 0.05 by twenty minutes as the
margin worth acting on.

Now watch how those numbers move with time. An arm carrying information about
the person sits at 0.833 at ten minutes and 0.838 at sixty. A full hour of glucose arrives and it
barely shifts. An arm with only the trajectory climbs from 0.519 to 0.608, because it has nothing
else to work with. If a model does not improve as the excursion unfolds, it was never reading the
excursion. A model scoring 0.833 is reading the diner.

The physiology is fine. Within one person, comparing their own unbolused meals
against their own bolused ones, carbohydrate raises the trace when no insulin was given and lowers
it when insulin was. In unbolused meals the slope is about 0.02 mg/dL per gram at ten minutes. That
is how 40 grams comes to be worth 0.83 mg/dL against a spread of 9.71. A twelfth of the noise. It
reaches a quarter of the noise only by the hour mark.

Dosing to an inferred meal size is therefore closed. The quantity is not in the trace while a
dose is still worth sizing, so a better model will not recover it.

## Telling whether it matters

At the moment it acts, a loop does not need either previous answer. It needs to know whether this
rise is worth treating.

That question can be scored without anyone announcing anything, because the answer is written in the
trace afterwards. Five studies that record no carbohydrate become usable.

First, how much is there to explain? Once a rise clears 25 mg/dL in half an hour, between 0.833 and
0.859 of them go on to reach 40 mg/dL above baseline. That holds across all seven studies, which
differ in therapy, era and age. Five in six declared rises matter, whoever is wearing the sensor.

Where the rise started carries most of the rest. Onset glucose alone reaches 0.812 for whether the
excursion passes 180 mg/dL. Add the hour of the clock, which costs nothing, and it is 0.829.

The shape of the trajectory does add on top. The addition is real, small, and late.

| what the shape adds, over onset glucose and clock | 10 min | 20 min | 30 min |
|---|---|---|---|
| peak rise of 60 mg/dL or more | +0.014 | +0.032 | +0.049 to +0.082 |
| glucose passes 180 mg/dL | +0.014 | +0.027 | +0.049 to +0.082 |

Every interval excludes zero, so this is not noise. It is the wrong shape to use, though. Meal size
was flat across horizons, which is how we knew it never came from the excursion. Here it grows
steadily, so information really is arriving from the trajectory. It just arrives late. Our 0.05
margin is cleared at thirty minutes and not before, and by thirty minutes the decision has been
taken.

## What the loop already has

Both useful quantities are free. We joined an engine record to outcomes to see what the loop actually
computed, across 27,619 rise onsets.

| scored against the outcome | area under the curve |
|---|---|
| base rate | 0.544 |
| the loop's forward projection | 0.544 |
| onset glucose and the clock | 0.625 |
| the whole loop record plus those two | 0.625 |

The forward projection is what every dosing decision rests on. It is at chance for whether the
excursion it is projecting will matter. Two numbers the loop holds at the same instant reach 0.625.
Adding the entire engine record on top of them is worth 0.001.

So there is no new signal here. There is a reading of existing signal that nothing currently makes.
It is the only lever in this programme that survived a control built to kill it.

## Whether a faster sensor helps

If the useful information turns up around thirty minutes, sample more often. That is the obvious
move and it does not work.

One person wore a five-minute sensor for 83 days, then a one-minute sensor for 61. Put both on one
axis, without resampling either, and they differ by a single scale factor. It is flat to within 7
per cent across a twenty-four-fold range of lag. No bend at the short end. Matching slopes in the
bands they share. Below five minutes, where only the fast sensor can see, the slope contains the
value from above.

The same power law runs from one minute to sixty. Neither record shows the flattening that
measurement noise would impose. There is nothing under five minutes to find.

What a fast feed does buy is scheduling. A loop running every minute waits less for its next chance
to act. Four instances running in parallel on one person, three of them sharing a sensor, put that
at 1.8 minutes to the first microbolus on a rise, and about 2.6 minutes on a basal suspension. Both
are the size of the sampling interval they came from.

## What to build

Detection is solved, so effort spent improving it is spent in the wrong place. Sizing a dose to an
inferred meal cannot be done from the trace at all. Richer inference from the shape of an excursion
is unlikely to repay the work, because what exists arrives after the decision.

Two things are worth doing. Price consequence at the onset, from onset glucose and the hour. The
loop holds both, does not combine them, and they beat its own forecast on that question by 0.08.
Then shorten the gap between information and action, because what the loop will act on is largely
settled by the time it acts. A faster decision cycle buys one to three minutes of that. It buys
nothing else.

None of this shows that acting differently would improve an outcome. No observational corpus can
show that. A dosing change here needs a within-participant randomised comparison first.

## How this was measured

Two corpora of announced meals: 492,440 meals from 839 people on an open-source automated system,
and 71,761 from 189 people on sensor-augmented pump therapy in an earlier era. The second is an
independent replication. Rescue carbohydrate and entries below 8 g are excluded. Meal onset is
inferred from the trace.

The consequence work uses rise onsets instead of announcements, so it needs nobody to have logged
anything, and draws on all seven studies: 1,986,123 onsets from 1,807 people. A rise onset is a
climb of at least 25 mg/dL within thirty minutes, starting above the hypoglycaemia threshold. That
is roughly the set of events a detector fires on.

People are held out as folds throughout. Every interval comes from resampling people. The analysis
plan, including the 0.05 margin at twenty minutes or less, was fixed before any of this was
measured.

Where two arms score the same events, we report the paired difference. Two areas under the curve
each carrying their own interval tell you nothing about whether they differ, because their errors
move together.

## Limitations

Announced carbohydrate is somebody's own estimate. Its error caps the accuracy anything here can
measure, and we cannot separate that cap from the one physiology imposes.

Meal onset is inferred, not observed. A meal announced well after the eating is anchored badly.

The undeclared class holds dawn phenomenon, stress responses and rebounds. Those differ in shape for
reasons unrelated to food, so the detection figures bound what a detector can do rather than
isolating carbohydrate.

Consequence outcomes come from traces produced under active insulin therapy. What gets predicted is
the excursion that happened given the treatment given. The modelling uses 200 of the 1,807 available
people; intervals are narrow and effect sizes stable across the sweep, but no full-corpus run has
been done.

The cadence comparison rests on one person, two sensor eras that are not glycaemically matched, and
four parallel instances of which three drove a virtual pump. Read its numbers as what controllers
propose, not what they achieve.

None of these people use the system this programme develops. What transfers is a statement about the
information in a glucose trace, not about how any controller responds to it.
