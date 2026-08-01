# Native one-minute sensing: implementation plan and crossover trial

Draft for review. Nothing here is built yet.

## 1. What actually happens today

This is the part that changes the scope, so it comes first.

`AutosensDataStoreObject.isAbout5minData()` measures the spacing of the raw readings and
tolerates 30 seconds of irregularity. A one-minute feed gives a 60 second spacing, which fails
that test, so a one-minute sensor already takes the `createBucketedDataRecalculated()` path
rather than the five-minute one.

That path does the following:

```kotlin
var currentTime = bgReadings[0].timestamp
val adjustedTime = adjustToReferenceTime(currentTime)
...
while (true) {
    // linear interpolation between the bracketing readings
    currentTime -= T.mins(5).msecs()
}
```

It steps backwards in fixed five-minute increments and linearly interpolates between whichever
readings bracket each step. On a one-minute feed that means four of every five readings are
discarded and replaced by an interpolation of their neighbours.

Two consequences follow, and they explain the behaviour measured in the field earlier this week.

The newest value is fresh at one-minute resolution, because `clone()` does not copy
`referenceTime` and the grid therefore re-anchors to the newest reading on every cycle. That is
why `suggested.bg` changes every 1.1 minutes for the one-minute user and every 5.0 minutes for
everyone else.

The history is not. Everything behind the newest point sits on a five-minute grid. So the deltas,
the slopes and anything else derived from history are computed over five-minute spacing even
today.

So the loop currently has a one-minute clock and a five-minute memory. Making it use one-minute
data properly means changing the memory, and that is a larger change than the current behaviour
suggests.

## 2. The blast radius, and a narrower way through

Bucket spacing is not only a resolution setting. A large amount of downstream arithmetic is
defined per bucket and implicitly per five minutes: autosens deviations, carbohydrate impact
through `min_5m_carbimpact`, the deviation arrays oref consumes, and the COB decay. Changing the
bucket interval to one minute without touching those silently rescales every one of them by a
factor of five.

That gives two options.

**Option A, global.** Bucket at the detected sensor cadence and renormalise every per-bucket rate
that depends on the interval. Correct in principle. It touches autosens, COB and the oref
sensitivity chain, and a mistake anywhere in it is a dosing error rather than a logging error.

**Option B, dual view.** Keep the existing five-minute bucketed series for autosens, COB and the
oref sensitivity chain, and add a parallel series at the sensor's native cadence which only the
dosing front end consumes. Deltas, the smoother and the meal state machine read the native
series. Sensitivity keeps its five-minute basis.

Option B is what I would build for this trial. It isolates the thing under test, keeps the
change reviewable, and does not put the sensitivity chain at risk to answer a question about
sampling rate. It is also honest about what we expect to find, which is covered in section 6.

## 3. Prerequisite: windows that count samples rather than minutes

Any window expressed as a count of cycles or readings silently changes duration by a factor of
five on a one-minute feed. Two have already been fixed on `Boost-V7-shadow`: the smoother's
compression baseline is now 25 minutes with a reading count retained only as a buffer cap, and
the meal state machine ages on a four-minute wall-clock tick.

The following remain, and should be converted before the trial rather than during it:

| Location | Constant | At 5 min | At 1 min |
|---|---|---|---|
| `MealHypothesis` | `CONFIRM_MIN_OBSERVING_AGE = 2` | 10 min | 2 min |
| `MealHypothesis` | `FALL_BACK_TO_IDLE_AGE = 2` | 10 min | 2 min |
| `MealHypothesis` | `RECOVERING_REENGAGE_MIN_AGE = 1` | 5 min | 1 min |
| `DetermineBasalBoostV5` | `scoreReadyStreak`, previous cycle | 5 min | 1 min |
| `MealSignalScore` | `ML_MEAL_RENORMALIZE_AFTER_CYCLES = 3` | 15 min | 3 min |
| `SleepStateDetector` | `WAKE_HR_SUSTAIN_CYCLES = 2` | 10 min | 2 min |
| `BoostV5AutoConfig` | `MIN_BG_READINGS = 1500` | ~7 days | ~1 day |
| `V7ResidualTracker` | `CYCLE_BUCKET_MS = 300_000` | 1 per cycle | discards 4 of 5 |
| `TwinShadow` / `TwinModel` | `forecast(6/12)`, `TWIN_SUBSTEPS = 5` | 30/60 min | 6/12 min |

One of these must not simply be retimed. `BoostMlFeatureBuilder.LOOKBACK = 6` builds lag features
for a model trained on five-minute lags. On a one-minute feed the same six lags span six minutes
rather than thirty, and the model is then being asked to extrapolate from inputs unlike anything
it saw in training. The correct treatment is to resample its input to five-minute spacing and
leave the lag count alone.

