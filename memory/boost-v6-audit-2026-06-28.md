---
name: boost-v6-audit-2026-06-28
description: Boost-V6 multi-agent safety/build audit (2026-06-28) — what was fixed, what's deliberately kept (amber, don't re-flag), and what's still pending Tim's decision.
metadata:
  node_type: memory
  type: project
  originSessionId: e3138941-0dfd-4662-b6e9-831ed2e3863a
---
Multi-agent deep audit of Boost-V6 (the published `Boost-in-AAPS_3.4` line), 2026-06-28: 40 agents, 29 confirmed findings. Build + Boost unit tests pass; full-release builds. Shadow activity/step/HR features verified to NOT influence the delivered dose.

**FIXED (commit `5417db230a` on wear-dynisf → cherry-pick `699280be1a` on experimental → pushed to origin=Boost-in-AAPS_3.4):**
- **Signing/secrets:** `keystore.properties` was TRACKED + pushed with real signing passwords (store/keyPassword=home853625) + an absolute storeFile path (broke third-party builds). Now `git rm --cached`'d (still on disk locally for Tim's builds, untracked) + `keystore.properties.template` added. build.gradle already guards signingConfig on the file existing → fresh clones build unsigned cleanly. **DO NOT re-add keystore.properties to git.** It was committed long ago (commit ad9d7fe175, pre-this-session), not introduced by this session.
- Concurrency/robustness: HealthConnect{Hr,Steps}Ingest.isAvailable wraps getSdkStatus in try/catch (could throw at top of runEngine); inFlight → AtomicBoolean.compareAndSet; HrIngest no longer leaks one-shot insert Singles; StepService stepsMap @Synchronized + previousStepCount @Volatile.
- UI: BoostOverviewV2 activity graph adds scale-owning series first (HR was flat-lining when HR+Steps both shown).
- Docs: V5 cap KDoc (cap is the pref, not a hard ceiling); OpenAPSBoostV5 header (production/selectable, not stale "PRE-ALPHA shadow-only").

**AMBER — DELIBERATE, decided by Tim, DO NOT re-flag as bugs:** (1) `ApsBoostAllowAllBgSources` hardcoded true in V1 engine (advancedFiltering always on → SMB on unfiltered CGM); (2) `ApsBoostBypassVersionCheck` — VersionCheckerPlugin.applyMaxIOBConstraints gutted to always return maxIob (app-expiry/maxIOB=0 safety net disabled for all users; toggle is dead code). Both keys default true. Tim acknowledged + accepted these.

**PENDING Tim's decision (NOT done):**
- #23 V5 meal-hypothesis reset triggers hardcoded (profileSwitched/pumpDisconnected/loopSuspended/timeJumpMinutes=false/0) → resetIfNeeded never fires live. Proposed: wire timeJumpMinutes only (safe), leave rest (benign — microBolusAllowed gates dosing during suspend/disconnect). Dosing-engine change — awaiting go.
- #26 retired plugins (V2/V3/v4.4/G3) hidden but a user previously on one stays on it post-upgrade (no migration/notice). Auto-switching active APS is risky; a startup notice is safer. Awaiting go.
- **keystore password rotation + git-history purge**: the password is still in git history even after the untrack (and was on remotes). Rotating the keystore password / `git filter-repo` purge is Tim's to own. Also `_docs/demo_keystore.jks` (upstream demo) — confirm its password ≠ the real one.
- PAT embedded in `.git/config` origin URL (local only, not distributed) — revoke/replace recommended.
