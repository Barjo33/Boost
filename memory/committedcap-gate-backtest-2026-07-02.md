---
name: committedcap-gate-backtest-2026-07-02
description: "Backtest (2026-07-02) of the committedCap OBSERVING→CONFIRMED gate vs the oref TimescaleDB. First pass (max-inferred cap) looked like a ship-blocker (85% block) but that was a BAD inference; with the ACTUAL auto-config cap it blocks ~41% (roughly the trivial population) — defensible, not a ship-blocker. STUCK 14% is the watch-item."
metadata: 
  node_type: memory
  type: project
  originSessionId: d9e18601-4d49-42cd-9289-f1b24cf4e999
---

**STATUS: defensible, not ship-blocked (corrected 2026-07-02).** The `committedCap` OBSERVING→CONFIRMED gate (`prospectiveShot = budget × 1.8 × aggKnob > min(committedCapU, 0.8×confirmedCapU)`) is committed **local-only** on `Boost-V6-experimental` (`4bfd7bea32`, `Boost-AAPS-core` worktree), built + unit-tested OK, **not pushed**.

**Backtest** (`~/.../scratchpad/backtest_committedcap{,2,3}.py`, `oref.boost_decisions`, local TimescaleDB not live NS, 3,299 real CONFIRMED shots, 7 users):
- **The problem is real:** ~44% of confirms delivered <0.15U (trivial — the eventualBG→372 token-waste). A gate IS warranted.
- **FALSE ALARM corrected:** first pass inferred committedCap = `max(COMMITTED finaldose)` → 85% block, looked like a ship-blocker. That inference was WRONG — max grabbed outliers (user A "cap"=6.65U, impossible for a hard cap; caps also drift over 5 months). **Tim caught this: use committedCap ACTUAL, not max.**
- **With the ACTUAL auto-config cap** (`clamp(TDD/40, 0.25, 2.5)`; TDD is in the DB): **block 40.7%** (roughly the trivial population). Of blocks: FIZZLE 17% (token saved) + DELAYED 48% (real meal, shot re-qualifies within 1h) = 65% benign; **STUCK 470 = 14% of all confirms** (real rise held at OBSERVING 0.3×) — the watch-item, but partly appropriate since STUCK = budget/baseInsulinReq stayed low (low actual need).
- **Caveats:** committedCapU/confirmedCapU are NOT columns in boost_decisions (console_error only logs V1 caps). `TDD/40` is a LOWER bound on the real cap (auto = `max(SMB_p75, TDD/40)`), so real block rate likely **40–55%**. Shot is pre-velocity `budget×1.8` (matches the code gate faithfully).

**STUCK resolved (2026-07-02) — gate VALIDATED.** Full auto-config cap `max(SMB_p75(v1_units), TDD/40)` (= the real auto formula on real DB inputs, not a proxy) → block 41.0%, benign 65% of blocks, STUCK 478 (14.5%). Diagnosis via `sug_insulinreq` (=baseInsulinReq) at the STUCK confirm cycles: **81% had baseInsulinReq < 0.30U (median 0.00U, user A even −0.38)** — BG drifting up but oref wanted ~no insulin (IOB already covering), so a big CONFIRMED shot would be OVER-dosing. Holding at OBSERVING is CORRECT. Only ~2.8% of all confirms are STUCK-with-real-need (mostly user C, 42% low-need). → **the gate is defensible/shippable**; backtest now supports keeping `4bfd7bea32`. Scripts: `~/.../scratchpad/backtest_committedcap{,2,3,4}.py`.

**Telemetry pipeline (done):** AAPS commit on experimental (local, unpushed) adds `RT.boostV5_committedCap/confirmedCap` (emitted in `OpenAPSBoostV5Plugin`, RT is @Serializable → auto-flows to NS). `~/oref-investigations-boost-v2/extract/extract_boost.py` edited to ADD COLUMN + parse `boostv5_committedcap/confirmedcap`. Columns auto-create on next extract run; populate once cohort devices run a build with this telemetry (historical rows stay null → keep using the auto-formula caps for old data).

**Infra learned:** TimescaleDB `oref` @ 127.0.0.1:5432, table `public.boost_decisions` (per-cycle inputs+outputs incl. boostv5_state/budget/actionmult/finaldose/score/age, sug_eventualbg/target, delta_acceleration, ml_hypo_risk; ts_utc/ts_epoch; user_id tim/A–F). Update = `~/oref-investigations-boost-v2/extract/extract_boost.py` (NS devicestatus → idempotent upsert ON CONFLICT (user_id,ts_utc)); runner `backfill_all.sh` (reads `~/.config/boost_backtest/sites.json`, sequential + 5s pause for per-host NS rate limits — do NOT parallelise). Trio replay (Swift, golden-master) reads the same DB via `Trio/BoostPort/sim/export_boost_decisions.sh`. See [[dev-fix]], [[boost-mlhyporisk-usage-2026-07-02]].
