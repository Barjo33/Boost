# Pricing a smaller committed dose against the record (2026-08-13)

*Reproduce: `commit_dose_replay.py`. 1,718 commits carrying a delivered dose across nine
participants, five hours of trajectory each, sensitivity taken per commit from the record.*

## What this is not

A replay that assigns a dose and then reads the recorded glucose is not a counterfactual. The
glucose that followed each commit followed the dose that was actually given, and reading it
against a different assigned dose measures nothing. The observational dose response in this cohort
comes out near 6 mg/dL per unit against a dithering estimate of about minus 45, because the dose is
assigned by the policy whose effect is in question. That is why the trial registered for this is
prospective.

## What can be bounded

Reducing the committed dose does not change the meal, so the carbohydrate side of the trajectory
can be held exactly as observed while the insulin side is recomputed. Insulin not given never acts,
so at any later time the counterfactual glucose is above the recorded glucose by

    delta_bg(t) = ISF x removed_dose x fraction of that dose that had acted by t

with sensitivity taken per commit from the record and the activity curve the app itself uses.

The bound is one-sided in a known direction. The recorded trajectory already contains whatever
counter-regulation the low provoked, and a smaller dose would have provoked less, so the true
counterfactual sits below this estimate. Lows avoided is therefore a ceiling. The loop's own
subsequent decisions are also unmodelled, and under a smaller dose it would have made different
ones.

## Result

Scaling every committed dose to 0.7, which is the registered intervention:

| arm | commits | observed lows | avoided | severe avoided | U removed | added mg/dL.h per commit | U per low avoided |
|---|---|---|---|---|---|---|---|
| uniform | 1,718 | 581 | 313 | 132 | 828.5 | 19.9 | 2.65 |
| oracle, late peak only | 161 | 67 | 29 | 16 | 74.1 | 0.5 | 2.55 |
| oracle, small excursion only | 855 | 337 | 170 | 70 | 394.7 | 3.7 | 2.32 |

The two oracle arms use knowledge unavailable at the time, so they upper-bound any targeting rule
that could ever be built.

Targeting is worth very little. Restricting the reduction to commits whose peak arrives within ten
minutes reaches 29 of the 313 avoidable lows, because those commits are 161 of 1,718. Its
efficiency, at 2.55 U removed per low avoided, is barely better than the uniform 2.65. Targeting on
the eventual excursion covers half the commits, reaches 54 per cent of the avoidable lows and is
12 per cent more efficient.

That is the answer to whether the late commit is worth singling out. Even with perfect foresight it
is not, and no rule built on observable state could reach that ceiling.

The result is robust to the insulin curve and scales as expected with the size of the reduction.

| activity peak | multiplier | lows avoided | severe avoided | U per low |
|---|---|---|---|---|
| 45 min | 0.50 | 424 | 158 | 3.26 |
| 45 min | 0.70 | 329 | 135 | 2.52 |
| 45 min | 0.85 | 211 | 107 | 1.96 |
| 55 min | 0.50 | 403 | 154 | 3.43 |
| 55 min | 0.70 | 313 | 132 | 2.65 |
| 55 min | 0.85 | 201 | 101 | 2.06 |
| 75 min | 0.50 | 374 | 146 | 3.69 |
| 75 min | 0.70 | 280 | 125 | 2.96 |
| 75 min | 0.85 | 177 | 94 | 2.34 |

Per participant the share of lows the reduction reaches varies widely, from 0.24 to 0.70.

| user | commits | observed lows | avoided | share | U removed |
|---|---|---|---|---|---|
| A | 260 | 42 | 24 | 0.57 | 183.4 |
| B | 348 | 90 | 51 | 0.57 | 194.5 |
| C | 273 | 121 | 59 | 0.49 | 98.3 |
| D | 177 | 88 | 21 | 0.24 | 72.2 |
| E | 39 | 11 | 7 | 0.64 | 14.0 |
| F | 211 | 53 | 30 | 0.57 | 99.4 |
| H | 56 | 13 | 9 | 0.69 | 35.9 |
| I | 16 | 7 | 3 | 0.43 | 4.7 |
| tim | 338 | 156 | 109 | 0.70 | 126.0 |

D is the participant the reduction reaches least and also the one with the highest commit-related
low rate, which is worth noting before the trial rather than after it.

## What follows

The cost side is the number to hold onto. Roughly 20 mg/dL hours of additional exposure above
180 per commit, at a ceiling of 313 lows avoided over 1,718 commits, is the trade the trial is
being asked to make, and the ceiling is optimistic on the low side while the cost estimate is not.

For the trial itself, two things change. The effect is large enough that the registered design
should detect it if the bound is anywhere near right, which is worth knowing before running it.
And there is no case for stratifying or targeting the intervention on commit lateness, because the
oracle version of that targeting is worth 29 lows against 313.

Confidence: PROVISIONAL. The insulin arm is well characterised and the sensitivity analysis is
stable, but a one-armed bound with the loop's own response unmodelled is not a substitute for the
trial, and the direction of its error is known rather than measured.
