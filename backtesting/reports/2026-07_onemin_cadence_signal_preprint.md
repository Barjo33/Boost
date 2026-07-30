# One-minute continuous glucose data: what information the extra samples carry, and what they do not

*[Author name(s) and affiliation to be added. Prepared from an anonymised single-subject
analysis; the analysis code is open and the processing steps are reproducible.]*

## Abstract

**Background.** Continuous glucose monitors have historically reported every five minutes.
Sensors that report every minute are now in ordinary use, and the assumption in both the
clinical and the do-it-yourself communities is that more frequent data is straightforwardly
better. The signal-processing literature makes the opposite prediction. Gough and colleagues
placed the frequency band edge of blood glucose near 1×10⁻³ Hz, implying that a ten-minute
sampling period is sufficient. Breton, Shields and Kovatchev went further for the compartment
a subcutaneous sensor actually measures, reporting that interstitial glucose in type 1
diabetes contains no patterns of period shorter than about thirty-six minutes, and that
sampling blood faster than eighteen minutes would be detrimental rather than helpful. Those
results predate the current generation of sensors and, to our knowledge, no one has tested
them against a modern one-minute record or asked what the additional samples are worth.

**Methods.** We analysed 83,550 consecutive readings collected over 66 days from a
one-minute subcutaneous continuous glucose monitor worn by a single adult with type 1
diabetes. The analysis is deliberately independent of any control algorithm: every estimator
is a generic one applied to raw glucose, so that the answer describes the signal rather than
a particular implementation. We characterised the power spectrum, estimated the measurement
noise floor, tested whether the withheld minutes can be reconstructed from a five-minute
subsequence, compared rate-of-change estimation and forward prediction at the two cadences,
compared standard glycaemic metrics, and compared threshold-crossing detection latency at a
matched false-alarm rate. Effect sizes carry day-level block-bootstrap confidence intervals
and an explicit verdict against the null.

**Results.** The spectrum is consistent with the older literature and with modern
sensors: 97.3 per cent of spectral power sits at periods longer than thirty-six minutes,
99.7 per cent at periods longer than ten minutes, and 0.034 per cent at periods shorter than
five minutes. Withheld minutes are recoverable from the five-minute subsequence to a
root-mean-square error of 1.56 mg/dL, an order of magnitude below sensor accuracy. Rate of
change is estimated *worse* from one-minute data than from the five-minute subsequence of
the same record (root-mean-square error 3.717 against 3.465 mg/dL per five minutes,
difference +0.252 [+0.220, +0.285]). Forward prediction improves by 0.065 mg/dL of
root-mean-square error at thirty minutes on a baseline of 21.95, statistically
distinguishable and practically nil. Mean glucose, coefficient of variation and time in
range are unchanged to two decimal places. The residual after removing a twenty-one-minute
trend has a standard deviation of 3.40 mg/dL with autoregressive order-two structure,
closely matching the published sensor error model, and its spectral shape accounts for all
observed content below a twenty-minute period without invoking any glucose dynamics at all.
The one exception is event timing: at a matched false-alarm rate, a fall of 20 mg/dL within
thirty minutes is detected a median of three to seven minutes sooner at one minute, with
sensitivity 93 against 79 per cent. That gain is symmetric in direction — rises are detected
earlier by the same margin as falls, to within a minute at every operating point — which is
what a pure sampling-grid effect predicts and a signal-content effect does not.

**Conclusion.** One-minute sampling adds essentially no new information about glucose,
because there is no glucose information at those frequencies to add: a first-order
blood-to-interstitial lag with a time constant near 3.8 minutes removes about 80 per cent of
the amplitude of any five-minute-period oscillation before the sensor transduces it, and
what remains is below the noise floor. What one-minute sampling does remove is up to five
minutes of *reporting* delay. The benefit is latency, not bandwidth. That distinction
predicts exactly which tasks improve — threshold crossings and event alarms — and which do
not — rate estimation, forward prediction, and any summary statistic.

## 1. Introduction

The five-minute reporting interval of continuous glucose monitors is a historical artefact of
the first commercially successful devices. It has since become an implicit assumption almost
everywhere downstream: rate of change is quoted per five minutes, look-back windows are
counted in samples, alarm logic is specified in consecutive readings, and clinical trials of
closed-loop control almost universally act on a five-minute cycle.

Sensors reporting every minute are now widely worn. The natural expectation is that the
faster feed is better — that it gives earlier sight of what glucose is doing, and that any
downstream task should improve. This expectation is rarely stated explicitly and, as far as
we can establish, has never been tested.

