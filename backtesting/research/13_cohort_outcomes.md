# Cohort outcomes, and what the migration actually changed

## Hypothesis

The V1 generation was replaced by the V5 and V6 generation across a small cohort of volunteers
through mid 2026. The hypothesis under test was the obvious one: that the newer generation produces
better glycaemic outcomes, and that the difference would be visible in the record.

## Investigation

The comparison was attempted three ways, each addressing a weakness in the last.

The first was a straightforward between-generation comparison of time in range across the cohort. The
second adjusted for the observation that participants who migrated differed from those who did not,
and that basal settings changed alongside the algorithm. The third was a within-participant
comparison over matched windows, which removes differences between people entirely.

## Methods

Recorded under `backtesting/scripts/2026-07-user-comparison/`, `2026-07-boost-review/` and
`2026-08-boost-cohort/`. Era membership is established from the algorithm's own telemetry rather than
from calendar dates, because a window of days is not a window of one algorithm: across this cohort
the record contains a participant on a shadow build of a different loop, one running a silent earlier
generation, one who moved to a different closed loop entirely part way through, and two who changed
build mid-window.

Days are admitted only where the intended engine accounted for at least ninety per cent of that day's
cycles, the day carried at least 250 readings and those readings spanned at least twenty hours.
Glucose is taken from the sensor series rather than from the decision cycles, because between a sixth
and a third of decision rows arrive under a minute after the previous one, which is the loop running
again on the same reading, and averaging over cycles counts those moments twice.

## Results

The unadjusted cohort difference was about thirteen percentage points. Adjusted for selection and for
basal differences it falls to 1.2 points, with a permutation p of about 0.27, and most of what
remains is overnight.

The within-participant comparison is the cleanest available and finds nothing distinguishable. Over
twenty days each side, on the five participants with enough data in both eras, time in range moves
0.2 points with an interval from minus 6.3 to plus 4.7, and every other outcome measure also spans
zero. Individually two participants move in opposite directions by comparable amounts and three do
not move.

A participant who spans both windows without migrating to the newer generation moved 4.8 points over
the same calendar, further than the migrated group.

Current cohort performance, over the seven days to 11 August 2026 across eight participants weighting
each equally, is 87.4 per cent time in range with an interval from 84.0 to 90.8, 73.6 per cent in the
tighter band, 4.0 per cent below 70 mg/dL and 0.4 below 54.

## Discussion

The migration is outcome-neutral on the evidence available, and saying so plainly is the point of
this document. Three separate approaches converge on it, and the third is a within-participant design
that removes the confound the first was criticised for.

What the third cannot remove is the calendar. Nobody held one algorithm across both windows, so
generation and season and settings and the participants' own accumulating experience all move
together. The participant who moved further without migrating is the closest thing to a comparator
and points the same way, which makes the calendar the more economical explanation for both.

This matters for how the rest of the series should be read. Several individual levers in this
programme have measured effects, and the aggregate of those effects is not visible in the cohort
outcome. That is not a contradiction: the levers are small, the cohort is single digits, day-to-day
variation in time in range has a standard deviation of about nine percentage points, and the smallest
difference a month-long comparison could detect is around seven points. An aggregate null over this
sample is what a collection of small true effects would look like.

The methodological corrections recorded here have changed published numbers twice. Selecting a
window by calendar date rather than by engine telemetry pooled four different algorithms into one
comparison. Averaging glucose over decision cycles rather than over the sensor series biased each
participant's time in range by up to two points in participant-specific directions. Both are the sort
of error that produces confident wrong answers rather than obvious failures, and both were found by
checking a result against an independent construction of the same quantity rather than by inspection.
