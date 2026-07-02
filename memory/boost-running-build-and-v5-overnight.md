---
name: boost-running-build-and-v5-overnight
description: "Tim's actual running Boost build + how V5 dosing manages overnight/night mode (sleep-gate, not a clock window)"
metadata: 
  node_type: memory
  type: project
  originSessionId: 2cafdbf8-0860-4435-96ee-1c01c6a6b5ae
---

Tim's CURRENTLY RUNNING build (as of 2026-06-24) is branch **`Boost-V6-mealtime-alpha`**, worktree `/Users/timstreet/StudioProjects/Boost-V6` (repo `Boost-AAPS-core`). It is a **V5-DOSING** build (V5 overrides the SMB), plus V6 meal-timing + HR + phone/Wear (HealthConnect) step sources. This is the source-of-truth for the Trio port — NOT `~/StudioProjects/AndroidAPS` (that repo has V5 in shadow, `runShadow` never writes `rT.units`).

**How V5 manages overnight / night mode** (`OpenAPSBoostPlugin.kt` ~1218, 1235-1245; identical in `Boost-V5-active`):
- **Sleep gate on the V5 override:** `if (v5Active && microBolusAllowed && v5decision != null && !v5Asleep) { it.units = v5decision.finalDose }`, where `v5Asleep = sleepStateCached.state == SleepStateDetector.SleepState.SLEEPING`. When asleep, V5 does NOT override — V1's SMB stands (V5 still logs shadow telemetry).
- **V1 fallback respects night mode:** `isNightModeActive()` → `isSMBModeEnabled` constraint false → V1 SMB suppressed (clock window + BG offset). So overnight = sleep-state suppresses V5 → V1 takes over → night mode suppresses V1's SMB.
- **There is NO clock-based "boost window" gating V5.** `boost_start_time`/`boost_end_time` exist only in V1 and shape `baseInsulinReq` (future_sens conditions 1&2 + tiers); V5 inherits that through the number. `DetermineBasalBoostV5`/`SafetyGates` contain zero window/night/sleep logic. (See `V1_VS_V5.md:243`.)

**Why this matters / how to apply:** I (Claude) wrongly invented a clock-based boost time-window that gated the Trio V5 SMB override (Trio commit `7a24410`, reverted `dfa874a`), based on the shadow-V5 AndroidAPS repo. WRONG mechanism. Faithful fix shipped in Trio commit `61ae3c3`: gate the V5 override on `microBolusAllowed && !asleep`. Tim's instruction: do NOT change critical Trio dosing pieces without consulting him and checking the Boost-V6 branch first. Related: [[activity-mealclimb-override-never-landed]].

**RE-BASELINE 2026-06-24 — 3 CRITICAL Trio gaps vs Boost-V6 (old audits missed these; they used the wrong repo):**
1. **Stale ML hypo model.** Trio ships v9 8-feature/50-tree `hypo_risk_model.json`; running build uses **v12 53-feature/100-tree** (17 static + 36 lag features) via `BoostMlFeatureBuilder.kt` + a 6-cycle ring buffer persisted in `ApsBoostMlRingBuffer`. Trio has no feature builder, no ring buffer, no v12 model → every `mlHypoRisk` is wrong → wrong budget damping/hypo-caution. (Meal model IS byte-identical/fine.)
2. **Stale SleepStateDetector.** Trio = old 288-line gen (HR-only; can't reach SLEEPING without live HR). Running build = 374-line **drought-based** detector (enters SLEEPING on a 30-min HR drought + low steps, + transmission-resume wake). On HealthKit (sparse overnight HR) Trio likely NEVER marks asleep → the `!asleep` V5 gate (61ae3c3) never fires → V5 doses SMBs all night. Fix needs an input-contract change (per-sample HR timestamps, not just avgHr).
3. **Missing flatBGsDetected/sensorQualityOk gate.** Running build trims V5 SMB ×0.7 on flat/suspect CGM; Trio doses full.
V5 ENGINE CORE (state machine/score/budget/gates/multiplier) + DynISF formulas + meal model + Karvonen + step windows + night-mode gates verified faithful.

**ALL THREE CRITICALS RESOLVED 2026-06-24** (Trio `boost` branch, each built + tested, ~193 core tests):
- v12 ML ported (`b7417eb`): BoostMlFeatureBuilder (53-feat) + 6-cycle ring buffer + BoostMlRingBufferStore; v12 model shipped; recentSmb60/timeSinceLastSmb from pump history; 3dp rounding.
- Drought sleep detector ported (`e4a53af`): raw HR readings, drought + transmission-resume; +sleep-gate on V5 override (`61ae3c3`).
- flat-CGM gate: Libre1-ONLY in AAPS; Trio glucose carries no sensor type → `sensorQualityOk=true` already matches for non-Libre1. Inert/equivalent (revisit only if Tim runs Libre 1).
Also: night-mode PRE_SLEEP (`05adcda`); SleepHistoryTracker learned night-window/resting-HR (`02a6c08`); AUDIT FIX (`7d72d48`) — APSManager now drives BoostActivityMonitor.refresh() every loop (was HK-callback-only → overnight staleness silently disengaged suppression — the key catch), + shadow enableSmbPreChecks + decode robustness.

**Tim confirmed 2026-06-24 his standard Boost has ALL of these DISABLED**, so Trio matches at defaults with no further port: boost time-window (boost_start/end), tuned sleep timings (preSleepLead/sleepHyst/wakeHyst ≠ 60/10/5), Use-TDD+AdjustSensitivity, TT-sensitivity (high/low-TT raises/lowers sens). ActivityLoadTracker stays DEFERRED (Tim's call; shadow-only telemetry, zero dosing). Off/shadow byte-identical contract verified PASS. Port is a verified faithful functional replica of Boost-V6-mealtime-alpha. NOT pushed — awaiting Tim's go-ahead.