It is worth being precise about what "better" could mean, because two quite different claims
are usually run together.

The first is a claim about **information**: that a one-minute record contains structure in
glucose that a five-minute record does not resolve. This is a claim about signal bandwidth,
and it is falsifiable by sampling theory. If glucose contains no meaningful variation at
periods shorter than roughly ten minutes, then five-minute sampling already satisfies the
Nyquist criterion with room to spare, and the intervening samples are redundant by
construction — not approximately redundant, but reconstructible from their neighbours to
within the measurement noise.

The second is a claim about **latency**: that a system acting on a one-minute feed learns of
a given event sooner than one acting on a five-minute feed. This is true almost by
definition and requires no new information at all. If a threshold is crossed at 12:03 and the
next five-minute sample lands at 12:05, the five-minute consumer waits two minutes for news
it could have had immediately. Averaged over arrival times the penalty is 2.5 minutes, and it
is a pure scheduling effect.

These are separable questions with, as it turns out, opposite answers. The purpose of this
paper is to separate them, to quantify each, and to explain mechanistically why the
information answer comes out negative — because a negative result that is merely reported is
easy to dismiss, and one that is explained is not.

We emphasise at the outset that this analysis is deliberately algorithm-independent. An
earlier companion analysis ran the same underlying data through the front end of one
particular open-source controller, which raised the objection that any null could be an
artefact of that controller's bucketing and window arithmetic. Everything below uses generic
estimators applied to raw glucose.

## 2. Prior work

### 2.1 The bandwidth of glucose

Gough, Kreutz-Delgado and Bremer characterised the frequency content of blood glucose
directly, from densely sampled blood, and concluded that the continuous portion of the signal
has a band edge near 1×10⁻³ Hz, corresponding to a period of about seventeen minutes [1].
Their practical recommendation was a sampling period of roughly ten minutes, with the
observation that faster sampling captures noise rather than physiology.

Breton, Shields and Kovatchev asked the sharper question of what happens in the interstitial
compartment that a subcutaneous sensor actually measures [2]. Applying Fourier analysis to
continuous monitor traces in type 1 diabetes, they found no patterns of period shorter than
about thirty-six minutes, and concluded that interstitial glucose can be perfectly
characterised with an eighteen-minute sampling period, with fifteen minutes a convenient
practical choice. They also made the point that is most relevant here: because diffusion
between blood and interstitium acts as a low-pass filter, sampling *blood* faster would be
detrimental in a system whose sensing is subcutaneous, since the fast blood dynamics simply
are not present at the sensor.

Fico and colleagues later characterised the spectrum of continuous monitor signals across
type 1 diabetes, type 2 diabetes and people at risk [3]. Their type 1 cohort showed a
dominant peak at a period of about 22.3 hours — the circadian term — with 75 per cent of
power accumulated by a period of roughly 1.4 hours, and a 3 dB bandwidth extending only to
around 4×10⁻⁵ Hz. Their monitors sampled at five minutes, which they noted gives a Nyquist
frequency well above anything in the signal.

Taken together this is a consistent and now fairly old result: glucose, especially as seen
subcutaneously, is a slow signal, and five-minute sampling oversamples it substantially. What
is missing from the literature is any test of whether that conclusion survives on current
one-minute hardware, and any quantification of what, if anything, the extra samples buy.

### 2.2 Sensor error

Vettoretti, Battocchio, Sparacino and Facchinetti developed an error model for a
factory-calibrated ten-day sensor and decomposed the error into three parts: the
blood-to-interstitial kinetics, a calibration term with drift, and a random measurement noise
term [4]. Two of their parameters are directly relevant. The blood-to-interstitial time
constant had a median of 3.8 minutes, with an interquartile range of 2.39 to 5.96 minutes.
The measurement noise had a median standard deviation of 3.19 mg/dL and was explicitly
*not* white — it was best described by a second-order autoregressive process, that is, it is
correlated from sample to sample.

The second point deserves emphasis, because it is the trap in this entire subject. Correlated
noise looks like signal. A record whose consecutive changes agree in sign more often than
chance appears to contain coherent structure, and it is tempting to read that coherence as
real physiology. An autoregressive noise process produces exactly the same appearance. Any
claim that a one-minute record contains fine structure must therefore be tested against a
noise null, not merely against independence.

### 2.3 Sampling interval and derived metrics

Russon and colleagues took five-minute records and decimated them to fifteen minutes [5].
Mean glucose, coefficient of variation and time in range were statistically unchanged.
Detection of glycaemic *episodes*, however, fell sharply: hypoglycaemic episodes by 19.2 per
cent, level-two hypoglycaemic episodes by 27.9 per cent, and hyperglycaemic episodes by 7.5
per cent, all with p < 0.001.

