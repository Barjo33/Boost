# Is one-minute CGM data more useful than five-minute data?

*Generated from `backtesting/scripts/2026-07-cgm-cadence/`. Every figure is read from the
JSON written by scripts 01 to 05.*

## The short answer

No, for accuracy. The two records carry the same information about glucose, and every
accuracy measure tested comes out the same at both cadences.

Yes, by about 2.2 minutes, for timing. A five-minute sensor reports a threshold crossing
later than a one-minute sensor does, by roughly the amount the sample spacing implies. That
is a scheduling difference and it requires no additional information.

## 1. What was compared

One person wore a five-minute sensor from 2026-03-01 to 2026-05-23, then a one-minute sensor
from 2026-05-23 to 2026-07-31. The two records are compared as they were recorded. Nothing
is decimated, interpolated or simulated.

This matters because the usual way to ask this question is to take a one-minute record and
discard four samples in five. That measures what a consumer loses by reading a fast sensor
slowly. It does not measure how a fast sensor and a slow sensor differ, because a slow
sensor filters internally before it reports.

| | Five-minute era | One-minute era |
|---|---|---|
| Days with data | 83 | 67 |
| Readings | 24,012 | 84,588 |
| Median gap (min) | 5.00 | 1.00 |
| Samples on cadence | 98.8% | 96.5% |
| Coverage of the period | 100.5% | 85.7% |
| Mean glucose (mg/dL) | 118.4 | 125.6 |
| SD (mg/dL) | 30.7 | 39.0 |
| CV | 25.9% | 31.1% |
| Time in range 70 to 180 | 93.5% | 86.2% |
| Time below 70 | 2.49% | 4.00% |
| Time below 54 | 0.30% | 0.15% |
| Time above 180 | 4.00% | 9.80% |
| Time above 250 | 0.13% | 0.83% |

The two periods are not matched. Control was worse during the later one: the squared ratio
of coefficients of variation is 1.438, with 1.60 times as much time below 70 and 2.45 times
as much above 180.

Glycaemic variability is a property of the person and the period rather than of the sensor,
so it cannot be allowed to decide the comparison. Every measure below is either scale-free
or divided by that era's own base rate. Where a measure is not, the point is not relied
upon.

## 2. Method

The main tool is the variogram, D(tau) = E[(x(t+tau) - x(t))^2], which is the mean squared
change over a lag of tau minutes.

It suits this question for two reasons. It is expressed in time rather than in samples, so a
five-minute record and a one-minute record can be placed on the same axis without resampling
either. It also separates noise from signal by construction: if a sensor adds independent
measurement noise of variance s^2 then every difference contains two independent noise
draws, so D is raised by 2s^2 at every lag, including the shortest. Real signal structure
vanishes as tau approaches zero, because glucose is continuous. A noise floor therefore
appears as a flattening of D at small lag, and its height gives the noise variance directly.

The log-log slope of D describes the character of the record independently of how large its
excursions were. A slope of 2 indicates a smooth differentiable signal and a slope of 0
indicates white noise.

Prediction is modelled at each era's own native cadence and validated out of sample with
GroupKFold over whole days. Both cadences are given the same look-back in minutes; the
faster record simply holds five times as many samples inside it. Intervals throughout are 95
per cent block bootstraps that resample whole days, which respects the autocorrelation of
glucose.

## 3. Do the records differ by anything other than volatility?

If the periods differ only in how volatile they were, the variogram of one will be a
constant multiple of the other at every lag. If the sensors differ, the ratio will bend at
short lag, since that is the only place their behaviour can diverge.

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

The ratio averages 1.602 over lags from 5 to 120 minutes, a twenty-four-fold range, with a
total spread of 6.6 per cent of its mean. It does not trend and it does not bend at the
short end.

The two records are therefore the same signal scaled by a single number. For reference, the
squared ratio of coefficients of variation is 1.438, so most of the scale factor is
accounted for by the change in control.

## 4. Is either sensor noisier?

