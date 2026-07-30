# One-minute versus five-minute CGM: what the extra samples carry

*Generated from `backtesting/scripts/2026-07-cgm-cadence/`. Every figure in this report is
read from the JSON written by scripts 01-05; none is transcribed by hand.*

## Summary

One person wore a five-minute sensor for 83 days and then a one-minute
sensor for 67 days. Nothing below is decimated, interpolated or simulated:
the two cadences are compared as they were actually recorded.

- **The two records differ by one number.** The ratio of their variograms is
  **1.602** across every lag both sensors can see, from 5 to 120 minutes, with a
  total spread of 6.6 per cent of the mean and no trend.
- **Neither cadence is noisier, and neither shows a measurement-noise floor.** At a
  one-minute lag D is 4.44 mg/dL², which is
  22 per cent of the 20.4 mg/dL²
  floor that a published sensor-noise standard deviation would impose at every lag. Both sensors report
  values that are already filtered.
- **Nothing new appears below five minutes.** The log-log slope there is
  1.49 [1.18, 1.71], containing the 1.35 measured just above it.
- **Forecasting does not improve.** Normalised RMSE intervals overlap at all
  5 horizons and the nominal winner alternates.
- **Predicting lows and highs does not improve.** The sign of the difference *reverses*
  between lows and highs, which no cadence effect can produce.
- **What does change is when you are told:** the five-minute record reports a threshold
  crossing 2.19 minutes later on average, against an arithmetic
  expectation of 2.00 minutes from sample spacing alone.

## 1. The records

| | Five-minute era | One-minute era |
|---|---|---|
| Dates | 2026-03-01 – 2026-05-23 | 2026-05-23 – 2026-07-31 |
| Days with data | 83 | 67 |
| Readings | 24,012 | 84,588 |
| Median gap (min) | 5.00 | 1.00 |
| On cadence | 98.8% | 96.5% |
| Coverage | 100.5% | 85.7% |
| Mean glucose | 118.4 | 125.6 |
| SD | 30.7 | 39.0 |
| CV | 25.9% | 31.1% |
| Time in range 70-180 | 93.5% | 86.2% |
| Time <70 | 2.49% | 4.00% |
| Time <54 | 0.30% | 0.15% |
| Time >180 | 4.00% | 9.80% |
| Time >250 | 0.13% | 0.83% |

The eras are **not** matched. The later period is more volatile — the squared ratio of
coefficients of variation is 1.438, there is 1.60 times
as much time below 70 and 2.45 times as much above 180. Glycaemic
variability is a property of the person and the period, not of the sensor, so every metric
below is scale-free or normalised by the era's own base rate. Where that is not possible the
confound is stated.

## 2. Noise and signal: the variogram

The variogram D(τ) = E[(x(t+τ) − x(t))²] is the mean squared change over a lag of τ minutes.
It is expressed in time rather than samples, so both cadences sit on one axis with no
resampling. It separates the two questions by construction: additive measurement noise of
variance s² lifts D by 2s² at *every* lag including the shortest, whereas real signal
vanishes as τ → 0. A noise floor therefore shows up as a flattening at small lag.

### 2.1 The two records differ by a single scale factor

| Lag | Five-minute era D | One-minute era D | Ratio |
|---|---|---|---|
| 5 min | 30.0 [27.3, 32.7] | 47.5 [42.3, 52.7] | 1.584 |
| 10 min | 79.5 [72.9, 85.8] | 121.8 [108.8, 134.1] | 1.533 |
| 15 min | 133.4 [123.2, 143.5] | 210.7 [188.1, 232.5] | 1.579 |
| 20 min | 195.7 [179.3, 211.1] | 315.1 [282.0, 348.6] | 1.610 |
| 25 min | 267.1 [244.6, 289.7] | 430.6 [384.0, 476.5] | 1.612 |
| 30 min | 339.6 [310.2, 369.5] | 556.3 [495.6, 618.1] | 1.638 |
| 40 min | 506.7 [461.7, 553.5] | 810.6 [716.0, 902.8] | 1.600 |
| 50 min | 658.4 [597.8, 721.0] | 1063.0 [941.2, 1185.2] | 1.615 |
| 60 min | 794.1 [717.0, 874.6] | 1283.9 [1130.1, 1427.8] | 1.617 |
| 90 min | 1112.1 [991.5, 1229.4] | 1787.5 [1560.9, 1997.7] | 1.607 |
| 120 min | 1320.9 [1157.2, 1485.2] | 2144.9 [1849.7, 2396.6] | 1.624 |

