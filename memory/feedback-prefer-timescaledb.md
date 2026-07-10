---
name: feedback-prefer-timescaledb
description: "Tim's standing directive (2026-07-06): run analyses against the local TimescaleDB first — it's the much wider dataset (months, 9 boost users + 28 oref-cohort sites incl. 22 Trio, refreshed weekly) — and use direct NS pulls only for what the DB lacks (recent hours, fields not extracted)."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**Prefer the TimescaleDB over direct Nightscout pulls for analyses.** (Tim, 2026-07-06: "for this and others run against the timescale dB. it's a much wider dataset and should land a better score.")

**Why:** the DB holds months of deduped, schema-aligned data across the whole cohort (boost_decisions ~350k rows for tim/A–H since May; boost_cgm; oref_live for the 28-site cohort incl. 22 Trio users, accumulating weekly via the Sunday launchd job) — statistical power and cross-user comparability that a 1-site NS pull can't match. Tim's own HR history extends back to the Garmin era (May) in the DB vs ~9 days visible in a recent NS window.

**How to apply:** start every analysis with a DB coverage inventory (which columns, which users, which eras — dedup last-invoke per 5-min bucket); fall back to NS only for (a) hours newer than the last refresh, (b) fields the extractor doesn't capture (some steps/sleep fields, treatments detail), (c) sites not yet registered. If a needed field isn't extracted, consider wiring it into extract_boost.py (self-migrating schema) rather than repeatedly pulling NS. Registry: ~/.config/boost_backtest/sites.json (boost) + sites_all.json (all 37).

See [[migration-cohort-backtest-2026-07-06]] (weekly job), [[boost_timescaledb_v6_shadow_2026-06-29]] (original infra).
