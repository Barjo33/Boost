---
name: cap-stepper-nogo-2026-07-08
description: "Evidence-gated cap-stepper (raise cap on cap-clip+high, revert on hypo) — NO-GO on BOTH committedCap and confirmedCap. Don't re-litigate."
metadata: 
  node_type: memory
  type: project
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**Settled 2026-07-08 (backtest, both tracks): the evidence-gated cap-stepper is a NO-GO. Don't rebuild it.**

Tim's idea: a Bayesian-ish per-user controller that steps a cap UP after ~10 instances of "hit the cap + stayed high," reverts immediately on a resulting hypo, gated to the low-IOB safe slice + absolute TBR headroom + exercise-clamp to auto-config. I built a **policy replay** (no counterfactual BG claimed — priced added insulin vs OBSERVED lows; V6 `boostv5_*` data, ~394k cycles, self+A–H) and ran BOTH cap tracks. Verdict on each:

- **committedCap** (COMMITTED holds): binds a lot but at HIGH IOB (recovering tail) → **43% of cap-changes reverted** (33–50% across a full param sweep), only 4 raises cohort-wide/6wk. The low-IOB safe slice is small and still ~10%+ low-prone.
- **confirmedCap** (CONFIRMED meal response): revert rate IS better (17–33% — Tim's low-IOB intuition was correct) BUT confirmedCap (2.5–3.0U) rarely BINDS → only 1–5 raises/6wk cohort-wide, all reverts from one user.

**Mirror-image failure: no track both binds AND is safe to add to.** committedCap = binds-but-unsafe; confirmedCap = safe-but-rarely-binds.

**The TBR-headroom arming gate works** — correctly froze B/C/D (safe-slice pre-low 12–29%, i.e. arming them would have churned lows).

**Conclusion: auto-config's initial cap derivation + the existing raise-guard IS the controller.** The only case the stepper would help — a confirmedCap set too LOW for a user's meals — is already covered by auto-config sizing confirmedCap from bolus history (n≥10). So it's an argument for auto-config sizing, not an online loop; also upholds "no training loop in the dose path."

This confirms [[recovering-highs-smb-rejected-2026-07-03]] (blanket committedCap raise rejected) now holds even in the narrow per-user evidence-gated form, and sits under [[two-test-bar-2026-07-06]]. Per-user caps still MATTER at derivation time ([[migration-cohort-backtest]], [[user-h-diagnosis-2026-07-05]]) — it's making them *adaptive online* that fails.

**SLIDER follow-up (2026-07-09): also NO-GO, both directions — and it generalises the conclusion.** Tim asked whether adjusting the continuous SLIDERS (aggression ∈[0.7,1.3] scales CONFIRMED dose; hypoCaution ∈[1.0,2.0] deepens mlHypoRisk backoff) beats the caps, since sliders are multipliers not ceilings (better engagement, faithful counterfactual — both enter dose as known multipliers, priced vs observed lows/highs). Result: **aggression up-on-highs = 45% revert (identical to cap-stepper, confirms highs are sizing/timing not global under-aggression); hypoCaution up-on-lows = flat good:wrong 0.74 at EVERY level (1.25–2.0)** — slider magnitude irrelevant, the mlHypoRisk>0.30 targeting signal is just coarse (catches more eventual-highs than lows among backed-off doses); the any-low controller ratchets everyone to max. BUT per-user hypoCaution IS well-targeted for genuinely hypo-prone users (D 32% pre-low, tim 28%) vs well-controlled (A 6%, E 1%) → **hypoCaution belongs as a per-user STATIC setting driven by TBR (auto-config territory), not an online loop.** UNIFYING CONCLUSION across caps+sliders+both directions: **online outcome-driven auto-tuning of dosing knobs does NOT beat auto-config + static per-user settings** — the controller keeps re-deriving badly what a one-time TBR-gated config sets right. Artifact: `backtesting/scripts/2026-07-slider-controller/` (slider_controller_replay.py + SLIDER_CONTROLLER_REPORT.md).

**CHECKED (2026-07-09): auto-config ALREADY implements the correct policy — no change to make.** `BoostV5AutoConfig.kt` sets hypoCaution per-user from TBR: `cautionRaw = 1.0 + max(0,TBR70−4%)/4 + max(0,TBR54−1%)×0.5`, clipped [1.0,2.0]; and aggression is NEVER auto-raised above 1.0. So: well-controlled users → hypoCaution 1.0 (untouched, matches the experiment's "don't caution A/E"); hypo-prone D (TBR70~12.9%) → clips to 2.0 (matches "caution the hypo-prone"); aggression never raised (matches both NO-GO aggression tracks). The slider/cap investigation independently RE-DERIVED the exact shipped design, and the data validates its discriminator (TBR gate is right: flat cohort 0.74 = don't caution broadly; per-user split = caution only the hypo-prone). Strong validation of auto-config, not a gap. Don't rebuild as an online loop.

**Under V7 the question dissolves** (V7 sizes distributionally, no committedCap to clip; V7 is shadow-only so there were no live caps to replay). The V7-equivalent — should the distributional sizer widen its upper tail on the same under-dosing evidence — is a SEPARATE untried experiment. See [[v7-design-2026-07-07]].

**Artifacts** (committed, both branches experimental + v7-shadow): `backtesting/scripts/2026-07-cap-stepper/` — `cap_stepper_replay.py` (parameterized, `--track committed|confirmed`), `CAP_STEPPER_PAPER.md` (2-experiment write-up), + the two generated `CAP_STEPPER_REPORT*.md`.