Mean ratio **1.602**, range 1.533 to 1.638, spread
6.6 per cent of the mean over a twenty-four-fold range of lag. It does not
trend and it does not bend at the short end, which is the only place the two sensors could
differ. For comparison the squared ratio of coefficients of variation is 1.438.

### 2.2 Neither record has a measurement-noise floor

| Lag | One-minute era D | Share of the floor a 3.19 mg/dL noise SD would impose |
|---|---|---|
| 1 min | 4.44 [2.93, 7.38] | 22% |
| 2 min | 12.24 [10.07, 15.94] | 60% |
| 3 min | 23.24 [20.22, 27.79] | 114% |
| 4 min | 36.16 [31.86, 41.93] | 178% |
| 5 min | 47.52 [42.28, 52.72] | 233% |
| 10 min | 121.78 [108.76, 134.10] | 598% |

D falls smoothly to 4.44 mg/dL² at a one-minute lag with no sign of
levelling off — 22 per cent of the
20.4 mg/dL² that independent noise of the published magnitude would
hold it at. If that were white noise it would correspond to a standard deviation of only
1.49 mg/dL. The values these sensors report are not raw
transducer output; they have been filtered before leaving the device, and the filtering rather
than the reporting interval is what governs how clean the series looks.

### 2.3 No new regime below five minutes

| Record | Lag band | Log-log slope |
|---|---|---|
| Five-minute era | 5–20 min | 1.35 [1.31, 1.39] |
| Five-minute era | 20–60 min | 1.29 [1.24, 1.33] |
| One-minute era | 1–5 min | 1.49 [1.18, 1.71] |
| One-minute era | 5–20 min | 1.35 [1.29, 1.40] |
| One-minute era | 20–60 min | 1.29 [1.24, 1.33] |

A slope of 2 would be a smooth differentiable signal and 0 would be white noise; both records
sit near 1.3 throughout. In the two bands the sensors share, the intervals overlap. Below five
minutes, where only the faster sensor can see, the slope contains the value measured just
above it, so the same power law continues from one minute to sixty with no break.

## 3. Forecasting — the automated-insulin-delivery case

Each era is modelled at its own native cadence and validated out of sample with GroupKFold
over whole days. Both get the same look-back in *minutes*; the faster record simply has five
times as many samples inside it. Error is divided by the standard deviation of the target, so
1.0 means no better than predicting the mean and the difference in variability between the
eras cannot drive the comparison.

| Horizon | Five-minute era | One-minute era | Verdict |
|---|---|---|---|
| +15 min | 0.346 [0.325, 0.367] | 0.345 [0.322, 0.369] | overlap, nominally 1-min |
| +30 min | 0.571 [0.543, 0.601] | 0.556 [0.519, 0.600] | overlap, nominally 1-min |
| +45 min | 0.720 [0.688, 0.753] | 0.717 [0.676, 0.767] | overlap, nominally 1-min |
| +60 min | 0.818 [0.792, 0.851] | 0.820 [0.780, 0.868] | overlap, nominally 5-min |
| +90 min | 0.915 [0.895, 0.940] | 0.920 [0.891, 0.954] | overlap, nominally 5-min |

Intervals overlap at every horizon and the nominal winner alternates, so there is no
forecast advantage to detect in either direction.

## 4. Predicting lows and highs

Base rates differ substantially between the eras, so **lift** — precision in the top risk
decile divided by that era's own base rate — is the metric to compare. AUC is shown alongside.

### low <70

