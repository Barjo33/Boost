# Brake-correctness audit — pricing the 34% BRAKE_SUPPRESS high-time

_Follow-up to `RESIDENCY_REPORT.md`, 2026-07-08. V6 telemetry, oref.boost_decisions, self+A–H. Reproduce: `brake_audit.py`._

## Question

The residency attribution found the composed brake owns ~34% of high-time and is foreseeable — but that is **proximate, not causal** ("the budget was crushed during the rise" ≠ "the brake was wrong"). This audit asks the causal question by **outcome**: on cycles where the brake genuinely suppressed a *wanted* dose while high (oref `insulinReq` > 0.05, composed `budget` < 0.10, BG > 170), what happened next, and in what IOB context?

Signals: the brake shows as **`budget` crushed to ~0** — `boostv5_actionmult` is only the per-state multiplier (0.3/0.4/1.0/1.8, never ~0), and `boostv5_floorwouldadd` is **100% NULL** in historical rows (the floor is too new to be logged, so it can't be priced directly here — a real limitation).

## Result

Brake-suppressed set: **135 cycles / 675 min** over ~6 weeks. State mix: OBSERVING 31%, IDLE 31%, RECOVERING 22%, COMMITTED 12%, CONFIRMED 4%.

| bucket | cohort share | meaning |
|---|---|---|
| **RIGHT_RESTRAINT** | **76%** | high IOB, no forward low — correct restraint; adding is the ~19%-pre-low recovering slice |
| **RIGHT_SAVEDLOW** | **13%** | a low (<70 within 3h) actually followed — the brake prevented it |
| HARMLESS_RESOLVED | 7% | low IOB, came back to range on its own |
| **WRONG_RECOVERABLE** | **3%** (20 min) | stayed high, low IOB, no low — the composed-floor's real target |

**Verdict: the brake is RIGHT 90% of the time** (76% correct high-IOB restraint + 13% saved a low). The genuinely recoverable slice — where a floor dose would have helped safely — is **3% (20 min over six weeks)**. And even that carries a price: the low-IOB high slice has a **12% forward-low rate** (n=590), so dosing it isn't free.

## What this changes

1. **The residency's "brake = 34% of high-time" was proximate over-attribution.** When you require that oref actually *wanted* insulin and the brake *crushed* it, the set shrinks to 675 min, and 90% of it was correct — mostly the brake correctly restraining at high IOB (the recovering-highs finding, again: the high tail is high-IOB, where adding causes lows). Much of the residency's 34% was high-episodes where oref didn't want more insulin anyway (IOB already covering).
2. **The composed floor's upside is small and bounded** — its recoverable target is ~3% of brake-suppression. This is consistent with the floor's standing characterisation as *a bounded defect-fix, not a selectivity-passing lever*; this audit quantifies that bound.
3. **The brake is not the lever.** Don't loosen it — 90% of its firing is correct, and 13% actively saved lows.

## Where the levers actually are (residency + audit together)

- **Lows dominate the addressable loss and are not a dosing-brake problem:** ACTIVITY 47% + RESCUE_OVERSHOOT 37% of low-time (stacking only 16%). → the Garmin HR/steps ingest + exercise protections, then rescue handling.
- **The addressable *high* loss is SIZING and TIMING, not the brake:** CAP_CLIP 15% + UNDERSIZED 9% are *not* foreseeable (sudden meal hits) → per-user cap sizing + V7 distributional sizing; LATE_CONFIRM 16% *is* foreseeable → confirm age-gate / score-ready.

## Caveats

- **Strict definition.** 675 min is the set where oref explicitly wanted insulin *and* the budget was crushed while high; it deliberately excludes highs where oref was content (IOB covering). It is the honest "did the brake wrongly block a wanted dose" set, not all high-time.
- **Counterfactual unprovable.** We can't show the WRONG_RECOVERABLE 3% would have resolved if dosed — the 12% forward-low price is the caution.
- **RIGHT_RESTRAINT** assumes high-IOB restraint is correct — grounded in the two-test finding that adding at high IOB prices ~19% into lows.
- **floorWouldAdd unavailable** in historical rows, so the floor's own injection couldn't be priced directly; that would need forward telemetry from a build that logs it.