This is the same dissociation this paper reports, observed from the other direction and on a
different cohort. Coarsening the interval leaves the distribution of glucose alone and
degrades the detection of short excursions. If that asymmetry is the governing one, then
refining the interval from five minutes to one should likewise leave the distribution alone
and improve short-excursion detection — and should do nothing else. That is the hypothesis we
test.

### 2.4 What appears not to exist

We searched for a head-to-head evaluation of one-minute against five-minute continuous
glucose sampling, for closed-loop control or for any other downstream task, and did not find
one. Closed-loop trials we reviewed act on five-minute data as a matter of course, without
the interval being treated as a design variable. We do not claim no such study exists; we
found none.

## 3. Data and methods

### 3.1 Data

The record comprises 83,550 consecutive glucose values collected over 66 days from a single
adult with type 1 diabetes wearing a subcutaneous continuous glucose monitor reporting at a
one-minute cadence. Values are reported as integers in mg/dL. Segmenting on gaps, the record
contains 125 contiguous one-minute runs of at least 128 minutes and 177 of at least 61
minutes.

Two checks on data provenance were run before anything else, because the whole analysis is
void if the extra samples are manufactured rather than measured. First, the record is not an
interpolation of a slower feed: reconstructing each withheld minute from the surrounding
five-minute anchors leaves a mean absolute error of 1.11 mg/dL with only 8.6 per cent of
values recovered exactly, against 4.76 mg/dL for a control comparison, whereas a genuinely
interpolated record would reconstruct essentially perfectly. Second, the cadence is stable,
with a median inter-sample interval of 1.00 minutes.

The sensor make and model are not recorded in the data available to us, which is a
limitation we return to in section 7.

### 3.2 Estimators

All estimators are generic. Rate of change is ordinary least squares slope over a stated
causal window, expressed in mg/dL per five minutes so that the two cadences are directly
comparable. Spectra are Welch estimates over Hann-windowed 256-minute segments with 50 per
cent overlap, with each segment mean-removed; note that this removes the circadian and
multi-hour terms, so the spectral fractions we report are fractions of *within-segment*
power, and are not comparable to the whole-record fractions in Fico et al. Prediction uses
linear regression with cross-validation grouped by day, so that no day contributes to both
training and test.

The measurement noise floor is estimated from the second difference of the series. For a
locally quadratic signal contaminated by white noise of variance σ², the second difference
has variance 6σ². This estimator captures only the *white* component of the noise; given
that the published error model is autoregressive, the figure it returns should be read as a
lower bound on total noise, and we treat it as such throughout.

### 3.3 Uncertainty

Glucose is strongly autocorrelated, so per-point confidence intervals are far too narrow. All
intervals are day-level block bootstraps with 4,000 resamples, resampling whole days with
replacement. Each effect carries an explicit verdict: an interval overlapping the null value
is reported as unproven, whatever the point estimate.

## 4. Results

### 4.1 The spectrum: the old result holds on modern hardware

Welch spectra over 386 segments give the following distribution of within-segment power.

| Power at periods shorter than | Fraction of power |
|---|---|
| 60 min | 5.035% |
| 36 min | 2.748% |
| 20 min | 1.339% |
| 10 min | 0.276% |
| 5 min | 0.034% |
| 2 min | 0.000% |

Cumulatively, 95.0 per cent of power lies at periods longer than 51.2 minutes, 99.0 per cent
longer than 17.1 minutes, and 99.9 per cent longer than 7.3 minutes.

This reproduces Breton et al. on a sensor two device generations later. Their claim that
interstitial glucose contains no patterns shorter than about thirty-six minutes is, on this
record, accurate to within 2.7 per cent of power — and as section 4.7 shows, that residual
2.7 per cent is itself consistent with sensor noise rather than glucose. Gough et al.'s
ten-minute recommendation is if anything conservative: 99.7 per cent of power sits at longer
periods than that.

The immediate consequence is arithmetic. If the signal is band-limited near a thirty-six
minute period, the Nyquist sampling interval is eighteen minutes. Five-minute sampling
oversamples by a factor of about 3.6; one-minute sampling by about 18. Oversampling does not
add information. It adds samples.

### 4.2 The noise floor and the quantisation limit

| Quantity | Value |
|---|---|
| Standard deviation of one-minute change | 1.78 mg/dL |
| Minutes with no change at all | 33.9% |
| Minutes with change within ±1 mg/dL | 72.8% |
| White noise component σ (second-difference estimator) | 0.54 mg/dL |
| Uniform quantiser standard deviation (1 mg/dL step) | 0.29 mg/dL |

