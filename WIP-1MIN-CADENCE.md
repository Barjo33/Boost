# WORK IN PROGRESS — do not treat this branch as released

This branch (`V6-experimental-1min`) exists so that the one-minute-CGM cadence work is
available if it is needed. It is **not** part of the normal release line and has **not**
been through the project's evidence bar.

## What is on it, over `Boost-V6-experimental`

Two code changes and the analysis that produced them.

**1. UKF compression baseline expressed in time rather than samples.** Sensing only, no
effect on dosing. `compressionWindow` counted five readings while its own comment claimed
"~25 min", which is true at a five-minute cadence and is five minutes at a one-minute one.
Measured on 83,550 readings of real one-minute glucose, the shipped rule fired 10 times
against 636 for the same glucose with a genuine 25-minute window, so the sensor-artefact
damper was roughly 98% suppressed for that user. This is a safety feature, and one-minute
feeds are where fast falls are seen soonest, so the cadence that would benefit most had it
effectively switched off.

**2. Meal-state machine ages advanced on wall clock rather than per loop invocation.**
*This one changes dosing.* The state machine's ages are counts of invocations, and the
thresholds were calibrated against a five-minute loop. Measured on live data, the interval
from entering the observing state to reaching the age threshold is 10.0 minutes for every
five-minute user and 2.0 minutes for the one-minute user. The change restores the intended
timing at any cadence.

## Why it is marked WIP

Change 2 alters dosing behaviour. It is inert for five-minute users, whose loop cycles
already clear the four-minute tick, and it makes a one-minute user materially slower to
confirm a meal. That is a **return to the timing the constants were calibrated for**, not a
new lever, but it has not been through the two-test bar or a pre-registered within-user
trial, and at the time of writing exactly one user in the cohort runs a one-minute sensor,
with too little history to trial it against.

Change 1 is sensing-only and carries no such caveat.

## Read this before building on it

There is an undocumented behaviour that the whole cadence question turns on.
`AutosensDataStoreObject.clone()` copies the readings, the autosens table and the bucketed
data, but not `referenceTime`, and the object is cloned on every autosens calculation. The
five-minute bucket grid therefore re-anchors to the newest reading every cycle. The comment
in that file states the opposite intent.

That accident is the only reason one-minute data is useful at all. With a persistent grid a
one-minute user's readings would collapse into 288 fixed buckets a day and four cycles in
five would re-evaluate glucose the algorithm had already seen. **Anyone repairing `clone()`
to match its documented intent will silently degrade every one-minute user**, and it will
not show up in any existing test.

## Evidence

- `backtesting/scripts/2026-07-onemin-cadence/` — scripts 01 to 09, including a front end
  verified byte-identical against the shipped implementation, and a live-build invariant
  (`median_bg_change_gap == 5.00` for five-minute users) that is a cheap regression test on
  the bucketer.
- `backtesting/reports/2026-07_onemin_cadence_preprint.md` — the write-up, including the
  results that came out negative: one-minute data carries no information about when a rise
  will end, and adds nothing to hypoglycaemia prediction. Its one measured benefit is seeing
  a fast fall about three minutes sooner.

## Status

Not merged, not released, not scheduled. Nothing here should reach a pump without the
prospective evaluation described above.
