---
name: resume-point-2026-07-08
description: "⭐ RESUME POINT (2026-07-08 EOD) — READ FIRST. dev==experimental==c2f2b1258c (promoted today). v7-shadow=3111f79ee2 has the sleep-in MERGE (shadow-only). TOMORROW: Tim flashes watch warm-flash + MUST flash wear HR-watchdog (cron reminder set ~08:47, session-only). Open: overnight HR keeps dying (3rd night, sleep-stack invalid until watchdog flashed); Garmin port plan ready; V7 warm60 reflash tomorrow."
metadata: 
  node_type: memory
  type: project
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**WHERE WE ARE (2026-07-08 end of day).**

**Branches/builds:** `dev == experimental == c2f2b1258c` — promoted experimental→dev today (force, identical base per [[feedback-boost-v6-branch-workflow]]). Promotion carried: **Simple-Mode mask bypass** (delivered+shadow+HC-ingest, per-setting VERIFIED [[simple-mode-mask-bypass-2026-07-08]]), **composed brake-floor** toggle + enforced **14d hypo-gate (TBR<63<2.0% AND TBR<70<3.5%)** re-validated on DB@07-08, **07-07 sensing batch** (F1/F2/F4/F5/F6/F9), **<54 co-guard**, **degraded-HR sleep fix**. `Boost-V7-shadow = 81ae6d4af6` = **FULL EXPERIMENTAL VEHICLE (2026-07-08 EOD): merged all of experimental into v7-shadow** (floor gate, Simple-Mode, doc fixes, workstream B) + its unique V7 engine + warm60 + **sleep-in MERGE (still v7-shadow ONLY)**. Merge conflicts resolved: backtesting→experimental's anonymized, SleepStateDetector/test→v7-shadow's (keeps lie-in), OpenAPSBoostPlugin sleep-inputs→getBoostDosing + sleep-in-merge fields (both). So Tim's flashable v7-shadow phone build now has everything. Workstream B also on experimental (@ 1b1a73173d) — folds the StepFeed.sleepInActive backstop INTO SleepStateDetector (single source of truth, lie-in held past nightEnd on sleepInSteps). **APK on Drive: `Boost-V7-shadow-sleepmerge-2026-07-08.apk`** (warm60 + sleep fix + merge).

**TOMORROW (07-09) — cron reminder set ~08:47 (job 20599fc3, SESSION-ONLY, may not survive):** Tim flashes the watch **warm-flash** AND must flash/verify the **wear HR-watchdog APK** (`Boost-WEAR-hrwatchdog`). Ask which watch APK(s) he's flashing.

**OPEN — overnight HR keeps dying (3rd night):** F4 = Oppo Wear `HeartRateListener` callback dies overnight (no watchdog), HR goes dark ~2/hr → **starves SLEEPING detection**. Last night (07-07/08) FAILED again: entered PRE_SLEEP 01:33, HR intermittent, stuck to the ~09:03 boundary, never SLEEPING. **The degraded-HR sleep fix (shipped dev) + the merge (v7-shadow) make the detector resilient to this** (reaches SLEEPING on a degraded feed via drought fallback; wakes on strong morning steps). But a truly VALID sleep-stack test needs the wear watchdog flashed. Also exposed: PRE_SLEEP has no wake path (times out at boundary) — the degraded-HR fix addresses the reach-SLEEPING side.

**GARMIN WATCH-FACE PORT — plan ready** ([[garmin-watchface-port-2026-07-08]]): TWO workstreams — A=display faces (cosmetic), **B=HR+steps INTO AAPS (do first — fixes the overnight-HR-loss)**. Garmin logs HR/steps 24/7 in firmware (no listener-death) + backfills on reconnect. AAPS change small (new /hr + /steps batch endpoints, storeHeartRates/storeSteps, timestamp fix; **F3 fix required** = live classifier reads phone StepService only, so even wear steps don't affect exercise state). One CIQ project, background ServiceDelegate 5-min pull from AAPS phone HTTP server (127.0.0.1:28891, NOT Nightscout — Tim's plumbing). Phase-0 spike pending (verify face-background reaches 127.0.0.1 + memory ceiling).

**V7:** substrate GO / sizing NO-GO (first formulation) [[v7-design-2026-07-07]]. warm60 lowers shadow warm threshold 150→60 → usable signal in ~3-6 days after reflash. This morning's 2 meals analysed: meal 1 slow-ramp confirm (gradual rise, fine peak 146); **meal 2 overshoot to 210 (prompt but UNDERSIZED 3U confirm, then 0)** — the case V7 distributional sizing targets. OFFERED but not run: V7 replay backtest on these 2 meals to price what distributional sizing would deliver.

**DISCORD RELEASE NOTES** written (scratchpad `boost_release_notes_2026-07-08.md`) — dev update, benefit-per-item, anonymization excluded, + honest forward-looking V7 section (shadow/unproven). Thresholds in mg/dL; Tim may want mmol.

**user H (user H) — CLOSED:** no aggression problem. Confirms meet need, committedCap fine (2.5), target fine (raising backfires), well-controlled (TIR 91.5%). The only real defect was the Simple-Mode masking (now fixed). Don't reopen the "not aggressive enough" thread.

**PENDING:** 07-10 review docket ([[hr-steps-review-2026-07-06]], [[two-test-bar-2026-07-06]]); V7 replay on this morning's meals; Garmin Phase-0; keystore rotation (long pending); weekly pipeline first run Sun 07-12. Prior resume: [[resume-point-2026-07-07]].
