# Research papers: the Boost automated insulin delivery programme

A record of what was investigated during the development of the V6 and V7 generations of the Boost
algorithm, what the investigations found, and what was built or abandoned as a result. Each document
takes one topic through hypothesis, investigation, methods, results and discussion, and names the
analysis folder its figures come from so that any number can be traced to the code that produced it.

The work covers a cohort in the single digits to low tens, self-selected, running a modified fork of
AndroidAPS on their own settings. Nothing here is a clinical trial and nothing generalises beyond the
people who took part. Several documents report nulls, and they are written at the same length as the
positive results, because the point of the record is to stop questions being asked twice and that
only works if the negative answers are as findable as the positive ones.

## The constraint

There is no glucodynamic simulator for these participants, so the counterfactual glucose trajectory
for a decision that was not taken cannot be produced. Prediction and detection questions therefore
get clean out-of-sample answers with participants held out as folds. Policy questions get associations
priced against observed outcomes, with the caveat stated, strengthened where possible by
within-participant and matched-baseline designs. The word "would" appears only where a randomised or
within-subject design supports it.

## The papers

**[00 The programme](00_the_programme.md).** The frame every other document works under. Sets out
the identification constraint and how it divides questions into the answerable and the merely
priceable; the three evidence rules adopted after each was violated, covering matched baselines,
leakage-free splits and intervals on every effect size; and why the shipping controller is
deterministic while everything inferential stays offline.

**[01 Dose timing and sizing](01_dose_timing_and_sizing.md).** Whether insulin can be moved earlier
in a meal without adding to it. Moving is harm-neutral; adding costs about fifteen percentage points
of lows, and the distinction has done more work than any single lever since it converts an argument
about aggression into a testable claim. Also the confirm gate blocking 26 to 29 per cent of confirms
that preceded a high, and a retired acceleration detector that leads the current confirm by fifteen
minutes at 98 per cent recall and 15 per cent precision.

**[02 The confirm state](02_the_confirm_state.md).** Three attempts to identify which confirms are
followed by hypoglycaemia, all returning chance, before anyone asked whether the post-confirm low
rate was elevated at all. Against controls matched within participant on glucose, insulin on board
and hour of day, confirming roughly doubles the chance of a low and quadruples the chance of a severe
one. Includes the acceleration metric misreading that started the enquiry.

**[03 Where the loss comes from](03_where_the_loss_comes_from.md).** Attribution of every episode
outside range to the mechanism that started it, with a foreseeability layer. Time above range is
mostly brake suppression, late confirm and cap clipping; time below range is mostly activity and
basal drift. Records the forward-flag artefact that had rescue overshoot ranked second among low
causes when it ranks fourth, a lever having nearly been built on it.

**[04 Restraint](04_restraint.md).** A brake credited with a third of time above range, priced
properly. Under a strict definition it owns 675 minutes over six weeks, of which 3 per cent was
safely recoverable and 13 per cent demonstrably prevented a low. The frequently quoted 90 per cent
correct splits into an outcome-proven part and a much larger correct-by-assumption part, on a sample
where one participant contributes half.

**[05 Prediction and the twin](05_prediction_and_the_twin.md).** A per-person state estimator that
is a good forecaster, a better hypoglycaemia detector than the incumbent at a third to a half the
false alarms, worse than a naive trend at rises, and not a controller. Insulin sensitivity can vary
eightfold inside the filter without changing its accuracy, because the latent meal term absorbs it,
so the forecast cannot be trusted off the policy that generated it.

**[06 Post-rescue behaviour](06_post_rescue.md).** Why demoting the algorithm's tier fails to
restrain a rebound after a treated low, and the graduated scale on the final microbolus that does.
The best-priced guard found anywhere in the programme, at 34 per cent of removed insulin sitting
before a low, with a leave-one-out floor of 27.

**[07 Exercise and activity](07_exercise_and_activity.md).** Steps nearly triple the forward
hypoglycaemia rate and the relationship does not transfer between people, which validates per-user
thresholds over a global model. The post-meal exercise crash is not dose-driven: the participants who
crash carry less insulin, not more, and the mechanism is a carbohydrate counterweight failure that
puts the loop on the wrong side of the problem.

**[08 Overnight and sleep](08_overnight_and_sleep.md).** The algorithm's advantage is overnight and
anti-phase with its predecessor, which is ahead after breakfast. The night gate suppresses 47 per
cent of amplifications, all of them nocturnal and unannounced. Learned bedtime converges to a
constant and is not learning.

**[09 Sensitivity and absorption](09_sensitivity_and_absorption.md).** Replacing a deviation-based
sensitivity estimate with one anchored to total daily dose, and why a proposed equivalence between
two sensitivity measures fails at clinical tolerance. Contains the observation that underpins most of
the algorithm's restraint: at recovering highs with substantial insulin on board, roughly 19 per cent
of cycles sit before a low against 7 per cent at low insulin.

**[10 Anticipation](10_anticipation.md).** Acting before the event rather than after. Exercise is
anticipable per participant and not across them; meals are the reverse. A weak detector at 0.63
precision becomes usable when the action is retractable, which moves safety from accuracy to design
and lowers the accuracy bar to something the data can meet.

**[11 Per-user configuration](11_per_user_configuration.md).** Four attempts to tune dosing knobs
online against outcomes, all of which failed, in both directions and for both caps and sliders, with
revert rates as the signature. The static per-user derivation they were trying to beat is what ships,
and these four experiments are the empirical argument for keeping learning out of the dose path.

**[12 The glucose signal](12_the_cgm_signal.md).** What a one-minute sensor carries. The two cadence
eras differ by a single flat scale factor, prediction gains nothing, and rate of change is estimated
slightly worse at one minute. A faster feed buys about two minutes of latency rather than bandwidth.
Also the ingestion path that discards four readings in five before the algorithm sees them, which
applies to anyone fitting a fast sensor to an unmodified loop.

**[13 Cohort outcomes](13_cohort_outcomes.md).** Whether the migration to the newer generation
improved anything. Three approaches converge on outcome-neutral, including a within-participant
comparison whose interval spans zero on every measure. Explains why that is consistent with the
individual levers being real, given the sample size and the day-to-day variation.

**[14 Methods and tooling](14_methods_and_tooling.md).** The attempts to relax the identification
constraint, and their limits. Simulator fidelity graded across six levels, failing in one direction
by being uniformly too easy; the survival-conditioning error that flattered an earlier assessment;
and the harness that drives the real engine from analysis code at 0.991 fidelity.

## Citations and provenance

Each paper names the analysis folder its figures come from, in the form
`backtesting/scripts/2026-07-residency/` and similar. Those folders hold the scripts, the raw
reports and the intermediate tables, and they live in a separate private repository alongside the
algorithm source. They are not reproduced here.

A reader of this repository therefore cannot follow a citation through to the code, and that is a
real limitation rather than an oversight. The citations are given so that anyone with access to the
analysis repository can find the exact script that produced a figure and re-run it, and so that the
provenance of every number in this series is recorded even where it cannot be followed from here.

The underlying data cannot be published in any case. It is the continuous glucose and insulin record
of a small number of identifiable people who consented to their data being used for this work and
not to its release.

## Conventions

Participants are identified by letter. Figures carry intervals, computed by resampling participants
rather than observations wherever the question concerns people rather than cycles. Where a figure has
been corrected, the corrected value is given and the original omitted, except in the two cases where
the correction is itself the finding and the reader cannot judge the result without seeing what it
replaced.
