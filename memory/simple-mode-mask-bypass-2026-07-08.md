---
name: simple-mode-mask-bypass-2026-07-08
description: "Simple-Mode masking of Boost dosing settings — FIXED + VERIFIED per-setting on experimental. GeneralSimpleMode masks any defaultedBySM key to factory default (the user H maxIOB→1.0 bug). Fix = read raw via getBoostDosing (or getIfExists). Audited 2026-07-08: all 66 live-path defaultedBySM Boost keys bypass the mask; 0 live masked reads. Retired V2/V3/V3ML/V3MLG3 plugins left masked (dead/unselectable — Tim's call)."
metadata: 
  node_type: memory
  type: project
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**Simple-Mode Boost-settings masking — FIXED and per-setting VERIFIED (experimental, 2026-07-08).**

**Mechanism:** `PreferencesImpl` — when GeneralSimpleMode is ON, any key with `defaultedBySM = true` is returned as its FACTORY DEFAULT, not the stored value. All ~71 Boost knobs are `defaultedBySM`. This silently masked V6 dosing settings (the user H/user-H maxIOB→1.0 and committedCap→0.5 symptom). Intermittent in the field (Simple Mode toggling), so a robust fix must read raw regardless of WHY the mask is active.

**Fix:** `plugins/aps/.../BoostDosingPreferences.kt` — `fun Preferences.getBoostDosing(key) = getIfExists(key) ?: key.defaultValue` (5 overloads: Double/Boolean/Int/UnitDouble/String). `getIfExists` bypasses the SM mask; bit-identical to `get()` when not masked. The doser reads every dose-path key via `getBoostDosing` (or `getIfExists` for the migration flag). Commits: `4dba4d534b` (delivered-dose path) + `1629141b38` (2026-07-08: shadow + HC-ingest completion).

**VERIFIED per-setting 2026-07-08 (key-by-key audit, not asserted):** 71 defaultedBySM Boost keys →
- **65 read raw via `getBoostDosing`** in the live V6 doser (OpenAPSBoostPlugin / OpenAPSBoostV5Plugin / V5StateStore / BoostIsfShadow / HealthConnect*Ingest). Covers ALL dose sizing (MaxIob, V5 caps/aggression/hypoCaution/sensitivity/composedFloor/fastCarb, cumulativeCap, bolus, insulinReqPct, scale), DynISF, night-mode+sleep (12 keys), HR/steps/activity/exercise (incl. the 5 postExercise* + HC keys), V6 pre-meal, and all state blobs (V5State, SleepState/History, MealTimeHistory, MlRingBuffer, DailyStepHistory, IntradayStepBank, IsfShadowState).
- **1 (`ApsBoostV5AutoConfigDone`)** read raw via `getIfExists` — safe.
- **0 STILL MASKED in any live path.**
- 6 not read in live doser: `V5ActiveDosing` + `BypassVersionCheck` = dead/UI-only (no read anywhere); `AutoConfigDone` = getIfExists; `AllowAllBgSources`/`StartTime`/`EndTime` = masked `preferences.get` but ONLY in retired plugins.

**Today's completion caught two real gaps:** (1) `ApsBoostHealthConnectHrEnabled` is `defaultedBySM` + default **FALSE** → Simple Mode was masking a user's ENABLED HC-HR back OFF = silent HR-ingest starvation (another overnight-HR-loss vector); (2) the ISF/activity shadow state blobs (mask would wipe them).

**Retired plugins (V2/V3/V3ML/V3MLG3) LEFT masked — Tim's call 2026-07-08.** They're `.showInList { false }` (not selectable → dead code), so their `preferences.get(ApsBoost*)` reads never execute. If a legacy plugin is ever re-enabled, it would need the same getBoostDosing treatment.

Related: [[user-h-diagnosis-2026-07-05]] (masking symptom found here), [[boost-v6-audit-2026-06-28]] (allowAllBgSources/bypassVersionCheck = deliberate forced-flags).
