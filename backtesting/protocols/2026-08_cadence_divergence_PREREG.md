# Dosing decisions under four sensing and delivery cadences: a pre-registered parallel instance study

Registered 2026-08-09. Version 1.4. The arms, measures and analysis set out below were fixed before
any data were collected.

Applies to the Boost fork of AndroidAPS, branch `v7-shadow-1m-test`, build `c0eaae13fe`, installed as the
flavours `full`, `fullb`, `fullc` and `fulld`, three of them on one handset and one on a second.

## 1. What this study is for

Whether a one minute continuous glucose monitor improves automated insulin delivery is an outcome
question, and the identification constraint that governs this programme prevents any backtest from
answering it, since the counterfactual glucose trajectory cannot be produced. An outcome trial is
possible but weak: the smallest difference in time in range detectable at 28 days per arm is around
seven percentage points against a baseline near 85, which no mechanism proposed for a two minute
latency gain predicts.

A prior question can be answered cheaply and exactly. Before asking whether a faster cadence changes
outcomes, it is worth establishing whether it changes decisions at all, and if so which decisions,
by how much, and in what circumstances. If the four configurations agree on almost everything, the outcome
question is largely settled by implication and no one need be exposed to an experiment to learn it.
If they disagree substantially, the disagreement itself shows where an outcome trial should be
aimed, rather than measuring aggregate glycaemia and hoping.

This study measures decisions. It does not measure outcomes and cannot be read as though it did.

## 2. Design

Four instances of the same build, run concurrently, differing in sensing cadence, decision cadence
and the minimum interval between automated boluses.

| Instance | Sensor | Handset | Glucose supplied | Decision taken | Minimum interval between boluses |
|---|---|---|---|---|---|
| A | its own, five minute | second handset | every 5 min | every 5 min | 5 min |
| B | the one minute sensor | first handset | every 1 min | every 5 min | 5 min |
| C | the same one minute sensor | first handset | every 1 min | every 1 min | 1 min |
| D | the same one minute sensor | first handset | every 1 min | every 1 min | 3 min |

B, C and D share one sensor and one handset. They see identical glucose, arriving at the same
instant, and differ only in what the software does with it. Those three comparisons are therefore
clean, and they carry the study.

A is on a separate handset with a separate five minute sensor, because a five minute view and a one
minute view cannot be taken from the same sensor at the same time. It is a control rather than a
contrast. A against B differs in sensor, in sensor site, in handset and in cadence at once, so a
difference between them cannot be attributed to any one of those, and certainly not to cadence.

A runs for the whole study alongside the others and is included in every reporting period, not
sampled or run briefly at the start. A noise floor estimated on part of the record would leave the
remaining comparisons uninterpretable over the rest of it.

The reason to run it anyway is that it measures the noise floor. Two sensors on one body disagree
for reasons that have nothing to do with the algorithm: different calibration, different sites,
different lag, different noise. A against B quantifies how much decision divergence arises from
nothing but wearing a second sensor. Every other comparison in the study has to be read against
that figure. If B against C is no larger than A against B, then the design cannot resolve the
question being asked, and the honest conclusion is that this study was not sensitive enough rather
than that cadence has no effect.

In A and B the decision cycle is five minutes and is therefore the binding constraint on how often
insulin can be given, whatever the configured minimum. In C and D the decision cycle is one minute,
so the configured minimum binds instead, at one and three minutes respectively.

D exists because B and C differ in two things at once. C against D holds the decision cadence at one
minute and moves only the minimum bolus interval, which makes it the only comparison in the set that
varies delivery frequency alone.

One instance is paired to the pump and delivers insulin. It is the participant's ordinary therapy
and is not altered for the study. The others take the virtual pump. They compute and record
decisions and deliver nothing.