| Lag | One-minute era D (mg/dL^2) | Share of the floor implied by a 3.19 mg/dL noise SD |
|---|---|---|
| 1 min | 4.44 [2.93, 7.38] | 22% |
| 2 min | 12.24 [10.07, 15.94] | 60% |
| 3 min | 23.24 [20.22, 27.79] | 114% |
| 4 min | 36.16 [31.86, 41.93] | 178% |
| 5 min | 47.52 [42.28, 52.72] | 233% |
| 10 min | 121.78 [108.76, 134.10] | 598% |

D falls smoothly to 4.44 mg/dL^2 at a one-minute lag and shows no sign of levelling off.
Neither record has a noise floor to measure.

The comparison worth making is with the published error models. Vettoretti and colleagues
fit a measurement-noise standard deviation of 3.19 mg/dL to a factory-calibrated sensor,
which would hold D at 20.4 mg/dL^2 at every lag. The measured value at one minute is 22 per
cent of that. Treated as white noise it would correspond to a standard deviation of 1.49
mg/dL.

The reading is that neither sensor reports raw transducer output. Both filter before the
value leaves the device, and it is the filtering rather than the reporting interval that
governs how clean the series looks. Section 3 already shows the point directly: there is no
lag at which the faster record sits proportionally higher than the slower one.

## 5. Does the faster sensor resolve anything below five minutes?

| Record | Lag band | Log-log slope of D |
|---|---|---|
| Five-minute era | 5 to 20 min | 1.35 [1.31, 1.39] |
| Five-minute era | 20 to 60 min | 1.29 [1.24, 1.33] |
| One-minute era | 1 to 5 min | 1.49 [1.18, 1.71] |
| One-minute era | 5 to 20 min | 1.35 [1.29, 1.40] |
| One-minute era | 20 to 60 min | 1.29 [1.24, 1.33] |

In the two bands the sensors share, the slopes agree to two decimal places and the intervals
overlap. The records have the same roughness at every timescale both can see.

Below five minutes, where only the faster sensor reaches, the slope is 1.49 [1.18, 1.71].
That interval contains the 1.35 measured just above it, so the same power law continues from
one minute to sixty with no break. The extra samples trace the curve more finely; they do
not open a new regime.

## 6. Does prediction improve?

This is the only category of use where a faster cadence could plausibly be more accurate
rather than merely earlier. Reading the current value, raising an alarm on it, and computing
retrospective statistics all depend either on the newest sample or on an average over
thousands of them.

### 6.1 Forecasting a future glucose value

Error is divided by the standard deviation of the target, so 1.0 means no better than
predicting the mean and the difference in volatility between the periods cannot drive the
comparison.

| Horizon | Five-minute era | One-minute era | Intervals | Nominally better |
|---|---|---|---|---|
| +15 min | 0.346 [0.325, 0.367] | 0.345 [0.322, 0.369] | overlap | 1-min |
| +30 min | 0.571 [0.543, 0.601] | 0.556 [0.519, 0.600] | overlap | 1-min |
| +45 min | 0.720 [0.688, 0.753] | 0.717 [0.676, 0.767] | overlap | 1-min |
| +60 min | 0.818 [0.792, 0.851] | 0.820 [0.780, 0.868] | overlap | 5-min |
| +90 min | 0.915 [0.895, 0.940] | 0.920 [0.891, 0.954] | overlap | 5-min |

The intervals overlap at every horizon and the nominal winner changes from one horizon to
the next, so there is no advantage to detect in either direction.

### 6.2 Predicting lows and highs

Base rates differ substantially between the periods, so lift is the figure to compare. Lift
is the precision within the top decile of predicted risk, divided by that era's own base
rate. AUC is shown alongside, with the caveat that it is sensitive to prevalence.

#### low below 70

