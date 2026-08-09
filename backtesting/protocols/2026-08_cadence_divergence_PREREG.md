# Dosing decisions under four sensing and delivery cadences: a pre-registered parallel instance study

Registered 2026-08-09. Version 1.1. The arms, measures and analysis set out below were fixed before
any data were collected.

Applies to the Boost fork of AndroidAPS, branch `v7-shadow-1m-test`, build `c0eaae13fe`, installed
four times on one handset as the flavours `full`, `fullb`, `fullc` and `fulld`.

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

One participant, one continuous glucose monitor, four instances of the same build running
concurrently on one handset. Each instance receives glucose from the same sensor and is configured
to a different combination of sensing cadence, decision cadence and minimum interval between
automated boluses.

| Instance | Glucose supplied | Decision taken | Minimum interval between automated boluses |
|---|---|---|---|
| A | every 5 min | every 5 min | 5 min |
| B | every 1 min | every 5 min | 5 min |
| C | every 1 min | every 1 min | 1 min |
| D | every 1 min | every 1 min | 3 min |

In A and B the decision cycle is five minutes and is therefore the binding constraint on how often
insulin can be given, whatever the configured minimum. In C and D the decision cycle is one minute,
so the configured minimum binds instead, at one minute and three minutes respectively.

The fourth arm exists because C and B differ in two things at once, and without D there is no way to
tell which of them is responsible for any difference between them. C against D isolates the minimum
bolus interval with the decision cadence held at one minute, which is the only comparison in the set
that varies delivery frequency alone.

One instance is paired to the pump and delivers insulin. It is the participant's ordinary therapy
and is not altered for the study. The other three take the virtual pump. They compute and record
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
counterfactual, and the discrepancy compounds the longer an instance runs without correction. Early
in a run the comparison is close to a like for like one; late in a long run it is a comparison of
divergent parallel worlds. Nothing in the design removes this, and the measures in section 5 are
chosen so the decay is visible in the results rather than assumed away.

To bound it, the virtual pump instances are re-anchored to the pumping instance daily, by restoring
their treatment history to the record of what was actually delivered. Elapsed time since the last
anchoring is recorded on every cycle and enters the analysis as described in section 6.

## 4. Hypotheses

The primary hypothesis is that A and B, which differ only in the cadence of the glucose feeding the
decision, propose the same insulin over matched intervals.

The second hypothesis is that C and D, which differ only in how closely spaced automated boluses may
be, propose the same insulin over matched intervals.

The third hypothesis is that B and C propose the same insulin over matched intervals. These differ
in both decision cadence and bolus spacing, so a difference here is attributed only with reference
to the C against D comparison.

The expectation, from the offline work summarised in section 1, is that A and B differ very little,
that C and D differ in the granularity and timing of delivery rather than in total insulin, and that
any difference between B and C is mostly the delivery frequency rather than the faster decision. A
null on any of these is a useful result.

## 5. Measures

All measures are computed over matched wall clock intervals rather than per decision cycle, since
the instances do not share a cycle count. The interval is thirty minutes unless stated.

Insulin proposed per interval, by instance. This is the primary measure and the comparison of
totals is the primary comparison.

The distribution of individual dose sizes and of the intervals between doses, which is where a
faster decision cycle is expected to show itself even if totals agree.

The separation between instances of insulin on board, expressed as the difference from the pumping
instance, and its behaviour as time since anchoring increases.

The proportion of cycles on which each instance's dose was limited by a cap or a brake rather than
by the sizing calculation, since a cadence that proposes more often may be restrained more often
without proposing more in total.

Agreement on direction at the five minute grid points where all four instances have taken a
decision, which is the closest this design comes to a like for like comparison.

## 6. Analysis

Differences are summarised over intervals, with confidence intervals from a bootstrap that resamples
whole days rather than intervals, since intervals within a day are strongly dependent.

Every comparison is additionally reported stratified by time since the last anchoring, in bands of
under six hours, six to twelve, and twelve to twenty four. If the differences grow materially across
those bands then the later bands are measuring accumulated divergence rather than cadence, and only
the first band supports a claim about cadence. This stratification is specified in advance precisely
so that a growing difference cannot be presented as a large effect.

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
be derived from this design. Four instances on one handset share a processor and a battery, and if
that degrades the timeliness of any instance it would appear as a difference between arms; cycle
timing is therefore recorded and checked before the comparisons are made.

## 9. What follows

If the instances agree closely, the outcome trial registered separately is not worth running in its
present form and the programme's offline conclusion stands. If they disagree, the circumstances in
which they disagree define a narrower and better powered outcome trial than measuring aggregate
glycaemia across arms.
