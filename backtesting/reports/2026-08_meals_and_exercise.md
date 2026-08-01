# Exercise around meals and the lows that follow: what a cohort of ten closed-loop users shows

*Prepared from an anonymised analysis of ten people using an open-source automated insulin
delivery system. Analysis code is committed and the processing steps are reproducible.*

## Summary

Meals followed by physical activity carry roughly double the rate of subsequent low glucose,
and the effect is large enough to account for a meaningful share of the time spent below range
across this cohort. The mechanism is not what it first appears. People who go low after eating
and moving are not carrying more insulin than those who do not. They are carrying less, and the
crash rate falls as insulin on board rises. That points away from over-dosing and towards a
shortfall in the carbohydrate available to offset the glucose drain that exercise creates.

Activity in the period after a meal can be detected early enough to be useful. Step counts in
the first 75 minutes separate the meals that end low from those that do not, with an area under
the curve of 0.650. Heart rate does not separate them at all. The discrimination is modest, so
it supports an intervention that can be withdrawn if it proves unnecessary, and does not support
one that cannot.

## 1. Question and data

Two questions are addressed. What does exercise around a meal do to glucose outcomes, and can
the meals that will end low be identified early enough to act on.

Ten adults using the same open-source system contributed continuous glucose data and per-cycle
loop telemetry from 1 May 2026. Meals are defined from glucose alone, as a rise of at least 40
mg/dL from a local trough within 90 minutes, so that the definition does not depend on
carbohydrate being announced or on any particular algorithm being active. Lows are glucose below
70 mg/dL sustained for at least 15 minutes, which excludes single-sample dips. Activity is taken
from the step feed, which blends phone and watch sources. Heart rate is used where a watch
was worn.

## 2. How often meals end low

Of 4,399 meals, 1,386 were followed by a low within six hours, or 31.5 per cent. The median
interval between the meal and the low ranged from 1.6 to 2.9 hours across individuals.

The variation between people is wide and is the first thing to note. Three users ended a meal
low between 12 and 15 per cent of the time. Three others did so between 43 and 50 per cent of
the time. Any cohort-level figure therefore conceals two quite different situations, and the
implications for treatment differ accordingly.

A further observation constrains how much of the problem this pathway represents. Of 1,135
sustained lows, 287, or 25 per cent, had no meal in the preceding six hours. Those arise from a
different route, most plausibly basal or overnight, and nothing in this analysis speaks to them.

## 3. What exercise does to the post-meal window

An earlier segmentation of the same cohort, dated 27 July 2026, divided time by whether a meal
was followed by activity. The picture it produced remains the clearest statement of the
trade-off.

| Segment | Share of time | Time in range | Time above 180 | Time below 70 |
|---|---|---|---|---|
| Background, non-meal | 59% | 93.3% | low | flat |
| Post-meal, no exercise | 17.9% | 75.8% | 21.7% | 2.5% |
| Post-meal, with exercise | 23.1% | not recorded | 17.8% | 4.0% |

Exercise after a meal improves the high side and worsens the low side. Time above 180 mg/dL
falls from 21.7 to 17.8 per cent, while time below 70 rises from 2.5 to 4.0 per cent, with
severe lows below 54 rising from 0.5 to 0.7 per cent. Removing post-meal exercise from the
cohort review lifts time in range by 2.5 percentage points, which gives a sense of the size of
the effect.

The post-meal window without exercise is worth reading on its own, because it isolates how the
loop handles a meal. Time below range there is 2.5 per cent, identical to the background rate,
while time above 180 is 21.7 per cent. The loop is therefore slow rather than heavy-handed after
meals. It spends time high without paying for it in lows. The low cost appears only when
exercise is added.

## 4. The mechanism is not the dose

The intuitive explanation is that meal insulin meets a body made more sensitive by exercise, so
the dose was too large. The data do not support it.

