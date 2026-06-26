# Boost (V5 / V6) — experimental AndroidAPS fork

> ⚠️ **Experimental. Not medical advice. Not a released or approved product.**
> This is a developer's research fork of AndroidAPS that changes the automated insulin-dosing
> decision. Do not run it on a pump unless you fully understand the code, accept the risk, and can
> self-manage the consequences. **You are the safety system.**

---

## 1. What Boost is

Boost keeps the entire AndroidAPS engine — basal, DynISF / `future_sens`, glucose predictions and
**every safety gate** — and replaces **only the SMB decision** with a meal-aware state machine plus
a layer of personal context (activity, heart rate, sleep).

**V5 — the meal-hypothesis state machine.** Instead of sizing one isolated micro-bolus each cycle,
V5 carries a *meal hypothesis* across cycles and scales dosing to its confidence:

`IDLE → OBSERVING → CONFIRMED → COMMITTED → RECOVERING`

- **OBSERVING** — a rise is building; dose lightly while evidence accrues.
- **CONFIRMED** — a meal is recognised (BG delta + acceleration + an ML meal-likelihood score +
  time-of-day + sustained-rise, minus a recent-low penalty); deliver the catch-up commit.
- **COMMITTED** — hold a measured per-cycle dose while the meal is clearly active.
- **RECOVERING** — **deliberately wind down** as insulin takes hold, instead of re-deciding from
  scratch and re-dosing a meal that's already handled.

Two cascade controls bound it: an **aggression budget** (a hard ceiling on insulin per burst) and a
**deceleration brake** that eases off the moment BG stops accelerating. An ML **hypo-risk score**
throttles the budget — higher modelled risk allows less insulin — and a recent-low penalty pulls
dosing back *before* a low rather than reacting after one.

**V6 — personal context.** On top of V5, V6 adds the learners: a **heart-rate / step** feed
(activity load + sleep detection), a **sleep-window** learner, and **meal-time** learning. These
shape sensitivity and the overnight behaviour described in §5.

---

## 2. How it runs — and the safety gate

Boost is selected as your APS plugin. **Active dosing is opt-in by which plugin you pick:**

| APS plugin you select | What drives your pump |
|---|---|
| (any non-Boost engine) | unchanged — Boost not involved |
| **"Boost"** | the shared engine with V5 in **shadow** — V5 computes what it *would* do and logs it to Nightscout, but it does **not** drive the pump |
| **"Boost V5"** | V5 **active** — the state machine drives the SMB |

A freshly built copy does **not** auto-dose on V5 — you must deliberately select the "Boost V5"
plugin. **The supported path for anyone but the developer is shadow first**: run "Boost", watch what
V5 *would* have done in Nightscout for a couple of weeks, then decide.

---

## 3. Auto-configuration (first activation)

The first time V5 runs active, Boost **seeds its settings from your own recent dosing history**
(last 14 days) rather than dropping you onto generic defaults. The principle: *start where your prior
dosing left off*, then tune — a safer transition than a cold jump to a stranger's calibration.

It runs once, in the background, and is **suggestion-only**: it writes a setting **only if you
haven't already changed it**, never overriding anything you've tuned. It needs ≥ 7 days of data; if
there isn't enough yet it does nothing and retries on a later cycle. When it runs it **logs the full
reasoning and shows you a notification** of exactly what it set and why.

