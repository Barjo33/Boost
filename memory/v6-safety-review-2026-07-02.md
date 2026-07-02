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

**PUSHED 2026-07-02 (late):** both fixes live — `origin/Boost-V6-experimental` == `origin/dev` == `1245d33a9a`; APK `Boost-V6-experimental-nonmealcap+rescueguard-2026-07-02.apk` on Drive. **Memory branch:** orphan `claude-memory` on tim2000s/Boost-in-AAPS_3.4 (public repo → SANITIZED: keystore password redacted; ting file only holds a token *path*, safe). README on branch = recipe to point another machine's Claude Code at it; update via worktree+push.

**MEDIUM backtests (2026-07-02, TimescaleDB):**
- **Velocity confirm gate — VALIDATED, do it:** of 1,860 deduped confirms, old raw gate passes 1,072 but **384 (35.8%) deliver sub-floor after velocity scaling** (token burnt). Velocity-scaled gate blocks exactly those (extra block = 20.6% of all).
- **postActionRiskCheck — exposure established:** 883 material meal-state doses (1,338U, ~65% of material doses) fired at ml_hypo_risk>0.3 with the brake dead; 112 preceded a low<70 in 2h. Exact damping unmeasurable offline (needs projected-IOB model re-run) — fix is cheap, wire the lambda in active mode.
- **resetIfNeeded — DEPRIORITIZED:** only 27 gap(>60m)-resumes in 5mo, 3 in stale meal states, **0** rise-without-confirm starvation events. Real in code, negligible in practice.
- Night-pref validation / fail-closed cumulative cap / night-cache @Volatile: not observable in NS data (config/device-side) — pure cheap hardening.

**TRANCHE 3 — ALL remaining actionable items FIXED (2026-07-02 eve, local commits on experimental, tests green, clean build exit 0):**
- `9545323fb1` velocity-scaled confirm gate (gate sizes the DELIVERED shot; velocityFactor hoisted, reused by Phase 2.5).
- postActionRiskCheck wired (commit amended after review+backtest) — **BUT expect it to ~NEVER fire**: offline re-run of the actual v12 model JSON (210 reconstructed cohort dose vectors + forced falling-BG probe to +3U) shows the model's learned IOB→risk response is **flat-to-INVERTED** (IOB confounded with meals-in-progress in training → fewer hypos in 4h), so projected ≤ current and the `proj > cur+0.15` trigger never trips. Wired anyway: zero-risk (pass-through, same as old null), auto-live if the model is retrained with a causal IOB response. Review also caught + fixed a schema bug in my first version: `iob_iob_lag0` duplicates current IOB in the lookback block — the projection must move BOTH (plus recent_smb_units_60m(+lag0) and time_since_last_smb_min:=0 for the faithful post-SMB state). Real post-action protection = iobHeadroomBrake + maxIOB clamp + state caps. **QUEUED candidate that WOULD fire: deterministic projected-eventualBG check (eventualBG − dose×variable_sens vs target) — own design + backtest.** Reconstruction caveat: offline calibration poor (corr 0.487; sug_expectedDelta/sug_minDelta not in DB) — the Δ-sign conclusion is tree-intrinsic and robust; absolute fire-rates aren't.
- `05d55cffda` hardening: SMB-volume DB failure FAILS CLOSED (volume := cap); malformed night times → 22:00/07:00 defaults (not midnight); start==end → EMPTY window (was always-night → V6 dead) w/ sleep detection intact; @Volatile night cache. Guards cover BOTH the Boost window and night-mode SMB (isNightModeActiveImpl delegates to isInNightSleepPeriod).
Review CLOSED: 2 HIGHs + 3 MEDIUMs fixed, resetIfNeeded deprioritized by data, LOWs documented below. The list below is now HISTORICAL (what the review found):

**Findings as found (historical):**
2. MED `OpenAPSBoostV5Plugin.kt:410` — `riskAtProjectedIob = null` hardwired ("V0 shadow" rationale stale) → postActionRiskCheck dead on the ACTIVE path; delivered dose gets neither V1's nor V5's post-SMB damper.
3. MED `DetermineBasalBoostV5.kt:165` — confirmDoseAdequate gates PRE-velocity shot; slow meal (×0.40) can pass the floor then deliver sub-floor → token burnt. Fix: gate on `budget×1.8×velocityFactor`, re-run backtest_committedcap4.py.
4. MED — resetIfNeeded triggers all hardwired false + no timestamp in V5StateStore → stale COMMITTED after overnight gap starves first meal (under-direction).
5. MED — night-pref fragility: no input validation (parse-fail→midnight), start==end ⇒ always-night ⇒ V6 silently off, NightModeEnabled=false removes the only scheduled V6 off-switch (only v5Asleep remains).
6. MED — cumulative-cap re-check fails OPEN on DB error (`Pair(0.0,720.0)` at OpenAPSBoostPlugin:1165) → V6 override anti-stacking disarmed exactly when degraded. Fix: fail closed.
7. LOWs: maxDelta fed abs(delta) not oref max-of-3; override ignores V1 internal enableSMB verdict; cumulative cap threshold-not-clamp (overshoot ≤ confirmedCapU); OBSERVING age off-by-one + single sub-0.36 blip restarts session; dynamicSpikeCap KDoc says 1.5× but const 2.5; round-before-spike-cap step misalignment; `lastNightModeRun/Result` not @Volatile (race can leak SMB on a night cycle).
8. Separate thread to pull: COMMITTED>2.5U "cap-buster" rows (31 May/47 Jun) — predate/around Fix-6 caps; verify none post-cap.

Data notes: boost_decisions rows are ~2× duplicated (multi-invoke per cycle) — halve raw counts. OBSERVING needed no cap fix (0 daytime big-excess — 0.3× works). See [[boostactive-nightgate-backtest-2026-07-02]], [[committedcap-gate-backtest-2026-07-02]], [[session-2026-07-02-boost-fixes-backtests]].