The sensor reports whole numbers of mg/dL. A change is therefore not resolvable in a single
sample interval unless it exceeds roughly 2σ ≈ 1.1 mg/dL, which at a one-minute cadence
requires a rate of change above about 5.4 mg/dL per five minutes. Most of the glucose record
does not move that fast. A third of all minutes carry literally no new number.

This is the first and simplest reason the extra samples underdeliver. It is not that the
sensor is inaccurate — it is that at one-minute spacing the quantity being measured usually
changes by less than the smallest amount the sensor can express.

### 4.3 Reconstruction: the decisive test

If the signal is band-limited well below the five-minute sampling rate, the four minutes
discarded between each pair of five-minute samples must be recoverable from their neighbours.
We withheld them — 61,672 samples, four of every five minutes — and reconstructed them from
the five-minute subsequence alone.

| Method | RMSE (mg/dL) | MAE (mg/dL) | Within 0.5 mg/dL |
|---|---|---|---|
| Zero-order hold | 4.13 | 2.63 | 19.8% |
| Linear interpolation | 1.65 | 1.09 | 37.9% |
| Cubic spline | **1.56** | 1.05 | 38.4% |
| Whittaker–Shannon (sinc) | 3.54 | 2.03 | 25.1% |
| *White-noise floor (σ)* | *0.54* | | |

A cubic spline through the five-minute samples predicts the withheld minutes to 1.56 mg/dL.
That is 2.9 times the white-noise floor, so the one-minute samples are not *entirely*
redundant — there is a component of about √(1.56² − 0.54²) = 1.46 mg/dL that the five-minute
subsequence does not imply. But its size is the point. A 1.46 mg/dL residual should be
compared with the sensor's own accuracy, which for a modern factory-calibrated device is on
the order of 10 mg/dL, and with the 3.19 mg/dL noise standard deviation in the published
error model [4]. The unique content of one-minute sampling is roughly an order of magnitude
smaller than the uncertainty already present in each reading. Section 4.7 argues that most of
even this residual is noise.

The zero-order hold row deserves separate note, because it is what a consumer of a
five-minute feed actually sees between samples: 4.13 mg/dL of error simply from holding the
last value. Almost all of that is removed by interpolation, which is available to any
five-minute consumer at no cost. The gap between what a five-minute feed *shows* and what a
five-minute feed *implies* is large; the gap between what it implies and what a one-minute
feed shows is small.

The Whittaker–Shannon reconstruction performing worse than a cubic spline is an artefact of
finite support: ideal band-limited reconstruction assumes an infinite sample record, and
truncating it to runs of a few hundred samples produces ringing that costs more than the
theoretical optimality gains. We report it for completeness and draw no inference from it.

### 4.4 Rate of change: one-minute data is worse

Rate of change is the quantity most downstream consumers actually use. We estimated it
causally at each cadence and scored both against a centred twenty-one-minute reference slope,
on a common mask of 11,440 points where all estimators are defined. Note that the reference
is itself computed from the full one-minute record, which if anything favours the one-minute
estimator.

| Causal window | 1-min RMSE | 5-min RMSE | 1-min minus 5-min | Verdict |
|---|---|---|---|---|
| 15 min | 3.717 [3.507, 3.939] | 3.465 [3.273, 3.669] | **+0.252 [+0.220, +0.285]** | distinguishable |
| 30 min | 4.270 [3.991, 4.558] | 3.908 [3.676, 4.152] | **+0.362 [+0.300, +0.433]** | distinguishable |

Using five times as many samples over the same window produces a *worse* slope estimate, and
the confidence intervals are nowhere near zero.

This is counterintuitive only if the noise is white, in which case more samples must help.
It is not white. The published error model is autoregressive of order two [4], and we recover
essentially the same structure on this record in section 4.7. Under strongly correlated
noise, additional samples within a window are close to duplicates: they add very little
independent information about the trend while contributing correlated error that ordinary
least squares, which assumes independence, weights as though it were independent. Integer
quantisation compounds this, since within a short window the dense series is a staircase
whose tread boundaries carry more leverage than the underlying trend warrants.

The practical statement is that anyone estimating rate of change from a one-minute feed
should either decimate first or use an estimator that models the noise correlation. Feeding
the raw one-minute series into a slope calculation designed for five-minute data makes the
answer noisier, not sharper.

### 4.5 Forward prediction

