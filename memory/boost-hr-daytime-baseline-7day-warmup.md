---
name: boost-hr-daytime-baseline-7day-warmup
description: hrLearnedDaytimeBpm needs 7 completed sleep sessions (~7 nights) of HR data before it populates; shows default 70 until then — NOT a wiring bug.
metadata:
  node_type: memory
  type: reference
  originSessionId: e3138941-0dfd-4662-b6e9-831ed2e3863a
---
**Boost V6 wear-HR learned daytime baseline = a 7-DAY warmup, not a bug.** When `hrLearnedDaytimeBpm` reads the default **70** in NS, do NOT flag it as a wiring/learning fault — it's expected until ~7 nights of HR have accrued.

Mechanism (`plugins/aps/.../openAPSBoost/SleepHistoryTracker.kt`, verified 2026-06-28):
- `MIN_SESSIONS_FOR_LEARNED = 7`. `daytimeHrBpm = if (daytimeHrSamples.size >= 7) median(daytimeHrSamples) else null`.
- A "session" closes once per night at wake (`onWake`), carrying that day's `daytimeHrP10`. So it needs **7 completed sleep sessions ≈ 7 nights**.
- Second gate: each session's `daytimeHrP10` needs **≥30 valid daytime HR samples** (`p10(..., minSamples = 30)`) or that day contributes null.
- While null, `OpenAPSBoostPlugin` line ~561 falls back `hrLearnedDaytimeBpmCached ?: hrRestingBpm` → the `ApsBoostHrRestingBpm` preference default (**70**). Same 7-session gate governs `restingHrBpm`, `wakeMinAvg`, etc.

**This session's context:** wear HR only began flowing **13:48 BST 2026-06-27** (see [[boost-v6-experimental-state-2026-06-27]] wear-HR fix). So qualifying sessions ≈ 0 as of 2026-06-28. Expect `hrLearnedDaytimeBpm` to stay 70 until **~2026-07-04** (7 clean nights), then jump to the learned median (likely ~mid-70s; observed `hrBpmAvg15m` ≈ 77). Only investigate if still 70 after ~a week of continuous HR.

Related: [[boost_wear_hr_steps_2026-06-24]], [[boost_sleep_hr_learned_2026-06-13]].
