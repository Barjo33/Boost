---
name: fastcarb-confirm-crash-2026-07-10
description: "V6 CONFIRMED shots over-treat modest fast-carb rises → crash (tim 29%, D 39%). The \"decelerating\" guard hypothesis FAILED; real signal = eager-confirm-context (low BG+IOB). V7 doses far less on these — likely the fix, pending a crash-vs-needed split."
metadata: 
  node_type: memory
  type: project
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**2026-07-10: from Tim's 48h review — V6 over-treats modest fast-carb rises into crashes; the fix is likely V7, not a V6 guard.**

**The trigger (Tim's 48h, 07-08→10):** 3 fast-carb rise-then-crash events (07-09 12:13 peak 211→nadir 64; 07-09 19:54 91→141→**44**; 07-10 14:00 →155→60-and-falling). Boost sat OBSERVING then fired ONE big CONFIRMED shot (2.35–2.60U) near the modest peak; the fast carb self-cleared and the SMB (3h action) landed into the fall → crash. NB Tim runs V6-ACTIVE so these are real deliveries. No carbs logged (unannounced meals). My initial "slow ramp" read was WRONG (window-averaging diluted the steep part) — Tim corrected: these are fast carbs.

**Backtest (`backtesting/scripts/2026-07-fastcarb-confirm/`):** CONFIRMED shots crash (nadir<70 in 3h) **tim 29%, D 39%, cohort ~20%** — a real over-treatment rate, generalises. 
- **HYPOTHESIS REJECTED:** "trim when decelerating+modest-peak" (from Tim's 3 events) does NOT generalise — only 10% of crashes fire decelerating; guard crash:needed 18:12 (poor). DON'T build it. (Another hand-guard that failed on test — pattern with cap-stepper/sliders.)
- **REAL actionable discriminator = eager-confirm CONTEXT:** crash shots fire at LOWER current BG (120 vs 137) + LOWER IOB (0.6 vs 1.2) → Boost confirms a meal shot BEFORE the rise proves itself; the "meal" is small/self-limiting (peak 143 vs needed 186) so the ~1U shot overshoots→crash 58. But separation is modest/overlapping → a hard guard would be imperfect + fights the early-dosing lever ([[early-dosing-audit-2026-07-03]]) + interacts with the confirm-gate.

**"Or just wait for V7?" — EVIDENCE (the go/no-go):** on tim's CONFIRMED shots with V7 logged (n=18, V7-shadow new): **V6 1.30U → V7(R7) 0.30U median, −0.80U, LESS on 100% of shots.** R-values now DIFFER (R4 0.45>R7 0.30>R10 0.20) — the first-formulation R-insensitivity (the NO-GO reason, [[v7-design-2026-07-07]]) appears partially fixed. So V7's asymmetric-loss distributional sizer IS doing the right thing (size to outcome distribution, back off modest rises). **RECOMMENDATION: lean WAIT FOR V7, don't build a V6 guard.** ⚠️ NOT proven: (a) n=18; (b) unknown if V7 doses less SELECTIVELY (less on crashes, holds on needed=good) vs GLOBALLY (trades crashes for highs=not free); (c) V7 sizing still unvalidated shadow. **GO/NO-GO: once more V7-shadow accrues, re-run the V7-vs-V6 dose comparison SPLIT by crash-vs-needed.** V7 less-on-crashes-holds-on-needed → ship nothing on V6. V7 less-on-everything → global-conservatism dial, real trade-off remains.
