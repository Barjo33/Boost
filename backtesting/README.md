# Boost — safe algorithm updates & shadow validation

How we change a live closed-loop insulin-dosing algorithm without hurting the people running it.
This folder holds the data-analysis tooling that makes that safe; this README is the method.

> **Scope.** Boost is an experimental AndroidAPS fork. Nothing here is medical advice. The tools
> read Nightscout history and produce **decision-level** analyses; they do **not** simulate
> glucose outcomes (that needs a physiologic model — see "What's not here").

---

## The core idea

Changing a dosing algorithm is dangerous in a way most software isn't: **users co-adapt to the
algorithm's behaviour** (manual pre-boluses, knob settings, meal habits). A "correct" fix can make
control *worse* until the user re-adapts. So every change is treated as a clinical-equivalence
question, framed by the taxonomy in Pfützner-adjacent work and, directly, by
**arXiv 2606.13882v1, "Safe Algorithm Updates in Automated Insulin Delivery Systems."**

**Bug taxonomy — classify every change first:**

| class | meaning | how we treat it |
|---|---|---|
| **Factual** | objective, wrong-by-computation | fix immediately (e.g. an inverted knob, a null-returning method) |
| **Heuristic** | co-adapted with the user's behaviour | transition **gradually**, shadow-first (e.g. dose aggressiveness, meal-confirm timing) |
| **Computational** | numeric / port differences | verify **equivalence** (e.g. a cross-repo or language port) |

**The bar:** a change should be *clinically equivalent or better* — validated on real history —
before it doses for anyone.

---

## The workflow

```
observe a problem
      │
      ▼
classify  (factual / heuristic / computational)
      │
      ▼
design the candidate change
      │
      ▼
REPLAY-VALIDATE on historical data  ──►  reject / re-design if it misfires
      │  (replay.py — does the candidate behave on real history? false-positive rate?)
      ▼
build it  (toggle-gated; default may be on, but always instantly revertible)
      │
      ▼
SHADOW or toggle-on, then RE-VALIDATE
      │  (shadow_equivalence.py + replay.py as the acceptance gate)
      ▼
roll out  (shadow-first → toggle on → watch live)
```

Two rules that fall out of this:
- **Don't flash an unvalidated dosing change right before the user is away** (e.g. travel). If it
  can't be watched, it doesn't ship — unless it's pure shadow (logs only, no dosing impact).
- **Shadow-first for anything heuristic.** New behaviour logs "what it *would* do" to Nightscout
  for a validation window before it's allowed to act.

---

## The toolkit

All three read Nightscout `devicestatus` (which already logs **paired** outputs — V1's actual dose,
V5's shadow/active decision, the `V1 would=` counterfactual, the ISF-shadow overlay, etc.), so they
reconstruct decisions from data we already have rather than re-implementing the algorithm.

| script | method (per the paper) | answers |
|---|---|---|
| **`shadow_equivalence.py`** | Method 1 — shadow execution | Per-component agreement/divergence between two algorithm paths (V1 vs V5 SMB; the ISF-EMA overlay equivalence; meal-state mix). "How different is the change, and where?" The rigorous version of "is it ready?" |
| **`parkes_grid.py`** | (precursor to Method 2) | Parkes Error Grid of Boost's **predicted** BG vs the BG that **actually occurred** — forecast accuracy, the clinical foundation dosing rests on. Type-1 zone boundaries are exact (Pfützner 2013, Table 1). |
| **`replay.py`** | Method 2 — data-driven replay | Re-runs a **candidate change** over real history and scores it (e.g. for the fast-carb fast-path: meals caught earlier vs false fires vs sleep fires). Lets us choose thresholds and reject unsafe designs **before** writing dosing code. |
| **`v5_shadow_backtest.py`** | applied shadow comparison | V5-vs-V1 dose redistribution around hypo/hyper episodes across the user cohort. |
| **`episode_impact.py`** | first-order impact estimate | Takes the V5-vs-V1 dose delta in the run-up to each real LOW/HIGH episode, weights it by the insulin-activity curve, and × ISF → an **estimated BG impact** ("how much shallower would this low have been? how much higher the peak?"). Open-loop, clamped, not a simulation — quantifies the trade V5 makes. |

**What's *not* here:** glucose-outcome simulation (the paper's Method 3 / UVA-Padova virtual
patients). Our tools validate **decisions and forecasts**, not counterfactual glucose. Full
two-version clinical equivalence on simulated glucose would need a physiologic model.

---

## Running them

```bash
python3 shadow_equivalence.py --window-days 30
python3 parkes_grid.py        --window-days 14 --horizon-min 30
python3 replay.py             --window-days 30
# --no-cache forces a fresh Nightscout pull; otherwise cached raw data is reused.
```

Each writes a Markdown/PDF report next to the script and prints a summary.

---

## Privacy & data handling (read before adding sites)

These tools touch real patients' Nightscout data. The rules, enforced by the scripts:

- **No URLs, tokens, or raw glucose series live in this repo.** Site URLs + read tokens are read
  from a config **outside** the repo (`$BOOST_BACKTEST_SITES`, default
  `~/.config/boost_backtest/sites.json`).
- **Raw pulled data is cached outside the repo** (`~/.cache/boost_backtest/`), never committed.
- **Reports show anonymous tags only** (`self`, `A`, `B`, …) and **aggregate statistics** — no
  per-user identifiers, no raw traces. Scripts are grep-checked clean before any report leaves the
  machine.
- Nightscout's host can 502 under load — pulls chunk into ≤7-day windows and retry with backoff.

---

## Worked example — the fast-carb confirm fast-path (2026-06-16)

The full loop, start to finish:

1. **Observe.** A fast carb spiked to 185 then crashed to 54. The data showed V5 sat in OBSERVING
   one cycle too long (dribbled 0.3U while V1 would have dosed 1.7U), committed ~5 min late — too
   late to blunt the peak, then overshot into the low.
2. **Classify.** *Heuristic* (confirm timing — co-adapted; the user manually pre-boluses fast carbs
   today). So: gradual, shadow-validated, toggle-gated.
3. **Design.** Promote OBSERVING→CONFIRMED in a single cycle on a sharp, accelerating rise.
4. **Replay-validate** (`replay.py`). The *raw* "sharp+accelerating" rule was **rejected**: it fired
   during sleep (compression-artifact risk) and ~2×/day falsely. Adding **corroboration** — require
   the meal score *and* awake *and* not-exercising — gave **zero sleep fires**, ~half the false
   rate, still catching ~⅓ of meals ~15 min earlier. The replay *chose the safe design*.
5. **Build** the corroborated rule, behind a toggle (default on, instant revert).
6. **Re-validate**: re-run `replay.py` + `shadow_equivalence.py` as the acceptance gate; watch live
   after flashing.

The point: **the replay caught that the obvious fix was unsafe before any dosing code was written.**

---

## Related design principle (for the learner-style features)

Features that adapt to the individual (activity load, sleep timing, meal timing) follow one rule:
**learn the user's personal baseline, act on *deviation* from it — but keep clinical absolutes
fixed.** Personalise the dials (sensitivity, activity response); never the guardrails (hypo
thresholds, min-guard, max-IOB, hard safety gates). And blend with autosens rather than stacking on
top of it.
