# Methods: what the record can be asked, and the instruments built to ask it

## Hypothesis

The absence of a glucodynamic simulator is the binding constraint on this programme, so two lines of
work attempted to relax it. The first asked whether an existing published simulator could stand in
for the participants, which would supply the counterfactual trajectories that the observational
record cannot. The second asked whether the shipped algorithm itself could be driven from analysis
code, so that a proposed change could be evaluated against real inputs without deploying it.

## Investigation

Simulator fidelity was assessed by building a registry of signatures measured on real data and
checking whether the simulator reproduces each. The signatures were graded rather than pooled,
because a simulator can match a distribution while failing on structure, and the two failures have
different consequences.

The harness question was answered by building one: a bridge that runs the real Kotlin engine
components from Python, so that the forecaster, the back-out state machine and the sleep detector
respond to reconstructed histories exactly as they would in the field.

## Methods

Recorded under `backtesting/scripts/2026-07-insilico/` with the fidelity suite, and
`backtesting/scripts/kotlin-harness/`. Fidelity signatures are graded across six levels, of which
distributional agreement is the weakest and structural agreement the strongest.

## Results

The simulator fails in one direction, consistently. It is too smooth, contains no unannounced meals,
has insulin that always works, and no sensor drift or exercise. Against the signature registry it
scored three failures, two structural gaps and one pass on the original assessment.

Two of the three failures were found by the weakest checks, which is the useful part. Carbohydrate
ratio and correction factor in the published configuration were drawn rather than measured, giving a
published-to-generated ratio of 1.10 against 0.63, and there was a zero-insulin opening step. Fixing
those was worth three signatures.

The structural gaps turned out to be the person and the loop rather than the physiology. Adding a
behaviour layer and a loop layer took the suite from three of eleven signatures to six. An earlier
assessment had silently conditioned on survival: a physiology-only run kills two or three virtual
participants in ten, and those runs were being dropped.

The harness reproduces the shipped forecaster to a fidelity of 0.991, which is close enough to treat
harness results as engine results for the components it covers. Driving the dose calculation itself
was deferred.

## Discussion

The simulator is not a substitute for the counterfactual and was not adopted as one. What it is
useful for is bounding a proposal's behaviour in circumstances the real record does not contain, and
for finding failures that are structural rather than statistical. The direction of its failure
matters: a simulator that is uniformly too easy will make any controller look good, and a controller
tuned against it would be tuned against an absence of the very events that make the problem hard.

The survival-conditioning error is the most transferable lesson here. An assessment that silently
drops the runs where the virtual participant died is measuring performance among survivors, which
flatters everything, and the flattery is invisible because the dropped runs leave no trace in the
output. Any evaluation that can fail catastrophically needs its failures counted rather than
excluded.

Two further method rules were adopted after being violated, and both are recorded in the register's
recurring lessons.

Effect sizes are provisional until measured against a matched baseline. The list of results in this
programme that shrank or reversed under one is long enough to be the default expectation rather than
a caveat: a brake credited with a third of high-time, a cohort advantage of thirteen points, a
doubled post-exercise hazard, and a low-cause ranking that inverted when a forward-looking flag was
replaced with a backward-looking one.

Cross-validation must hold participants out. Tuning a hypoglycaemia model with Optuna produced a
fourteen point gain when the split was random and seven tenths of a point when it was grouped by
participant. The production model was not replaced, and grouped splitting is now the default
everywhere in the programme.

The final methodological position is that the bottleneck is identification rather than modelling, and
that the instruments built here do not change it. They make it cheaper to ask the questions the
record can answer, and they make it clearer which questions it cannot.
