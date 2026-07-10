---
name: boost-v5-idle-fastpath-fix
description: "V5 cold-IDLE fast-path — REVERTED 2026-06-26 (full-cohort data didn't support it); kept as-is"
metadata: 
  node_type: memory
  type: project
  originSessionId: 2cafdbf8-0860-4435-96ee-1c01c6a6b5ae
---

## ⚠️ OUTCOME: REVERTED — cold-IDLE fast-path kept AS-IS (no change shipped)
The "fix" below was **reverted the same day**. The original call rested on a **stale/partial replay
cache** showing IDLE→CONFIRMED firing once. A fresh full-cohort pull (7 sites, after loading E/F into
`sites.json`) showed it fires **59×/30d** (11 for tim), catches real fast carbs at **61%** (≈ OBSERVING's
59%); removing it loses ~89% of those (only 11% re-confirm at +1 cycle). A forward-sim counterfactual
(`boost_v5_forward_sim.py`) then showed the real harm is tiny: 4/5 of tim's "undershoots" were 0 U
safety-gated confirms *during* pre-existing lows, not caused by the cold dose — only the 07:08 event was
a genuine over-treatment (1/30d, mild, self-resolving). A dose-conservative prototype (factor 0.6) was
built + replay-validated + **discarded** (fixed the 1 case but cost up to +18 mg/dL coverage on real
in-range fast carbs). **Decision: leave cold-IDLE alone, monitor.** Reverts: AAPS `2e5468b9c0`
(git revert); Trio reset to `56ccad6aa` + **force-pushed** (gone from GitHub). Analysis in `backtesting/`:
`idle_fastpath_analysis.py`, `cold_idle_dose_validation.py`, `IDLE_FASTPATH_REPORT.md`.
**APK:** use `Boost-V6-PHONE-TIRpie-2026-06-26.apk` (07:35, before the bad commit) — NOT `…IDLEfastpathfix…`.
**Lesson:** don't trust a backtest cache without confirming it's fresh AND the cohort is complete.

---
### (historical) the reverted change
**2026-06-26 — V5 dosing fix: fast-path is OBSERVING-only (no cold-IDLE fire).**

`plugins/aps/.../openAPSBoostV5/MealHypothesis.kt` — removed the `IDLE → CONFIRMED` branch of the
2026-06-16 fast-carb fast-path. A cold-IDLE single-cycle spike (Δ≥8/accl≥15/score≥0.60 with no prior
OBSERVING build-up) is structurally a compression/transient artifact — a genuine sharp rise has
nearly always tripped ENTER_OBSERVING the cycle before. From IDLE a sharp rise now enters OBSERVING
(one beat); the OBSERVING→CONFIRMED fast-path is untouched, so real fast carbs still fast-confirm one
cycle later, still ahead of the age-gated normal path.

**Why (evidence):** found reviewing NS overnight — an eager 07:08 dawn SMB (in-range 6.4 → V5 cold
IDLE→CONFIRMED → 0.65U → undershoot to 4.3). It was a transient (Δ10.8 at 07:08 collapsed to 1.8 by
07:13). Replay (`backtesting/replay.py`, 30d × 5 users, 525 confirms): IDLE→CONFIRMED fired exactly
**ONCE** (that 07:08 fire); all 495 OBSERVING-origin confirms unaffected (61% sustained real rises).
So removing the branch deletes one confirmed false-fire and costs zero real meals. Validated by:
replay origin-split + unit tests (MealHypothesisFastConfirmTest, rewritten) + 07:08 trace.

**SHADOW TESTING — NOT AFFECTED.** This is a *verified safety fix* (removes a confirmed false-fire
over-treatment), so it's an allowed exception to the "don't pile unvalidated changes on the moving
V5/V6 alpha" rule. Deploying it to the live build does **not** contaminate or invalidate ongoing
shadow analysis (activity-load shadow, festival data, V5-vs-V1 shadow equivalence) — the change only
suppresses a single, demonstrably-spurious cold-start fire; it doesn't alter the shadow-computed
factors or the V5 dosing logic that shadow is comparing.

**Where:** branch `Boost-V6-wear-dynisf`, commit `e95561aa02` (NOT pushed). The branch also carries
the display-only watch additions (DynISF / ranged-BG / TIR-pie complications + EventData.Status
`variableSens`+`tirWeights`); the IDLE removal is the ONLY dosing-path change on it.

**APK to flash (on the PHONE):** `Boost-V6-PHONE-IDLEfastpathfix-2026-06-26.apk` (96 MB, V2-signed
CN=Tim) on [user-Drive-account] Drive `Boost-v2-Analysis/`. Installing it also lights up DynISF + the TIR
pie on the watch faces (it sends variableSens + tirWeights). Flash when ready to watch a few cycles.

**Also ported to Trio** (2026-06-26): faithful mirror in
`~/StudioProjects/Trio/BoostPort/BoostV5Core/Sources/BoostV5Core/MealHypothesis.swift` (.idle case),
branch `Boost-in-Trio-v0.1`, commit `9386c6b6e`. Updated MealHypothesisTests (one-beat) +
BoostV5EngineTests (thread the extra OBSERVING cycle for the cap/clamp/persistence tests). All 198
BoostV5Core tests pass. Pure-logic change in the SwiftPM package — no pbxproj/file-reference changes.

Related: [[boost-running-build-and-v5-overnight]], [[boost-trio-port-complete]], [[boost-wff-watchface]], [[drive-apk-destination]].
