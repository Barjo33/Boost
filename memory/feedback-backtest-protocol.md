---
name: feedback-backtest-protocol
description: "Tim's standing backtest protocol (2026-07-06): (1) source = TimescaleDB, refreshed to t=now first (backfill_all.sh / incremental --since) if recent data missing; (2) backtests = local Python scripts; (3) reports + results stored locally; (4) report COMMITTED to the repo (Boost-AAPS-core/backtesting/) with results contained. No more scratchpad-only scripts — evidence for shipped dosing changes must survive the session."
metadata:
  type: feedback
---

**Backtest protocol (Tim, 2026-07-06) — every backtest follows this:**
1. **Source = TimescaleDB** (`oref` @127.0.0.1:5432). If data is missing up to t=now, refresh FIRST: `~/oref-investigations-boost-v2/extract/backfill_all.sh` (or targeted `extract_boost.py --since` for one user). Never analyze stale windows silently.
2. **Backtests are local Python scripts** — written to run standalone (not notebook fragments), reproducible.
3. **Reports + results stored locally** alongside the scripts.
4. **Report committed to the repo**: `Boost-AAPS-core/backtesting/` (scripts under `backtesting/scripts/`, reports under `backtesting/reports/` or as the established top-level *_REPORT.md files) — with the RESULTS CONTAINED in the report (numbers in the committed markdown, not just pointers). Commit rides the normal experimental→dev flow.

**Why:** the 2026-07 week's scripts (re-engage, cap-raise, post-rescue, early-dosing, phase3-floor, migration cohort ×4, forensics) all lived in session scratchpads — evidence for SHIPPED dosing changes that would have evaporated. Apply Test A/Test B framing ([[two-test-bar-2026-07-06]]) inside every report.

**How to apply:** at backtest start: check DB freshness (max ts_utc per relevant user) → refresh if stale → write script under backtesting/scripts/ (in the repo worktree) → run → write report with results → commit. Session scratchpad is for intermediates only.