| Horizon | Era | Base rate | AUC | Lift |
|---|---|---|---|---|
| 15 min | 5-min | 1.26% | 0.9581 [0.9428, 0.9721] | 8.86× [8.23, 9.43] |
| 15 min | 1-min | 1.80% | 0.9714 [0.9605, 0.9801] | 9.16× [8.71, 9.57] |
| 20 min | 5-min | 1.75% | 0.9411 [0.9265, 0.9554] | 8.27× [7.81, 8.84] |
| 20 min | 1-min | 2.30% | 0.9595 [0.9450, 0.9721] | 8.66× [8.06, 9.11] |
| 30 min | 5-min | 2.42% | 0.8935 [0.8659, 0.9226] | 7.25× [6.67, 7.95] |
| 30 min | 1-min | 3.30% | 0.9275 [0.9074, 0.9440] | 7.61× [7.01, 8.14] |
| 45 min | 5-min | 3.39% | 0.8232 [0.7895, 0.8617] | 6.05× [5.44, 6.73] |
| 45 min | 1-min | 4.76% | 0.8575 [0.8207, 0.8857] | 6.23× [5.61, 6.78] |
| 60 min | 5-min | 4.37% | 0.7707 [0.7312, 0.8122] | 5.14× [4.62, 5.68] |
| 60 min | 1-min | 6.18% | 0.7925 [0.7487, 0.8287] | 5.36× [4.79, 5.93] |

### low <54

| Horizon | Era | Base rate | AUC | Lift |
|---|---|---|---|---|
| 15 min | 5-min | 0.21% | 0.9794 [0.9581, 0.9972] | 9.62× [8.09, 10.01] |
| 15 min | 1-min | 0.14% | too rare to model | — |
| 20 min | 5-min | 0.30% | 0.9492 [0.9162, 0.9776] | 8.43× [7.29, 9.47] |
| 20 min | 1-min | 0.17% | too rare to model | — |
| 30 min | 5-min | 0.45% | 0.9147 [0.8662, 0.9674] | 8.08× [7.07, 9.31] |
| 30 min | 1-min | 0.24% | 0.9429 [0.9112, 0.9798] | 8.45× [7.31, 9.85] |
| 45 min | 5-min | 0.65% | 0.8319 [0.7758, 0.9112] | 7.04× [6.17, 8.22] |
| 45 min | 1-min | 0.35% | 0.8567 [0.7746, 0.9480] | 6.84× [5.56, 8.25] |
| 60 min | 5-min | 0.88% | 0.7429 [0.6573, 0.8411] | 5.64× [4.54, 7.15] |
| 60 min | 1-min | 0.47% | 0.7744 [0.6592, 0.9185] | 5.40× [4.17, 7.09] |

### high >180

| Horizon | Era | Base rate | AUC | Lift |
|---|---|---|---|---|
| 15 min | 5-min | 1.31% | 0.9668 [0.9436, 0.9842] | 9.35× [8.85, 9.85] |
| 15 min | 1-min | 2.85% | 0.9611 [0.9508, 0.9691] | 8.56× [8.08, 8.95] |
| 20 min | 5-min | 1.73% | 0.9525 [0.9336, 0.9689] | 8.63× [8.14, 9.12] |
| 20 min | 1-min | 3.58% | 0.9442 [0.9317, 0.9543] | 7.80× [7.35, 8.24] |
| 30 min | 5-min | 2.39% | 0.9283 [0.9057, 0.9488] | 7.77× [7.18, 8.35] |
| 30 min | 1-min | 5.02% | 0.8912 [0.8731, 0.9076] | 6.39× [5.99, 6.80] |
| 45 min | 5-min | 3.35% | 0.8868 [0.8561, 0.9135] | 6.65× [6.08, 7.22] |
| 45 min | 1-min | 7.07% | 0.8079 [0.7838, 0.8309] | 4.87× [4.57, 5.21] |
| 60 min | 5-min | 4.28% | 0.8413 [0.8086, 0.8711] | 5.51× [4.97, 6.06] |
| 60 min | 1-min | 9.03% | 0.7469 [0.7208, 0.7709] | 4.04× [3.73, 4.34] |

### high >250

| Horizon | Era | Base rate | AUC | Lift |
|---|---|---|---|---|
| 15 min | 5-min | 0.05% | too rare to model | — |
| 15 min | 1-min | 0.44% | 0.9939 [0.9912, 0.9968] | 10.00× [9.96, 10.00] |
| 20 min | 5-min | 0.07% | too rare to model | — |
| 20 min | 1-min | 0.56% | 0.9920 [0.9875, 0.9962] | 9.93× [9.77, 10.00] |
| 30 min | 5-min | 0.11% | too rare to model | — |
| 30 min | 1-min | 0.77% | 0.9769 [0.9529, 0.9939] | 9.53× [8.77, 10.00] |
| 45 min | 5-min | 0.16% | too rare to model | — |
| 45 min | 1-min | 1.10% | 0.9377 [0.8607, 0.9890] | 8.91× [7.60, 9.86] |
| 60 min | 5-min | 0.21% | 0.9884 [0.9744, 0.9996] | 10.00× [10.00, 10.00] |
| 60 min | 1-min | 1.44% | 0.9003 [0.7964, 0.9734] | 8.16× [6.59, 9.40] |

