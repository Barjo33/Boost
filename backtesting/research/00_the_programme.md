# The Boost research programme: what was asked, and how it was answered

This is the first of a series recording the investigations behind the V6 and V7 generations of the
Boost algorithm. Each subsequent document takes one topic and sets out the hypothesis that was
entertained, how it was investigated, what the methods were, what the results said and what follows.
This one sets out the constraint every other document works under, the standards the series holds
itself to, and the map of what is covered where.

## The constraint that shapes everything

There is no glucodynamic simulator for these participants, so the counterfactual glucose trajectory
for a dosing decision that was not taken cannot be produced. The record contains what the algorithm
decided and what glucose then did. It does not and cannot contain what glucose would have done had
the algorithm decided otherwise.

This divides the questions into two kinds, and the division runs through the whole series.

Prediction and detection questions are clean. Whether a rise can be seen an hour ahead, whether a
sensor artefact can be distinguished from a real fall, whether exercise can be anticipated from
habit: these are answered out of sample, with participants held out as folds so that cross-user
generalisation rather than per-person memorisation is what gets measured. The answer is a number with
an interval and it means what it says.

Policy questions are not clean. Whether a smaller confirm dose would have avoided a low, whether an
earlier bolus would have flattened a peak: these require the counterfactual. What the record can do
is price a policy against observed outcomes, which is an association and is stated as one, and then
strengthen it with within-participant and matched-baseline designs. The word "would" appears in these
documents only where a randomised or within-subject design supports it.

The bottleneck across the programme has been identification rather than modelling. Where a question
turned out to need a better model, the model was usually available. Where it turned out to need a
counterfactual, no model helped. This is why the series contains more discarded levers than shipped
ones, and why several of the discarded ones were discarded after the modelling worked.

## What separates a finding from an impression

Three rules were adopted after each was violated, and they are applied retrospectively in these
documents, which is why some earlier numbers appear here smaller than they were first reported.

An effect size is provisional until it has been measured against a matched baseline. Several
large-looking results dissolved when one was constructed: a brake credited with a third of high-time
turned out to be right for a different reason, a cohort advantage of thirteen percentage points fell
to one once selection and basal differences were accounted for, and a doubled post-exercise hazard
turned out to be an artefact of the window length. Where a number in this series has been corrected,
the corrected number is given and the original is not.

An effect size is provisional until it has survived a leakage-free split. Tuning a hypoglycaemia
model with Optuna produced a fourteen point gain in cross-validation and seven tenths of a point when
the split held participants out. The production model was not replaced.

Every effect size carries an interval, and where the interval spans the baseline the result is
reported as unproven rather than as suggestive. The population is small, self-selected and in single
digits to low tens, so intervals are computed by resampling participants rather than observations
wherever the question is about people rather than about cycles.

## What is measured and what ships

The shipping controller is deterministic: a state machine, multipliers, caps, a composed brake floor,
a rule-based sleep detector and a per-user configuration derived offline. Two pre-trained machine
learning models are applied at inference and neither learns online. Everything Bayesian or
inferential in this programme is offline decision support, and its output is a decision about what to
build rather than a number the loop consumes.

That separation is deliberate and is the reason several otherwise interesting results were not
shipped. A model that learns in the dose path would be learning the person and the policy at the same
time, from data the policy generated, with no way to tell the two apart.

## The series

The topics are ordered roughly as the algorithm encounters them: seeing glucose, deciding to act,
sizing the action, restraining it, and configuring the whole thing per person.

The CGM signal, covering cadence, smoothing, compression artefacts and what a faster sensor does and
does not carry. Dose timing and sizing at meals, covering the question of whether insulin can be
moved earlier without adding to it. The confirm state and the crash that sometimes follows it.
Restraint, covering brakes, caps and the composed floor. Post-rescue behaviour and the rebound guard.
Exercise and activity. Overnight and sleep. Insulin sensitivity, total daily dose and absorption.
Prediction, forecasting and the digital twin. Anticipation, which is the attempt to act before rather
than after. Per-user configuration and the repeated failure of online tuning. Cohort outcomes and the
migration from the V1 generation. Finally the methods themselves, covering the simulator fidelity
work and the harness that lets the real engine be driven from analysis code.

Each document names the investigation folder under `backtesting/scripts/` that holds the scripts and
the raw report, so that any number in the series can be traced to the code that produced it and
re-run. Where a document reports a null, the null is stated as plainly as a positive result, because
the register exists to stop questions being asked twice and that only works if the negative answers
are as findable as the positive ones.
