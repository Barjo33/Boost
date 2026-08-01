# Three-way sensor cadence crossover: protocol and pre-registered expectations

Written before the trial starts. The expectations below are recorded now so that a null result
reads as a result rather than as a disappointment, and so that a surprise is recognisable as one.

## 1. What is being tested

Whether running an automated insulin delivery loop on a one-minute glucose sensor changes what
it does, and if so which part of the change comes from seeing glucose sooner and which from
seeing it more finely.

Those are two different things and the trial separates them, which is the reason for having
three arms rather than two.

## 2. The three arms

| Tag | Build | Sensor | What it has |
|---|---|---|---|
| `x5s` | `Boost-V7-shadow` | 5 minutes | the reference |
| `x1s` | `Boost-V7-shadow` | 1 minute | a one-minute clock and a five-minute memory |
| `x1n` | `v7-shadow-1m-test` | 1 minute | one minute throughout |

The middle arm is easy to mistake for a duplicate of the reference and it is not.

On the stock build a one-minute sensor already produces a fresh current value every cycle. The
loop buckets glucose to a five-minute grid, but `clone()` does not copy `referenceTime`, so the
grid re-anchors to the newest reading on each cycle. The current value is therefore one-minute
fresh while everything derived from history sits on five-minute spacing. This is measurable in
the existing field data: the reported glucose changes every 1.1 minutes for the cohort's
one-minute user against 5.0 minutes for everyone else.

So `x1s` runs five times as often on a fresh value, with five-minute-resolved trend signals.
`x1n` additionally computes its deltas and its acceleration from every reading the sensor
produced rather than one in five, and smooths the same series.

The comparison `x1s` against `x5s` therefore isolates the effect of cycling more often. The
comparison `x1n` against `x1s` isolates the effect of the finer history, with the cycle rate
held constant. Neither is available from a two-arm design.

## 3. Design

Three fifteen-day periods. In each period one arm holds the pump and the other two run on
virtual pumps, computing what they would have delivered without delivering it. The pump moves to
a different arm at each changeover. The first period has the pump on `x5s`.

Each arm uploads to its own Nightscout instance, so all three are recorded per cycle throughout.

The primary analysis is the contrast between arms within a period, not one period against the
next. All three observe the same person at the same moment, so day-to-day variation is common
and cancels in the difference. That pairing is what makes fifteen days sufficient. An earlier
power check found that unpaired fifteen-day windows resolve a difference in area under the curve
of only about 0.13, whereas the paired contrast in the closed-loop replay resolved differences
an order of magnitude smaller.

The period-to-period structure serves a narrower purpose. It checks that an arm holding the pump
behaves as its own virtual-pump record predicted it would, which is the assumption every
shadow-mode result depends on and which has never been tested directly.

## 4. What the existing evidence says

Three pieces of prior work bear on the expectations, and they point in a consistent direction.

Measured on real records from both cadences on the same person, the two feeds carry the same
information about glucose. The ratio of their variograms is constant at 1.602 across every lag
both can see, from 5 to 120 minutes, and the log-log slopes agree to two decimal places. Below
five minutes, where only the faster sensor reaches, the same power law continues with no break.
Neither prediction of glucose nor prediction of hypoglycaemia improved with the faster feed once
base rates were accounted for.

What the faster feed does deliver is less waiting. Measured on real threshold crossings, the
five-minute sensor reports them 2.19 minutes later on average, against an arithmetic expectation
of 2.00 minutes from the sample spacing alone.

In closed-loop replay, with the one-minute arm free to dose every cycle and the five-minute arm
at the shipped three-minute microbolus interval, insulin came out at 1.519 against 1.407 units
per day. The one-minute arm dosed more often and in smaller amounts, 15.0 microboluses a day at
0.10 units against 9.0 at 0.16. The per-episode difference was 0.028 units with an interval of
minus 0.067 to plus 0.120, which spans zero.

## 5. Pre-registered expectations

Each is stated with what would falsify it.

### H1. Insulin delivery rises modestly with cycle rate

`x1s` delivers more than `x5s`, in the
region of 5 to 10 per cent, with more microboluses of smaller size. Falsified by a difference
above 20 per cent, or by no difference at all.

