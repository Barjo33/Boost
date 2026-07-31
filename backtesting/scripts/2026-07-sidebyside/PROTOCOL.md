# Side-by-side cadence study: collection protocol

## Design

Four sensors worn simultaneously for at least 15 days. Two report at one minute and two at
five. The duplicate of each cadence is the point of the design: two sensors that differ only
by unit give the empirical null against which any cadence difference is judged. Without them
a cadence difference cannot be told apart from a sensor difference.

## The one threat that the design cannot remove

If the one-minute sensors and the five-minute sensors are different models, then reporting
rate is confounded with manufacturer, and a difference could be filtering rather than cadence.
Use the same model at both rates if that is possible at all. If it is not, the finding must be
stated as a comparison of two products rather than of two sampling rates, and the same-cadence
pairs become more important rather than less.

## Placement

Balance sites across cadence. One sensor of each cadence on each side of the body is the
minimum. Interstitial glucose genuinely differs between sites, so allowing all the one-minute
sensors to sit on one arm would put site and cadence in the same term.

Record which sensor sat where.

## Running the study

Wear all four for the whole period. If one fails, note the time and continue; the analysis
handles missing arms but loses the null for that cadence while the sensor is absent.

Exclude the first 12 hours after each sensor session begins. Warm-up behaviour is not
representative and the scripts drop it automatically when a `session` column is present.

Avoid calibration mid-study if the sensors allow it. If a calibration is entered, record the
time so those hours can be excluded.

Upload from a single phone where possible so that all timestamps share a clock. If separate
uploaders are unavoidable, record any known clock offset; script 01 reports pairwise bias and
drift, which will reveal a clock problem as an apparent constant offset in time rather than in
glucose.

## Duration

Fifteen days settles the signal questions comfortably. Those consume samples, of which the
period supplies tens of thousands.

Event questions consume events rather than samples. At roughly four meal climbs a day, 15 days
gives about 60. Analysed as a paired contrast the resolution is far better than an unpaired
study of the same length, because the day-to-day variation is common to both arms and cancels,
but 60 events remains thin. If the meal questions matter, 30 days is materially better and
costs one extra sensor session per arm.

## Export format

A single CSV, one row per reading:

```
sensor_id,cadence_min,ts_utc,mgdl,session
A1,1,2026-08-01T09:00:00Z,142,1
```

`ts_utc` may be ISO 8601 or epoch milliseconds. `session` is optional but recommended, and
should increment whenever a sensor is replaced.

## Running the analysis

```
python3 01_qc_and_null.py   data.csv
python3 02_variogram_paired.py data.csv
python3 03_prediction_paired.py data.csv
```

Each writes JSON to `results/`. Run against `fixture_sidebyside.csv` first to confirm the
pipeline works on this machine; that fixture contains a deliberately planted cadence effect,
so all three scripts should report a difference outside the null. Finding nothing on the
fixture means something is broken.
