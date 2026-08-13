# Where the time out of range actually comes from

## Hypothesis

Improving an algorithm requires knowing which of its behaviours is costing the most, and that had
never been measured. The working assumption in mid 2026 was that time above range came mainly from
dosing too little too late at meals, and that time below range came mainly from insulin stacking.
Both assumptions were made without evidence, and both turned out to be wrong in important respects.

The design question was therefore an attribution one. If every episode outside range could be
assigned to the mechanism that started it, the resulting shares would say where effort belonged. A
second question followed: whether the episodes were foreseeable, since a mechanism that produces
avoidable episodes is a better target than one that produces surprises.

## Investigation

Each participant's timeline was segmented into episodes above 180 mg/dL and below 70 mg/dL, brief
interruptions bridged where the gap was under twenty minutes. Each episode's onset was attributed to
one proximate mechanism from the telemetry in the forty five minutes before the crossing, and the
onset was made to own the episode's minutes on the reasoning that preventing the onset prevents the
episode. Minutes were counted as cycles multiplied by the sensor interval.

A separate machine learning layer then asked whether each episode had been foreseeable, by predicting
forward highs and forward lows an hour ahead and scoring the cycle forty five minutes before each
onset. A cause whose episodes carry high model risk in advance is a cause worth anticipating; one
whose episodes arrive unforeseen needs a faster response instead.

## Methods

Roughly 87,000 decision cycles and 1,100 episodes from eight participants over February to July 2026,
recorded under `backtesting/scripts/2026-07-residency/`. The prediction layer used gradient boosting
with participants held out as folds, so no within-participant leakage could inflate the foreseeability
figures. Base rates were 0.09 for forward highs and 0.04 for forward lows.

The attribution is purely descriptive over observed data. No counterfactual glucose is claimed, and
the assignment of an episode to a mechanism says that the mechanism was proximate, not that fixing it
would have prevented the episode.

## Results

Time above range divided, across the cohort, into brake suppression at 34 per cent, late confirm at
16, cap clipping at 15, recovering hold at 11, undersizing at 9, uncoverable at 10 and non-meal highs
at 5. The dispersion between participants is larger than the cohort figure suggests: brake
suppression ran from 11 per cent for one participant to 47 for another, and cap clipping from 0 to 59.

Foreseeability separated these cleanly. Forward highs were predictable at an area under the curve of
0.83 and forward lows at 0.78. Brake suppression carried 1.5 times base risk forty five minutes
before onset and non-meal highs 2.4 times, so both were already visible. Cap clipping, undersizing
and uncoverable highs carried 0.5 to 0.9 times base risk, meaning they arrived unannounced. That
distinction assigns anticipation to the first group and faster or larger response to the second.

Time below range is where the study corrected itself, and the correction reversed a conclusion. The
original attribution put rescue overshoot at 37 per cent pooled and 44 per cent by participant
median, making it the second largest low mechanism after activity. The classifier had taken its
rescue antecedent from a forward-looking flag, meaning a low within the following three hours, which
leaks the outcome into the cause and makes the bucket close to tautological. Replaced with a backward
antecedent, meaning a low in the preceding three hours and therefore a genuine recurring or
see-sawing pattern, rescue overshoot collapsed to 7 per cent pooled and 5 by median. The time
reallocated almost entirely to basal and sensitivity drift, which rose from 1 per cent to 30 pooled
and 37 by median.

Activity was unaffected by the correction at 48 per cent pooled and 36 by median, as was stacking at
16 and 17. The corrected ranking is therefore activity first, basal drift second, stacking third and
rescue a distant fourth.

## Discussion

The headline is that lows dominate the addressable loss and are not a dosing-brake problem, while the
addressable part of the highs is sizing and timing rather than restraint. That reoriented the
programme: the exercise protections and the step ingest follow from the activity share, per-user cap
sizing follows from cap clipping, and the confirm age gate follows from late confirm being both large
and foreseeable.

The forward-flag error deserves recording in its own right, because it was not a small error and it
was not obvious. A rescue-handling lever was on the point of being designed on the strength of rescue
overshoot ranking second among low causes. It ranks fourth. Anything built on the original figure
would have been built on a flag that partly encoded its own outcome, and the corrected result says
plainly not to build a rescue lever on this data. The general lesson, which recurs elsewhere in this
series, is that a feature computed with a forward window has to be checked against the direction of
the question being asked.

The brake share of 34 per cent is the other number that did not survive contact with scrutiny, though
for a different reason. It is proximate rather than causal: the brake suppressing during a rise is
not the same as the brake being wrong, since some suppression is correct restraint at high insulin on
board. Pricing that properly required a separate audit, which is the subject of the next document and
which reduced the apparent opportunity considerably.

Two limitations bound everything here. The mechanism assignment uses an ordered chain, so a cycle
matching two causes is credited to whichever appears first, and the ordering was chosen before the
results were seen but is still a choice. And the per-participant spread is wide enough that the
cohort column is a poor description of any individual, which is the argument for per-user
configuration that appears throughout this programme.
