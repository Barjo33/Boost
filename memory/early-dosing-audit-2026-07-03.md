---
name: early-dosing-audit-2026-07-03
description: "Capstone audit answering 'how do we dose earlier, safely': MOVED insulin is harm-neutral (0.0pp) vs NEW insulin (~+15pp to lows). Ranked levers: (1) confirm-gate over-blocking FIX — 26-29% of blocked confirms preceded BG>180, Tim worst row, live regression risk from the 07-02 gate; (2) age-gate −1 when score-ready (1.5U/day shifted, zero harm); (3) fast-path retune D6/A10/S0.65. OBSERVING raise only in BG≥140∧IOB<5%TDD cell; meal-time anticipation DEAD (onsets ≈ uniform chance)."
metadata: 
  node_type: memory
  type: project
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**Early-dosing audit (2026-07-03, capstone of the three-lever rejection series).** Cohort DB, 73,840 deduped cycles, 1,094 confirms with detectable rise onset. Base rate: 17–20% of ALL cycles precede <70-in-3h.

**Core principle established: MOVED insulin ≠ NEW insulin.** Shifting the same commit shot 1–2 cycles earlier (where score was already ≥0.55): Δ harm 0.0pp / +0.5pp, landing IOB 1.15→0.60U. Every lever that ADDED insulin priced at +14–17% into lows. Timing corrections are free; volume additions are not.

**IOB-harm curve is real but TRAPPED:** pre-low % of dosing cycles at BG≥140 rises monotonically 6.7% (IOB<2.5%TDD) → 19.5% (10–15%TDD). BUT the unguarded early pool (onset→confirm window) is 21.4% pre-low — 24.9% in its LOW-IOB slice — because rises from low IOB are disproportionately hypo-rebounds. "Early = safe" holds ONLY inside **BG≥140 ∧ IOB<5%TDD** (14.1%). Never propose a blanket OBSERVING raise (0.3×→X unguarded is contraindicated).

**Confirm latency:** median 15 min onset→CONFIRMED (Tim 20); **53% mechanically limited** (score was ready ≥2 cycles before; the age gate is the blocker), 47% score-limited.

**RANKED LEVERS (shadow-first sequence):**
1. **Confirm-gate over-block — LIVE REVIEW DONE same day (23h sample): NO red flag, no immediate retune.** Live arithmetic: Tim's committedCap=0.5, confirmedCap=3.0 (manual boluses doubled the proxy), floor=0.5, **aggression knob ≈1.25–1.3** (inferred from dose arithmetic), gate-moment vf 0.57–1.0 → block probability near the 26% end or below. Only daytime block self-corrected in ONE cycle (5-min delay, benign) — a class the historical audit couldn't see, so its "26% needed" over-estimates true harm. **TELEMETRY GAPS (one-line fixes before 07-10): gate verdict + prospective shot not logged (block indistinguishable from score fade); aggression knob not surfaced.** RT fields live: boostV5_{score,state,age,budget,actionMult,finalDose,gateReduction,active,committedCap,confirmedCap} (RT.kt:91-100). 07-10 week review recipe: eligible-OBSERVING cycles (score≥0.55∧age≥2∧offset≥30, boostActive=true) w/ knob-adj shot ≤ floor; outcomes split >180-in-90min / fizzled / delayed-then-passed-≤2-cycles (benign); confirms/day vs 7.5 pre-gate baseline; vf+knob at eligibility.
2. **Age-gate −1 when score-ready** (confirm on 3rd OBSERVING cycle if score≥0.55 held 2 cycles): 1.5 U/user-day arrives 5 min sooner, 0.0pp harm. `CONFIRM_MIN_OBSERVING_AGE` conditional.
3. **Fast-path retune delta≥6/accl≥10/score≥0.65**: +21 meals ~9 min earlier (vs 100 current), false fires 39%→32%. Pareto point of the sweep; plain relaxation (S0.55) is WORSE (40% false). 3 constants.
4. OBSERVING multiplier raise ONLY in the guarded cell (BG≥140 ∧ IOB<5%TDD + recent-low guard): 0.21 U/day at 14.1% — skip unless 1–3 underdeliver.
5. **Meal-time anticipation DEAD**: rise onsets within ±90min of top-3 personal modes = 34–52% vs 37.5% uniform chance (Tim 34% ≈ chance). The shadow pre-meal-target/meal-time learner will not pay for the cohort as-is — deprioritize.

**ADDENDUM (2026-07-05) — Tim's "stuck above 160 after small meals" analysis:** complaint real (4.2 h/day >160) but REFRAMED: 49% of his >160 time is BIG-meal (>200) aftermath; strict small-meal plateaus = only 10% of small meals, median 10 min, ~5% of high time. Plateau v1-parity gap measured properly = 0.13 U/day (binding constraint = vf 0.4 flat-BG floor × OBSERVING 0.3× rounding to zero, NOT gates/ML) — **plateau-parity lever REJECTED for Tim** (31.7% of that insulin pre-low; his plateau base 21% = 2× cohort). Stacking premise FAILED (second rises confirm at 64% vs 34% fresh). **REAL MECHANISM: his cumulative SMB cap 2.5 < his formula 4.0** (confirmedCap 3 + 2×0.5) — 18 cap suppressions in 2 live days, 8 at BG 150-190: one meal confirm spends the hour's budget, then every plateau correction zeroes. Same config-hygiene class as user H/A/C/D. **SUPERSEDED: Tim raised his cumulative cap himself to 5.0 on 2026-07-05** (above the 4.0 formula value; the "wait for post-rescue validation" sequencing was overtaken — post-rescue cap was already live on his build by then, so acceptable). NOTE: cumulative is NOT in RT telemetry — analyses must not assume 2.5; two stuck-high episodes on 07-06 initially mis-attributed to the cap because of this staleness. Follow-up telemetry gap: log the cumulative cap + rolling 60-min volume to RT. Optional code lever parked: flat-high OBSERVING treated as IDLE-for-dosing (still v1-capped) — cohort-fine/Tim-neutral, shadow-first if ever.

Scripts: early_dosing.py, confirm_events.csv (session scratchpad, ephemeral). Caveats: UTC+1 assumed for all users; onset detection excludes confirms without ≥2-consecutive-delta>3 onsets; #4's harm metric coarse at 5–10 min shifts; gate numbers era-reconstructed with vf∈[0.4,1].

See [[recovering-highs-smb-rejected-2026-07-03]] (why late levers fail), [[committedcap-gate-backtest-2026-07-02]] (the gate), [[v6-safety-review-2026-07-02]] (velocity-scaled gate ship).
