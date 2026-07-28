# Boost V6 — experimental AndroidAPS fork

[![Support Server](https://img.shields.io/discord/629952586895851530.svg?label=Discord&logo=Discord&colorB=7289da&style=for-the-badge)](https://discord.gg/aUzQ8q5zQd)

> ⚠️ **Experimental. Not medical advice. Not a released or approved product.**
> This is a developer's research fork of AndroidAPS that changes the automated insulin-dosing
> decision. Do not run it on a pump unless you fully understand the code, accept the risk, and can
> self-manage the consequences. **You are the safety system.**

## What Boost V6 is

Boost keeps the **entire AndroidAPS engine** — basal, dynamic ISF, glucose predictions and **every
safety gate** — and changes **only one thing**: the super-micro-bolus (SMB) decision. Stock
AndroidAPS sizes one isolated micro-bolus each cycle, from scratch. **Boost V6 instead carries a
*meal hypothesis* across cycles and scales its dosing to how confident it is that a meal is under
way.** Nothing else about how AndroidAPS runs your pump is touched.

The result is a system that holds back before a meal is proven, then catches up firmly once it is —
and that you tune with **three dials, not twenty-four** (most people never touch even those, because
Boost sets them from your own history on day one).

> *Naming:* the plugin is labelled **"Boost V6"**, but its code and its settings keys still carry the
> earlier **"V5"** name (`ApsBoostV5…`) from its lineage — V1 → V2 → V3 → v4.4 → v4.4.2 → V6. If you
> read the source, "V5" and "V6" refer to the same current engine.

## How it works — in one glance

A meal-hypothesis state machine drives the SMB:

```
IDLE → OBSERVING → CONFIRMED → COMMITTED → RECOVERING → IDLE
```

It observes lightly while a rise builds, commits a firm **catch-up shot** once a meal is confirmed,
holds through the meal, then deliberately winds down as insulin takes hold. A layer of safety guards
and personal context (heart rate, sleep, activity) sits around it, and every stock AndroidAPS gate
still runs underneath.

**→ Full detail: [How Boost V6 works](docs/v6-how-it-works.md)** (the dosing core, the state machine,
the July-2026 safety guards, and the learners).

## Getting started safely

For anyone but the developer, the supported path is **shadow first** — watch what Boost *would* do
before it does anything:

1. **Install the build and select the "Boost" plugin** as your APS (note: **"Boost"**, *not*
   "Boost V6"). In this mode Boost computes what it would dose and logs it to Nightscout, but it
   **does not drive your pump** — your existing engine still doses.
2. **Watch it for ~2 weeks.** Use the **[Analyser](https://tim2000s.github.io/Boost-in-AAPS_3.4/boost_analyser.html)**
   on your own Nightscout data to see, cycle by cycle, what Boost would have delivered versus what
   actually dosed — a real paired comparison, not a simulation.
3. **Only then decide** whether to switch the active **"Boost V6"** plugin on. A freshly built copy
   never auto-doses; going active is a deliberate choice.

| APS plugin you select | What drives your pump |
|---|---|
| (any non-Boost engine) | unchanged — Boost not involved |
| **"Boost"** | the engine with the V6 override in **shadow** — logs what it *would* do, does not dose |
| **"Boost V6"** | **active** — the state machine drives the SMB |

## The three levers

These are the only dials you would normally touch — and **auto-config already sets each one from your
own dosing history**, so most people leave them alone. Each scales *aggressiveness*; none can bypass
a safety limit.

| Lever | Range (default) | What it is | Turn it **up** → | Turn it **down** → |
|---|---|---|---|---|
| **Aggression** | 0.7–1.6 (1.0) | How firm the **one meal catch-up shot** is (it scales the CONFIRMED commit only — routine holds are bounded by the caps, not this). | a bigger catch-up shot on confirmed meals — for people who peak high | a gentler meal response |
| **HypoCaution** | 1.0–2.0 (1.0) | How hard Boost **backs off when its hypo-risk model is worried** (it does nothing while risk is low). | more insulin trimmed on elevated risk = more hypo-defensive | **1.0 is the floor** = least caution |
| **Sensitivity** | 0.8–1.2 (1.0) | A **whole-budget calibration** for how the engine suits you (not a dynamic-ISF multiplier). | firmer overall — for insulin-resistant users | gentler overall — for users it runs hot on |

**How to use them:** start from the auto-config values, change **one at a time**, and check the
caps and Max IOB first — if a cap or the IOB clamp is what's binding, more Aggression changes
nothing. The **[Tuning Guide](https://tim2000s.github.io/Boost-in-AAPS_3.4/boost_tuning_guide.html)**
shows each dial on a conservative→aggressive spectrum with worked scenarios.

Everything else — the dose caps, the cumulative cap, fast-carb confirm, the opt-in aggression levers,
DynISF, activity — is **[advanced and set for you on install](docs/v6-advanced-settings.md)**. You
should rarely need to open that page except to understand a value auto-config chose.

## Interactive tools

Three self-contained HTML tools — no install, no data leaves your machine. A good order for a
newcomer is **Tuning Guide → Simulator → Analyser**:

- **[▶ Tuning Guide](https://tim2000s.github.io/Boost-in-AAPS_3.4/boost_tuning_guide.html)**
  ([source](boost_tuning_guide.html)) — *learn what each setting does.* Every knob on a
  conservative→aggressive spectrum, with real-world tuning scenarios, for both V1 and V6.
- **[▶ Simulator](https://tim2000s.github.io/Boost-in-AAPS_3.4/boost_simulator.html)**
  ([source](boost_simulator.html)) — *play with the dosing maths.* Set BG, trend, IOB, TDD and the
  settings (or pull a snapshot from Nightscout) and watch the ISF and SMB recompute, live, for both
  the V1 tier ladder and the V6 state machine.
- **[▶ Analyser](https://tim2000s.github.io/Boost-in-AAPS_3.4/boost_analyser.html)**
  ([source](boost_analyser.html)) — *V1 vs V6 on **your own** data.* Enter your Nightscout URL + a
  read token and it reads the shadow telemetry every Boost build logs, for a real paired comparison.
  Runs entirely in your browser; the token goes only to your Nightscout.

> These tools validate **decisions** (what dose, which state, why) — not glucose outcomes. They model
> the algorithm, not a body.

## Learn more

- **[How Boost V6 works](docs/v6-how-it-works.md)** — the dosing core, state machine, safety guards, learners.
- **[Advanced settings](docs/v6-advanced-settings.md)** — everything auto-config sets on install, and how.
- **[Heart rate, steps & night mode](docs/v6-heart-rate-and-sleep.md)** — sleep detection and overnight dosing.
- **[Safety, "no training" & validation](docs/v6-safety-and-validation.md)** — why changing a live dosing algorithm is defensible.
- **[Backtesting method & shadow validation](backtesting/README.md)** — the data-analysis toolkit.
- **[Legacy V1 / V2 / v4.x settings](docs/boost-v1-settings.md)** — the earlier plugins' full reference.

---

*Boost is a research fork. For everyone but the developer, **shadow is the supported mode**. Read the
code, understand the risk, watch before you switch.*