We predicted glucose at three horizons from the current value and three causal slopes,
computed at each cadence, with cross-validation grouped by day.

| Horizon | 5-min features RMSE | 1-min features RMSE | Difference | Verdict |
|---|---|---|---|---|
| +15 min | 13.71 [12.75, 14.77] | 13.65 [12.68, 14.71] | −0.065 [−0.099, −0.032] | distinguishable |
| +30 min | 21.95 [20.38, 23.60] | 21.88 [20.31, 23.53] | −0.068 [−0.112, −0.026] | distinguishable |
| +60 min | 32.11 [29.97, 34.36] | 32.09 [29.95, 34.35] | −0.020 [−0.045, +0.004] | unproven |

The one-minute features win, and at fifteen and thirty minutes the win is statistically
distinguishable from zero. It is also 0.3 per cent of the error. An improvement of 0.068
mg/dL on a prediction that is wrong by 21.95 mg/dL is not a difference any consumer of the
prediction can act on. This is a good illustration of why a confidence interval excluding
zero is necessary but not sufficient: with 83,550 samples, tiny effects become detectable,
and detectability is not importance.

We had previously found the same shape on two more specific tasks: predicting the end of a
carbohydrate rise gave an area under the curve of 0.482 [0.465, 0.500] at one minute against
0.487 [0.456, 0.517] at five, both indistinguishable from the 0.510 of a persistence
baseline; and predicting hypoglycaemia within thirty minutes added 0.001 to an area under the
curve of 0.962.

### 4.6 Aggregate metrics

Decimating the same record and recomputing standard metrics:

| Interval | n | Mean | CV% | TIR% | TING% | <70% | Hypo episodes | L2 hypo | Hyper episodes |
|---|---|---|---|---|---|---|---|---|---|
| 1 min | 83,550 | 125.8 | 31.0 | 86.2 | 70.4 | 3.92 | 66 | 2 | 11 |
| 5 min | 16,710 | 125.8 | 31.1 | 86.2 | 70.4 | 3.95 | 64 | 2 | 12 |
| 15 min | 5,570 | 125.8 | 31.1 | 86.3 | 70.5 | 3.88 | 54 | 2 | 11 |

Against the five-minute reference, one-minute sampling moves mean glucose by −0.01 mg/dL,
time in range by +0.03 percentage points and coefficient of variation by −0.03 percentage
points. Fifteen-minute sampling moves them by −0.03, +0.09 and +0.00 respectively. The
distribution of glucose is, for practical purposes, invariant to the sampling interval across
a fifteen-fold range.

Episode counts behave entirely differently. Coarsening to fifteen minutes loses 15.6 per cent
of hypoglycaemic episodes, closely replicating the 19.2 per cent reported by Russon et al.
[5] on an independent cohort. Refining to one minute gains 3.1 per cent. Episode counts here
are small and the individual percentages are not individually well determined, but the
direction and the asymmetry are clear and externally corroborated: the sampling interval is
irrelevant to the distribution and relevant to the detection of short excursions.

### 4.7 Is the fine structure glucose, or is it noise?

A one-minute record looks coherent. On this record the lag-one autocorrelation of the change
series is +0.724, and during a climb only 15.2 per cent of consecutive non-zero changes
reverse sign, against roughly 50 per cent for an independent series. In earlier work we read
that coherence as evidence of real glucose structure. Given the published finding that sensor
noise is autoregressive [4], that reading was not justified, and testing it properly reverses
it.

We removed a twenty-one-minute centred trend and fitted a second-order autoregressive model
to the residual.

| Quantity | This record | Vettoretti et al. [4], Dexcom G6 |
|---|---|---|
| Residual standard deviation | 3.40 mg/dL | 3.19 mg/dL |
| Model | AR(2), a₁ = +1.532, a₂ = −0.692 | AR(2) |
| Innovation standard deviation | 1.04 mg/dL | — |

The residual magnitude is within 7 per cent of the published noise standard deviation for a
current commercial sensor, and the autoregressive structure is strong. We then compared the
observed power spectrum with the spectrum implied by this noise process, scaling on the band
below a twenty-minute period.

| Period | Observed PSD | AR(2) noise PSD | Ratio |
|---|---|---|---|
| 20 min | 8359.9 | 6429.4 | 1.30 |
| 15 min | 4162.5 | 6060.0 | 0.69 |
| 10 min | 1390.3 | 1506.6 | 0.92 |
| 6 min | 175.3 | 171.4 | 1.02 |
| 4 min | 28.0 | 39.3 | 0.71 |
| 3 min | 10.9 | 16.9 | 0.64 |
| 2 min | 11.1 | 9.2 | 1.20 |