### H2. The finer history reduces delivery rather than increasing it

`x1n` delivers the same as
or less than `x1s`. The reasoning is that the delta windows are fixed in elapsed time, so on the
native series each window averages five times as many readings, which reduces the noise in delta
and in acceleration. Fewer spurious threshold crossings should mean fewer marginal triggers.
Falsified if `x1n` delivers more than `x1s`.

This is the least certain of the expectations and it is the one the branch exists to test. If it
fails, the finer history is adding sensitivity to noise rather than removing it, and that is a
reason not to proceed.

### H3. Glucose outcomes do not differ detectably between any pair of arms

Time in range, time
below 70 and time above 180 will not separate on fifteen days in one person. This follows from
the variogram result and is expected rather than hoped for. It would be falsified by a
separation, which given the power available would more likely indicate a fault than a benefit and
should be investigated as such.

### H4. Both one-minute arms detect crossings about two minutes sooner than `x5s`

They should not differ from each other. Both have a fresh current value each cycle, so the latency
gain is the same for both. Falsified by a difference between `x1s` and `x1n` on detection timing.

### H5. Meal state occupancy on `x1n` sits closer to `x5s` than `x1s` does

In replay, stock
V7-shadow on one-minute data reached CONFIRMED on 0.49 per cent of cycles against 1.31 per cent
at five minutes, and RECOVERING on 1.82 against 3.81. The one-minute branch converts the
remaining count-based windows to wall clock, so the gap should narrow. Falsified if `x1n` matches
`x1s` rather than `x5s`, which would mean the conversion missed something.

### H6. The smoother's output differs materially between `x1s` and `x1n`

On the stock build the
filter runs after bucketing, so it smooths an interpolated reconstruction in which four of every
five readings have been replaced. On the branch it sees every reading. If the two smoothed series
are indistinguishable, the routing change has not taken effect and that is a setup fault rather
than a finding.

## 6. Safety monitoring

At a one-minute microbolus interval the interval limiter is effectively disabled, which leaves
`maxIOB` and `maxSMBBasalMinutes` as the only bounds on delivery. Both should be logged from the
first day, with how often each binds, so that a divergence is visible while the pump is still on
the reference arm.

Stopping conditions, to be judged on the virtual-pump arms before an arm ever takes the pump:

- delivered insulin on a virtual arm exceeding its predicted figure by more than half again
- `maxIOB` binding on more than a small minority of cycles on any arm
- the meal state machine reaching COMMITTED at a rate materially above the reference
- any arm delivering a single microbolus above the configured cap

## 7. Endpoints

Primary, measurable within a period:

- insulin delivered per day, and microbolus count and mean size
- elapsed time from a rise beginning to the first dose
- meal state occupancy, particularly CONFIRMED and COMMITTED
- what each arm saw, logged per cycle:
  - readings consumed, and the cadence detected from them
  - delta and short average delta
  - the acceleration derived from those

Secondary, and underpowered on one person for fifteen days. To be reported without being led on:

- time in range, time below 70, time above 180

Diagnostic:

- agreement between an arm's virtual-pump record and its behaviour when it later holds the pump
- frequency with which each delivery bound binds

## 8. Limitations

One person. The three arms use different sensors as well as different rates, so a difference
between a one-minute and a five-minute arm is attributable to the pairing rather than to cadence
alone. Sensor site differs between arms. Period effects and carryover both apply.

The virtual-pump arms compute what they would have delivered given the glucose they observed,
which is not the glucose they would have observed had they been delivering. That is the standard
shadow-mode caveat and it is why the period-to-period comparison is retained despite being the
weaker design.

Nothing here establishes that any arm is better for glycaemic outcomes. The trial is powered to
detect differences in what the loop does, not in what happens to the person.

## 9. What would make this worth having done

A null on H3 with a confirmed H1 and H2 would say that one-minute sensing changes the loop's
behaviour in a small and predictable way without changing the outcome, and that the native path
is at least not worse than the stock path. That is enough to decide whether to carry the branch
further.

A failure of H2 or H6 would say the native path is not doing what it was built to do, and would
stop the work before it reaches anyone else. That is the more valuable result of the two, and it
is the reason the trial runs on virtual pumps first.