## 4. Implementation, in order

**Step 1. Detect cadence properly.** Replace the binary `isAbout5minData` with a cadence estimate
from the median inter-sample gap, classifying the feed as one-minute, five-minute or irregular,
and expose it. Keep the existing boolean as a derived value so nothing downstream breaks.

**Step 2. Add the native series.** Add `bucketedDataNative` alongside `bucketedData`, built by the
same interpolation routine with the step size taken from the detected cadence rather than the
hardcoded `T.mins(5)`. Leave `bucketedData` exactly as it is.

**Step 3. Decide the anchoring deliberately.** The re-anchoring caused by `clone()` not copying
`referenceTime` is currently load-bearing and undocumented. Whatever we decide, it should be a
decision rather than an accident, with a regression test pinning it. Note that a persistent grid
on a one-minute feed would collapse the day to 288 fixed buckets and re-evaluate identical
glucose on four cycles in five, which is worse than today.

**Step 4. Route the front end.** Point `DeltaCalculator` at the native series. It already works in
elapsed time rather than sample counts, so it needs the list and nothing else. Point the smoother
at the native series. Leave autosens, COB and the sensitivity chain on the five-minute series.

**Step 5. Instrument for readability.** Log the detected cadence, the bucket interval actually
used and the count of readings consumed per cycle, into the reason string and the extractor. The
trial is unreadable without this, because otherwise we cannot tell a behavioural difference from
a configuration difference.

## 5. The trial

**Design.** Two phones, two sensors, both running V7-shadow. One month with the five-minute
sensor driving delivery and the one-minute sensor on the second phone in shadow, then one month
with those roles reversed.

**What the shadow arm is for.** It is not a spare. It gives a paired comparison at every cycle:
both arms see the same person at the same moment, so day-to-day variation is common to both and
cancels in the difference. That is far more powerful than comparing month one against month two,
and it is what makes a one-month period sufficient for behavioural endpoints. The earlier power
check found that unpaired fifteen-day windows resolve an AUC difference of only about 0.13,
whereas the paired contrast in the closed-loop replay resolved differences an order of magnitude
smaller.

**Endpoints, in order of what the design can actually support.**

Primary should be behavioural rather than glycaemic: insulin delivered per day, microbolus count
and size, time from a rise beginning to the first dose, and meal state occupancy. These are
measurable within a month and are where the replay predicts a difference.

Secondary, and underpowered on one person for a month: time in range, time below 70, time
above 180. Report them, do not lead with them.

Diagnostic throughout: the count of readings consumed per cycle, the detected cadence, and any
divergence between the two arms in what they saw rather than what they decided.

**Confounds to state up front.** The two arms are different devices as well as different rates,
so a difference is attributable to the pairing rather than to cadence alone. Period effects and
carryover both apply to the crossover. Sensor site differs. None of these is fatal, but the
write-up should not claim more than the design supports.

## 6. What the existing evidence says to expect

This should be said plainly before the work is committed to, because it sets the value of the
trial.

Measured on real records from both cadences on the same person, the two feeds carry the same
information about glucose. Their variograms differ by a single scale factor across every lag
both can see, the log-log slopes agree to two decimal places, and no new regime appears below
five minutes. Prediction of glucose, of hypoglycaemia and of hyperglycaemia showed no advantage
to the faster feed once base rates were accounted for.

What the faster feed does deliver is about two minutes less reporting delay. In closed-loop
replay the engine translated that into roughly eight per cent more insulin per day at natural
microbolus intervals, with a confidence interval spanning zero.

So the expected result is no outcome difference, slightly more insulin and slightly earlier
detection. The trial is worth running for three reasons that do not depend on finding a benefit:
it tests the dosing behaviour on real delivery rather than in replay, it exercises the
native-cadence path before anyone else uses it, and it would detect the failure modes that
replay cannot show. It should not be run in the expectation of a glycaemic gain, and the
protocol should say so, so that a null is reported as a result rather than as a disappointment.

## 7. Open questions for you

Whether Option B is acceptable, or whether you want the global change with the sensitivity
chain renormalised.

Whether both phones can upload to distinguishable Nightscout targets, since the extractor
currently has no device identifier and the two arms would otherwise be indistinguishable in the
database.

Whether the one-minute arm should run with the microbolus interval left at its default of three
minutes, which is what a user switching sensors would experience, or matched to the five-minute
arm. The replay showed this single setting accounts for most of the difference in delivered
insulin, so it is the most consequential choice in the protocol.
