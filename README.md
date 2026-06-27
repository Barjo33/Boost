# Boost V6 — experimental AndroidAPS fork

[![Support Server](https://img.shields.io/discord/629952586895851530.svg?label=Discord&logo=Discord&colorB=7289da&style=for-the-badge)](https://discord.gg/aUzQ8q5zQd)

> ⚠️ **Experimental. Not medical advice. Not a released or approved product.**
> This is a developer's research fork of AndroidAPS that changes the automated insulin-dosing
> decision. Do not run it on a pump unless you fully understand the code, accept the risk, and can
> self-manage the consequences. **You are the safety system.**

This page covers **what is different in Boost V6, how it works, and its settings.** The detailed
settings reference for the earlier plugins lives on a separate page:
**[Boost V1 / V2 / v4.2 — legacy settings reference](docs/boost-v1-settings.md)**. The data-analysis
method that lets a live dosing algorithm be changed safely has its own page too:
**[backtesting — safe algorithm updates & shadow validation](backtesting/README.md)**.

---

## Contents

1. [What Boost V6 is — and what's different](#1-what-boost-v6-is--and-whats-different)
2. [How it runs — the shadow-vs-active safety gate](#2-how-it-runs--the-shadow-vs-active-safety-gate)
3. [How it works — the dosing core and the learners](#3-how-it-works--the-dosing-core-and-the-learners)
4. [Auto-configuration (first activation)](#4-auto-configuration-first-activation)
5. [Settings reference (V6)](#5-settings-reference-v6)
6. [Heart rate, steps & night mode](#6-heart-rate-steps--night-mode)
7. [Backtesting, "no training", and robustness](#7-backtesting-no-training-and-robustness)
8. [Testing & evidence](#8-testing--evidence)
9. [Legacy V1 / V2 / v4.2 settings](#9-legacy-v1--v2--v42-settings)

---

## 1. What Boost V6 is — and what's different

Boost keeps the **entire AndroidAPS engine** — basal, DynISF / `future_sens`, glucose predictions and
**every safety gate** — and replaces **only the SMB (super-micro-bolus) decision** with a meal-aware
state machine plus a layer of personal context (activity, heart rate, sleep). Nothing else about how
AndroidAPS runs your pump is touched.

The single difference that matters: **stock AndroidAPS sizes one isolated micro-bolus each cycle, from
scratch. Boost V6 carries a *meal hypothesis* across cycles and scales dosing to its confidence.**

`IDLE → OBSERVING → CONFIRMED → COMMITTED → RECOVERING`

- **OBSERVING** — a rise is building; dose lightly while evidence accrues.
- **CONFIRMED** — a meal is recognised (BG delta + acceleration + an ML meal-likelihood score +
  time-of-day + sustained-rise, minus a recent-low penalty); deliver the catch-up commit.
- **COMMITTED** — hold a measured per-cycle dose while the meal is clearly active.
- **RECOVERING** — **deliberately wind down** as insulin takes hold, instead of re-deciding from
  scratch and re-dosing a meal that's already handled.

On top of the dosing core, V6 adds **learners** that personalise *sensitivity and timing* (never the
safety limits): a **heart-rate / step** feed (activity load + sleep detection), a **sleep-window**
learner, and **meal-time** learning.

> Where this came from: V6 is the current generation of a line that ran V1 → V2 → v3 → v4.2 → V5. The
> earlier plugins and their settings are documented in the
> [legacy settings reference](docs/boost-v1-settings.md). V6 still runs *on top of* the V1 engine and
> derives its day-one defaults from your V1/oref history (see §4).

---

## 2. How it runs — the shadow-vs-active safety gate

Boost is selected as your APS plugin. **Active dosing is opt-in by which plugin you pick:**

| APS plugin you select | What drives your pump |
|---|---|
| (any non-Boost engine) | unchanged — Boost not involved |
| **"Boost"** | the shared engine with the V6 override in **shadow** — it computes what it *would* do and logs it to Nightscout, but it does **not** drive the pump |
| **"Boost V6"** | **active** — the state machine drives the SMB |

A freshly built copy does **not** auto-dose — you must deliberately select the "Boost V6" plugin.
**The supported path for anyone but the developer is shadow first:** run "Boost", watch what it *would*
have done in Nightscout for a couple of weeks, then decide. This is not a disclaimer — it is the
designed onboarding (see §7).

---

## 3. How it works — the dosing core and the learners

### The dosing core (the meal-hypothesis state machine)

Two cascade controls bound the state machine above:

- an **aggression budget** — a hard ceiling on insulin delivered per rise/"burst", and
- a **deceleration brake** — eases off the moment BG stops *accelerating*, so Boost stops adding
  insulin to a meal that's already turning.

Two risk inputs pull dosing back *before* trouble, not after:

- an **ML hypo-risk score** throttles the aggression budget — higher modelled risk allows *less*
  insulin (it can only ever reduce delivery — see §7), and
- a **recent-low penalty** damps the meal-confirm score for a window after any low.

Every stock AndroidAPS safety gate still runs underneath — most importantly the hard
**`minGuardBG ≥ 80`** gate, which blocks dosing into a projected low regardless of any Boost setting.

### DynISF / `future_sens`

V6 uses the AndroidAPS dynamic-ISF path. The settings (normal target, BG cap, velocity, adjustment
factor) shape the dynamic-ISF curve and how far ahead it projects; the activity learner (below) nudges
*sensitivity* around the user's own baseline rather than overriding the curve.

### The learners (personal context)

These shape **sensitivity and timing only** — never the guardrails:

- **Activity load** — a personal daily-step baseline; high-activity days raise ISF, sedentary days
  lower it. *(Currently shadow — logs what it would do; see §5/§8.)*
- **Heart rate & sleep** — see §6.
- **Meal-time learning** — an anticipatory pre-meal target around habitual meal times. *(Shadow.)*

> Design rule for every learner: **learn the user's personal baseline, act on *deviation* from it, keep
> the clinical absolutes fixed.** Personalise the dials (sensitivity, activity response); never the
> guardrails (hypo thresholds, min-guard, max-IOB, hard gates). Blend with autosens rather than
> stacking on top of it.

---

## 4. Auto-configuration (first activation)

The first time V6 runs active, Boost **seeds its settings from your own recent dosing history** (last
14 days) rather than dropping you onto generic defaults. The principle (from the shadow-equivalence
work in §7): dose calibration is *co-adapted to the individual*, so the safe onboarding is to **start
where your prior dosing left off** and tune from there — not a cold jump to a stranger's numbers. It
works from **any prior engine** (standard oref/AndroidAPS, not just Boost), since it reads only dosing
history + glycaemia.

**The guard-rails:**
- Runs **once**, in the background, the first cycle V6 is active (one-shot flag).
- **Suggestion-only** — writes a setting **only if you haven't already changed it** from the factory
  default. It never overrides anything you've tuned.
- Needs **≥ 7 days of data and ≥ 1500 CGM readings**; otherwise does nothing and **retries later**.
- **Never auto-raises aggression** above neutral on day one; safety knobs only ever *tighten*.
- **Wrapped so any failure is logged and swallowed** — it can never block or alter the dose path.
- It **logs the full reasoning and notifies you** of exactly what it set and why.

### How it determines each setting (the exact rules)

Over the last 14 days it gathers: your **true TDD** (basal + bolus), your **bolus and SMB sizes** (meal
boluses vs SMBs), your **time-below-range** (% < 70 and % < 54 mg/dL), and your **max-IOB / max-bolus**
limits. Then:

| Setting (range) | Rule |
|---|---|
| **HypoCaution** (1.0–2.0) | `clamp(1.0 + max(0, TBR<70% − 4)/4 + max(0, TBR<54% − 1)×0.5, 1.0, 2.0)` — climbs above 1.0 only as time-low exceeds the consensus targets (4% / 1%). |
| **Aggression** (0.7–1.3) | `0.85` if hypo-prone; `0.92` if TBR<70% > 4%; else **1.0**. Never set above 1.0. |
| **Confirmed cap** (0–7.5 U) | `clamp(max(p90 of meal boluses, p95 of SMBs), 1.5, 7.5)` — covers your biggest *typical* single dose so real meals aren't clipped. |
| **Committed cap** (0–2.5 U) | `clamp(max(p75 of SMBs, TDD/40), 0.25, 2.5)` — your routine per-cycle hold. |
| **Cumulative SMB cap / 60 min** (≥ 1 U) | `clamp(Confirmed cap + 2×Committed cap, 1.0, max(5.0, Confirmed cap))` — bounds dose *frequency*; the ceiling tracks the Confirmed cap so a big-meal user's hourly budget is never below a single confirmed shot. |
| **Max IOB / Bolus cap** | carried from your existing limits (clamped to range). |
| **Fast-carb confirm** | **off** if hypo-prone, otherwise on. |

"Hypo-prone" = TBR<54% > 1.5% **or** TBR<70% > 6%. A well-controlled user lands on a fully neutral
config (Aggression 1.0, HypoCaution 1.0, fast-carb on); a low-prone user gets gentler aggression, more
hypo damping, tighter caps, and fast-carb off — all conservative. Aggression can only be matched
*precisely* once shadow data has accrued, so the day-one value is deliberately cautious and is the one
knob most worth reviewing after a couple of weeks.

**Validation.** The derivation was checked against **12 real users** from a research database (an
OpenAPS/Trio cohort and an AndroidAPS cohort, 400–720 days each): the rules were applied to each user's
real history to produce the knobs, those knobs were run through the **V6 engine over the user's own
logged cycles**, and the dosing was probed for danger. **Result: no dangerous dosing** — dose-into-low
≤ 0.2% (the `minGuardBG ≥ 80` gate blocks it regardless of knobs); well-controlled users ran at neutral
V6; for hypo-prone users the protective knobs **reduced** dose-into-low events 15–24%. In no case did
auto-config make dosing *more* aggressive than the engine's default. *(That replay is open-loop — it
does not feed V6's doses back into glucose — so absolute insulin totals from it are inflated artifacts,
not real closed-loop amounts.)*

**Hardening (2026-06-26 adversarial review).** A multi-perspective review of auto-config and the
active-override path (Android + the Trio port) closed three things: the cumulative-60-min cap is now
re-checked on the V6 active-override path itself (a V6 override could previously bypass the base-engine
check); V6's IOB headroom is clamped to the system/oref max-IOB; and the cumulative-cap ceiling was
raised to track the Confirmed cap. None changed the derivation; all tightened the safety envelope. The
derivation was checked line-for-line against the Trio (Swift) port and is in full parity.

---

## 5. Settings reference (V6)

All Boost settings live under the plugin preferences. Defaults shown; most are auto-seeded (§4).

**Dosing**
- **Aggression** `0.7–1.3` (1.0) — global confidence/size multiplier on V6 dosing.
- **HypoCaution** `1.0–2.0` (1.0) — scales the aggression budget down; higher = more hypo-defensive.
- **Sensitivity** `0.8–1.2` (1.0) — fine sensitivity multiplier on top of DynISF.
- **CONFIRMED dose cap** `0–7.5 U` (2.5) — hard limit on the meal-confirm commit shot.
- **COMMITTED dose cap** `0–2.5 U` (0.5) — hard limit on the per-cycle holding SMB.
- **Cumulative SMB cap / 60 min** `0–5 U` (1.5) — rolling-hour ceiling across all SMBs.
- **Max IOB** `0.1–12 U` and **Bolus cap** `0.1–10 U` — overall Boost insulin limits.
- **Fast-carb confirm** (on) — single-cycle confirm on a sharp, accelerating, score-corroborated rise.

**V6 DynISF / `future_sens`**
- **DynISF normal target** (99 mg/dL), **BG cap** (210), **velocity** (100), **adjustment factor** —
  shape the dynamic-ISF curve and how far ahead it projects.

**V6 activity (currently shadow — logs what it *would* do)**
- **Activity / inactivity %** and the **step thresholds** (5/15/30/60-min) — learn a personal
  daily-step baseline and would raise ISF on high-activity days / lower it on sedentary ones.

**V6 heart-rate, sleep & night mode** — see §6.

**Post-exercise recovery** — optional gentler target and dosing scale for a configurable window after
detected exercise.

> The full per-control reference for the underlying V1/V2 plugins (DynISF V1/V2 formulae, tier system,
> Boost start/end times, UAM Boost tiers, Acceleration Bolus, step-count features, BG-source warnings)
> is on the **[legacy settings page](docs/boost-v1-settings.md)**.

---

## 6. Heart rate, steps & night mode

Boost reads **heart rate and steps from a wear device** (via Health Connect, with a Wear OS step
bridge) and uses them to detect sleep and shape overnight dosing — there is **no fixed clock window
doing the dosing**; the clock only sets a broad outer band.

**Sleep detection** (`SleepStateDetector`, 3 states):
- **PRE_SLEEP** — a time-only pre-warm window before your configured night-start (lead default 60 min).
  It engages night-mode SMB suppression *proactively* so you don't carry excess IOB into the night.
- **SLEEPING** — entered when, together and held for a hysteresis: HR within ~15% of resting HR, steps
  near-zero, inside the outer night band, and no meal imminent. Because the HR feed can be
  intermittent, a *drought* of HR transmissions also counts as a sleep signal.
- **AWAKE** — exit requires a **genuine wake**: an HR rise **and** step activity (a BG rise alone
  doesn't wake it — REM can lift HR without waking you).

**Learned night window** (`SleepHistoryTracker`): learns your personal sleep-onset and wake times over a
rolling 28-day window, but the wake boundary is **anchored to your configured night-end and only allowed
to move ± 90 min**, and only learns from *genuine* HR/step wakes. This anchoring stops a feedback loop
that used to ratchet the learned wake ever-earlier when overnight HR data was sparse.

**What night mode does** (`ApsBoostNightModeEnabled`, optionally auto-triggered by sleep detection
rather than a clock): **raises the BG target** by a configurable offset (default 27 mg/dL) and
**suppresses SMB** while you sleep, and the aggressive meal override is **gated off** when asleep — so
overnight Boost runs gentle and basal-led, then resumes full behaviour on a genuine wake. Optional
guards disable night mode if carbs are on board or a low temp-target is set.

---

## 7. Backtesting, "no training", and robustness

This is the part that makes changing a *live* dosing algorithm defensible. The full method and tooling
are on the **[backtesting page](backtesting/README.md)**; the essentials:

### There is no training loop in the dose path — "no training"

This is the point people most often get wrong about Boost, so it's stated plainly:

- **The dose decision is a deterministic, rule-based state machine.** It is *not* a model trained to
  output insulin. Nothing in the dosing path is fit to data, learned online, or a black box. Given the
  same inputs it produces the same dose, and every branch is readable in source.
- **The only trained model is the hypo-risk score, and it can only *reduce* insulin.** It is a small
  on-device gradient-boosted tree (validated **leave-one-user-out**, so it is scored on users it never
  saw in training) that throttles the aggression budget. It can never *add* a dose or relax a limit.
- **Personalisation ≠ training.** Auto-config (§4) and the learned baselines (§6) derive *suggestions*
  from **your own history** — they tune settings, they do not learn the dose. Auto-config is
  suggestion-only, one-shot, and only ever tightens safety knobs.
- **Validation is replay on real history, not curve-fitting.** Candidate changes are scored against
  real recorded decisions before any dosing code ships (below) — there is no parameter sweep optimising
  a glucose objective on the same data, which is exactly how dosing algorithms overfit.

### Why changing a dosing algorithm is treated as a clinical-equivalence problem

Users **co-adapt** to an algorithm's behaviour (manual pre-boluses, knob settings, meal habits). A
"correct" fix can make control *worse* until the user re-adapts. So every change is framed by the
taxonomy in **arXiv 2606.13882v1, "Safe Algorithm Updates in Automated Insulin Delivery Systems"** and
classified before it ships:

| class | meaning | how it is treated |
|---|---|---|
| **Factual** | objective, wrong-by-computation | fix immediately (e.g. an inverted knob, a null-returning method) |
| **Heuristic** | co-adapted with the user's behaviour | transition **gradually, shadow-first** (e.g. dose aggressiveness, meal-confirm timing) |
| **Computational** | numeric / port differences | verify **equivalence** (e.g. the Android↔Trio port) |

**The bar:** a change should be *clinically equivalent or better* — validated on real history — before
it doses for anyone. Two rules fall out of this: **don't flash an unvalidated dosing change right before
the user is away** (if it can't be watched, it doesn't ship — unless it's pure shadow); and
**shadow-first for anything heuristic.**

### The backtesting toolkit (`backtesting/`, reproducible on real Nightscout data)

All scripts read Nightscout `devicestatus`, which already logs **paired** outputs (V1's actual dose,
V6's shadow/active decision, the `V1 would=` counterfactual, the ISF-shadow overlay), so they
reconstruct decisions from data we already have rather than re-implementing the algorithm:

| script | what it answers |
|---|---|
| **`shadow_equivalence.py`** | Per-component agreement/divergence between two algorithm paths. "How different is the change, and where?" Divergence concentrates in meal cycles; basal is identical. |
| **`replay.py`** | Re-runs a **candidate change** over real history and scores it (meals caught earlier vs false fires vs sleep fires) — lets us reject unsafe designs **before** writing dosing code. |
| **`parkes_grid.py`** | Parkes Error Grid of Boost's **predicted** BG vs the BG that **actually occurred** — forecast accuracy (Type-1 zone boundaries exact, Pfützner 2013). |
| **`episode_impact.py`** / **`cold_idle_dose_validation.py`** | First-order / counterfactual BG-impact estimates around real low/high episodes — quantify the trade a change makes (open-loop, clamped, not a simulation). |

**Worked example — the fast-carb fast-path (2026-06-16).** Observed a fast carb spike-then-crash where
V5 sat in OBSERVING one cycle too long. Classified *heuristic*. Designed a one-cycle promotion on a
sharp accelerating rise. **Replay rejected the obvious rule** — it fired during sleep and ~2×/day
falsely; adding corroboration (require the meal score *and* awake *and* not-exercising) gave zero sleep
fires, half the false rate, still caught ⅓ of meals ~15 min earlier. **The replay chose the safe design
before any dosing code was written.** A separate proposed cold-IDLE fast-path was likewise **reverted**
after a full-cohort re-run didn't support it.

### Robustness, in one list

- **Every stock AndroidAPS safety gate is unchanged** — Boost only replaces the SMB decision; the hard
  `minGuardBG ≥ 80`, max-IOB and max-bolus gates all still run underneath.
- **Shadow mode is a real execution path**, not a simulation — the same code runs and logs without
  touching the pump, so what you watch *is* what would dose.
- **Auto-config is suggestion-only, one-shot, failure-swallowing**, and only ever tightens safety knobs.
- **Caps are layered**: per-shot magnitude caps (Confirmed/Committed) *and* a rolling-hour cumulative
  cap on frequency, the latter now enforced on the override path too and clamped to the system max-IOB.
- **Android and the Trio (Swift) port are kept in numeric parity**, checked line-for-line.
- **What is *not* claimed:** there is **no glucose-outcome simulation** (UVA-Padova-style virtual
  patients) and **no Parkes-grid clinical-equivalence pass on simulated glucose**. The tools validate
  *decisions and forecasts*, plus real single-user outcomes — not a population glucose-outcome
  guarantee. **For everyone but the developer, shadow is the supported mode.**

---

## 8. Testing & evidence

A single developer running V6 **active** on their own pump for ~5 months, plus a small cohort running it
in **shadow**. **This is real-world experience and shadow analysis, not a clinical trial.**

**Developer's own V6-active glycaemia** (honest, full picture):
- **Time in range (70–180): ~85%**, mean ~6.9 mmol/L.
- **Normal weeks: within hypo targets** — TBR<70 ~2.5–3%, severe <54 < 0.5%.
- **Very-high-activity weeks** (multi-day festival / heavy training): **hypo above target** — TBR<70
  7–8%, severe <54 2–3.5%. This is **exercise-into-correction** (a correction firing into an
  already-falling, activity-driven BG), not a baseline dosing fault; the activity-load ISF mitigation
  (§5) is in shadow and is the next thing to land. **Watch this if you run it through heavy exercise.**

Period reports live in `backtesting/` (`SHADOW_EQUIVALENCE_REPORT.md`, `V5_VS_V1_SUMMARY.md`,
`EPISODE_IMPACT_REPORT.md`, `IDLE_FASTPATH_REPORT.md`).

---

## 9. Legacy V1 / V2 / v4.2 settings

The detailed, per-control reference for the earlier plugins — DynISF V1/V2 formulae and when to use
each, the tier system, Boost start/end times, UAM Boost tiers, the Acceleration Bolus, Night Mode
settings, step-count features, the on-device ML hypo-risk model details, and the BG-source safety
warning — has moved to keep this page focused:

**→ [Boost V1 / V2 / v4.2 — legacy settings reference](docs/boost-v1-settings.md)**

---

*Boost is a personal experiment shared in the open-source loop tradition. Nothing here is medical
advice; decisions about your diabetes are yours and your clinician's.*
