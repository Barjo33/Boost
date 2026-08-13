# Post-rescue behaviour and the rebound guard

## Hypothesis

A participant who treats a low with carbohydrate produces a sharp rise from a low starting point.
That rise looks to the algorithm exactly like the onset of a meal, because in the narrow sense it is
one. The concern was that the algorithm would respond to a rescue rebound as though it were an
unannounced meal and dose into it, driving the participant back down into the low they had just
treated.

The initial belief was that demoting the algorithm's tier during the post-rescue window would be
enough restraint, since a lower tier commands a smaller multiplier.

## Investigation

Two incidents on one participant in July 2026, in which the loop was disabled by the participant
after being dosed twice into a post-rescue rebound, prompted a forensic reconstruction of what the
engine had actually done. The reconstruction traced the dose through every stage of its composition
to find which stage failed to restrain it.

That was followed by a systematic pricing exercise across the dosing record, testing candidate guards
by the share of the insulin each would remove that sat in the window before a low.

## Methods

Recorded under `backtesting/scripts/2026-07-postrescue-rebound-guard/`, over roughly 103,000 dosing
cycles. Candidate guards were priced by removed-insulin-before-a-low, which values a guard by the
harm it plausibly prevents rather than by how often it fires, and were costed by the genuine meals
they would also restrain.

A leave-one-user-out floor was computed for the winning candidate, so that the headline figure could
not rest on a single participant.

## Results

Tier demotion alone does not restrain, and the reconstruction showed why. The upper tiers are not
capped by the fast-carbohydrate scale, and a delta-weighted sensitivity term inflates the underlying
insulin requirement during a sharp rise. The composition of those two produced a 3.55 U dose at a
glucose of 97 mg/dL immediately after a hypoglycaemic event, which is the incident that disabled the
loop.

A graduated scale applied to the final microbolus within the post-rescue window, rather than to the
tier, prices at 34 per cent of removed insulin sitting before a low, with an interval from 32 to 37.
That is the best-priced guard found anywhere in the programme. The leave-one-user-out floor is 27 per
cent with the strongest participant dropped, so the result does not rest on one person. The cost is
about 9 per cent of genuine meals restrained, at a median of 0.80 U.

An earlier and simpler guard, suppressing the meal-state exemption when a recent low was present,
priced at 27 per cent and shipped in July.

## Discussion

The mechanism finding matters more than the guard. A tier is a label attached to a state, and
restraining a label does not restrain a dose when the dose reaches the pump through terms the label
does not gate. The composed multiplier chain has to be restrained at the point where the final
quantity is determined, which is why the shipped guard scales the final microbolus rather than
anything upstream of it.

The pricing method is worth noting because it recurs. Asking how often a guard fires is close to
useless: a guard that fires constantly and removes insulin that was harmless is worse than one that
fires rarely and removes insulin that was about to cause a low. Pricing by the share of removed
insulin sitting before a low, and reporting the genuine-meal cost alongside, gives a comparison that
survives between candidates. It remains associational, since the counterfactual is unavailable, and
the interval is computed by resampling participants.

There is no velocity escape within the window, which was considered and rejected: a rebound is by
definition fast, so exempting fast rises would exempt the case the guard exists for.
