# Boost research papers

What was investigated during the development of the V6 and V7 generations of the Boost algorithm,
what the investigations found, and what was built or abandoned as a result. Each document takes one
topic through abstract, introduction, methods, results and discussion, and names the analysis folder
its figures come from.

Several documents report nulls, and they are written at the same length as the positive results,
because the point of the record is to stop questions being asked twice and that only works if the
negative answers are as findable as the positive ones.

Rendered PDFs of these papers are published in the separate research-papers repository, alongside
the simulator series. This folder holds the sources.


### The papers

**[00 The programme](00_the_programme.md)**. The frame every other document works under. Sets out the identification constraint and how it divides questions into the answerable and the merely priceable, the three conditions imposed on every effect size, and why the shipping controller is deterministic while everything inferential stays offline.

**[01 Moving insulin earlier in a meal, and the cost of adding to it](01_dose_timing_and_sizing.md)**. Movement is harm-neutral and addition costs about fifteen percentage points of lows, which converts an argument about aggression into a testable claim about which insulin is in question. Also the commitment gate blocking 26 to 29 per cent of commitments that preceded a high, and a retired detector leading the current commitment by fifteen minutes at 98 per cent recall and 15 per cent precision.

**[02 Confirming a meal doubles the chance of a subsequent low](02_the_confirm_state.md)**. Three attempts to identify which commitments end in hypoglycaemia, all returning chance, before anyone asked whether the post-commitment low rate was elevated at all. Against controls matched within participant on glucose, insulin on board and hour of day, committing roughly doubles the chance of a low and quadruples the chance of a severe one.

**[03 Attributing every excursion to the mechanism that started it](03_where_the_loss_comes_from.md)**. Attribution of every episode outside range to the mechanism that started it, with a foreseeability layer scoring each cause forty five minutes before onset. Time above range is mostly brake suppression, late commitment and cap clipping; time below range is mostly activity and basal drift. Shows what a forward-looking antecedent does to a causal ranking.

**[04 A brake credited with a third of time above range, priced by outcome](04_restraint.md)**. A brake credited with a third of time above range, priced by outcome. Under a strict definition it owns 675 minutes over six weeks, of which 3 per cent was safely recoverable and 13 per cent preceded a low that did not occur. The frequently quoted 90 per cent correct separates into an outcome-proven part and a much larger correct-by-assumption part, on a sample where one participant contributes half.

**[05 A state estimator that is a good sensor and cannot be a controller](05_prediction_and_the_twin.md)**. A per-person state estimator that is a good forecaster, a better hypoglycaemia detector than the incumbent at a third to a half the false alarms, worse than a naive trend at rises, and not a controller. Insulin sensitivity can vary eightfold inside the filter without changing its accuracy, so the forecast cannot be trusted off the policy that generated it.

**[06 Restraining the rebound that follows a treated hypoglycaemia](06_post_rescue.md)**. Why demoting an algorithm's response tier fails to restrain a rebound after a treated low, and the graduated scale on the final microbolus that does. The best-priced guard in the programme, at 34 per cent of removed insulin sitting before a low with a leave-one-participant-out floor of 27.

**[07 The post-meal exercise crash is a carbohydrate failure, not an insulin excess](07_exercise_and_activity.md)**. Steps nearly triple the forward hypoglycaemia rate and the relationship does not transfer between people, which settles per-participant thresholds over a global model. The post-meal crash is not dose-driven: the participants who crash carry less insulin, not more, and the mechanism is a carbohydrate counterweight failure that puts the loop on the wrong side of the problem.

**[08 Where a more aggressive algorithm earns its advantage, and the gate that switches it off](08_overnight_and_sleep.md)**. The algorithm's advantage is overnight and anti-phase with its predecessor, which leads after breakfast. The night gate suppresses 47 per cent of amplifications, all of them nocturnal and unannounced. Learned bedtime converges to a constant and is therefore not learning.

**[09 Anchoring insulin sensitivity to consumption rather than to prediction error](09_sensitivity_and_absorption.md)**. Replacing a sensitivity estimate computed from the algorithm's own residuals with one anchored to consumption, and why a proposed equivalence between two sensitivity measures fails at clinical tolerance. Contains the observation underpinning most of the restraint in the controller: at recovering highs with substantial insulin on board, roughly 19 per cent of cycles sit before a low against 7 per cent at low insulin.

**[10 Making a weak predictor safe by making the action retractable](10_anticipation.md)**. Acting before the event rather than after. Exercise is anticipable per participant and not across them; meals are the reverse. A detector at 0.63 precision becomes usable when the action is retractable, which moves safety from accuracy into design and lowers the accuracy bar to something the data can meet.

**[11 Four online controllers that converge on the configuration they were built to beat](11_per_user_configuration.md)**. Four attempts to tune dosing parameters online against outcomes, all of which failed, in both directions and for both caps and sliders, with revert rates as the signature. The static per-participant derivation they were trying to beat is what ships, and these four experiments are the empirical argument for keeping learning out of the dose path.

**[12 A one-minute sensor buys latency, not bandwidth](12_the_cgm_signal.md)**. What a one-minute sensor carries. The two cadence eras differ by a single flat scale factor, prediction gains nothing, and rate of change is estimated slightly worse at one minute. A faster feed buys about two minutes of latency rather than bandwidth. Also the ingestion path that discards four readings in five before the algorithm sees them.

**[13 A generation change that is outcome-neutral, and why that is consistent with the levers being real](13_cohort_outcomes.md)**. Whether the migration to the newer generation improved anything. Three approaches converge on outcome-neutral, including a within-participant comparison whose interval spans zero on every measure, and explains why that is consistent with the individual levers being real given the sample size.

**[14 Instruments that make the answerable questions cheaper without making the unanswerable ones tractable](14_methods_and_tooling.md)**. The attempts to relax the identification constraint and their limits. Simulator fidelity graded across six levels, failing in one direction by being uniformly too easy; the survival conditioning that flatters any evaluation which drops its own failures; and the harness that drives the real engine from analysis code at 0.991 fidelity.

**[15 The two learned components that dose, audited against the people running them](15_learned_components_in_the_dose_path.md)**. The only statistical objects allowed to change what the pump does, audited against the people running them. The meal model replicates its training accuracy on a different cohort a year later; the hypoglycaemia model adds a real increment over the glucose reading from sixty minutes outward, where its predecessor added none. The consumption thresholds were placed against a distribution that has since moved.

**[16 The signal available to a glucose forecaster is nearly exhausted](16_forecasting_and_the_information_ceiling.md)**. Whether a better forecast is available and whether it would help. The trajectory, its rate of change and its curvature are essentially all the signal there is, and the physiological insulin decomposition and the sensitivity regime both make the forecast worse. Contains the result that reoriented the programme, which is that the best available forecaster implies insulin does almost nothing.

**[17 Four searches for the decision that goes wrong, all returning chance](17_what_could_not_be_learned.md)**. Four searches for the decision that goes wrong, all returning chance, and the rule against learning on the dose path that followed. Includes a fourteen-point improvement that was entirely participant leakage, and the two transfer tests that decide where personalisation belongs.

### Citations and provenance, Boost

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