| Horizon | Era | Base rate | AUC | Lift |
|---|---|---|---|---|
| 15 min | 5-min | 1.26% | 0.9581 [0.9428, 0.9721] | 8.86x [8.23, 9.43] |
| 15 min | 1-min | 1.80% | 0.9714 [0.9605, 0.9801] | 9.16x [8.71, 9.57] |
| 20 min | 5-min | 1.75% | 0.9411 [0.9265, 0.9554] | 8.27x [7.81, 8.84] |
| 20 min | 1-min | 2.30% | 0.9595 [0.9450, 0.9721] | 8.66x [8.06, 9.11] |
| 30 min | 5-min | 2.42% | 0.8935 [0.8659, 0.9226] | 7.25x [6.67, 7.95] |
| 30 min | 1-min | 3.30% | 0.9275 [0.9074, 0.9440] | 7.61x [7.01, 8.14] |
| 45 min | 5-min | 3.39% | 0.8232 [0.7895, 0.8617] | 6.05x [5.44, 6.73] |
| 45 min | 1-min | 4.76% | 0.8575 [0.8207, 0.8857] | 6.23x [5.61, 6.78] |
| 60 min | 5-min | 4.37% | 0.7707 [0.7312, 0.8122] | 5.14x [4.62, 5.68] |
| 60 min | 1-min | 6.18% | 0.7925 [0.7487, 0.8287] | 5.36x [4.79, 5.93] |

#### low below 54

| Horizon | Era | Base rate | AUC | Lift |
|---|---|---|---|---|
| 15 min | 5-min | 0.21% | 0.9794 [0.9581, 0.9972] | 9.62x [8.09, 10.01] |
| 15 min | 1-min | 0.14% | too rare to model | |
| 20 min | 5-min | 0.30% | 0.9492 [0.9162, 0.9776] | 8.43x [7.29, 9.47] |
| 20 min | 1-min | 0.17% | too rare to model | |
| 30 min | 5-min | 0.45% | 0.9147 [0.8662, 0.9674] | 8.08x [7.07, 9.31] |
| 30 min | 1-min | 0.24% | 0.9429 [0.9112, 0.9798] | 8.45x [7.31, 9.85] |
| 45 min | 5-min | 0.65% | 0.8319 [0.7758, 0.9112] | 7.04x [6.17, 8.22] |
| 45 min | 1-min | 0.35% | 0.8567 [0.7746, 0.9480] | 6.84x [5.56, 8.25] |
| 60 min | 5-min | 0.88% | 0.7429 [0.6573, 0.8411] | 5.64x [4.54, 7.15] |
| 60 min | 1-min | 0.47% | 0.7744 [0.6592, 0.9185] | 5.40x [4.17, 7.09] |

#### high above 180

| Horizon | Era | Base rate | AUC | Lift |
|---|---|---|---|---|
| 15 min | 5-min | 1.31% | 0.9668 [0.9436, 0.9842] | 9.35x [8.85, 9.85] |
| 15 min | 1-min | 2.85% | 0.9611 [0.9508, 0.9691] | 8.56x [8.08, 8.95] |
| 20 min | 5-min | 1.73% | 0.9525 [0.9336, 0.9689] | 8.63x [8.14, 9.12] |
| 20 min | 1-min | 3.58% | 0.9442 [0.9317, 0.9543] | 7.80x [7.35, 8.24] |
| 30 min | 5-min | 2.39% | 0.9283 [0.9057, 0.9488] | 7.77x [7.18, 8.35] |
| 30 min | 1-min | 5.02% | 0.8912 [0.8731, 0.9076] | 6.39x [5.99, 6.80] |
| 45 min | 5-min | 3.35% | 0.8868 [0.8561, 0.9135] | 6.65x [6.08, 7.22] |
| 45 min | 1-min | 7.07% | 0.8079 [0.7838, 0.8309] | 4.87x [4.57, 5.21] |
| 60 min | 5-min | 4.28% | 0.8413 [0.8086, 0.8711] | 5.51x [4.97, 6.06] |
| 60 min | 1-min | 9.03% | 0.7469 [0.7208, 0.7709] | 4.04x [3.73, 4.34] |

#### high above 250

| Horizon | Era | Base rate | AUC | Lift |
|---|---|---|---|---|
| 15 min | 5-min | 0.05% | too rare to model | |
| 15 min | 1-min | 0.44% | 0.9939 [0.9912, 0.9968] | 10.00x [9.96, 10.00] |
| 20 min | 5-min | 0.07% | too rare to model | |
| 20 min | 1-min | 0.56% | 0.9920 [0.9875, 0.9962] | 9.93x [9.77, 10.00] |
| 30 min | 5-min | 0.11% | too rare to model | |
| 30 min | 1-min | 0.77% | 0.9769 [0.9529, 0.9939] | 9.53x [8.77, 10.00] |
| 45 min | 5-min | 0.16% | too rare to model | |
| 45 min | 1-min | 1.10% | 0.9377 [0.8607, 0.9890] | 8.91x [7.60, 9.86] |
| 60 min | 5-min | 0.21% | 0.9884 [0.9744, 0.9996] | 10.00x [10.00, 10.00] |
| 60 min | 1-min | 1.44% | 0.9003 [0.7964, 0.9734] | 8.16x [6.59, 9.40] |

