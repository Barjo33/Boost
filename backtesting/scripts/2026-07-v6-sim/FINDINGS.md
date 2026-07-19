# Semi-closed-loop insulin-perturbation replay — V6 vs the IOB-ramp fix

*2026-07-19. Tim's design: DynISF-anchored, semi-closed-loop. Keep each user's OBSERVED glucose trace
(real unannounced meals), replay the fix, perturb by the insulin-action difference (oref exponential
activity × DynISF-at-the-time), let the fix RE-DOSE on the perturbed trace. No carb model → sidesteps
the ReplayBG unannounced-meal problem. `sim_lib.py` (activity + ports), `sim_fidelity.py` (gate),
`sim_replay.py` (per-user, parallel). Intermediate per-meal JSON = scratchpad (gitignored).*

## Fidelity gate — PASSED
The ported V6 confirm shot reproduces the logged `boostv5_doseaftercaps` at **MAE 0.000U** (100%
within 0.05U) where the dose-chain telemetry exists. The activity curve is the loop's own oref
exponential. So the sim is trustworthy for the confirm mechanism the fix touches.

## The fix
Smaller first shot `= v6_shot × iob_ramp(IOB)` (floor 0.25 at IOB≈0 → full at IOB≥2U), remainder held
as a **conditional follow-up** — committedCap-sized holds while the perturbed BG stays above
target+20, stopping once BG normalises. So on a modest meal that would crash, the follow-up doesn't
fire → less total → crash avoided. Fix delivers ≤ V6 ⇒ BG_fix ≥ BG_actual by construction (it can
prevent lows, never create them).

## Result (564 confirm meals, all users)
| | V6 (actual) | IOB-ramp fix |
|---|---|---|
| insulin / confirm meal | 2.04U | 1.89U (−7%) |
| crash <70 | 22% | **15%** (36 of 123 prevented, −29%) |
| deep low <54 | 8% | **5%** (16 of 47 prevented, −34%) |
| recovery plateau vs actual | — | +13 mg/dL mean; 38% higher, 62% same, 0% lower |

- On the 123 meals V6 crashed: nadir 56→69 (+13), but only **29% actually clear 70** — the lift
  isn't enough for the deep crashes; plateau cost +22 mg/dL on those.
- On the 441 non-crash meals: plateau +11 mg/dL, **39% end >160** under the fix — a real high-tail cost.

## Verdict
A **modest safety win with a real high-side cost**: prevents ~⅓ of crashes/deep-lows, but pushes
glucose ~+11–13 mg/dL and lands 39% of good meals >160. It sits on a tunable frontier — the ramp
floor/full are the dial (gentler ramp = fewer crashes caught, less high cost). Whether it's worth it
is the crash-vs-high value call; safety-first favours it, TIR resists it.

## Caveats (load-bearing)
First-order perturbation → the +13 magnitude is soft (linearity degrades for big deltas). DynISF held
observed. Follow-up state approximated as COMMITTED-while-elevated. **tim's DynISF logs at 139 mg/dL/U
(vs 33–52 others)** — anomalously high (U200 + possible unit scaling); it inflates his 140 meals in the
pool, so read per-user too. NOT a full glucodynamic sim — prices the confirm-fix vs actual on real traces.
