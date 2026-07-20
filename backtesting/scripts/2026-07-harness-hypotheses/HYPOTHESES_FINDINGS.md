# Harness-hypothesis batch (2026-07-20) — four hypotheses, run in parallel, with CIs

*Real Twin (Kotlin harness) + full history (8 users, ~1159 user-days, Feb–Jul, eras by telemetry:
BoostV1_415 = the early v4.1.5 Boost / no explicit v1/v6 telemetry, then V5V6). Validate-before-building
discipline: every effect size has a bootstrap 95% CI + a distinguishable-from-baseline verdict.*

## H12 — did Boost get better across versions? **No measurable change. (SOLID, season-confounded.)**
Within-user paired Boost V1 (4.1.5) → V5/V6, median Δ [95% CI] — **every metric overlaps 0:**
- ΔTING −2.0 [−4.8, +6.5] · ΔTIR −0.65 [−2.6, +3.5] · ΔTBR<70 −0.21 [−0.47, +0.47] · ΔCV −0.70 [−3.1, +1.4]
Cohort medians are near-identical (TING 69 vs 70, TIR 88 vs 87, TBR 3.6 vs 3.5, CV 30 vs 29). Per-user it's
a wash — some up (C +7 TING, F +7), some down (A −5, H −7), most flat. **The program's versions did not move
the net glycaemic numbers.** (Season-confounded: V1=spring, V6=summer; but even so, no signal — the
meal-window −7.5 regression is a within-window effect that washes out at the whole-day level, offset by
night-mode gains.) Humbling and important: outcome-wise, v4.1.5 ≈ V5/V6.

## H4 — Twin+GBM hybrid forecaster? **GBM beats the Twin; the Twin adds ~nothing. (SOLID.)**
BG+30 RMSE (OOS GroupKFold, n=308k): Twin 23.6, GBM 21.5, GBM+Twin 21.48.
- GBM − Twin: **−2.05 [−2.11, −1.99]** → the GBM is a distinctly better forecaster (confirms E01).
- hybrid − GBM: −0.05 [−0.06, −0.04] → distinguishable only by the huge n; **practically zero.**
So for raw BG prediction, use the GBM; **the Twin contributes nothing on top of it.** The Twin's value is
NOT forecasting — it's its physiological state (Ra/IOB compartments) for meal-detection / control substrate.

## H2 — activity-gate the withdrawal? **Helps selectivity, doesn't rescue it. (SOLID.)**
lo30<60 withdrawal, gated on recent-hour steps ≥200: median %-justified rises **18% → 23%**, paired Δ
**+4.9 [+0.7, +11.0]** (gate helps). BUT even gated, **77% of firings are still unjustified**, and gated
bouts are far fewer. So activity+lo30 beats lo30-alone but is still not a viable standalone auto-withhold.

## H7 — Twin distinguishes compression from real lows? **No. (UNPROVEN.)**
139 compression vs 1190 real-low overnight dips. Twin 30-min-forecast "surprise" AUC **0.48 [0.43, 0.53]**
— chance. Mean surprise nearly identical (compression +27, real +30). The forecast-error proxy fails
because a *real* low is also a forecast surprise. Would need the filter's actual update INNOVATION (how
physiologically-impossible the drop is), which the Twin doesn't currently expose — a possible follow-up.

## Net
Three of four came back null/negative, one a weak positive — exactly what the machine is for: four honest
verdicts in one parallel pass on the real engine. The standout is **H12** — across every version and all
the data, Boost's net glycaemic outcomes are statistically indistinguishable. The one clean *positive*
across the whole session's search remains: **a DB-trained GBM is a genuinely better BG forecaster than the
Twin (~2 mg/dL, SOLID)** — the sensor win, reconfirmed here at scale.

Data note: pre-Feb extension attempted for tim (his NS has CGM back to Aug 2025) but there are NO loop/
devicestatus records before Feb 2026 → the dosing DB can't extend; Feb is a hard boundary. Other users
need the private site registry (not locally available) to extend at all.
