---
name: resume-point-2026-07-07
description: "RESUME POINT (updated 07-07 midday, after the sensing batch + overnight review + V7 foundation backtests): branch/build/device state, the 22:44 confirm-overshoot hypo (open thread), 07-10 review docket, per-user threads, tonight = first valid sleep-stack test night. Read this first, then follow links."
metadata: 
  node_type: memory
  type: project
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**WHERE WE ARE (2026-07-07 midday).**

**Branches/builds**: `origin/Boost-V6-experimental` = **`1a81fee60a`** (07-07 sensing batch: F2 exercise wiring, F1/F9 availability guard + resolve-grace, <54 co-guard, F4/F5/F6 HR hardening + minGuardBG source annotation, IntradayStepBank rollover fix-fix, + V7 backtest artifacts). **`origin/dev` = `2554b7f963` — deliberately ~7 commits behind** (floor toggle + docs + batch) until Tim's floor test verdict; then push experimental→dev. Trio = `fccae0df6` pushed (parity through auto-config amendments); **Trio port QUEUE: floor pin/shadow/toggle + cumulative telemetry + this morning's batch** (F2-equivalent may be N/A — check Trio's exercise wiring; <54 co-guard portable). **Tim's device**: flashed `floorACTIVE-2026-07-06` 21:53 on 07-06, committedCap=1.0 confirmed, floor toggle state unverifiable until it binds. **NEW APKs on Drive awaiting his flash: phone `sensinghardening-2026-07-07` + wear `Boost-WEAR-hrwatchdog-2026-07-07`** (wear manually apksigner-signed; :wear lacks release signing config — small build-file fix someday).

**OPEN SAFETY THREAD — the 22:44 confirm-overshoot (07-06 night)**: 3.0U CONFIRMED (prospective 5.19 = budget 2.2×1.8×knob1.3, confirmedCap-bound) on an unannounced evening snack → nadir 52, 2h10 <75, zero-temp recovery. Same class as festival Sun 17:09. Distributional sizing (the would-be fix) is NO-GO as formulated; needs its own investigation: evening-confirm sizing / wide-uncertainty damping. Watch his 14d TBR<54 (was 0.37%, this event moves it). Post-rescue guard + night gate worked correctly around it.

**07-10 REVIEW DOCKET** (recipes in [[early-dosing-audit-2026-07-03]] + [[phase3-brake-compounding-2026-07-06]]): confirm-gate live rates; early-confirm/fast-path behaviour; post-rescue windows; **floorWouldAdd/applied** (+ the Episode-B v1_units correction — recorded 0 not 0.3, so RECOVERING uplift may be v1-bound-zeroed; live data adjudicates); cohort floor activation (gate: A/E/F yes, B/C/D hold per [[two-test-bar-2026-07-06]]); cumulative cap/vol telemetry; Tim's cap-raise hold behaviour; the 22:44 class; new fields boostSteps_feed/hrBpmMax5m/stepHistory banking breadcrumbs.

**TONIGHT (07-07/08) = first VALID sleep-stack night**: HR restored (force-stop 07-07 ~10:20, streaming 1/min verified; watchdog automates this once wear APK installed). Checks: SLEEPING entry (reason=hr, not drought/PRE_SLEEP-starvation — 7.5h PRE_SLEEP timeout was the third failure mode found), lump-tolerant wake at real wake, daytime-HR baseline warmup progress (still default 70, sessions stuck at 47), midnight `day-close banked` breadcrumb (needs the new APK flashed to bank today's wear steps).

**V7 status** ([[v7-design-2026-07-07]]): substrate GO / sizing NO-GO (2 acceptance criteria: R-sensitivity must appear; quiet-flat ≈ 0 drift — debias carbs from residuals) / efficacy damper awaits SENS-FROZEN retest / flag shelved. Next V7 step = sens-frozen innovation re-test + residual debiasing, both offline.

**PER-USER**: user H — send behaviour message; re-measure ~07-12; velocity-budget opt-in approved-if-needed. user A — needs the cohort APK (his fix); re-measure +7d, kill-switch = absolute 4%. B/C/D — rescued/held by migration+guards; D & G = clinical-conversation track. G — Trio data accruing.

**PERENNIALS**: keystore rotation (10 days pending); meal-learner retirement decision; Design 9; oref_live deep backfill; extractor uncommittable (not a git repo — consider git-init someday); duplicate devicestatus uploads (18/223 min, watch item). Weekly pipeline: first scheduled run Sunday 07-12 06:00.

Day-detail: [[hr-steps-review-2026-07-06]] (batch + overnight findings), [[phase3-brake-compounding-2026-07-06]] (floor lineage), [[migration-cohort-backtest-2026-07-06]], [[two-test-bar-2026-07-06]], [[feedback-backtest-protocol]].

**UPDATE (07-07 ~16:00): evening-confirm + floor day-1 analyses done (report 5a041bfadd committed).**
- **NEW MITIGATION QUEUED — pre-sleep confirm damper**: evening not broadly worse, but PRE-SLEEP (≥22:00 / within 90min of night start) confirms fizzle 41% + double the <54 rate. Rec: first-confirm cap = min(confirmedCap, base-would×1.5), staged remainder to holds; removal-only (Test A can only improve); verified on the 22:44 incident (staged 1.5U not 3.0). Ship candidate — code + no backtest needed beyond this (it's a cap, not added insulin). SHADOW-FIRST per taxonomy or ship active given removal-only — Tim's call.
- **FLOOR IS A NO-OP ON RECOVERING** (see [[phase3-brake-compounding-2026-07-06]] tail) — the original Episode-B mechanism gets zero uplift because v1_units=0 there. Floor helps COMMITTED/CONFIRMED only. MUST resolve before 07-10 activation review (RECOVERING v1-bound exemption OR re-engage fix) + wire floorWouldAdd telemetry properly.
- committedCap 1.0 live+working. tim 14d TBR<70 3.11% / <54 0.51% (incident cost +0.63/+0.05pp; <70 now 0.4pp under the 3.5 gate — the pre-sleep damper buys it back).
- 22:44 confirm-overshoot thread: root-caused (fizzle + high-ISF tail, confirm shot is an eventualBG bet decoupled from ISF-correction scale), mitigation identified. Partially CLOSED pending the pre-sleep damper decision.