Each instance uploads to its own Nightscout site, from which records are extracted into the local
analysis database under separate participant keys. This matters more than it appears to: the
analysis table is keyed on participant and timestamp, so several instances writing to one site would
overwrite one another and leave whichever record arrived last, with the remaining arms disappearing
and nothing reporting an error.

## 3. What the parallel instances are and are not

The three instances on the virtual pump run a complete loop. They accumulate their own insulin on
board from the doses they decide to give, and their subsequent decisions reflect that accumulated
state. This is deliberate. A cadence that doses more often builds a different insulin trajectory,
and the brakes and caps that respond to insulin on board therefore engage differently. That
interaction is part of the behaviour under study and suppressing it would answer a narrower and less
interesting question.

The limitation that follows is stated here rather than in a footnote. The glucose these instances
observe is real, and it responds to the insulin the pumping instance delivered, not to the insulin
they believe they delivered. Their state is therefore internally consistent but externally
counterfactual.

An earlier version of this protocol assumed that discrepancy grew without limit and specified a
daily re-anchoring to contain it. That was wrong, and the instances are not re-anchored. Insulin
decays, so the difference in insulin on board between an instance and reality is a steady state
rather than an accumulation, and it settles at approximately the difference in dosing rate
multiplied by the mean residence time of a unit. Under this participant's configured curve that
residence time is about seventy five minutes, so an instance dosing ten per cent more than reality
carries roughly 0.05 U of insulin on board that does not exist, against the 1.6 U it typically
carries. An instance dosing fifty per cent more carries about 0.25 U. Those are modest offsets and
they do not grow with the length of the run.

What does persist is a standing difference rather than a growing one. An instance whose insulin
never moves glucose will keep seeing glucose that has not responded, and will therefore keep dosing
somewhat differently from reality for as long as it runs, bounded by its own caps and its maximum
insulin on board. Every instance is subject to this equally and all of them see identical glucose,
so the comparison between them remains sound. What cannot be claimed is that any single instance
behaves as it would have done in reality, since in reality its insulin would have moved the glucose
it is looking at.

The difference in insulin on board between each instance and the pumping instance is recorded on
every cycle and reported, so that the size of the offset is visible in the results rather than
assumed from the argument above.

## 4. Hypotheses

The primary hypothesis is that C and D, which differ only in how closely spaced automated boluses may
be, propose the same insulin over matched intervals. This is the cleanest comparison available, since
both run on the same sensor and the same handset and differ in one setting.

The second hypothesis is that B and C propose the same insulin over matched intervals. These share a
sensor and a handset but differ in both decision cadence and bolus spacing, so a difference is
attributed only with reference to the C against D result.

The third is a control rather than a hypothesis about cadence. A and B are expected to produce
similar results, and the size of the difference between them estimates how much divergence arises
from wearing a second sensor on a second handset rather than from anything the software does. A
large difference here invalidates the interpretation of the other two rather than constituting a
finding of its own.

The expectation, from the offline work summarised in section 1, is that C and D differ in the
granularity and timing of delivery rather than in total insulin, that any difference between B and C
is mostly the delivery frequency, and that A and B agree to within sensor to sensor variation. A
null on any of these is a useful result.

## 5. Measures

All measures are computed over matched wall clock intervals rather than per decision cycle, since
the instances do not share a cycle count. The interval is thirty minutes unless stated.

Insulin proposed per interval, by instance. This is the primary measure and the comparison of
totals is the primary comparison.

The distribution of individual dose sizes and of the intervals between doses, which is where a
faster decision cycle is expected to show itself even if totals agree.

The separation between instances of insulin on board, expressed as the difference from the pumping
instance, and whether it settles or grows as the run continues.

The proportion of cycles on which each instance's dose was limited by a cap or a brake rather than
by the sizing calculation, since a cadence that proposes more often may be restrained more often
without proposing more in total.

Agreement on direction at the five minute grid points where all four instances have taken a
decision, which is the closest this design comes to a like for like comparison.