Across a ten-fold range of period the ratios scatter between 0.64 and 1.30 with no systematic
excess. This is a test of spectral *shape*, not level, and it says that the entire
sub-twenty-minute content of the one-minute record can be produced by a correlated noise
process with the published parameters. No glucose dynamics need be invoked to explain any of
it.

This closes the argument opened in section 4.3. The 1.46 mg/dL of variation that the
five-minute subsequence does not imply is not, on this evidence, glucose that a five-minute
sensor misses. It is the sensor's own coloured noise, which is uninformative by construction
and which a consumer would be better off filtering than acting on.

### 4.8 Where one-minute data does win: event timing

Detection latency is usually compared at a fixed threshold, which is not a fair comparison: a
noisier, faster feed will cross any fixed threshold sooner partly by crossing it on noise. We
therefore tuned the threshold separately for each cadence so that both raise the same rate of
false alarms on quiet periods, and only then compared latency and sensitivity.

Events are defined as a fall of at least 20 mg/dL completing within thirty minutes (15,554
event starts); quiet periods are starts whose maximum fall over the same horizon is below 40
per cent of that (45,498 starts).

| False-alarm rate | 1-min: threshold / lag / sensitivity | 5-min: threshold / lag / sensitivity | Latency gain |
|---|---|---|---|
| 2% | 7 mg/dL / 8.0 min / 93% | 7 mg/dL / 15.0 min / 79% | **+7.0 min** |
| 5% | 7 / 8.0 / 93% | 6 / 15.0 / 80% | **+7.0 min** |
| 10% | 6 / 7.0 / 93% | 4 / 10.0 / 81% | **+3.0 min** |
| 20% | 4 / 6.0 / 94% | 2 / 10.0 / 82% | **+4.0 min** |

For a 30 mg/dL fall the gains are +5.0, +6.0, +7.0 and +3.0 minutes at the same false-alarm
rates. Sensitivity is consistently 12 to 14 percentage points higher at one minute, because a
crossing that occurs and partially reverses between two five-minute samples is never seen at
all.

Separately, and using a fixed rather than a matched threshold, a 5 mg/dL fall is seen a
median of 3.0 minutes sooner on 89 per cent of 5,961 sharp falls.

This is a real and reasonably large effect, and it is the only one in the paper. It is worth
being clear about its source. Nothing here contradicts sections 4.1 to 4.7: no new frequency
content has appeared. The gain is that a five-minute consumer must wait for the next grid
point, and the grid costs on average 2.5 minutes and up to 5. The remainder of the observed
gain comes from the sensitivity difference — events that fully or partly resolve inside a
five-minute interval are invisible to the slower feed, which is the same phenomenon Russon et
al. measured as lost episodes at fifteen minutes.

### 4.9 The latency gain is symmetric in direction

The account above makes a falsifiable prediction. If the benefit is a property of the
sampling grid rather than of the glucose signal, it cannot care which way glucose is moving:
a rise should be detected earlier by the same margin as a fall. If instead the benefit came
from some direction-specific feature of the physiology, the two should differ. We ran the
identical matched-false-alarm design on rises.

The two event classes are closely comparable to begin with: 16,242 rise starts against 15,554
fall starts at the 20 mg/dL threshold, with median steepness 1.18 against 1.11 mg/dL per
minute, and median times to reach the threshold of 17 and 18 minutes respectively.

| Excursion | False-alarm rate | Fall: latency gain | Rise: latency gain | 1-min sensitivity, fall / rise |
|---|---|---|---|---|
| 20 mg/dL | 2% | +7.0 min | +6.0 min | 93% / 95% |
| 20 mg/dL | 5% | +7.0 min | +6.0 min | 93% / 95% |
| 20 mg/dL | 10% | +3.0 min | +2.0 min | 93% / 95% |
| 20 mg/dL | 20% | +4.0 min | +4.0 min | 94% / 95% |
| 30 mg/dL | 2% | +5.0 min | +5.0 min | 92% / 95% |
| 30 mg/dL | 5% | +6.0 min | +6.0 min | 93% / 95% |
| 30 mg/dL | 10% | +7.0 min | +7.0 min | 93% / 95% |
| 30 mg/dL | 20% | +3.0 min | +3.0 min | 93% / 95% |

The gains agree to within one minute at every operating point, and are identical at all four
operating points for the larger excursion. Sensitivity at one minute is, if anything, very
slightly higher for rises. The effect is symmetric, which is what a pure grid-delay
explanation requires and what a signal-content explanation would not predict.

