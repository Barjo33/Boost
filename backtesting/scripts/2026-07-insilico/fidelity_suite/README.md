# Simulator-fidelity suite

A reproducible test suite that measures **where a published in-silico simulator
(simglucose / UVA-Padova) diverges from our real historic data**, so we know exactly
which questions it can and cannot stand in for.

This exists because every policy claim in the backtesting work carries the caveat
*"there is no glucodynamic simulator, so we cannot generate the counterfactual."* A
simulator does exist; the honest question is not "does one exist" but "does it
reproduce the statistics of our data well enough to trust a controller A/B on it." This
suite answers that, signature by signature, against roughly a year of real data per
user rather than a single month.

## What it is

A **registry of signatures**. Each signature computes the *same statistic* on both
cohorts and returns a verdict:

- **PASS** — the simulator reproduces the real statistic within tolerance.
- **FAIL** — it diverges (the statistic is in the model but comes out wrong).
- **STRUCTURAL** — the mechanism is absent from the model by construction, so the
  statistic cannot be reproduced at any parameter setting (see `../fidelity_test.py`).

The real cohort is the 9 users in the local TimescaleDB (`boost_cgm` +
`boost_decisions`, 2025-08 to 2026-07). The sim cohort is 10 UVA/Padova adults over 21
days with **realistically randomised announced meals** (jittered times/sizes, skipped
snacks), so the simulator is given its best shot and any surviving gap is the model's,
not a clockwork scenario.

## Files

| File | Role |
|---|---|
| `gen_sim_cohort.py` | run simglucose with randomised meals, cache `sim_cohort.npz` |
| `common.py` | DB loaders, sim-cohort loader, cadence handling, stats (bootstrap CI, ACF, KS) |
| `signatures.py` | the signature registry — add a signature here |
| `run_suite.py` | run every signature, emit `REPORT.md` + `fig_fidelity.png` |
| `REPORT.md` | generated: the verdict table + figure + notes |

## Signatures (v1)

1. **Glucose variability (CV%)** — distribution of per-person CV, real vs sim.
2. **Short-horizon delta tails (5 min)** — fat positive tails are unannounced-meal
   onsets; the sim only sees announced meals.
3. **Autocorrelation (30/60 min)** — how fast the glucose curve decorrelates (smoothness).
4. **Outcome unpredictability (BG 180-240, +30 min)** — the spread of where you end up
   30 min after a stuck-high band. Real is wide (efficacy and absorption vary); the sim
   is narrow (deterministic dynamics + sensor noise). The efficacy blind spot, measured.
5. **Insulin-sensitivity drift (weekly)** — real sensitivity drifts week to week; the
   virtual patient's parameters are fixed. STRUCTURAL.
6. **Post-meal-exercise counterweight** — crash rate falls with insulin-on-board; the
   model has no exercise input. STRUCTURAL (see `../fidelity_test.py` Probe A).

The scope is deliberately extensible: adding a signature is one function in
`signatures.py`. Candidates for v2 are noted in the report.

## Reproduce

```
python3 -m venv ~/.venvs/boost-insilico
~/.venvs/boost-insilico/bin/python -m pip install simglucose scipy matplotlib psycopg2-binary "setuptools<81"
cd fidelity_suite
~/.venvs/boost-insilico/bin/python gen_sim_cohort.py --days 21
~/.venvs/boost-insilico/bin/python run_suite.py
```

The DB must be reachable at `dbname=oref host=127.0.0.1 port=5432`.
