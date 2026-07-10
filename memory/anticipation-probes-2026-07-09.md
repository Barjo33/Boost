---
name: anticipation-probes-2026-07-09
description: Recurring-structure probes for anticipatory dosing. Post-exercise recovery tail = the win; exercise anticipation viable; dawn frequent-but-loose; bedtime too variable.
metadata: 
  node_type: memory
  type: project
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**2026-07-09: four Bayesian/pattern probes on TimescaleDB (V6, self+A–H) for ANTICIPATORY dosing (predict recurring events → prep, vs react). Distinct from the dead knob-tuning thread [[cap-stepper-nogo-2026-07-08]] — counterfactual wall doesn't block the DETECTION stage. Rankings INVERTED from my a-priori guess.**

- **D — post-exercise recovery tail = STRONGEST/most actionable.** After exercise ENDS, hypo-rate is low at +1h (0.59×), crosses baseline ~+2–3h, climbs — a DELAYED, universal (all 8 users) recovery window. Trigger (exercise-end) is sharply detectable, risk is time-lagged → clean anticipatory target with hours of runway. ⚠️ +4–6h magnitudes (1.5–1.9×) partly a longer-window artifact; the CROSSOVER (~+2–3h) is the clean signal. NEXT: matched-window baseline to de-artifact, then spec a post-exercise damper (check if V4's existing recovery window matches this 2–6h delayed shape).
- **A — exercise anticipation = STRONG/viable.** Habit prior P(exercise|weekday,time) OOS AUC 0.85; pre-arms 55% of episodes ~55 min BEFORE onset at 0.63 precision → genuinely LEADS Boost's reactive steps signal (which was the gating question — PASSED). Per-user strength: C 91%/H,E,F 67–74% habitual, B 0% (irregular exerciser — skip). 37% false preps OK for a GENTLE hypo-prep (asymmetric loss). Activity=47% of low-time [[residency-lever-map-2026-07-08]] so upside is real.
- **C — dawn phenomenon = FREQUENT but not schedulable.** Fasting dawn rise on 82% of nights (+55 mg/dL median) but onset SD 75 min → too variable for a TIMED pre-dawn shot. Lever = standing overnight-into-dawn stance, fold into night-mode work, not a scheduled correction.
- **B — bedtime posterior = WEAKEST.** Sleep-onset SD ~92 min, weekday adds nothing, learned prior doesn't beat fixed clock → too loose to carry SLEEPING when HR dies (the failure I hoped it'd fix). Only regular-sleeper D (SD 43 min) benefits. ⚠️ onset PROXIED from activity-cessation (sleepState not logged) → SD inflated; provisional NO, re-check if sleepState gets extracted. INVERTS my earlier recommendation to lead with bedtime.

**FOLLOW-UP (2026-07-09, same day) — D DE-ARTIFACTED + 2 new threads:**
- **D recovery tail was mostly ARTIFACT.** The "delayed 2× ramp" was a window-length bug (cumulative +Nh vs fixed-3h baseline). Per-hour hazard (matched baseline) = flat **~1.2×, immediate not delayed, gone by +6h**. My V4-window-mismatch hypothesis was **WRONG** — V4's 2h recovery window (default 2.0h, SMB×0.5, target 144; hazard 1.25× @ 0-2h) is roughly right; at most extend to ~4h taper for the mild 1.15× tail. So the recovery tail is a MINOR refinement, not the headline lever the first pass implied. Downgraded.
- **Rolling-24h step load → subsequent sensitivity = NULL.** Matched-IOB forward-low hi/lo 1.06; BGI-residual slope wrong-signed (+1.03); autosens tdd_adj_factor corr −0.06. No reliable signal → NO-GO (the 24-48h sensitivity boost isn't detectable/consistent here).
- **Exercise anticipation (A) SURVIVES** (not artifact) → `EXERCISE_PREP_SPEC.md`: confidence-gated (Beta lower-bound) gentle dampen-only pre-exercise prep, opt-in, habitual-users-only, SHADOW-LOG FIRST. Calibrated to the modest ~1.2× effect — real+asymmetric-safe but not a headline jump.
- **METHOD NOTE (recurring):** 2nd de-artifact deflation in 2 days (cf brake 34%→90%-right, cohort +13pp→overnight-confounded). ALWAYS price against a matched baseline before believing an effect size. Artifacts: recovery_tail_matched.py, steps24h_sensitivity.py, RECOVERY_AND_SENSITIVITY_REPORT.md, EXERCISE_PREP_SPEC.md.

Bayesian value = the DECISION layer (Beta posteriors for sparse weekday×time cells + lower-bound gating under asymmetric hypo≫high loss), not raw prediction. Meal-time anticipation NOT probed — already ≈chance [[early-dosing-audit-2026-07-03]] (meals irregular; exercise/recovery regular). All detection validated OOS; dosing action still hits counterfactual-BG wall + needs pricing/safety. Artifacts: `backtesting/scripts/2026-07-anticipation/` (anticip_common.py cache + a/b/c/d scripts + ANTICIPATION_REPORT.md + PNG).