Across 686 meal-and-exercise events in eight users with a step feed, those who went low were
carrying a median of 0.96 U of insulin at the onset of exercise. Those who did not were carrying
1.61 U. The meal boluses were near identical, at 2.4 and 2.1 U. Crash rate fell monotonically
across tertiles of insulin on board at the point exercise began, at 32, 22 and 18 per cent. A
dose-driven effect would slope the other way.

The present analysis reproduces this on an independent construction and a larger sample. Across
4,086 meals, mean insulin on board discriminated the meals that ended low with an area under the
curve of 0.463, which is below 0.5 and therefore inverted. Peak carbohydrate on board was
similarly inverted at 0.482. Less insulin and less carbohydrate were both associated with going
low.

The reading we favour is a shortfall in the counterweight rather than an excess of insulin.
Exercise recruits a glucose drain that is largely independent of insulin, through
contraction-mediated transport and heightened sensitivity. Whether that tips into hypoglycaemia
depends on what is available to offset it, which is the rate at which carbohydrate is still
appearing plus the glucose already circulating. High insulin on board is a marker of a
substantial meal still being absorbed, and the upward flux from that absorption offsets the
drain. Low insulin on board marks a small meal, or one already finished, leaving the drain
unopposed.

This interpretation is stipulated rather than demonstrated. What the data establish firmly is
the negative: the effect is not driven by the size of the dose. The physiological account of why
is consistent with the evidence but is not tested by it.

There is a reverse-causation caveat worth stating. Low insulin on board partly reflects the loop
having already withdrawn insulin on a falling glucose, so the association is not wholly
independent of what the loop did. Both readings point the same way, which is some comfort, but
it is not a controlled comparison.

## 5. Can the meals that end low be identified early

For an anticipatory response to be possible, the signal has to be visible before the descent.
Two windows were therefore examined. The first runs from the meal to the low, which describes
what was present but cannot support a prediction, since it ends at the event. The second covers
only the first 75 minutes after the meal, before any descent is apparent.

| Indicator, first 75 minutes | Area under the curve |
|---|---|
| Peak 30-minute step count | 0.650 (0.631 to 0.670) |
| Peak 60-minute step count | 0.655 (0.634 to 0.673) |
| Peak heart rate | 0.478 (0.446 to 0.508) |
| Peak heart-rate reserve | 0.469 (0.440 to 0.502) |

Three observations follow.

Step counts separate the two groups and heart rate does not. Both heart-rate measures include
0.5 within their intervals, so neither is distinguishable from chance. This is convenient in
practice, since heart rate requires a worn watch and was absent altogether for four of the ten
users, whereas step data was available for nine.

The signal is concentrated early. Restricting to the first 75 minutes improves discrimination
relative to the full meal-to-low window, from 0.620 to 0.650 for the 30-minute step count. The
activity that matters happens soon after the meal, which is what makes early action possible at
all rather than merely making the association visible after the fact.

The effect is present in most individuals. Seven of the eight users with sufficient data showed
a discrimination distinguishable from chance, ranging from 0.582 to 0.719. One did not, at
0.483.

## 6. Whether the signal is strong enough to act on

Discrimination indicates that a signal exists. It does not indicate that acting on it is
sensible. A simple rule was therefore evaluated: flag a meal when the peak 30-minute step count
within 75 minutes exceeds a threshold.

| Threshold | Meals flagged | Precision | Lift over base rate | Lows captured |
|---|---|---|---|---|
| 300 steps | 38.6% | 44.3% | 1.44 | 55.7% |
| 750 steps | 16.5% | 48.9% | 1.60 | 26.3% |

The base rate is 30.7 per cent. At the threshold giving the best lift, just under half of flagged
meals were followed by a low, against just under a third of all meals. Roughly a quarter of lows
were captured.

The rule does not select for severity. Among lows captured the median nadir was 57 mg/dL, and
among those missed it was 58 mg/dL. It identifies more of the same kind of event rather than the
worse ones.

