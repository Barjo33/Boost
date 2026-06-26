# Boost (V5 / V6) — experimental AndroidAPS fork

> ⚠️ **Experimental. Not medical advice. Not a released or approved product.**
> This is a developer's research fork of AndroidAPS. It changes the automated insulin-dosing
> decision. Do not run it on a pump unless you fully understand the code, accept the risk, and are
> capable of self-managing the consequences. **You are the safety system.**

## What it is

Boost keeps the entire AndroidAPS engine (basal, DynISF / `future_sens`, predictions, every safety
gate) and replaces **only the SMB decision** with a meal-aware state machine:

`IDLE → OBSERVING → CONFIRMED → COMMITTED → RECOVERING`

Instead of V1's per-cycle 8-tier if/else ladder, **V5** carries a meal *hypothesis* across cycles and
scales dosing to its confidence — small while observing, catch-up on a confirmed meal, sustain while
committed, then **deliberately winds down** as insulin takes hold. An **aggression budget** caps each
burst and a **deceleration brake** eases off the moment BG stops accelerating; an ML **hypo-risk
score** throttles the budget (higher modelled risk → less insulin). **V6** adds the activity/HR,
sleep-window and meal-time learners on top.

## How it runs — and the safety gate

| You select as APS plugin | What doses your pump |
|---|---|
| your existing engine (oref/AAPS) | unchanged — Boost not involved |
| **"Boost"** | the shared engine, V5 in **shadow** (V5 computes what it *would* do; it does **not** drive the pump) |
| **"Boost V5"** | V5 **active** — the state machine drives the SMB |

**V5-active is opt-in by plugin selection.** A freshly built copy does **not** auto-dose on V5 — you
must deliberately select the "Boost V5" plugin. The recommended path for anyone but the developer is
**shadow first** (run "Boost", watch what V5 *would* have done in Nightscout for a couple of weeks)
before ever considering active.

## Honest scorecard (developer's own V5-active data)

Single-user, the developer's own pump, ~5 months. **This is one person's experience, not a trial.**

- **Time in range (70–180): ~85%**, mean ~6.9 mmol/L.
- **Normal weeks: within hypo targets** — TBR<70 ~2.5–3%, severe <54 <0.5%.
- **High-activity weeks (festival/training): hypo above target** — TBR<70 7–8%, severe <54 2–3.5%.
  This is **exercise-into-correction** (a correction SMB firing into an already-falling, activity-driven
  BG), not a baseline dosing fault. Mitigation (an activity-load ISF factor that raises ISF on
  high-step days) is **in shadow**, not yet acting — this is the next thing to land and the main thing
  to watch if you run it during heavy exercise.
- vs the logged "V1 would=" counterfactual, V5 is **gentler on high corrections** and uses **broadly
  similar total daily insulin** (basal is identical; the difference is correction-SMB only).

## What is NOT yet validated

- **Clinical-equivalence (Parkes Error Grid on counterfactual glucose) is not yet passed.** The
  shadow-equivalence work measures *decision divergence* between V5 and V1, not simulated glucose
  outcomes. For some shadow users V5 would dose *more* than their V1 — so it is **not** validated as
  safe for the general population to run active.
- Treat **shadow** as the supported mode for everyone but the developer.

## Settings worth knowing

V5 exposes a small set of user knobs (the state-machine thresholds are population-calibrated, not
per-user): **Aggression** (0.7–1.3), **HypoCaution** (1.0–2.0), **Sensitivity** (0.8–1.2), and the
dose caps — **CONFIRMED cap default 2.5 U** (0–7.5) and **COMMITTED cap default 0.5 U** (0–2.5).
Lower the caps and aggression / raise HypoCaution to run more conservatively.

## Analysis & reproducibility

The `backtesting/` directory holds the shadow-equivalence and replay tooling used to evaluate V5 vs V1
on real Nightscout data (`replay.py`, `shadow_equivalence.py`, `idle_fastpath_analysis.py`,
`cold_idle_dose_validation.py`) plus the per-period reports. `SHADOW_EQUIVALENCE_REPORT.md` and
`V5_VS_V1_SUMMARY.md` are the current verdicts.

---
*Boost is a personal experiment shared in the open-source loop tradition. Nothing here is medical
advice. Decisions about your diabetes are yours and your clinician's.*