This matters for how the result should be used. An earlier analysis of ours examined falls
only and concluded that the benefit of faster sensing accrues to withholding insulin rather
than to delivering it. At the level of the signal that conclusion is not supported: the data
are as informative, as early, about a rise as about a fall. Any asymmetry in how a controller
*acts* on the two is a decision about the asymmetry of clinical risk, not a property of what
the sensor is able to tell it.

## 5. Why the nulls occur

A negative result is worth little unless the mechanism is identified, because otherwise it
cannot be generalised. Here the mechanism is a physical filter with a measured time constant.

Glucose reaches the subcutaneous interstitium from blood by diffusion, which to first order is
a single-pole low-pass filter. Vettoretti et al. estimated the time constant at a median of
3.8 minutes [4]. The attenuation of a sinusoid of period *P* through such a filter is
1/√(1 + (2πτ/P)²).

| Period | Gain | Attenuation | Blood amplitude needed to clear 1 SD of sensor noise |
|---|---|---|---|
| 2 min | 0.083 | −21.6 dB | 38.2 mg/dL |
| 5 min | 0.205 | −13.8 dB | 15.6 mg/dL |
| 10 min | 0.386 | −8.3 dB | 8.3 mg/dL |
| 20 min | 0.642 | −3.8 dB | 5.0 mg/dL |
| 36 min | 0.833 | −1.6 dB | 3.8 mg/dL |
| 60 min | 0.929 | −0.6 dB | 3.4 mg/dL |
| 240 min | 0.995 | −0.0 dB | 3.2 mg/dL |

Four-fifths of the amplitude of any five-minute-period oscillation in blood is destroyed
before the sensor transduces it. For such an oscillation merely to reach one standard
deviation of the sensor's noise, blood glucose would have to swing by ±15.6 mg/dL — a 31
mg/dL peak-to-peak excursion every five minutes — which does not occur physiologically. At a
two-minute period the required amplitude is ±38.2 mg/dL.

This single filter explains every null in section 4 and reconciles all of the prior
literature. It is why Breton et al. found no structure below thirty-six minutes and warned
that faster *blood* sampling would be detrimental to a subcutaneous system [2]. It is why
Gough et al.'s blood band edge at a seventeen-minute period translates to an even slower
requirement once measurement moves to the interstitium [1]. It is why the spectrum in section
4.1 has 0.034 per cent of its power below a five-minute period, and why section 4.7 finds
that what little there is is noise. And it is why refining the sampling interval cannot move
a distributional metric: the underlying process has no energy at the frequencies the extra
samples uniquely resolve.

The filter is a property of physiology and sensor placement, not of any particular device.
Faster electronics cannot recover what diffusion has already removed. The only routes past it
are a different measurement site or a model-based deconvolution of the kinetics, and the
latter amplifies noise in exactly the band where, as shown above, there is nothing but noise.

## 6. Implications

**For information, refining the interval below five minutes is close to worthless.** The
signal is band-limited an order of magnitude below the one-minute Nyquist rate. Withheld
minutes are reconstructible to 1.56 mg/dL, and the part that is not reconstructible matches
the sensor's own noise process. Nothing that depends on the *shape* of the glucose curve —
rate of change, forward prediction, variability, time in range — improves materially, and
rate of change measurably degrades if the raw feed is used without accounting for the noise
correlation.

**For latency, refining the interval is genuinely useful, but only for events.** Removing up
to five minutes of grid delay is worth three to seven minutes on the detection of a
clinically meaningful fall, at matched false-alarm rate and with materially higher
sensitivity. Anyone whose task is a threshold crossing — a hypoglycaemia alarm, a suspend
decision, a safety interlock — should prefer the faster feed. Anyone whose task is an
estimate should not expect a benefit and should check that they have not incurred a cost.

**The benefit does not favour one direction.** Rises are detected earlier by the same margin
as falls, to within a minute at every operating point. A system that uses a faster feed to
catch impending hypoglycaemia sooner is entitled to use it to catch a rise sooner as well;
the signal does not distinguish them. Whether it *should* is a question about the relative
cost of the two errors, not about the data.

**The asymmetry of the literature is now consistent.** Russon et al. coarsened five to
fifteen minutes and found distributional metrics unchanged and episode detection degraded
[5]. We refined five to one and found distributional metrics unchanged and episode detection
improved. Both are the same finding: across at least a fifteen-fold range, the sampling
interval of a continuous glucose monitor is a parameter of event detection and not of signal
content.

## 7. Limitations