| Setting it seeds | Derived from your history |
|---|---|
| **HypoCaution** | your time-below-range (<70 and <54) vs the consensus targets (4% / 1%) — more lows ⇒ more caution |
| **Aggression** | neutral, eased gentler for a hypo-prone history — **never auto-raised** above neutral on day one |
| **Confirmed / Committed caps** | your actual meal-bolus and micro-bolus sizes (so big meals aren't clipped, routine holds stay modest) |
| **Max IOB / Bolus cap** | carried from your existing AndroidAPS limits |
| **Fast-carb confirm** | on, unless your history is markedly hypo-prone (then off, cautiously) |

Aggression can only be matched *precisely* once shadow data exists (it needs paired cycles), so the
day-one value is intentionally on the cautious side and is the one knob most worth reviewing after a
couple of weeks.

---

## 4. Settings reference (V5 / V6)

All Boost settings live under the plugin preferences. Defaults shown; most are auto-seeded (§3).

**V5 dosing**
- **Aggression** `0.7–1.3` (1.0) — global confidence/size multiplier on V5 dosing.
- **HypoCaution** `1.0–2.0` (1.0) — scales the aggression budget down; higher = more hypo-defensive.
- **Sensitivity** `0.8–1.2` (1.0) — fine sensitivity multiplier on top of DynISF.
- **CONFIRMED dose cap** `0–7.5 U` (2.5) — hard limit on the meal-confirm commit shot.
- **COMMITTED dose cap** `0–2.5 U` (0.5) — hard limit on the per-cycle holding SMB.
- **Cumulative SMB cap / 60 min** `0–5 U` (1.5) — rolling hour ceiling across all SMBs.
- **Max IOB** `0.1–12 U` and **Bolus cap** `0.1–10 U` — overall Boost insulin limits.
- **Fast-carb confirm** (on) — single-cycle confirm on a sharp, accelerating, score-corroborated rise.

**V6 DynISF / `future_sens`**
- **DynISF normal target** (99 mg/dL), **BG cap** (210), **velocity** (100), **adjustment factor** —
  shape the dynamic-ISF curve and how far ahead it projects.

**V6 activity (currently shadow — logs what it *would* do)**
- **Activity / inactivity %** and the **step thresholds** (5/15/30/60-min) — learn a personal daily-step
  baseline and would raise ISF on high-activity days / lower it on sedentary ones.

**V6 heart-rate, sleep & night mode** — see §5.

**Post-exercise recovery** — optional gentler target and dosing scale for a configurable window after
detected exercise.

---

## 5. Heart rate, steps & night mode

Boost reads **heart rate and steps from a wear device** (via Health Connect) and uses them to detect
sleep and shape overnight dosing — there is **no fixed clock window doing the dosing**; the clock only
sets a broad outer band.

**Sleep detection** (`SleepStateDetector`, 3 states):
- **PRE_SLEEP** — a time-only pre-warm window before your configured night-start (lead default
  60 min). It engages night-mode SMB suppression *proactively* so you don't carry excess IOB into the
  night.
- **SLEEPING** — entered when, together and held for a hysteresis: HR is within ~15% of your resting
  HR, steps are near-zero, you're inside the outer night band, and no meal looks imminent. Heart-rate
  feed can be intermittent, so a *drought* of HR transmissions also counts as a sleep signal.
- **AWAKE** — exit requires a **genuine wake**: both an HR rise **and** step activity (a BG rise alone
  doesn't wake it — REM can lift HR without waking you).

**Learned night window** (`SleepHistoryTracker`): Boost learns your personal sleep-onset and wake
times over a rolling 28-day window, but the wake boundary is **anchored to your configured night-end
and only allowed to move ± 90 min**, and it only learns from *genuine* HR/step wakes (not from the
hard clock boundary). This was added to stop a feedback loop that used to ratchet the learned wake
ever-earlier when overnight HR data was sparse.

**What night mode does to dosing** (`ApsBoostNightModeEnabled`, optionally auto-triggered by sleep
detection rather than a clock): it **raises the BG target** by a configurable offset (default
27 mg/dL) and **suppresses SMB** while you sleep, and V5's aggressive meal override is **gated off**
when asleep — so overnight Boost runs gentle and basal-led, then resumes full behaviour on a genuine
wake. Optional guards disable night mode if carbs are on board or a low temp-target is set.

---

## 6. Testing & evidence

Single developer running V5 **active** on their own pump for ~5 months, plus a small cohort running it
in **shadow** (their existing engine doses; Boost logs what it would do). **This is real-world
experience and shadow analysis, not a clinical trial.**

**Developer's own V5-active glycaemia** (honest, full picture):
- **Time in range (70–180): ~85%**, mean ~6.9 mmol/L.
- **Normal weeks: within hypo targets** — TBR<70 ~2.5–3%, severe <54 < 0.5%.
- **Very-high-activity weeks (multi-day festival / heavy training): hypo above target** — TBR<70 7–8%,
  severe <54 2–3.5%. This is **exercise-into-correction** (a correction firing into an already-falling,
  activity-driven BG), not a baseline dosing fault; the activity-load ISF mitigation (§4) is in shadow
  and is the next thing to land. **Watch this if you run it through heavy exercise.**

**Methods used** (tooling in `backtesting/`, reproducible against real Nightscout data):
- **Shadow-equivalence** (`shadow_equivalence.py`) — per-cycle agreement/divergence vs the prior
  engine across the cohort. Divergence concentrates in meal cycles; basal is identical.
- **Decision replay** (`replay.py`, `idle_fastpath_analysis.py`) — re-runs the dosing gate over
  historical inputs to evaluate candidate changes *before* shipping. (This caught a proposed
  fast-path change that a full-cohort re-run did **not** support — it was reverted.)
- **Counterfactual BG forward-sim** (`cold_idle_dose_validation.py`) — projects glucose under an
  alternative dose path to test whether a change actually helps.
- Period reports: `SHADOW_EQUIVALENCE_REPORT.md`, `V5_VS_V1_SUMMARY.md`, festival/episode summaries.

**Not yet validated:** a clinical-equivalence gate (Parkes Error Grid on *simulated* glucose) has not
been passed. The work above measures *decision* behaviour and real single-user outcomes, not a
population glucose-outcome guarantee. For everyone but the developer, **shadow is the supported mode.**

---

*Boost is a personal experiment shared in the open-source loop tradition. Nothing here is medical
advice; decisions about your diabetes are yours and your clinician's.*