Two conclusions follow for design. A lift of 1.6 with precision near 50 per cent is adequate for
an intervention that can be withdrawn when it turns out to be unnecessary, such as a temporary
basal reduction that is reversed once the expected activity does not materialise. It is not
adequate for an intervention that cannot be withdrawn. At this precision, half of any insulin
withheld or carbohydrate consumed would have been unnecessary.

## 7. Two populations, not one

The cohort does not respond to exercise in a single way, and this bears directly on what should
be built.

Two users showed post-meal time above 180 of 31 to 37 per cent with essentially no post-meal
time below range. For them exercise after a meal is beneficial, trimming a high they would
otherwise sit in, and the useful lever is more or earlier meal insulin rather than protection
from activity.

Three other users showed post-meal-with-exercise time below range of 6 to 14 per cent. For them
there is no glucose buffer and activity tips them low. Protection is the appropriate response.

These sets are disjoint. The people who need more insulin are not the people who need exercise
protection, and a single cohort-wide setting would move both in the same direction and help only
one. Per-user configuration is the mechanism that makes the distinction actionable.

## 8. What this means for the loop

The loop's difficulty here is not that its algorithm is poorly tuned. It is that the variable
that determines the outcome is not one it controls. The defence against an exercise-driven
glucose drain is glucose coming in. The loop's only lever is insulin going out, and by the time
the drain begins that lever has largely been spent, partly because the loop has already withheld
insulin on a falling glucose.

Two loop-side responses remain available. The first is anticipatory withdrawal before the meal
bolus commits, which requires knowing that activity is likely before it starts. Habitual timing
supports this: a prior built from time of day and weekday reached an area under the curve of
0.85 in earlier work, arming before movement in 55 per cent of episodes about 55 minutes ahead,
at a precision of 0.63. The second is prompting for carbohydrate, which addresses the right
variable but requires the person to act.

A reactive withdrawal triggered by observed steps was assessed separately and found to prevent
around 19 per cent of activity-driven lows at approximately a one-to-one cost in insulin, which
is too marginal to justify on its own and was retained only as a per-user option.

The post-exercise recovery period deserves a brief note because an early estimate of it was
wrong. The apparent delayed doubling of hypoglycaemia risk after exercise ended proved to be an
artefact of comparing a cumulative window against a fixed baseline. Measured against a matched
baseline the hazard is about 1.2 times, immediate rather than delayed, and gone within six
hours. The existing two-hour recovery window is therefore roughly correct, and at most warrants
extending to a gentle four-hour taper.

## 9. Limitations

The comparison is observational throughout. Meals followed by activity differ from meals not
followed by activity in more than the activity, including in the time of day they occur and in
what the person ate. No causal claim is made.

Meals are inferred from glucose rather than from announcement, so a rise from another cause will
be counted as a meal, and a meal that produced no rise will be missed. Activity is inferred from
step counts, which register walking well and register cycling, swimming or resistance work
poorly. The heart-rate null should be read with that in mind, since heart rate was available for
only six users and would be the natural signal for exactly the activity types steps miss.

The cohort is ten self-selected users of an open-source system, several of whom are actively
adjusting settings, so the population is neither random nor stable.

Finally, and most importantly, none of this establishes that acting on the signal prevents the
low. That question requires a controlled comparison, and the appropriate design is a
pre-registered within-person trial rather than further observation.

## Provenance

Analysis scripts are committed under `backtesting/scripts/`. The present work is in
`2026-08-meal-exercise-lows/`. Earlier findings drawn on here are in
`2026-07-postmeal-exercise-mechanism/` for the insulin-on-board result,
`2026-07-performance-segmentation/` for the segmentation, `2026-07-anticipation/` for the habit
prior and the recovery tail, and `2026-07-v6-activity/` for the reactive withdrawal assessment.

Findings are provisional. Effect sizes carry bootstrap intervals where stated, and results
without an interval should be read as indicative.
