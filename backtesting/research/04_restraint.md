# Restraint: the brake, the caps and the composed floor

## Hypothesis

The attribution study made the composed brake the single largest proximate mechanism behind time
above range, at 34 per cent of it, and showed those episodes were already visible to a forecaster
forty five minutes ahead. The obvious reading was that the brake was too aggressive and that
loosening it was the largest available improvement.

That reading contains an inference the attribution cannot support. The brake suppressing a dose
during a rise is not the same as the brake being wrong. The competing hypothesis was that most
suppression is correct restraint at high insulin on board, in which case the apparent opportunity is
mostly insulin that should not have been given, and loosening the brake would convert time above
range into time below it.

A separate question concerned the mechanism rather than the policy. The brake is a product of several
multipliers, and a product of fractions falls faster than any of its terms, so there was reason to
suspect it could reach zero in circumstances where no single term was extreme.

## Investigation

The mechanism question was answered by reconstructing the multiplier chain cycle by cycle from
telemetry during a sustained high, to see what the composed value actually was and how it got there.

The policy question was answered by outcome rather than by mechanism. The set was narrowed to cycles
where the underlying algorithm genuinely wanted insulin and the composed budget crushed it while
glucose was high, and each such cycle was classified by what happened next and in what insulin
context. Correct restraint and wrong restraint have different signatures: the first is followed by no
low and occurs at high insulin on board, the second leaves glucose high with little insulin present
and no low following.

## Methods

The forensic reconstruction covered seventeen consecutive cycles and is recorded under
`backtesting/scripts/2026-07-v6-dosing-forensics/`.

The audit used V6 telemetry across eight participants over roughly six weeks, recorded under
`2026-07-residency/BRAKE_AUDIT_REPORT.md`. Inclusion required the underlying insulin requirement
above 0.05 U, the composed budget below 0.10, and glucose above 170 mg/dL. That is deliberately
strict: it excludes highs where the algorithm was content because insulin on board already covered
them, and isolates the set where a wanted dose was actually blocked.

The suppression signal had to be taken from the composed budget rather than from the state
multiplier, since the latter never approaches zero, and the floor's own injection could not be priced
at all because the field recording it is absent from historical rows.

## Results

The mechanism concern was justified. At a glucose around 270 mg/dL the chain multiplied to
0.4 × 0.40 × 0.85 × 0.30, which is 4.1 per cent of budget and rounds to no dose at all for thirty
minutes. No individual term is unreasonable and the product is. This is what the composed floor was
built to prevent.

The policy concern was not. The strict set contained 135 cycles, some 675 minutes over six weeks,
which is far smaller than the 34 per cent of high-time the attribution suggested. Of those, 13 per
cent were followed by a low within three hours, so the brake demonstrably prevented one. Another 76
per cent occurred at high insulin on board with no low following, which is treated as correct
restraint. Seven per cent resolved on their own at low insulin on board. Only 3 per cent, about
twenty minutes in six weeks, were cycles that stayed high at low insulin on board without a low
following, which is the only category the floor could safely recover, and even those carried a 12 per
cent forward-low rate.

## Discussion

The direction holds and the number does not. The brake should not be loosened: only 3 per cent of a
strictly defined suppression set was safely recoverable and 13 per cent actively prevented a low. But
the frequently quoted figure of 90 per cent correct splits into two very different parts, and saying
so matters. Thirteen per cent is outcome-proven, in that a low actually followed and did not happen.
Seventy six per cent is correct by assumption, defined as high insulin on board with no subsequent
low, and grounded in the separate finding that adding insulin in that context prices about 19 per
cent into lows, but not demonstrated cycle by cycle. The honest statement is thirteen proven plus
seventy six presumed, and the composite should not be quoted as though it were measured.

The sample is also weaker than the cohort framing implies. One participant contributes 51 per cent of
the 135 cycles and four others contribute one to three cycles each, which is noise. The result is
self-dominated and pooled, and cannot be resolved per participant for most of the cohort. The
category credited with saving a low also credits the brake for any low within three hours, a window
wide enough to catch lows caused by activity or by rescue treatment and unrelated to the suppressed
dose.

Taken with the attribution study, the practical conclusion is that the composed floor is a bounded
defect fix rather than a lever. It addresses a real failure, demonstrated in the forensic
reconstruction, in which a product of individually reasonable multipliers reaches zero during a rise.
Its upside is small and now quantified at about 3 per cent of brake suppression, and it was
characterised that way rather than promoted as the largest opportunity, which is what the raw
attribution number would have suggested.

The wider lesson is the one the register records first among its recurring lessons. Several
large-looking effects in this programme shrank when a matched or strictly defined comparison was
constructed, and the brake is the clearest case: a third of high-time became 675 minutes once the
question was posed as whether a wanted dose had been wrongly blocked.
