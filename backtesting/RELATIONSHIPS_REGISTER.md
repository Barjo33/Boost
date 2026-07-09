# Relationships register — Boost analysis

A record of the data relationships and dosing levers we've examined, with the verdict and the number or reason behind it. The point is to avoid re-testing things that are already settled. Grouped by outcome: used, discarded, and partial/unproven. Most entries are from the July 2026 work; a few earlier ones are included for completeness.

## Found and used

| Relationship / lever | Finding | Key evidence | Status |
|---|---|---|---|
| Activity → forward hypo | Recent steps are a leading indicator of lows, per-user (not cross-user) | dose-response 13%→38.5% by step load; steps ~1.5–1.6× baseline up to 3h before a low; grouped-OOS lift ~0 (per-user, not global) | Validates exercise protections + the Garmin steps ingest |
| Time-of-day + weekday → activity | Exercise is habitual | OOS AUC 0.73–0.85 (time features only); ~30% of a user's activity in its top-3 hours | Basis for exercise anticipation |
| Habit prior vs reactive steps signal | The habit prior fires before the person moves | pre-arms 55% of episodes ~55 min before onset; AUC 0.85; armed-window precision 0.63 | Spec written (shadow-log first) |
| State → forward high / low | Both are predictable an hour out | grouped-OOS AUC 0.83 (high) / 0.78 (low) | Foreseeability layer in the residency work |
| Where TIR loss comes from (attribution) | Highs: sizing/timing. Lows: activity + rescue | high-time — sizing/timing dominate; low-time — activity 47% + rescue-overshoot 37%, stacking only 16% | The lever map |
| Brake (composed multipliers) correctness | The brake is mostly right | on wanted-dose-suppressed cycles: 76% correct high-IOB restraint + 13% saved a low; only ~3% recoverable | Don't loosen it; composed floor's target is small |
| Recovering-highs IOB context | The high tail is high-IOB; adding insulin there causes lows | ~19% pre-low when adding at recovering-level IOB vs ~7% at low IOB | Rationale for the dosing guards |
| Overnight vs daytime (Boost vs oref cohort) | Boost's advantage is overnight | +13.3 pp overnight TIR (both fewer lows and highs); daytime ~flat; anti-phase with oref | Protect the night-mode machinery (causal test still pending) |
| Post-breakfast (Boost vs oref) | oref beats Boost mid-morning | −4 to −7 pp ~09:00–13:00 | Points at confirm sizing/timing as the daytime lever |
| Post-exercise recovery tail | Modest, immediate hypo elevation | ~1.2× baseline hazard, flat 0–5h, gone by +6h (de-artefacted) | V4's 2h recovery window is roughly right; at most a small extension |
| hypoCaution by TBR (per-user, static) | Well-targeted for the hypo-prone, off for the well-controlled | pre-low share of removed insulin: hypo-prone users ~28–32%, well-controlled ~1–6% | Already implemented in auto-config; validated |
| Auto-config policy (never auto-raise aggression; TBR-driven hypoCaution) | The correct static policy | four online-tuning experiments re-derived it | Validated; ships |
| Per-user caps at derivation | Per-user caps matter (earlier migration/cohort work) | cap-clipped users rescued, TBR-heavy users tightened | Used via auto-config |
| HR resting baseline | median of per-session p10, ≥7 sessions → Karvonen HRR | robust order statistic | Ships (runtime) |
| Sleep bedtime/wake | circular mean of onset/wake clock-minutes | directional statistic (wrap-safe) | Ships (runtime) |
| V7 residual substrate | Regime-conditioned residual pools debias the IOB forecast | criterion met when QUIET_FLAT median drift ≈ 0 | GO as a substrate (shadow) |

## Discarded (no-go, null, or artefact)

| Relationship / lever | Why discarded | Key evidence |
|---|---|---|
| Online cap-raise, committedCap | Binds often but at high IOB; churns | 43% of cap-changes reverted (33–50% across a sweep); ~4 raises cohort-wide/6wk |
| Online cap-raise, confirmedCap | Rarely binds; nothing to act on | 1–5 raises cohort-wide/6wk; all reverts from one user |
| Online aggression slider, up-on-highs | Mis-targeted; highs are sizing/timing, not global under-aggression | 45% revert (same failure as the cap-stepper) |
| Online hypoCaution slider, up-on-lows | Coarse targeting; ratchets to max | good:wrong 0.74 flat across all levels; the static per-user version is what's used instead |
| Rolling-24h step load → insulin sensitivity | No reliable signal | matched-IOB forward-low hi/lo 1.06; residual slope wrong-signed; autosens corr −0.06 |
| Learned bedtime → lead sleep detection | Bedtime too variable to carry the clock prior | onset SD ~92 min; learned ≈ fixed clock; only one very regular sleeper benefits |
| Dawn phenomenon → timed pre-dawn correction | Frequent but timing too loose to schedule | 82% of fasting nights, +55 mg/dL median, but onset SD 75 min |
| Meal-time anticipation (earlier) | ≈ chance | predictability at baseline |
| Brake as a loosening target | The brake is 90% right | the "34% of high-time" was proximate over-attribution |
| Cohort +13 pp as a clean Boost effect | Mostly overnight + selection/basal confound | +2.9 pp raw → +1.2 pp adjusted for case difficulty; permutation p ≈ 0.27 (NS) |
| Post-exercise "delayed 2× ramp" | Window-length artefact | de-artefacted to ~1.2×, flat |
| Blanket committedCap raise (earlier) | Suppressed confirms; priced badly | rejected 2026-07-03 |
| RECOVERING-state SMB / "re-engage tuning" (earlier) | Add insulin into a high-IOB tail → lows | rejected 2026-07-03 |

Cross-cutting note on the online-tuning group (first four rows): tuning a dosing knob online against outcomes did not beat auto-config with static per-user settings, in either direction, for either caps or sliders. Auto-config already encodes the policy those experiments converged on.

## Partial / unproven

| Relationship | State |
|---|---|
| Overnight is causally Boost's doing (not selection/basal) | Suggestive from the regime split; the pre-registered within-user night-mode A/B is the test, not yet run |
| Bedtime prior for regular sleepers | Works for the one very regular sleeper; not general |
| Exercise-anticipation prep helps in practice | Detection validated; the dosing benefit needs the shadow-log before any claim |
| Post-exercise window extension (V4 2h → ~4h) | Supported by the mild 2–6h tail; a small refinement, not yet tested |

## Recurring lesson

Several of the discarded entries started as large-looking effects that shrank once measured against a matched baseline (the brake 34%, the cohort +13 pp, the recovery 2×). Effect sizes are treated as provisional until baselined.
