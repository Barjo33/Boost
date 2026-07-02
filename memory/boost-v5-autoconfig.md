---
name: boost-v5-autoconfig
description: "Boost V5 auto-configuration — derives V5 knobs from a user's prior (oref/V1) dosing history on first activation; Android + Trio"
metadata: 
  node_type: memory
  type: project
  originSessionId: 2cafdbf8-0860-4435-96ee-1c01c6a6b5ae
---

**Boost V5 auto-config** — on first switch to Boost V5 active, seed the V5 knobs from the user's own
last-14-day prior dosing + glycaemia (works from standard oref, not just Boost-V1). Suggestion-only
(writes a knob only if still at factory default), one-shot (a done-flag), error-swallowing (never
blocks the dose path), needs ≥7d/≥1500 readings (else retries later).

**Shared pure calculator** `BoostV5AutoConfig` (Kotlin: `plugins/aps/.../openAPSBoostV5/`; Swift:
`BoostPort/BoostV5Core/Sources/BoostV5Core/`). Maps history → knobs:
- HypoCaution from TBR<70/<54 vs targets (4%/1%); Aggression neutral, eased to 0.85/0.92 for
  hypo-prone, NEVER auto-raised; Confirmed cap = clamp(max(p90 manual, p95 smb),1.5,7.5); Committed
  cap = clamp(max(p75 smb, tddMedian/40),0.25,2.5); **CumulativeSmbCap60Min = clamp(confirmed+2×committed,1,5)**
  (added after validation); maxIOB/bolus carried from existing limits; fastCarbConfirm off if hypo-prone.

**Wiring:** Android `OpenAPSBoostV5Plugin.maybeAutoConfigure()` (in invoke(), real `TddCalculator`
TDD, writes the DoubleKeys incl. ApsBoostCumulativeSmbCap60Min, flag `ApsBoostV5AutoConfigDone`).
Trio `APSManager.maybeAutoConfigureBoostV5()` (in determineBasal(); `TDDStorage.calculateTDD` over 14d
with 5–200 U/day sanity guard + bolus-only fallback; flag `Preferences.boostV5AutoConfigDone`).
**Trio has NO cumulative-SMB-cap engine knob** → the calculator derives it but Trio doesn't write it
(adding the enforcement engine was out of scope).

**Validation (2026-06-26):** two background agents ran the auto-config knobs through the Python V5
harness (`boost_v5_harness.py`) over **12 real oref users** (oref_v5 Trio + oref_v6 AAPS cohorts in
the TimescaleDB). Verdict: **NO dangerous dosing** — dose-into-low ≤0.2% (blocked by the hard
minGuardBG≥80 gate), neutral config for well-controlled users, protective knobs reduce lows 15–24%
for hypo-prone. Two real improvements found → applied (cumulative cap; true TDD on Trio). The agents'
"CommittedCap inversion bug" was a TEST artifact: the research tables have sug_TDD = 100% NULL so the
agent improvised a `2×SMB` fallback that inflated TDD — production never does this (Tim caught it).
The scary open-loop totals (replay has no glucose feedback) are NOT real dosing.

**Build/verify:** Android Kotlin tests + KSP/Dagger OK. Trio: `BoostV5Core` swift-tests pass AND the
full **Trio Xcode build succeeds** (`xcodebuild -workspace Trio.xcworkspace -scheme Trio -destination
'platform=iOS Simulator,name=iPhone 17' build CODE_SIGNING_ALLOWED=NO`). Gotcha: BoostV5Core is
compiled INTO the Trio target by file-reference (NOT a separate module) — do NOT `import BoostV5Core`
(it won't resolve); new BoostV5Core source files need 4 pbxproj entries (build-file, file-ref, group,
sources-phase) mirroring MealHypothesis.swift.

**Pushed:** Android `Boost-V6-experimental` (`3717d5f0ef`, tim2000s/Boost-in-AAPS_3.4); Trio
`Boost-in-Trio-v0.1` (`50b137b83`, tim2000s/Trio). NOT compile-run on a real device yet.

Related: [[boost-trio-port-complete]], [[boost-running-build-and-v5-overnight]], [[boost-v5-idle-fastpath-fix]].
