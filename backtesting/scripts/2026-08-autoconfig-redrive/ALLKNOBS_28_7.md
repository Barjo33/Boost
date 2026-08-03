# Every knob under 28-day window / 7-day cadence

8 users, 28d window, 7d step. Supersedes the drift-to-noise screen, which was the wrong test for quantised knobs.


## Does each knob track its own driver?

| knob | driver | changes_per_6mo | expected_sign | driver_corr | corr_ci | moves_agreeing_with_driver | agree_ci |
| --- | --- | --- | --- | --- | --- | --- | --- |
| aggression | tbr70 | 3.034 | -1 | -0.606 | [-0.74, -0.48] | 1 | [1.00, 1.00] |
| hypoCaution | tbr70 | 5.48 | 1 | 0.731 | [0.60, 0.86] | 0.986 | [0.96, 1.00] |
| confirmedCap | manual_p90 | 10.321 | 1 | 0.974 | [0.93, 1.00] | 1 | [1.00, 1.00] |
| committedCap | tdd40 | 15.873 | 1 | 0.927 | [0.81, 0.99] | 0.98 | [0.95, 1.00] |
| cumulative60 | committedCap | 14.055 | 1 | 0.311 | [0.06, 0.57] | 0.783 | [0.68, 0.89] |
| primerCap | committedCap | 12.191 | 1 | 0.554 | [0.22, 0.81] | 0.935 | [0.85, 1.00] |

`driver_corr` = correlation between the knob's change and its driver's change across consecutive windows. `moves_agreeing_with_driver` = of the moves that happen, the share going the same direction as the driver — 0.5 is a coin flip, 1.0 is perfect tracking.


## Direction — is re-derivation tightening or loosening?

| knob | tightenings | loosenings | tighten_share |
| --- | --- | --- | --- |
| aggression | 9 | 10 | 0.474 |
| hypoCaution | 17 | 18 | 0.486 |
| confirmedCap | 34 | 32 | 0.515 |
| committedCap | 48 | 53 | 0.475 |
| cumulative60 | 48 | 42 | 0.533 |
| primerCap | 40 | 40 | 0.5 |

Tightenings apply unconditionally under the existing design; loosenings of a dose cap go through the TBR/<54 raise-guard. A knob that mostly tightens is mostly running on the safe side of that asymmetry.


## Deadband interaction — and where a deadband would freeze a knob

| knob | deadband | quantum | deadband_exceeds_quantum | median_move | changes_per_6mo | past_deadband_per_6mo | share_surviving |
| --- | --- | --- | --- | --- | --- | --- | --- |
| aggression | 0.025 | 0.07 | False | 0.047 | 3.034 | 3.034 | 1 |
| hypoCaution | 0.16 | 0.1 | True | 0.131 | 5.48 | 3.414 | 0.629 |
| confirmedCap | 0.47 | 0.01 | True | 0.464 | 10.321 | 3.326 | 0.303 |
| committedCap | 0.067 | 0.01 | True | 0.034 | 15.873 | 2.606 | 0.139 |
| cumulative60 | 0.54 | 0.1 | True | 0.219 | 14.055 | 2.749 | 0.178 |
| primerCap | 0.056 | 0.01 | True | 0.02 | 12.191 | 2.081 | 0.15 |

**`deadband_exceeds_quantum = True` is a defect**: the deadband is wider than the smallest move the formula can make, so single-step changes can never be written and the knob is silently frozen at anything below a double step.


## Per user

| user | aggression | committedCap | confirmedCap | cumulative60 | hypoCaution | primerCap |
| --- | --- | --- | --- | --- | --- | --- |
| A | 0 | 16.295 | 3.259 | 6.518 | 0 | 14.122 |
| B | 8.69 | 14.898 | 17.381 | 18.622 | 12.415 | 12.415 |
| C | 6.952 | 17.381 | 17.381 | 17.381 | 12.167 | 12.167 |
| D | 1.241 | 7.449 | 2.483 | 8.69 | 2.483 | 6.207 |
| E | 0 | 17.381 | 20.64 | 17.381 | 0 | 11.949 |
| F | 2.173 | 18.467 | 9.777 | 16.295 | 2.173 | 17.381 |
| H | 0 | 17.381 | 4.345 | 10.863 | 0 | 8.69 |
| tim | 5.214 | 17.729 | 7.3 | 16.686 | 14.6 | 14.6 |