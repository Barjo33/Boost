---
name: prediction-lines-validity-2026-07-03
description: "Verdict on AAPS/NS prediction lines under Boost (2026-07-03): lines come from the V1 engine pre-override (V6 never touches predBGs); IOB@30min trustworthy (MAE 21, night bias ~0, Parkes A+B 98.9%, zero D/E); UAM = unchecked-rise upper bound (+20/+48 on climbs); falls over-deepened −33/−64 by per-tick DynISF; eventualBG is NOT a forecast (R² −2.32, replicates April). No code change warranted."
metadata: 
  node_type: memory
  type: project
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**Prediction-line validity under Boost — verdict 2026-07-03** (code audit + 14-day empirical, 2026-06-19→07-03, 5,994 records, actuals from entries.sgv only).

**Code facts:** predBGs/eventualBG computed by the V1 Boost engine (`DetermineBasalBoost.kt:570-770`) BEFORE the V6 override, which mutates only `units`+`reason` (`OpenAPSBoostPlugin.kt:1296-1320`). Drawn graph == NS devicestatus upload (same RT object). V6 consumes eventualBG/minGuardBG from that result. Boost-specific: per-tick DynISF re-evaluation at predicted BG for IOB/ZT/UAM curves (stock uses one constant sens); TDD-EMA shadow does NOT feed predictions; minor inconsistency — circadian ISF applies to `sens` but not per-tick `getIsfByProfile`.

**Empirical (Tim, 14d):**
- IOB @30min: MAE 21 (night 12.7, bias ~0 — TDD-blend variable_sens well calibrated), Parkes A+B 98.9%, ZERO D/E on all curves.
- UAM on climbs: +20 @30 / +48 @60 — by design (projects un-dosed rise, then engine doses against it). Off-meal fine.
- Falls: all curves over-deepen (−33 @30, −64 @60) — per-tick DynISF raises ISF as predicted BG falls. Safety-conservative (feeds minGuardBG early), visually alarmist: predicted 60 on a fall typically realizes ~110+.
- eventualBG: R²(identity vs actual@240) = **−2.32** — confirms April's −2.35; it is a control/settle target, never a forecast.
- COB line: never rendered for Tim (no carb entries, 0% of records).

**Verdict: lines valid, no code change.** Any softening of the fall-side pessimism (consistent circadian, capping per-tick ISF growth) would change minGuardBG behaviour → must be treated as a DOSING change (shadow-first), not display. UX framing notes only.

See [[feedback_observed_sgv_for_labels]], [[recovering-highs-smb-rejected-2026-07-03]] (same-day analyses).
