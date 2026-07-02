---
name: boostactive-nightgate-backtest-2026-07-02
description: "DB backtest (2026-07-02) VALIDATES the boostActive←night-mode change (c94c5c72d6): suppresses ~47% of V6's excess-over-V1 amplifications, ALL at night/lie-in, ALL unannounced (COB=0); zero announced meals or daytime meals touched. Clean safety win."
metadata: 
  node_type: memory
  type: project
  originSessionId: d9e18601-4d49-42cd-9289-f1b24cf4e999
---

**VALIDATED (2026-07-02).** The boostActive←night-mode-sleep-period change (`c94c5c72d6`, local on `Boost-V6-experimental`, gates the V6/V5 SMB override on `!isInNightSleepPeriod()`) backtested against `oref.boost_decisions` (local TimescaleDB, not live NS). Counterfactual "if V6 were active", measuring V6 finaldose − v1_units (excess V6 would dose over V1) that the night gate would suppress.

**Result — clean safety win:**
- 1,288 / 2,744 (47%) of V6 amplifications (excess>0.1U) fall in night/lie-in → suppressed → fall back to conservative V1 oref1. Avg excess 0.66U, max **6.35U**, **205 cases >1U** over-dose prevented. The 1.55U incident is representative, not exceptional.
- **100% of suppressions were unannounced (COB≤1)** — deep-night(842)+lie-in(192)+22-23(254), all COB=0. ZERO announced meals suppressed.
- **Daytime 09-21: 0 suppressed** — all 1,456 daytime amplifications preserved. Doesn't touch daytime meal response.
- COB is meaningful for 5/7 users (announce carbs daytime); B is UAM-only + tim mostly-UAM (COB uninformative) — but their suppressed doses were the LARGEST (B max 6.35U), so suppressing is most clearly right there.

**Method/caveats:** local hour from `ts_utc` (stored offset = server-local +01, proxy for user-local; European cohort ±1h; deep-night robust, 22:00 boundary softest). Assumed default night window 22:00-07:00 + enabled + 2h lie-in (real gate uses per-user config + HR sleep-state extension not modelled → real suppression likely slightly MORE). Shadow users (V6 not active) → counterfactual.

**Status:** all 3 experimental local commits now data-supported — `c94c5c72d6` (night gate, this), `4bfd7bea32` (committedCap gate, [[committedcap-gate-backtest-2026-07-02]]), telemetry `e29630409b`. **None pushed** (Tim's call). See [[dev-fix]].
