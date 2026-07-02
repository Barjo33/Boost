---
name: v6-safety-review-2026-07-02
description: "V6 (openAPSBoostV5) safety review 2026-07-02: ranked findings, the data-validated non-meal-state cap fix (5b5026e10b, IDLE/OBS/RECOV capped at v1WouldDose), and the remaining queued fixes (fast-path rescue guard, velocity-scaled confirm gate, postActionRiskCheck re-enable, night-pref validation, fail-closed cumulative cap)."
metadata: 
  node_type: memory
  type: project
  originSessionId: d9e18601-4d49-42cd-9289-f1b24cf4e999
---

**V6 safety review (2026-07-02, 3-agent + line-verified, scope = `openAPSBoostV5/` + override seam).** Full write-up in session; key state:

**FIXED — `5b5026e10b` (local, built OK, backtested):** non-meal-state cap. In IDLE/OBSERVING/RECOVERING the V5 override is now `min(finalDose, v1WouldDose)` — V6 may only OUT-dose V1 with a meal hypothesis. Data that drove it (`boost_decisions`, 5mo/7users): IDLE p50 dose 0 but tail 176 cycles >1U (max 3.7U vs V1 0.45U at BG 210, incl. **2.0U at 05:02 where V1 dosed 0**), ~1,430U cumulative IDLE excess; triggers were GENUINE HIGHS not artifacts (some decelerating w/ IOB aboard). Fix backtest: kills the whole class (residual >1U excess = 0 day+night), CONFIRMED/COMMITTED provably untouched (0 rows), forfeits ~1.8kU non-meal excess (~60% on v1=0 cycles). Reason line logs `non-meal-capped from X U` → live NS counter. Night gate already killed 51/85 IDLE big-excess (nocturnal); this cap removes the ~34+17 daytime residual.

**ALSO FIXED — `1245d33a9a` (local, tests green):** fast-path rescue guard. `fastConfirmAllowed(toggle, recentLowBg)` suppresses the fast path when the 60-min low < **80** (`FAST_CONFIRM_MIN_RECENT_LOW_MGDL`). Replay over 613 real fast-path firings picked the threshold: my first spec (<100) over-blocked 63% (normal pre-meal dips into the 80s–90s — Tim's backtest-first instinct saved it); 80 blocks 36%, 47 of which preceded a second low <70 within 2h; blocked rebound-meals confirm ~2 cycles later via the normal path. Marginal 80→90 poor (8 catches per 80 blocks). Key insight: the fast path was the ONLY CONFIRMED entry exempt from confirmDoseAdequate, i.e. the only unguarded post-hypo commit.

**REMAINING findings (ranked, not yet fixed):**
2. MED `OpenAPSBoostV5Plugin.kt:410` — `riskAtProjectedIob = null` hardwired ("V0 shadow" rationale stale) → postActionRiskCheck dead on the ACTIVE path; delivered dose gets neither V1's nor V5's post-SMB damper.
3. MED `DetermineBasalBoostV5.kt:165` — confirmDoseAdequate gates PRE-velocity shot; slow meal (×0.40) can pass the floor then deliver sub-floor → token burnt. Fix: gate on `budget×1.8×velocityFactor`, re-run backtest_committedcap4.py.
4. MED — resetIfNeeded triggers all hardwired false + no timestamp in V5StateStore → stale COMMITTED after overnight gap starves first meal (under-direction).
5. MED — night-pref fragility: no input validation (parse-fail→midnight), start==end ⇒ always-night ⇒ V6 silently off, NightModeEnabled=false removes the only scheduled V6 off-switch (only v5Asleep remains).
6. MED — cumulative-cap re-check fails OPEN on DB error (`Pair(0.0,720.0)` at OpenAPSBoostPlugin:1165) → V6 override anti-stacking disarmed exactly when degraded. Fix: fail closed.
7. LOWs: maxDelta fed abs(delta) not oref max-of-3; override ignores V1 internal enableSMB verdict; cumulative cap threshold-not-clamp (overshoot ≤ confirmedCapU); OBSERVING age off-by-one + single sub-0.36 blip restarts session; dynamicSpikeCap KDoc says 1.5× but const 2.5; round-before-spike-cap step misalignment; `lastNightModeRun/Result` not @Volatile (race can leak SMB on a night cycle).
8. Separate thread to pull: COMMITTED>2.5U "cap-buster" rows (31 May/47 Jun) — predate/around Fix-6 caps; verify none post-cap.

Data notes: boost_decisions rows are ~2× duplicated (multi-invoke per cycle) — halve raw counts. OBSERVING needed no cap fix (0 daytime big-excess — 0.3× works). See [[boostactive-nightgate-backtest-2026-07-02]], [[committedcap-gate-backtest-2026-07-02]], [[session-2026-07-02-boost-fixes-backtests]].
