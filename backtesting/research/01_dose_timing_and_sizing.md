# Moving insulin earlier without adding to it

## Hypothesis

The V6 generation was built on the belief that its advantage over the preceding oref-derived
behaviour lay in acting sooner at a meal rather than acting harder. If that is right, then insulin
moved earlier in a meal should be roughly harm-neutral, whereas insulin added to a meal should carry
a cost in subsequent lows. The distinction matters because almost every proposed lever in the
programme takes one of the two forms, and the two have been repeatedly conflated in discussion.

The associated hypothesis is that the gate which holds the algorithm in its observing state before it
commits is too conservative, and that some of what it blocks is insulin that should have been given.

## Investigation

Two lines of enquiry, run against the dosing record rather than against a simulation.

The first classified every dosing difference between the shipped algorithm and its predecessor as
either a movement of insulin within a meal or an addition to it, and priced the two separately
against what glucose subsequently did. The second examined the confirm gate directly: how often it
blocks, what happens after the blocked cycles, and whether the blocked population differs from the
population that passes.

A third line examined the acceleration gate that the V1 generation used and the V6 generation
retired, to establish whether the earlier detector was seeing something the later one had given away.

## Methods

Dosing cycles were drawn from the local analysis database, which holds one row per decision per
participant with the algorithm's own state and the glucose that followed. The early-dosing audit
covered the record to 3 July 2026. The confirm-gate work used the same source over the same period.
The acceleration comparison covered 14,430 gate fires and is recorded under
`backtesting/scripts/2026-07-v1-acceleration/`.

Outcomes were taken forward from each cycle rather than aggregated by day, since the question is
about individual dosing decisions and a daily summary would average away the events of interest.
Where a lever was priced, the measure was the share of the insulin it would have removed or added
that sat in the window before a low, which prices a lever by the harm it plausibly prevents or causes
rather than by how often it fires.

## Results

Moving insulin earlier within a meal was harm-neutral. Adding insulin that was not previously given
carried roughly fifteen percentage points of additional lows. The two forms are therefore not
interchangeable, and a lever that moves is safe to consider on evidence that would not support a
lever that adds.

The confirm gate blocks more than it should, but not by much and not everywhere. Between twenty six
and twenty nine per cent of blocked confirms preceded a glucose above 180 mg/dL, which is the
signature of a block that cost something. Relaxing the age requirement by one cycle when the score is
already sufficient was harm-neutral and shifted about 1.5 U per day.

Raising the observing-state dose was defensible only inside a narrow cell, specifically where glucose
was at or above 140 mg/dL and insulin on board was below five per cent of total daily dose. Outside
that cell a blanket raise was contraindicated, and it was rejected.

The retired acceleration gate turned out to lead the current confirm by a median of fifteen minutes
at ninety eight per cent recall, with precision of fifteen per cent. It is a genuine early detector
with a high false positive rate, which is a different thing from a detector that does not work.

## Discussion

The move-versus-add distinction has done more work in this programme than any single lever, because
it converts an argument about aggression into a testable claim about which insulin is in question.
It also explains why several later proposals were rejected quickly: any lever that adds insulin into
a period where insulin is already present inherits the fifteen point cost, and the burden on it is
correspondingly higher.

The fifteen per cent precision on the acceleration gate is the reason it was retired and also the
reason it was reconsidered. A detector that fires wrongly six times in seven is unusable if each fire
commits insulin that cannot be withdrawn. The same detector is usable if each fire commits a small
retractable amount that is netted off the later commitment, which is the design recorded under
`2026-07-v1-acceleration/REINTEGRATION_SPEC.md` and shipped in part as the primer.

The confirm-gate result is the weakest of the three and is marked as a fix candidate rather than a
fix. Twenty six to twenty nine per cent of blocked confirms preceding a high is a statement about
what followed a block, not about what a different decision would have produced, and the identification
constraint applies in full. It justifies looking, not acting.

What none of this addressed is the size of the dose once the algorithm commits, which is the subject
of the next document and where the residual harm turns out to concentrate.