The analysis is of a single subject over 66 days. The spectral, noise and reconstruction
results are properties of the sensing chain and we would expect them to generalise, since
they agree closely with published population estimates [2, 4, 5]; the effect sizes for
prediction and detection should be treated as provisional until replicated across subjects.

The sensor make and model are not recorded in the data available to us. Different
manufacturers apply different internal smoothing before reporting, and a heavily smoothed
one-minute feed would show less high-frequency content than a lightly smoothed one. This
would change the noise figures but not the central argument, which rests on the physiological
filter upstream of the electronics.

The white-noise floor of 0.54 mg/dL is a lower bound, since the second-difference estimator
does not capture the coloured component. The correct comparator for the reconstruction
residual is arguably the full 3.40 mg/dL residual of section 4.7, which would make the
one-minute samples entirely redundant; we used the conservative figure.

The detection analysis defines events on the sensor signal itself, so an event that exists
only in the sensor's noise counts as an event. The matched false-alarm construction controls
for this in the comparison between cadences but does not establish clinical significance for
either.

No outcome data is analysed. This paper is about what is in the signal; whether acting on a
three-to-seven-minute earlier fall detection improves glycaemic outcomes is a separate
question requiring a controlled trial.

## 8. Conclusion

There is very little information in one-minute continuous glucose data that five-minute
sampling does not already carry, and the reason is physical rather than statistical. A
first-order diffusion lag with a time constant near four minutes sits between blood and the
sensing site and removes about 80 per cent of the amplitude of anything oscillating at a
five-minute period. What survives is below the sensor's own noise, and that noise is
correlated, so it presents as coherent fine structure that is easily mistaken for signal. On
a 66-day one-minute record, 99.7 per cent of spectral power sits at periods longer than ten
minutes, withheld minutes are recoverable from a five-minute subsequence to 1.56 mg/dL, rate
of change is estimated worse rather than better, forward prediction improves by three-tenths
of one per cent, and standard metrics do not move at all.

The one thing faster sampling reliably delivers is time. A five-minute consumer waits for the
next grid point, and misses excursions that resolve between points. Removing that delay is
worth three to seven minutes on the detection of a 20 mg/dL fall, with sensitivity 93 against
79 per cent, at a matched false-alarm rate — and the same three to seven minutes on a rise of
the same size. That symmetry is the strongest single piece of evidence for the interpretation
offered here, because a grid effect must be direction-blind and an information effect need
not be.

One-minute sampling buys latency, not bandwidth. That single sentence predicts which
downstream tasks will improve and which will not, and every result above is consistent with
it.

## References

1. Gough DA, Kreutz-Delgado K, Bremer TM. Frequency characterization of blood glucose
   dynamics. *Annals of Biomedical Engineering*. 2003;31(1):91–97.
2. Breton MD, Shields DP, Kovatchev BP. Optimum subcutaneous glucose sampling and Fourier
   analysis of continuous glucose monitors. *Journal of Diabetes Science and Technology*.
   2008;2(3):495–500.
3. Fico G, Hernanz L, Cancela J, et al. Exploring the frequency domain of continuous glucose
   monitoring signals to improve characterization of glucose variability and of diabetic
   profiles. *Journal of Diabetes Science and Technology*. 2017;11(4):773–779.
   doi:10.1177/1932296816685717
4. Vettoretti M, Battocchio C, Sparacino G, Facchinetti A. Development of an error model for
   a factory-calibrated continuous glucose monitoring sensor with 10-day lifetime. *Sensors*.
   2019;19(23):5320. doi:10.3390/s19235320
5. Russon CL, Pulsford RM, Allen MJ, et al. Impact of recording interval in continuous
   glucose monitoring on calculating the metrics of glycemic control. *Journal of Diabetes
   Science and Technology*. 2025;19(2):590–592. doi:10.1177/19322968241310892

## Appendix: reproducibility

Analyses are in `backtesting/scripts/2026-07-onemin-cadence/`. The controller-independent
results in this paper are produced by:

- `10_generic_signal_analysis.py` — spectrum, noise floor, rate estimation, prediction,
  fixed-threshold detection latency
- `11_generic_rigour.py` — reconstruction from the five-minute subsequence, rate estimation
  on a common mask, detection latency at matched false-alarm rate
- `12_generic_metrics_and_mechanism.py` — aggregate metrics by interval, interstitial filter
  attenuation, AR(2) noise attribution
- `13_rise_vs_fall_symmetry.py` — the matched-false-alarm detection design run on rises and
  falls side by side

Scripts `01`–`09` are the earlier implementation-specific study and are retained for the
provenance checks and the earlier prediction results cited in section 4.5.
