# How sensor cadence affects oref dosing

Drives the shipped `DetermineBasalSMB.determine_basal()` on a desktop JVM over the same glucose
record at one minute and at five.

## Building it

oref is less self-contained than the Boost engine but still reachable. It needs the core
`aps` interface types, which are almost pure, plus `kotlinx-serialization` and `joda-time` from
the Gradle cache and the serialization compiler plugin. Two collaborators are stubbed because
they take no part in the calculation: `ProfileUtil` formats numbers for the console log and
`FabricPrivacy` records one diagnostic line. `APSResult` is reduced to the algorithm enum,
which is all oref references, avoiding its Android and JSON dependencies.

## Two mistakes worth recording

`iobWithZeroTemp` must be populated or oref throws. With no temp running at the moment of the
call it coincides with the main figure.

**oref reads the microbolus interval gate from `iob_data.lastBolusTime`, not from
`meal_data.lastBolusTime`.** Setting the wrong one leaves `SMBInterval` permanently unbinding,
which inflated the microbolus count from 253 to 710 per day and made the cadence ratio look
like 5.1 rather than 1.8.

Insulin activity must also be supplied. Passing zero makes oref's BGI term zero, so it never
sees existing insulin working and projects a mean eventualBG of 65 while still dosing. Activity
is derived here as the smoothed negative slope of the recorded IOB, floored at zero.

## Result

| | 1-minute | 5-minute | Ratio |
|---|---|---|---|
| Temp basal delivered, U/day | 19.68 | 19.28 | 1.021 |
| Mean temp basal rate, U/h | 0.827 | 0.811 | 1.020 |
| Microboluses per day | 253.4 | 138.3 | 1.832 |
| Microbolus insulin, U/day | 41.96 | 23.34 | 1.798 |

At the 2,680 instants both arms evaluated, the requested temp basal rate is identical in 80.2%
of cycles and the microbolus in 66.4%.

The dose splits into two parts with opposite behaviour. The temp basal is a rate in units per
hour, so asking for it five times as often does not deliver five times as much, and it comes
out cadence-invariant. The microbolus is a per-cycle amount and does scale, limited only by
`SMBInterval`, which ships at three minutes and was chosen when every loop ran on five.

## What this does not establish

The replay is open loop: IOB comes from the record, so a microbolus never raises IOB and never
engages the maxIOB brake. Absolute totals are therefore inflated and should not be read as
dosing. The equivalent closed-loop run on the Boost engine showed feedback absorbs most of the
rate effect, taking 51% down to 8%, and the same would be expected here.

What the open-loop replay does establish is the mechanism and the split, which is the part that
generalises.
