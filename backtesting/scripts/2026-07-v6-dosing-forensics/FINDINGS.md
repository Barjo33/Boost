# V6 vs V1 dosing forensic — why V6's meal window loses tight-range, and how to fix it

*2026-07-19. Four parallel analyses (`f1`–`f4`) over the transition window (18 Jun–12 Jul, 5 users
with V1 sleep telemetry, meal-dosing-active cycles only, seasonality held). Same-cycle dosing +
matched-state + meal-onset-aligned trajectories.*

## The question
Outcome analysis found V6's meal-dosing window has −7.5 TING vs the previous Boost gen (V1),
season-controlled, all 5 users. Why — and how to improve?

## What each forensic showed
- **f1 (decomposition):** V6's genuine dose divergence over V1 concentrates in **CONFIRMED** state
  (+0.135U/cyc, ~all the net excess; shots avg ~1.98U) and **fast-rise** (+0.043U); it **restrains at
  high IOB** (−0.010U). Amplification is aggression/velocity, survives caps+brakes.
- **f2 (matched-state forward):** at matched pre-state (BG×trend×IOB), V6 and V1 forward trajectories
  are **near-identical** — forward BG range Δ+0.3, swing Δ+0.9, **<70 Δ−1.0 (V6 fewer lows)**, only
  **>180 Δ+3.4 (V6 slightly more highs)**. V6 doses **slightly LESS per matched state** (−0.03U). ⇒
  the "+11% more insulin" (unmatched) is a **state-distribution artifact**, not per-state aggression;
  V6 is if anything more conservative per state. NOT a variance/overshoot story.
- **f3 (shots):** V6 shots crash <70 at ~14–17% regardless of over-dosing; worst contexts BG<150
  (17%) and CONFIRMED (17%, 1.98U). **No V1 shot-crash baseline** → can't call V6's shots worse.
- **f4 (meal-onset-aligned, the tiebreaker):** meals **rise identically** (onset ~146, peak ~170 at
  +25min for both) but **V6 UNDER-RECOVERS**: post-peak it plateaus at 143–150 while V1 returns to
  132. **BG@+180: V6 143 vs V1 132.** Per-user peaks similar (Δ −8..+9) → it is the descent, not the
  peak.

## Mechanism (resolved)
**V6's high-IOB brake over-suppresses the post-meal RECOVERY.** After the peak, IOB is high and
glucose is descending through 150→145; V1 keeps making small corrections that nudge it into tight
range (132); V6's composed brake / high-IOB restraint shuts those corrections off, leaving a
**mild-high plateau (143–150) for hours** = the lost TING. Consistent with everything: lows unchanged
(f2 −1%), variance unchanged (f2), doses less per state (f2), the register's brake trade-off, and the
phase-3 brake-compounding note ([[phase3-brake-compounding]]).

My earlier "V6 adds variance" reading was **wrong** — corrected to under-recovery.

## How to improve (data-grounded, but in the danger zone)
Let V6 keep V1's small post-meal recovery corrections in the **140–160, descending, IOB-present**
window instead of braking them off. **Crucially the data shows this is achievable low-safely: V1
reaches 132 at the SAME (actually lower) low rate as V6's 143** — so the tight-range is recoverable
without paying in lows. This is the exact context the register says feeds lows
([[recovering-highs-smb-rejected-2026-07-03]]), so it MUST be shadow-first — but V1's own recovery
behaviour is a proven-safe target to aim at, which the earlier rejected RECOVERING-SMB levers lacked.

Natural discriminator: the KAIROS Twin's calibrated 30-min floor (`lo30`) — keep correcting the 145
plateau when `lo30` says no low is coming; withdraw when it does. Ties the recovery fix to the one
Twin signal that survived its gates.

## Caveats
5/7 users (H,E lack V1 sleep telemetry); within-window V1/V6 split is a flash-date (≤~2wk internal
gap; anything changed at the flash rides along); meal-onset detection is a CGM proxy; C has only 7 V6
onsets. The mechanism is consistent across f1–f4 but the magnitude carries these confounds.