Predicted glucose, from two independent sources. The loop publishes its own forward projection on
every cycle, and a shadow forecaster runs alongside it and publishes a thirty and a sixty minute
forecast. Both are recorded for every instance, so both can be compared across arms and tracked
across the life of the study rather than pooled into a single figure.

Two cautions govern how those are read. The first is that the loop's eventual glucose figure is not
a forecast. Earlier work in this programme established that it is an artefact of the control
calculation and does not behave as a prediction, while the insulin projection at thirty minutes
does. Eventual glucose is therefore reported as a divergence measure between arms and is not scored
for accuracy; the insulin projection and the shadow forecaster are scored.

The second is that a virtual instance's prediction is conditioned on insulin it did not deliver.
Scoring it against the glucose that actually occurred therefore penalises it for the counterfactual
rather than for its cadence, by an amount that scales with the insulin on board offset from section
3. That offset is reported alongside every accuracy figure, and any accuracy comparison is
additionally reported restricted to cycles where the offset is small, so the reader can see whether
the ranking survives.

Accuracy is scored against the glucose observed thirty and sixty minutes later, with the assumption
of no change as the reference. A forecaster that cannot beat that assumption is adding nothing, and
on a recent cohort the shadow forecaster beat it by about one milligram per decilitre, which is
consistent but too small to act on. The question here is whether a faster feed changes that margin.

The A against B difference on every measure above, reported alongside the others as the noise floor.
No difference between B, C and D is interpreted without it.

The noise floor is computed over the same windows as the comparison it qualifies, rather than pooled
once across the study. Sensor to sensor disagreement is not constant: it changes with sensor age, with
site, and across the warm-up of each new sensor. A floor averaged over the whole period would
understate it early in a sensor's life and overstate it later, and either error would be applied to
the wrong comparison.

## 6. Analysis

Differences are summarised over intervals, with confidence intervals from a bootstrap that resamples
whole days rather than intervals, since intervals within a day are strongly dependent.

Every comparison is additionally reported against elapsed time since the run began, and alongside
the insulin on board offset described in section 3. The reasoning there says that offset should
reach a steady state within a few hours and stay there. If instead it grows with time, that
reasoning is wrong and the later part of the record is measuring accumulated divergence rather than
cadence, in which case only the early period supports a claim. Specifying the check in advance means
a growing difference cannot be presented afterwards as a large effect.

Days on which any instance received fewer than ninety per cent of expected glucose readings are
excluded from all instances together, not from the affected instance alone, so that the arms always
cover the same wall clock. The number of days excluded is reported.

No hypothesis test is planned. The study is descriptive, the participant is one, and the quantity of
interest is the size of the difference rather than its statistical significance against a null that
no one holds.

## 7. Safety

Only the pumping instance can deliver insulin, and it runs the participant's ordinary configuration.
The three virtual pump instances have no route to the pump. The study therefore introduces no change
to therapy and no additional risk, which is the reason for running it before any outcome trial.

The standing time below range limits continue to apply to the participant's therapy as they would on
any other day. They are not stopping rules for this study, because this study does not alter
therapy.

## 8. Limitations

One participant and one sensor, so nothing here generalises to other people or other sensors. The
virtual pump instances are counterfactual in the sense set out in section 3. Decisions are not
outcomes, and no statement about time in range, time below range or any other glycaemic measure can
be derived from this design. Arm A introduces a second sensor and a second handset, so nothing about it isolates cadence and it
is used only to bound the noise. Three instances on the first handset share a processor and a battery, and if
that degrades the timeliness of any instance it would appear as a difference between arms; cycle
timing is therefore recorded and checked before the comparisons are made.

## 9. What follows

If the instances agree closely, the outcome trial registered separately is not worth running in its
present form and the programme's offline conclusion stands. If they disagree, the circumstances in
which they disagree define a narrower and better powered outcome trial than measuring aggregate
glycaemia across arms.
