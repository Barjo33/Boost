# In-silico trial of the confirm dose, one participant, thirty days (2026-08-13)

*Reproduce: `insilico_confirm.py`. Self, 29.8 days to 2026-08-13 21:15, 8,518 sensor readings,
84 confirms carrying 165.9 U. Sensitivity taken from the record at each confirm: median
111 mg/dL/U, range 41 to 216. Multiplier drawn uniformly on [0.5, 1.0] per confirm, 400
replicates.*

## What is computed

Each entry into CONFIRMED has its delivered dose scaled by a randomly drawn multiplier, and the
glucose series is recomputed with every draw in place, so confirms that overlap in time accumulate
rather than being scored in isolation.

The counterfactual is one-armed. A smaller dose does not change the meal, so the carbohydrate side
is held exactly as recorded and only the insulin side recomputed: insulin not delivered never acts,
so glucose is higher by the sensitivity at that confirm times the removed dose times the fraction
of that bolus which would have acted by then.

The bound applies only while the removed insulin is still acting. Carried further it accumulates
without limit, since the modelled lift never washes out, and superposing eighty-four confirms
across thirty days produces a mean glucose in the thousands. Everything below is therefore computed
inside a five-hour window after each confirm, which covers 42 per cent of the record, and the
observed baseline is computed on the same masked set so the comparison is like for like.

## The observed period

| | whole record | inside the windows |
|---|---|---|
| TBR < 70 | 5.63% | 6.54% |
| TBR < 54 | | 0.74% |
| TIR | 86.6% | 77.6% |
| TAR | | 15.8% |
| mean | 118 | 131 |
| low episodes | | 35 |

The windows are worse than the record as a whole on every measure, which is what would be expected
if the confirm is implicated in the excursions at both ends.

## The randomised trial

| measure | observed | median across replicates | 2.5% | 97.5% | change |
|---|---|---|---|---|---|
| TBR < 70 | 6.54 | 0.63 | 0.25 | 1.32 | −5.91 |
| TBR < 54 | 0.74 | 0.05 | 0.00 | 0.19 | −0.69 |
| TIR | 77.64 | 55.90 | 50.71 | 60.61 | −21.74 |
| TAR | 15.82 | 43.40 | 38.71 | 48.85 | +27.57 |
| mean | 131 | 179 | 172 | 184 | +47 |
| episodes | 35 | 3 | 1 | 8 | −32 |

A median of 41.6 U withheld of the 165.9 U committed, or 25 per cent.

The replicate interval is the spread of the random assignment, which is what a real trial would
draw from once. It is not the spread of the participant or of the estimate.

## The deterministic dose response

| multiplier | U withheld | TBR<70 | TBR<54 | TIR | TAR | mean | episodes |
|---|---|---|---|---|---|---|---|
| 1.00 | 0.0 | 6.54 | 0.74 | 77.6 | 15.8 | 131 | 35 |
| 0.90 | 16.6 | 1.13 | 0.14 | 76.5 | 22.3 | 150 | 6 |
| 0.80 | 33.2 | 0.36 | 0.03 | 65.0 | 34.6 | 169 | 3 |
| 0.70 | 49.8 | 0.16 | 0.00 | 49.9 | 49.9 | 188 | 0 |
| 0.60 | 66.4 | 0.08 | 0.00 | 36.4 | 63.5 | 206 | 0 |
| 0.50 | 83.0 | 0.03 | 0.00 | 26.0 | 74.0 | 225 | 0 |

Almost all of the hypoglycaemia benefit is bought by the first ten per cent of the reduction.
Going from 1.00 to 0.90 takes time below 70 from 6.54 to 1.13 and episodes from 35 to 6, at
16.6 U. Every further step buys little below range and costs heavily above it.

## Which side of this to believe

The loop is not re-run. Under a smaller confirm the algorithm would have seen higher glucose and
dosed into it, and none of that is modelled, so the longer the window the more the estimate assumes
the algorithm sat still. Splitting the window shows how that bites.

| window | covers | change in TBR<70 | change in TBR<54 | change in TAR | TAR per point of TBR |
|---|---|---|---|---|---|
| 60 min | 12% | −3.35 | −1.03 | +8.24 | 2.5 |
| 120 min | 23% | −5.79 | −0.88 | +10.75 | 1.9 |
| 180 min | 31% | −6.33 | −0.91 | +17.76 | 2.8 |
| 300 min | 42% | −6.37 | −0.74 | +34.07 | 5.3 |

The hypoglycaemia benefit is essentially complete by three hours, at −6.33 of an eventual −6.37.
The hyperglycaemia cost doubles between three hours and five, from +17.8 to +34.1, entirely because
the model has the loop doing nothing for two further hours. The rising ratio is the unmodelled arm
made visible.

The reading that follows is that the benefit side is reasonably estimated and the cost side is not.
Time below range falls by roughly six percentage points at a 0.70 multiplier, most of it inside two
hours of the confirm. The true cost above range is somewhere below the +17.8 measured at three
hours, and well below the +34.1 at five.

Both sides remain ceilings for a second reason: the recorded trajectory already contains whatever
counter-regulation each low provoked, and a smaller dose would have provoked less.

## The cost of individual confirms

Each confirm halved with every other left alone, sorted by hypoglycaemia removed within its own
window.

| when | dose | ISF | BG | IOB | U withheld | ΔTBR<70 | ΔTBR<54 | ΔTAR |
|---|---|---|---|---|---|---|---|---|
| Thu 16 22:17 | 2.05 | 141 | 145 | −0.14 | 1.03 | −46.7 | 0.0 | +51.7 |
| Wed 22 13:40 | 1.56 | 104 | 132 | 0.61 | 0.78 | −31.7 | −8.3 | +18.3 |
| Mon 03 12:26 | 1.65 | 131 | 127 | 0.34 | 0.83 | −25.0 | −5.0 | +41.7 |
| Mon 10 17:46 | 3.75 | 68 | 137 | 0.96 | 1.88 | −21.1 | 0.0 | +71.9 |
| Tue 21 14:05 | 2.20 | 98 | 144 | 0.78 | 1.10 | −20.0 | −5.0 | +46.7 |
| Thu 13 17:10 | 4.50 | 48 | 183 | 1.49 | 2.25 | −18.4 | −8.2 | +44.9 |
| Sun 02 15:51 | 2.25 | 81 | 266 | 2.50 | 1.12 | −18.3 | −5.0 | +20.0 |

Fifty of the eighty-four confirms remove some time below range when halved; thirty-four change
nothing below range at all, so the harm is not spread evenly and a little over a third of confirms
are not implicated.

The event that prompted this work appears sixth. The pattern in the top rows is a confirm at
ordinary glucose, between 122 and 148, with little insulin already present, which is the small-meal
signature identified separately.

## Conclusion

For this participant over these thirty days, reducing the committed dose is a large intervention
with a favourable shape at small reductions and a poor one beyond them. A ten per cent reduction
removes most of the confirm-attributable hypoglycaemia, taking episodes from 35 to 6, at a cost
above range that this method cannot price honestly but which is smaller than any figure in the
table above.

That argues for testing a shallower reduction than the registered 0.70. The registered multiplier
sits well past the point where the benefit has saturated, and buys almost nothing below range that
0.90 has not already bought while costing several times as much above it.

Confidence: PROVISIONAL for the benefit side and SPECULATIVE for the cost side. One participant,
84 confirms, a one-armed bound with the loop's response unmodelled, and no counterfactual for the
carbohydrate arm.
