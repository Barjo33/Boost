---
name: boost-trio-port-complete
description: "Boost→Trio (iOS/Swift) port — COMPLETE & pushed; where it lives, what's done/deferred, how to build/test"
metadata: 
  node_type: memory
  type: project
  originSessionId: 2cafdbf8-0860-4435-96ee-1c01c6a6b5ae
---

The **Boost→Trio port is complete and pushed** (as of 2026-06-24). It is a faithful functional replica of Tim's running AndroidAPS build (`Boost-V6-mealtime-alpha`, worktree `~/StudioProjects/Boost-V6` — see [[boost-running-build-and-v5-overnight]] for the source-of-truth details and the overnight mechanism).

**Cumulative-SMB-cap parity (2026-06-29, commit 724d15cd5 on Boost-in-Trio-v0.1):** Trio previously computed `cumulativeSmbCap60MinU` in auto-config but NEVER enforced it (no anti-stacking safeguard). Now matched to AAPS: pref `boostCumulativeSmbCap60Min` (+ `…UserSet` flag; default permissive 10U, auto-config lowers per-user via `APSManager.maybeAutoConfigureBoostV5`); enforced in `OpenAPSSwift` determineBasal override (port of `OpenAPSBoostPlugin.kt:1262` — when cap>0 && rolling-60min SMB volume `recentSmb60` >= cap → `det.units=0`, reason "V6 suppressed (cumulative SMB cap X/Y reached)"); slider in Advanced Boost settings. Both forks now enforce it. (AAPS side this session: surfaced the same cap in V6 Safety settings + raised bound to 0–10 + permissive default — see [[boost-v6-audit-2026-06-28]].) Xcode build (iPhone 17 sim) green.

**Location & branch:** repo `~/StudioProjects/Trio`; branch **`Boost-in-Trio-v0.1`** pushed to `origin` = `github.com/tim2000s/Trio` (Tim's fork). `upstream` = nightscout/Trio — **NEVER push there**. origin's fetch refspec was widened to `+refs/heads/*:refs/remotes/origin/*` so all branches track cleanly. (Pre-existing `TidepoolService` submodule shows ` m` in status — not ours, leave it.)

**Architecture:** `BoostPort/BoostV5Core/` = pure SwiftPM package (no Trio/HealthKit deps, unit-tested, compiled INTO the Trio target by file-reference — new core files need 4 pbxproj entries each). Glue in `Trio/Sources/APS/OpenAPSSwift/Boost/` (BoostISF, BoostV5Adapter, BoostMLModels) + the second pass in `OpenAPSSwift.determineBasal`. HealthKit feed: `Trio/Sources/Services/HealthKit/BoostActivityMonitor.swift`.

**Modes:** off / shadow / active (`BoostMode` in BoostV5Store.swift). **Shadow-safety contract (verified):** off/shadow byte-identical to stock Trio; every dosing change gated on `== .active`.

**Ported & verified faithful:** DynISF V1 + future_sens; V5 engine (state machine/score/budget/Phase-3 gates/multipliers); **v12 ML hypo model** (53-feature + 6-cycle ring buffer, BoostMlFeatureBuilder + BoostMlRingBufferStore, model byte-identical) + 8-feature meal model; drought sleep detector + V5 override sleep-gate (`microBolusAllowed && !asleep`); night mode (incl. PRE_SLEEP); SleepHistoryTracker (learned night window + resting HR); the what-if `simulation` flag that skips the V5 pass so previews don't corrupt state.

**Deferred (Tim's call):** ActivityLoadTracker telemetry (ported but UNWIRED — shadow-only, zero dosing; this is the festival activity-load logging); exercise/post-exercise dose modifiers (inputs collected, not fed to dosing). **Confirmed disabled in Tim's build → not ported:** boost time-window, tuned sleep timings (≠60/10/5), Use-TDD+AdjustSensitivity, TT-sensitivity; flat-CGM gate is Libre1-only → inert.

**Records on the branch:** `BOOST.md` (overview/safety), `BoostPort/docs/AUDIT.md`, `BoostPort/docs/TESTS.md`. **Build/test:** `cd BoostPort/BoostV5Core && swift test` (193 tests, 0 failures); app via `xcodebuild -scheme Trio` iOS Simulator `CODE_SIGNING_ALLOWED=NO` (exit 0). Build constraints (from prior work): NEVER `xcodebuild -resolvePackageDependencies`; TidepoolKit pinned. Commit msgs end `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. SourceKit "No such module"/"Cannot find type" diagnostics are false positives (whole-module compiled only by xcodebuild).
