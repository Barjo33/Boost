# Overnight behaviour, sleep detection, and where the advantage sits

## Hypothesis

Comparing the Boost generation against the oref behaviour it replaced showed an aggregate advantage,
and the question was where in the day it came from. The hypothesis was that it would be distributed,
since the algorithm does not distinguish night from day except through a gate.

A second hypothesis concerned that gate. Boost amplifies dosing relative to its predecessor, and the
night-mode gate suppresses the amplification while the participant is asleep. The question was
whether this was doing useful work or merely reducing the algorithm to its predecessor overnight.

## Investigation

The advantage was decomposed by time of day across the migration cohort. The gate was investigated by
counting the amplifications it suppresses and characterising when they occur. Sleep detection itself
was investigated separately, since the gate depends on knowing when the participant is asleep, and
two candidate detectors were compared: a learned bedtime prior and a rule-based detector driven by
heart rate and movement.

## Results

The advantage is overnight and is anti-phase with the predecessor. Boost runs about 13.3 percentage
points ahead overnight. Between roughly nine in the morning and one in the afternoon the predecessor
is ahead by four to seven points. The algorithm is therefore not uniformly better; it is better at
night and worse after breakfast, which localises the daytime problem to meal sizing and timing.

The night gate suppresses about 47 per cent of Boost's amplifications over its predecessor. All of
the suppressed amplifications occur at night and all are unannounced, meaning no meal was entered.
The gate is doing substantial work and was shipped.

Learned bedtime does not beat a fixed clock. The standard deviation of sleep onset across the cohort
is about 92 minutes, and the learned prior converges to something indistinguishable from a fixed
time. It works for one very regular sleeper and not for anyone else.

The historical failure mode that motivated the architecture is recorded from two incidents in May
2026, in which the previous generation fired a cascade of microboluses on the rebound out of a hard
overnight streak, reaching nadirs of 51 and 48 mg/dL.

## Discussion

The overnight result is the strongest aggregate claim in the programme and also the least
identified. The regime split is suggestive, and a pre-registered within-participant A/B is the test
that would settle it. That test has not been run, and until it is, the overnight advantage is
associational: participants who chose this algorithm may differ overnight for reasons unrelated to
it, and the basal settings they run differ too.

The 13.3 point figure should also be read against the cohort correction recorded elsewhere in this
series. A separate cohort comparison reporting an advantage of thirteen points fell to 1.2 once
selection and basal differences were adjusted for, with a permutation p of about 0.27. The two
figures are not the same analysis, but the correction is a caution about how much of an
unadjusted between-generation difference survives adjustment.

The sleep detection result is a small negative with a useful shape. A learned parameter that
converges to a constant is not learning; it is an expensive way of writing down a constant, and the
honest response is to write down the constant. The runtime detector retained is rule-based, using a
robust order statistic for the resting heart rate baseline and a circular mean for onset and wake
times, both of which are chosen because the underlying quantities are periodic or heavy-tailed rather
than because they performed best in a search.

One operational note is recorded because it was mistaken for a fault. The daytime heart rate baseline
populates only after about seven nights, showing a default until then, which is expected behaviour.
