---
name: recovering-highs-smb-rejected-2026-07-03
description: "ALL THREE post-confirm-high levers REJECTED on cohort data (2026-07-03): RECOVERING standard-SMB, sustained-delta re-engage, AND committedCap raise. Common cause: the stuck tail is tiny (2.5%), high-IOB by nature, and every added-insulin mechanism feeds lows at base rate (14-17%); cap raise also SUPPRESSES confirms via the gate floor. Read before proposing ANY 'dose more after a confirm' mechanism."
metadata: 
  node_type: memory
  type: project
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**RECOVERING-highs standard-SMB proposal — REJECTED by data (2026-07-03).**

Tim asked: when BG stays high after a CONFIRMED and V6 sits in RECOVERING, could we dose oref-style (portion of basal as SMB)? Cohort backtest (606 episodes, 7 users, 271 user-days, deduped `boost_decisions`):

- 77% of episodes self-resolved <160 within 2h on existing IOB; 19% ended <70 within 3h — and **112 of 115 lows came from the self-resolving group** (BG flat-or-rising at dose time, crashed anyway = V1's late-tail cascade signature, the exact failure RECOVERING exists to prevent).
- Genuinely addressable (stuck + falling IOB + never re-engaged): **26/606 (4%), ~8.4U total (~0.03 U/user/day)**.
- Counterfactual dosing to v1WouldDose: +227U total, +32.8U into episodes that ended low (21 lows deepened >0.5U, 11 by >1U). Benefit:harm ≈ 1:4 wrong direction.
- Spot check: Tim 2026-06-04 — CONFIRMED 18:19, climb to 255 by 19:39, V6 held back, then 255→59 free-fall on 3.4U IOB. Extra insulin would have made it severe. Machine correct by outcome.

**Structural point:** RECOVERING persists only while delta ≥ 0 AND score ≥ 0.18 (MealHypothesis.kt:260-278); falling delta exits to IDLE where the non-meal cap gives V1-parity. So "stuck in RECOVERING while high" = still-rising, score-corroborated — the most dangerous population for extra insulin. Median episode = 10 min.

**Re-engage tuning ALSO backtested and rejected (same day, Tim asked for it).** Sustained-delta re-engage (delta>3 ×N cycles, offset>20 → COMMITTED) tested in 10 guard variants over 1,501 RECOVERING runs. STRUCTURAL failure: the only guard that refuses the tim 06-04 crash episode is IOB-headroom (G2, iob > p75-at-CONFIRMED), but high IOB is the DEFINING property of the stuck episodes the rule targets — so the safe family catches 1/26 residual episodes (0.00U realistic recovered) and the effective family (7/26) fires on 06-04 (+2.03U before the 255→59 plunge) with 15% of firings into <70-within-3h (base rate — guards give zero selectivity on lows). Every variant fails ≥1 leg of the bar. Existing accel re-engage kept unchanged. NOT implemented; if ever revisited, only as shadow telemetry (would-fire logging) — the least-bad variant was G1b+G2+G3 N=3 BG>160 (never fires tim-class, but catches ~nothing). Target population ≡ harm population on the IOB axis — remember this before re-proposing ANY "dose more in RECOVERING" mechanism. Scripts: reengage_backtest.py / reengage_variants.csv (session scratchpad, ephemeral).

**Third lever — committedCap raise — ALSO REJECTED (same day, Tim proposed it after the re-engage verdict).** Era-aware backtest (caps only operative from ~06-01 tim / ~06-17 cohort; tim's auto-config cap = 0.40): cap binds on 42% of dosing COMMITTED cycles (tim 39%, ~4 clips/day) BUT (1) 72% of meal-phase under-delivery vs V1 happens on cycles BELOW the cap (budget/velocity/gates, not the ceiling); (2) clipped phases → stuck-high 7% vs unclipped 8% — premise "cap-starved meals cause post-confirm highs" not in the data; (3) extra insulin from ×1.5/×2 lands pre-low at 14–17% (base rate, above the ≲10% bar); (4) CONFIRM-FLOOR COUPLING: floor = min(committedCap, 0.8×confirmedCap), so raising the cap BLOCKS more confirms — +18% of tim's confirms newly blocked at ×2 — actively counterproductive. Auto-config p75→p85 sits in the same tested band, same verdict.

**Genuine actionables that survived:** (a) cohort users A/C/D ran hardcoded 0.25 BELOW their auto-config formula caps (1.0/0.6/0.55) for most of the window — config-hygiene fix, frame it as that; (b) WATCH-ITEM: the shipped confirm gate blocks 35–56% of fresh confirms (reconstructed; matches the 07-02 backtest's ~41%) — cap/gate telemetry is live in NS since e29630409b, review a week of live data to check it blocks the RIGHT confirms.

See [[session-2026-07-02-boost-fixes-backtests]] (non-meal cap context), [[boost_v1_daytime_lows_2026-06-11]] (the late-tail failure mode), [[committedcap-gate-backtest-2026-07-02]] (the gate this couples to).
