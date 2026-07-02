---
name: session-2026-07-02-boost-fixes-backtests
description: "Session summary 2026-07-02: reviewed the sleep-in fix branch, shipped 3 data-validated V6 dosing fixes to experimental+dev (night-gate, committedCap gate, cap telemetry) + phone-anchored stepbridge, refreshed the TimescaleDB and backtested both dosing changes against it, built APKs to Drive."
metadata: 
  node_type: memory
  type: project
  originSessionId: d9e18601-4d49-42cd-9289-f1b24cf4e999
---

**Session 2026-07-02 — Boost V6 fixes + DB backtests.** All work in `~/StudioProjects/Boost-AAPS-core` worktree on `Boost-V6-experimental` (see [[boost-v6-experimental-state-2026-06-27]] for the corrected worktree layout — V6 = worktrees off Boost-AAPS-core, NOT a standalone repo).

**Shipped to `origin/Boost-V6-experimental` == `origin/dev` (lockstep, HEAD `e29630409b`):**
1. **Phone-anchored stepbridge** (`47a5f049df`+`d29dfc5de5`, cherry-picked from `Boost-V6-activity-source`) — watch-swap calibration + HC phone-sensor backfill. Pushed earlier in session.
2. **`c94c5c72d6` night-gate** — `boostActive = !isInNightSleepPeriod()` (night-mode active state MINUS the BG gate; excludes BG so a nocturnal high can't re-enable Boost). Gates V6/V5 SMB override on boostActive; retired ApsBoostStartTime/EndTime. **Backtest-validated** → [[boostactive-nightgate-backtest-2026-07-02]].
3. **`4bfd7bea32` committedCap gate** — OBSERVING→CONFIRMED requires `budget×1.8 > min(committedCapU, 0.8×confirmedCapU)`; budget hoisted above step(); fast-carb exempt; +test. **Backtest-validated** (after Tim caught a max-vs-actual cap bug) → [[committedcap-gate-backtest-2026-07-02]].
4. **`e29630409b`** — log committedCap/confirmedCap to RT telemetry (+extract_boost.py wired).

**Design decisions made with Tim:** night-gate uses the night-mode *active state* (HR/step-aware via AutoBySleep), not the static enabled flag; must EXCLUDE the BG gate (else re-triggers the asleep-overdose incident); committedCap chosen as the "beat one hold" anchor (not maxIob/confirmedCap). Both dosing changes are heuristic/shadow-first per `backtesting/README.md`.

**Backtest infra (learned + used):** local TimescaleDB `oref` @127.0.0.1:5432, `public.boost_decisions` (~332k cycles, 7 users tim/A–F). Update = `~/oref-investigations-boost-v2/extract/extract_boost.py` (NS→idempotent upsert), runner `backfill_all.sh` (sequential, per-host rate limits — do NOT parallelise). Refreshed DB to today (incremental --since). Backtest scripts in scratchpad (`backtest_committedcap{,2,3,4}.py` + inline SQL). Decision-level replays (Method 2), NOT glucose sim.

**APKs to Drive** (street.tj, `Boost-v2-Analysis/`): `Boost-V6-experimental-2026-07-02.apk`, `...-phoneAnchor-...`, and final `Boost-V6-experimental-nightgate+committedcap-2026-07-02.apk` (all 3 fixes, v3.4.2.2). Also built shadow-line `Boost-ML-Beta-shadow-WearStepBridge-2026-07-02.apk` earlier.

**Also this session:** confirmed the sleep-in review branch `claude/sleep-in-function-review-4zs33i`; established the v2 UI 3rd graph is ALREADY steps/HR ([[sensitivity-graph-not-removed-from-dev]]); the dev-fix committedCap plan ([[dev-fix]]) — the boostActive part evolved into the night-gate above. master untouched; AndroidAPS shadow-mirror repo left on Boost-ML-Beta-shadow.

**Build gotchas:** dirty-tree guard blocks ALL gradle tasks (commit before build/test); stale-KSP `ActivitiesModule_ContributesHistoryBrowseActivity` false error → fix with `clean`; JAVA_HOME = Android Studio jbr. Background-task note: check the REAL `GRADLE EXIT: n` line, not the wrapper exit (echo always exits 0).

**AFTERNOON/EVENING (same day) — V6 safety review cycle, all shipped:** full 3-agent + line-verified safety review of `openAPSBoostV5/` → every finding backtested against the TimescaleDB BEFORE fixing → fixed → pushed. Final state: `origin/Boost-V6-experimental` == `origin/dev` == **`8ecaf7bbd9`**. Fixes shipped after the morning batch: non-meal-state cap at v1WouldDose (`5b5026e10b`), fast-path rescue guard @80 (`1245d33a9a`), velocity-scaled confirm gate (`9545323fb1`), postActionRiskCheck wired-but-honestly-scoped (`921a56ea27` — v12 model's IOB response is INVERTED, gate ~never fires; deterministic projected-eventualBG check queued), hardening (`8ecaf7bbd9` — fail-closed SMB cap, night-pref guards, @Volatile). Full detail → [[v6-safety-review-2026-07-02]]. Definitive APK: `Boost-V6-experimental-reviewcomplete-2026-07-02.apk` on Drive (supersedes all earlier same-day APKs). **Memory now also on GitHub:** orphan branch `claude-memory` on tim2000s/Boost-in-AAPS_3.4 (PUBLIC repo → always sanitize: redact the keystore password before every sync; sync recipe in the branch README; local worktree copy at scratchpad/membranch is session-temporary — recreate from the branch if gone). Open threads: deterministic post-action brake design, COMMITTED cap-buster rows pre-Fix-6, LOWs batch, keystore password rotation (STILL pending).