### 6.3 The direction of the difference reverses

If one-minute sampling carried more predictive information it would help whichever way
glucose was moving. It does not.

| Task | AUC gap, one-minute minus five-minute | Behaviour with horizon |
|---|---|---|
| low below 70 | 15m +0.0132, 20m +0.0185, 30m +0.0340, 45m +0.0343, 60m +0.0218 | favours 1-min more strongly at long horizons |
| low below 54 | 30m +0.0282, 45m +0.0248, 60m +0.0316 | roughly flat with horizon |
| high above 180 | 15m -0.0058, 20m -0.0083, 30m -0.0370, 45m -0.0789, 60m -0.0944 | favours 5-min more strongly at long horizons |

On lows the one-minute era scores higher. On highs above 180 it scores lower, and the
deficit widens with horizon. A sampling interval cannot help in one direction and hinder in
the other.

A genuine cadence benefit would also be largest at the shortest horizon, where recent detail
matters most, and would fade as the horizon lengthened. Neither task behaves that way. What
these differences track is how difficult each period was to predict.

## 7. Point to point acceleration

Velocity is the first difference of consecutive readings and acceleration the difference of
consecutive velocities, so over one sampling interval h the literal construction is (x(t) -
2x(t-h) + x(t-2h)) / h^2. Figures below are in mg/dL per 5 min per 5 min so that the two
cadences can be read against each other.

| | Five-minute era | One-minute era |
|---|---|---|
| Samples | 23,727 | 81,645 |
| SD over one interval (mg/dL) | 6.32 | 1.93 |
| SD as mg/dL per 5 min per 5 min | 6.32 | 48.32 |
| Median absolute value (mg/dL) | 3.00 | 1.00 |
| Exactly zero | 11.9% | 37.6% |
| Predicted from the variogram (mg/dL) | 6.36 | 2.35 |
| Lag-1 autocorrelation | -0.290 | -0.049 |

The mean square of a second difference is exactly 4D(h) minus D(2h) for any process, so the
variogram of section 3 predicts the acceleration magnitude with no free parameters. On the
five-minute record the prediction is 6.36 mg/dL against a measured 6.32. Acceleration
therefore carries nothing the variogram did not already describe.

### 7.1 It has no cadence-independent value

For a process whose variogram goes as h to the power alpha, the second difference goes as
the same power, so acceleration, which divides by h squared, goes as h to the power alpha/2
minus 2. The number therefore depends on the interval it was computed over.

| Ratio of one-minute to five-minute acceleration SD | Value |
|---|---|
| Measured | 7.65 |
| Predicted from alpha = 1.35 (this record, 5 to 20 min) | 8.43 |
| Predicted from alpha = 1.49 (this record, 1 to 5 min) | 7.53 |
| Predicted from alpha = 0.00 (white noise) | 25.00 |
| A twice differentiable signal | 1.00 |

A twice differentiable signal would give a ratio of 1.00, because the leading term cancels
and acceleration converges on a real value as the interval shrinks. This record gives 7.65,
close to the value implied by its own roughness. Acceleration measured over one minute is
roughly eight times larger than the same quantity measured over five minutes, and neither is
more correct than the other.

The practical consequence is that any threshold placed on a point to point acceleration is a
threshold at one particular cadence and does not transfer to another. The construction used
by the controller avoids this, since it averages rates over windows fixed in minutes rather
than in samples, and normalises by the slower of the two.

### 7.2 It adds nothing to prediction

Adding acceleration to a feature set that already contains the current value and several
backward differences and slopes, then predicting within 30 minutes:

