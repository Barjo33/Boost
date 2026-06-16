# Boost-ML-Beta-2 — multi-user shadow testbed

This branch is built **on top of the existing `Boost-ML-Beta`** with one purpose: collect
**multi-user shadow data** for the next round of Boost features, while changing as little as
possible about how the loop actually doses.

> **Nothing here is medical advice.** Boost is an experimental AndroidAPS fork. Run it only if you
> already run Boost-ML-Beta and understand the risks.

## What's the same as Boost-ML-Beta

- **The acting engine is unchanged** — plain Boost (V1) still makes every dosing decision.
- **The ML hypo-risk model is unchanged** — the same model ships as on `Boost-ML-Beta`. None of the
  experimental "v12" model work is included here.
- **Out of the box, dosing behaviour is effectively unchanged** — every new feature below is either
  shadow/telemetry-only or defaults to off.

## What changed — and why

### 1. Active-dosing changes — safety only (all *reduce* insulin in hypo-risk situations)

These are the only changes that touch live dosing. Each one withholds insulin when a low is likely;
none make the loop more aggressive.

| Change | What it does |
|---|---|
| **Fix A v2** | Blocks the aggressive UAM boost tiers (T3/T4/T5) when BG was below 75 in the last 45 min — stops the loop dosing *into* a post-rescue rebound. |
| **Fix D** | Gates the "eventual-BG override" on `recentLowBG ≥ 75`, so fast-carb damping isn't lifted during a post-rescue climb. |
| **Fix B — cumulative SMB cap** | Caps total SMB delivered in any rolling 60-minute window. **Default 6.0 U** (range 0–10; 0 disables). This is a high, *non-binding* backstop — real-world maxima are well under it, so you don't need to change anything. Lower it if you want a tighter ceiling. |

### 2. Shadow / telemetry only — **no dosing impact**

These compute and log to Nightscout what a feature *would* do, so it can be validated across users
before it's ever allowed to act.

- **V5 meal engine (shadow)** — V5 stays hidden and never doses here; it logs its observe→confirm→
  commit decisions (`boostV5_*`). Includes the latest V5 logic and the new **fast-carb fast-path**
  (single-cycle confirm on a sharp, accelerating, score-corroborated rise while awake and not
  exercising) — visible only in V5's shadow decision.
- **Activity-load (steps)** — reads Health Connect step data from a single source, learns your
  personal daily-step baseline, and logs what an activity/inactivity ISF modifier *would* do
  (`boostActivityLoad_*`). Never changes dosing. **Requires the Health Connect step permission**
  (see below); silently inert until granted.
- **Autosens / TDD-DynISF coordination** — logs the real Autosens ratio vs the DynISF-curve ratio
  each cycle (`boostAutosens_*`) so the two can be compared. The behaviour switch
  (`ApsBoostAutosensWhenNoTdd`) **defaults OFF**, so dosing is unchanged; it only logs.
- **V6 anticipatory pre-meal target** — learns habitual meal times (from V5 meal commits) and logs
  "V6 pre-meal WOULD apply …". Pure shadow on this build (no UI toggle), so it cannot change dosing.
- **Sleep detection + Health Connect heart-rate ingest** — supporting telemetry.

## Permissions

For the activity-load shadow to collect data, grant **Health Connect → Steps (read)** to AndroidAPS
(Boost settings → Health Connect). Until then it's silently inert — no harm, just no step data.

## New Nightscout fields you'll see

`boostActivityLoad_baselineSteps / lastDaySteps / ratio / wouldDeltaIsfPct / source`,
`boostAutosens_mode / orefRatio / curveRatio / appliedRatio`, plus the existing `boostV5_*`,
`sleep*`, and `hr*` fields. All are observational.

## Explicitly NOT included

- The experimental v12 (53-feature) ML hypo-risk model and its loader changes.
- Any active-dosing change beyond the three hypo-safety fixes above.
- V5 as a selectable/active APS (it remains shadow-only here).