### The sign reverses, which settles it

If one-minute sampling carried more predictive information it would help on every task. It
does not. On lows the one-minute era scores nominally higher; on highs above 180 it scores
**lower**, and substantially so at the longer horizons.

| Task | AUC gap, one-minute minus five-minute, by horizon | Trend |
|---|---|---|
| low <70 | 15m +0.0132, 20m +0.0185, 30m +0.0340, 45m +0.0343, 60m +0.0218 | favours 1-min more strongly at long horizons |
| low <54 | 30m +0.0282, 45m +0.0248, 60m +0.0316 | roughly flat with horizon |
| high >180 | 15m -0.0058, 20m -0.0083, 30m -0.0370, 45m -0.0789, 60m -0.0944 | favours 5-min more strongly at long horizons |

A genuine cadence benefit would be largest at the shortest horizon, where fine-grained recent
detail matters most, and would wash out as the horizon lengthens. Neither task behaves that
way, and the two tasks disagree on direction. These differences track how hard each period was
to predict, not how often it was sampled.

## 5. What cadence does change: reporting delay

A threshold is crossed at some instant between two reported samples. Locating that instant by
interpolation and measuring the wait until the next sample the sensor actually reported gives
the delay directly, on the real records.

| Crossing | Five-minute era mean delay | One-minute era mean delay | Difference |
|---|---|---|---|
| falling below 70 | 3.04 [2.79, 3.29] min (n=110) | 0.86 [0.80, 0.91] min (n=114) | **+2.18 min** |
| falling below 54 | 2.27 min (n=18) | too few crossings | — |
| rising above 180 | 2.90 [2.59, 3.21] min (n=101) | 0.71 [0.66, 0.76] min (n=177) | **+2.19 min** |
| rising above 250 | too few crossings | 0.64 min (n=29) | — |

The average difference is **2.19 minutes**, against an arithmetic
expectation of 2.00 minutes from the sample spacing alone. This is pure scheduling: it
requires no extra information and it is the whole of what the faster feed delivers.

## 6. Reading

The two sensors record the same process at the same relative noise, and their records differ
by a single scale factor that is the volatility of the period. The faster sensor resolves no
new regime below five minutes, forecasts no better at any horizon between fifteen and ninety
minutes, and predicts neither lows nor highs better once each era's own base rate is divided
out — with the sign of the difference reversing between the two, which no property of the
sampling interval could produce.

What a one-minute feed does deliver is about two minutes less waiting to be told that
something has happened. Whether two minutes is worth having depends on what consumes it: it is
available in full to an alarm and to a person who can act at once, and it is small against the
onset of any insulin action.

## 7. Limitations

One subject. The comparison is observational and between eras, so sensor hardware, season,
therapy and glycaemic control all change at the boundary. The analysis is built to be robust
to exactly that — variogram ratios, log-log slopes, normalised error and base-rate lift are
all scale-free — but a single person cannot establish that the finding generalises.

The sensor makes and models are not recorded in the data available. The noise conclusion is
about the *reported* series, not the raw transducer signal behind it.

Two tasks were too rare to model in one era or the other and are shown as such rather than
being forced.

No outcome data is analysed, and none is needed for the question asked, which is what the two
records contain.

## Reproducing

```
python3 01_profile.py          # coverage, cadence stability, glycaemic distribution
python3 02_variogram.py        # ratio, noise floor, log-log slopes
python3 03_forecast.py         # normalised forecast error by horizon
python3 04_events.py           # lows and highs, AUC and base-rate lift
python3 05_reporting_delay.py  # real delay from crossing to next reported sample
python3 06_report.py           # regenerates this document from results/*.json
```

PROVISIONAL — one subject; observational between-era comparison.
