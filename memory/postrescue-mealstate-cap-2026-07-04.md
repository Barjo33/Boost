---
name: postrescue-mealstate-cap-2026-07-04
description: "2026-07-03 19:47 incident: V6 CONFIRMED 2.7U at BG 119 thirty min after nadir 40 (rescue-carb rebound) — rescue guard worked (fast-path only) but the non-meal cap's MEAL-STATE EXEMPTION discarded V1's hypo-restrained 1.05U. Fix (backtested SHIP 2026-07-04): suppress the exemption when recentLowBG45Min < 75 — meal states capped at v1WouldDose for 45 min post-low. 27% of removed insulin sat pre-low (worst-priced found); cost 10% genuine meals @ 0.15U median."
metadata: 
  node_type: memory
  type: project
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**The 2026-07-03 19:47 incident + post-rescue meal-state cap (shipped 2026-07-04).**

Anatomy: hypo nadir 40 @19:17 BST (pump suspended, IOB≈0), unannounced rescue carbs ~19:30, rebound +31 mg/dL/5min. Fast-path rescue guard (recentLowBg≥80) correctly blocked TWO fast confirms (BG 57, BG 88). Normal path CONFIRMED at 19:47 BG 119 → 2.7U (budget 1.17 × 1.8 × knob 1.3 × vf 1.0, under confirmedCap 3.0). **V1's 45-min post-rescue tier guard (recentLowBG45Min<75 blocks T3/T4/T5) HAD restrained base to 1.05U — but `overrideDose = if (inMealState) finalDose else min(finalDose, v1WouldDose)` exempted CONFIRMED and discarded it.** The 2.7U tripped the 2.5U cumulative cap → V6 silent for the next hour. Outcome 181→nadir 81, zero margin. Same shape as the incident that motivated V1's guard (1.8U@112 after nadir 54 → crash 35). Cleared: yesterday's fast-path retune irrelevant (fast path never fired); early-confirm only moved the shot 1 cycle earlier.

**Normal-path hypo protections are all soft by design**: score penalty floored (0.782 vs 0.55 bar), mlHypoRisk reads rising BG as safe (0.033), minGuardBG inflated by rebound delta (146), IOB brake empty. The gap was the exemption, not the guard.

**Fix**: `inMealState && !inPostRescueWindow` where inPostRescueWindow = recentLowBG45Min < 75 — SAME source value + threshold as V1's tier guard (alignment load-bearing: inside the window v1WouldDose is itself hypo-restrained, so the cap inherits a hypo-aware limit). De-amplifies, never blocks; expires ≤45 min after the low; smaller commit shot preserves cumulative-cap headroom so V6 stages follow-on doses instead of being locked out. + boostV5_postRescueWindow RT field.

**SHIPPED**: experimental-local `c306241a35` (full: code + sim/guide/README docs + tests; in APK `Boost-V6-experimental-postrescuecap-2026-07-04.apk` on Drive). **dev: cherry-picked as `79a3e53220` PUSHED same day** (code + guide + tests only — README/simulator hunks skipped, dev still has pre-rework versions; RT.kt resolved to ONLY the postRescueWindow field since the confirmGate trio is held). dev now = 9d0a747584 + analyser cbcfb28c3d + this; future experimental→dev promotion supersedes both cherry-picks (same content).

**Backtest (SHIP verdict)**: 20.4% of ALL meal-state cycles are post-rescue (4.1/user-day). Era-honest removal 0.30 U/user-day; **27% of removed insulin sits directly pre-<70** (every other lever this review: 14–19%); post-rescue meal episodes double-dip at 33% vs 19% base. Cost: 10% genuine post-hypo meals, median 0.15U under-delivery, 0% double-dip, peaks ~228 even WITH amplification. Incident replay: 1.05U + preserved headroom ≈ right insulin (actual nadir 81 proves 2.7U was ~1.6U over). Shadow watch-items: delayed catch-up on genuine meals lands ≤2 cycles post-expiry; delivery distributions in windows should NARROW; audit via the new RT field on 07-10.

U200 flag (unresolved): Tim's 17:12 profile switch = "New U200 basal" — if the Dana cartridge is U200, pump-units understate insulin mass 2×; Tim to confirm how ISF/CR fold it in.

See [[early-dosing-audit-2026-07-03]] (the 24.9% low-IOB rebound slice this insulin lives in), [[v6-safety-review-2026-07-02]] (non-meal cap origin), [[recovering-highs-smb-rejected-2026-07-03]].