| Era | Task | Feature set | AUC | Lift |
|---|---|---|---|---|
| 5-min | low below 70 | velocity only | 0.8935 [0.8668, 0.9224] | 7.25x |
| 5-min | low below 70 | + point to point acceleration | 0.8934 [0.8666, 0.9223] | 7.22x |
| 5-min | low below 70 | + controller acceleration | 0.8939 [0.8683, 0.9220] | 7.29x |
| 5-min | low below 70 | + both | 0.8938 [0.8681, 0.9219] | 7.29x |
| 5-min | high above 180 | velocity only | 0.9283 [0.9058, 0.9478] | 7.77x |
| 5-min | high above 180 | + point to point acceleration | 0.9284 [0.9062, 0.9477] | 7.77x |
| 5-min | high above 180 | + controller acceleration | 0.9283 [0.9059, 0.9478] | 7.77x |
| 5-min | high above 180 | + both | 0.9282 [0.9057, 0.9477] | 7.77x |
| 1-min | low below 70 | velocity only | 0.9275 [0.9074, 0.9442] | 7.61x |
| 1-min | low below 70 | + point to point acceleration | 0.9268 [0.9047, 0.9444] | 7.58x |
| 1-min | low below 70 | + controller acceleration | 0.9276 [0.9070, 0.9445] | 7.60x |
| 1-min | low below 70 | + both | 0.9268 [0.9042, 0.9446] | 7.56x |
| 1-min | high above 180 | velocity only | 0.8912 [0.8745, 0.9069] | 6.39x |
| 1-min | high above 180 | + point to point acceleration | 0.8938 [0.8768, 0.9091] | 6.44x |
| 1-min | high above 180 | + controller acceleration | 0.8918 [0.8751, 0.9074] | 6.39x |
| 1-min | high above 180 | + both | 0.8943 [0.8771, 0.9096] | 6.44x |

Every variant sits within a few thousandths of the velocity-only baseline and every interval
overlaps it. Neither the point to point form nor the controller form carries predictive
information that the velocity terms do not already hold, at either cadence.

## 8. What cadence does change

A threshold is crossed at some instant between two reported samples. Locating that instant
by interpolation and measuring the wait until the next sample the sensor actually reported
gives the delay directly, from the real records.

| Crossing | Five-minute era | One-minute era | Difference |
|---|---|---|---|
| falling below 70 | 3.04 [2.79, 3.29] min, n=110 | 0.86 [0.80, 0.91] min, n=114 | +2.18 min |
| falling below 54 | 2.27 [1.72, 2.95] min, n=18 | too few crossings |  |
| rising above 180 | 2.90 [2.59, 3.21] min, n=101 | 0.71 [0.66, 0.76] min, n=177 | +2.19 min |
| rising above 250 | too few crossings | 0.64 [0.54, 0.72] min, n=29 |  |

The mean difference is 2.19 minutes, against an arithmetic expectation of 2.00 minutes from
the sample spacing alone. This is the whole of what the faster feed delivers, and it is a
matter of scheduling rather than of information.

Whether two minutes is worth having depends on what consumes it. An alarm can use it in
full, as can a person who is able to act at once. It is small against the onset of
rapid-acting insulin, which is on the order of fifteen minutes.

## 9. Limitations

The comparison rests on one person. It is observational and between eras, so the sensor
hardware changed at the boundary and so did glycaemic control. The analysis is built to be
robust to the latter, since every measure used here is scale-free, but a single subject
cannot show that the result generalises across people or devices.

The sensor makes and models are not recorded in the data available. The noise conclusion
concerns the reported series rather than the raw transducer signal behind it, which the
published error models address and which is not available here.

Some tasks were too rare to model in one era or the other. They are marked as such rather
than being forced.

No outcome data is analysed. None is needed for the question asked, which is what the two
records contain.

## Reproducing

```
./run_all.sh

01_profile.py          coverage, cadence stability, glycaemic distribution
02_variogram.py        ratio across shared lags, noise floor, log-log slopes
03_forecast.py         normalised forecast error by horizon
04_events.py           event prediction, AUC and base-rate lift
05_reporting_delay.py  delay from an interpolated crossing to the next reported sample
06_acceleration.py     point to point acceleration, scale dependence, predictive value
08_report.py           regenerates this document from results/*.json
09_style_check.py      house-style gate on the generated document
```

Provisional. One subject, observational between-era comparison.
