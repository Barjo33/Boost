# Insulin sensitivity, total daily dose, and the shape of absorption

## Hypothesis

The algorithm needs an estimate of how much a unit of insulin will move glucose, and that quantity
drifts. Two approaches were considered. The first derived sensitivity from recent deviations between
predicted and observed glucose, which is the conventional approach. The second anchored it to the
ratio between recent and longer-run total daily dose, on the reasoning that a person whose total
consumption has risen is temporarily less sensitive.

A separate hypothesis concerned absorption. The single-peak absorption curve assumed by the dose
calculation may be wrong in a way that matters, specifically that real meals produce later secondary
waves the model does not expect.

## Investigation

The two sensitivity approaches were compared directly, and the deviation-based function was removed
in favour of an exponentially weighted ratio of the twenty four hour to seven day total daily dose,
with a time constant of three hours and a warm start seeded from the database.

A proposed equivalence between that ratio and a separately maintained sensitivity overlay was tested
by asking how often the two agreed within a clinically meaningful tolerance.

Absorption shape was examined from the glucose record following meals.

## Results

The total-daily-dose anchored estimate replaced the deviation function and ships. The deviation
function was removed in April 2026.

The proposed equivalence between the ratio and the overlay does not hold. The two agree within five
per cent on only 28 to 58 per cent of cycles depending on the participant, which is not clinical
equivalence, and the overlay was not treated as interchangeable.

Absorption is multi-phase, with secondary waves at roughly eighty minutes. This is handled as a soft
ceiling rather than by modelling the waves explicitly.

The high tail is a high-insulin tail. At recovering-high glucose with substantial insulin on board,
about 19 per cent of cycles sit before a low, against about 7 per cent at low insulin on board. This
single observation is the justification for most of the restraint in the algorithm, and it recurs
throughout this series as the reason that adding insulin into a recovering high is rejected.

## Discussion

The sensitivity work is the least glamorous topic in the programme and among the most consequential,
because every dose is scaled by it. The move from a deviation-based to a dose-anchored estimate
replaced a quantity computed from the algorithm's own prediction errors with one computed from what
the participant actually consumed. The former is partly a measure of the model's own failure and
feeds it back into dosing, which is a loop the latter does not have.

The equivalence test is included here as an example of a negative that prevented a simplification.
Two quantities that track each other loosely are frequently proposed as interchangeable, and the
answer depends on the tolerance the application needs rather than on the correlation. Agreement on
between a quarter and a half of cycles is not enough to substitute one for the other in a dose path.

The recovering-high finding deserves its prominence. It converts a general intuition, that stacking
insulin is dangerous, into a specific measured contrast with a factor of nearly three between the
high-insulin and low-insulin contexts. Several proposals in this programme were rejected by pointing
at it, and the register lists it among the recurring lessons for that reason.

What remains unaddressed is whether sensitivity could be learned per person from continuous glucose
directly, rather than anchored to dose. That was attempted as part of the twin work and found to be
unidentifiable observationally, because the latent meal appearance term absorbs any insulin gain. It
would need a deliberate within-participant probe rather than a better estimator.
