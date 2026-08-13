# The glucose signal: cadence, smoothing, and what a faster sensor carries

## Hypothesis

Sensors reporting every minute became available, and the expectation attached to them was that a
closed loop given five times as many readings would control glucose better. The hypothesis to be
tested was whether the additional readings carry additional information, and separately whether the
software can use them if they do.

A second line concerned smoothing. Continuous glucose sensors produce artefacts, notably compression
lows when a participant lies on the sensor, and the question was whether a state estimator could
distinguish those from real falls well enough to protect against dosing into them.

## Investigation

Cadence was investigated by comparing a real five minute era against a real one minute era from the
same participant, using a variogram to characterise how much structure exists at each lag, and by
testing whether short-horizon forecasting improves with the faster feed.

Smoothing was investigated by implementing an unscented Kalman filter with a
Rauch-Tung-Striebel smoother and adaptive measurement noise, mirroring the shipped Kotlin
implementation operation for operation in Python, and comparing it against simpler alternatives on
identical input.

Whether the software could use a faster feed at all was established by reading the ingestion path.

## Methods

The cadence work is recorded under `backtesting/scripts/2026-07-cgm-cadence/` and the associated
preprint, using 83 days of five minute data against 61 days of one minute data. The smoothing work is
under `2026-07-ukf-smoothing/`, over 45,698 readings.

One methodological point cost a false result and is recorded as a rule: a cadence view must never be
selected by timestamp modulo, because the jitter in arrival times cripples the slower feed and
inflates every comparison against it.

## Results

The two eras differ by a single scale factor of 1.602, flat across every lag from five minutes to two
hours. There is no noise floor at either cadence, both feeds having been pre-filtered by the vendor.
Prediction gains essentially nothing: the lift is 9.14 against 9.18. Rate of change is estimated
slightly worse at one minute than at five, because a shorter baseline makes the estimate noisier and
differencing amplifies it.

The physiological explanation is that interstitial fluid lags blood by roughly four minutes and that
lag behaves as a low-pass filter. Glucose is already smoothed by the time the sensor reads it, and
sampling a smoothed signal more often does not recover what the smoothing removed. What a faster feed
buys is latency rather than bandwidth, and the latency gain is about two minutes, which is the
expected wait for the next reading on a five minute grid.

Sub-twenty-minute structure in the signal is autoregressive sensor noise rather than glucose, which
corrects an earlier reading that treated it as coherent in sign.

On smoothing, the filter is tuned to be responsive rather than quiet: adaptive noise falls toward its
floor so the gain stays high and the estimate tracks the raw closely, and a kinetic hypoglycaemia
guard deliberately reverts the estimate toward the raw value when glucose is low and falling. No
jitter-reduction claim can therefore be made. Its value, if any, is in trend and prediction rather
than denoising. It absorbs the least of an injected compression dip among the candidates tested, at
0.71 against 0.90 for an exponential smoother, which is the property wanted.

The ingestion finding is the one with the broadest reach. Glucose passes through a bucketing step
before the algorithm sees it, and that step works on a five minute grid whatever the sensor does. The
loop is then triggered from the newest entry in that series with a guard rejecting any cycle whose
glucose timestamp has already been used. Fitting a one minute sensor to an unmodified loop therefore
gives a five minute view feeding a five minute decision, with four readings in five discarded during
ingestion.

## Discussion

The programme's position on fast sensors is that they buy latency and not information, that the
latency is worth about two minutes, and that two minutes matters only during rapid movement, which is
a small fraction of the day. That is a deflationary conclusion and it was reached before the
engineering rather than after, which saved building a great deal.

It also has a consequence nobody anticipated. Because the smoothing filter sizes its windows from the
observed spacing, a window meaning ninety minutes is eighteen readings at five minute cadence and
ninety at one. Estimating sensor noise from ninety samples rather than eighteen is a materially
better-conditioned estimate, and it has nothing to do with dosing sooner. If a fast sensor helps, it
may help through the noise estimate rather than through the response time.

The bucketing finding applies to anyone fitting a fast sensor to a stock loop today, not only to this
fork, and it means that changing the sensor alone changes nothing. Extracting value requires changing
the software, and the first thing to change is what decides how often the algorithm runs.

The parity caveat on the smoothing work is recorded because it bounds the claims. Bit-exact agreement
between the Python mirror and the shipped Kotlin was never formally unit-tested, so absolute numbers
from the mirror should not be trusted. The relative ranking is robust, since every candidate is fed
an identical stream and sub-unit float drift cannot flip a multi-percent gap.
