# Three-way cadence crossover

Three arms, each on its own Nightscout instance, each 15 days with the pump on one of them and
the other two on virtual pumps.

| Tag | Branch | Sensor | What it tests |
|---|---|---|---|
| `x1n` | `v7-shadow-1m-test` | 1 minute | native one-minute sensing end to end |
| `x1s` | `Boost-V7-shadow` | 1 minute | stock Boost handed a one-minute feed |
| `x5s` | `Boost-V7-shadow` | 5 minutes | the reference |

The middle arm is not a duplicate of the reference. On stock V7-shadow a one-minute sensor
already gets a fresh newest value each cycle, because `clone()` does not copy `referenceTime` and
the bucket grid therefore re-anchors, while the history behind it stays on five minutes. So it
isolates "one-minute clock, five-minute memory" from "one-minute throughout".

## Registry entries to add

Add three entries to `~/.config/boost_backtest/sites.json` in the existing shape. Nothing here
should carry a URL or token into the repository.

```json
{ "tag": "x1n", "base": "<instance>", "token": "<token>", "tz_offset_hours": 1,
  "boost": "crossover arm: native 1-min (v7-shadow-1m-test)" }
{ "tag": "x1s", "base": "<instance>", "token": "<token>", "tz_offset_hours": 1,
  "boost": "crossover arm: stock V7-shadow on a 1-min sensor" }
{ "tag": "x5s", "base": "<instance>", "token": "<token>", "tz_offset_hours": 1,
  "boost": "crossover arm: stock V7-shadow on a 5-min sensor" }
```

`refresh_all.py` picks them up with no change, since it iterates the registry and resumes each
site from its own latest row.

## Why the pairing matters

All three arms observe the same person at the same moment, so day-to-day variation is common and
cancels in the difference. That is what makes 15 days sufficient for behavioural endpoints. An
unpaired comparison of period one against period two would need far longer: the earlier power
check found unpaired 15-day windows resolve an AUC difference of only about 0.13, whereas the
paired contrast in the closed-loop replay resolved differences an order of magnitude smaller.

Because of that, the primary analysis is the per-cycle contrast between arms within a period,
NOT the comparison of one period against the next. The period-to-period crossover exists to
check that whichever arm holds the pump behaves as its virtual-pump twin predicted it would.

## Endpoints

Primary, and measurable in 15 days:
- insulin delivered per day, and microbolus count and size
- time from a rise beginning to the first dose
- meal state occupancy, particularly time in CONFIRMED and COMMITTED
- what each arm SAW: readings consumed per cycle, detected cadence, delta and deltaAccl

Secondary, and underpowered on one person for 15 days. Report, do not lead with:
- time in range, time below 70, time above 180

Safety, watched throughout rather than analysed at the end:
- maxIOB and maxSMBBasalMinutes binding frequency, since at a one-minute microbolus interval
  these become the only limiters
- any arm whose delivered insulin diverges from its twin by more than the replay predicted
