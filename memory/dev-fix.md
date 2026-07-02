---
name: dev-fix
description: "Resume point (2026-07-02) for the Boost dev fix branch claude/sleep-in-function-review-4zs33i: what it fixes, review verdict, and the AGREED next change — swap the OBSERVING→CONFIRMED dose gate from maxIob to committedCap. Come back here to implement."
metadata: 
  node_type: memory
  type: project
  originSessionId: d9e18601-4d49-42cd-9289-f1b24cf4e999
---

**RESUME POINT — Boost dev fixes (2026-07-02).** Branch `claude/sleep-in-function-review-4zs33i` on `tim2000s/Boost-in-AAPS_3.4` = **2 commits on top of `dev` (@ 9740d4c431)**. Reviewed as sound + mergeable; one agreed refinement pending (below).

**What the branch fixes (two 2026-07-01 incidents):**
1. `c883864df9` — gate the V6/V5 SMB override on `activityResult.boostActive`; compute a step-based `sleepInActive` independent of boostActive, cache it, and make `isNightModeActiveImpl()` enable night mode during a morning lie-in. Safe because night mode still ends with `bgCurrent < profileTarget+bgOffset`, so a genuine high is still corrected. Fixes the 1.55U-SMB-while-asleep (steps=0, HR machine wrongly AWAKE) incident. One-cycle cache lag documented + safe.
2. `803704d1a9` (Option A) — gate OBSERVING→CONFIRMED on dose adequacy so the single per-session CONFIRMED commit-shot isn't spent on a trivial pre-meal upswing (the eventualBG→372, held 0.4–0.5U/cycle incident). Fast-carb fast-path exempt. Has unit test `MealHypothesisDoseGateTest`.

**AGREED CHANGE TO MAKE (not yet coded):** replace the Option-A gate denominator. Current = `baseInsulinReq >= 0.10 × maxIob` — bad, because maxIob is a SAFETY ceiling, not meal size (effective strictness drifts unpredictably; for high-maxIob users it already behaves like "must saturate the cap"). **New gate = compare the prospective commit-shot to `committedCapU`:**
```kotlin
val prospectiveConfirmShot = budget * mealActionMultiplier(CONFIRMED, aggKnob)   // budget × 1.8 × knob
val confirmDoseAdequate = prospectiveConfirmShot > inputs.committedCapU
```
Rationale: the CONFIRMED shot is a catch-up bolus; only worth spending the one-shot token (+ tripping `committedInSession`) if it beats one routine hold cycle. `committedCapU` is auto-configured per user (≈ routine SMB size), so no arbitrary fraction needed and it self-scales. Catches the incident cleanly (shot ~0.05U < committedCap ~0.5U → blocked).

**Implementation checklist:**
- Drop const `CONFIRM_MIN_INSULINREQ_FRAC_OF_MAXIOB`; add the caps-based gate in `DetermineBasalBoostV5.decide()`.
- **Hoist the `AggressionBudget` calc ABOVE the state-machine `step()`** (budget is state-independent) so `budget` is available to compute the prospective shot, then reuse it (no behaviour change, avoids double compute). Currently Phase 1.b (step) runs before Phase 1.c (budget).
- Guard edge case: if `committedCapU >= confirmedCapU` (manual override) the gate can NEVER pass → V6 meal response silently disabled. Clamp threshold to `min(committedCapU, 0.8 * confirmedCapU)`.
- Optional: bare-default users have committedCapU=0.25U → weak bar; consider `max(committedCapU, absMin)`. Optional margin `COMMIT_MARGIN ∈ 1.0–1.5` (keep small — blocking confirm keeps state at OBSERVING 0.3×, not COMMITTED, so a too-high bar under-doses early real rises).
- Keep the gate on the mlHypoRisk-DAMPED budget (confirm suppressed when hypo risk elevated) — that's the intended behaviour.
- Update `MealHypothesisDoseGateTest` to drive via the two caps.
- Fast-carb fast-path stays exempt.

**Key mechanic refs:** CONFIRMED multiplier = 1.8× (`MealActionMultiplier.kt`), aggressionKnob scales CONFIRMED only. Commit-shot hard-capped at `confirmedCapU` (auto `max(bolus p90, SMB p95)` [1.5,7.5]); COMMITTED hold capped at `committedCapU` (auto `max(SMB p75, TDD/40)` [0.25,2.5]) — both in `BoostV5AutoConfig.kt`, supplied via `DoubleKey.ApsBoostV5ConfirmedCapU`/`CommittedCapU`. See [[boost-mlhyporisk-usage-2026-07-02]], [[feedback-boost-v6-branch-workflow]] (push experimental→dev, not straight to dev), [[boost-v5-autoconfig]].
