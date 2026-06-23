# Boost V5 vs V1 — what changed, and what difference it makes

*With festival data, 17–22 June 2026. Companion chart: `Boost-Festival-Summary-2026-06-17_22.pdf`.*

> Experimental AndroidAPS fork; nothing here is medical advice. On the developer's build V5 was the
> **acting** engine; the V1 figures are the logged `V1 would=` counterfactual (what plain Boost would
> have done on the same cycle), so the two can be compared cycle-for-cycle.

---

## The one-line difference

V1 decides **cycle by cycle** — every 5 minutes it re-reads BG/trend/IOB, picks one of eight response
tiers, and sizes an SMB. It has no memory of what it just did. **V5 carries a meal *hypothesis*
across cycles** (`OBSERVING → CONFIRMED → COMMITTED → RECOVERING`) and scales dosing to its
confidence, then deliberately winds down as insulin takes hold. Same engine underneath (basal,
DynISF, predictions, every safety gate); V5 replaces only the *SMB decision*.

---

## What actually changed

| | V1 (current) | V5 |
|---|---|---|
| **Decision model** | 8-tier if/else ladder, re-evaluated each cycle | continuous 0–1 meal score driving a 5-state machine |
| **Meal detection** | threshold tiers (binary "eligible or not") | weighted score: BG delta, acceleration, ML meal-likelihood, time-of-day, recent-low penalty, exercise, sustained-rise |
| **Dose shaping** | size the tier that fires | confidence-scaled: small while OBSERVING, catch-up at CONFIRMED, sustain at COMMITTED, **back off in RECOVERING** |
| **Over-dose guards** | per-tier caps | an **aggression budget** (hard ceiling per burst) + a **deceleration brake** that eases off the moment BG stops accelerating |
| **ML** | scores logged only (observational) | **hypo-risk score throttles the aggression budget** (higher modelled risk → less insulin allowed) |

**How those translate to behaviour:**

1. **Graduated, not all-or-nothing.** V1 either fires a tier or doesn't; a near-miss pattern gets
   nothing. V5's score accumulates, so a slow or ambiguous rise still builds toward a response
   instead of falling off a threshold cliff.
2. **Cascade control is the headline.** The aggression budget + deceleration brake stop V5 stacking
   correction on correction once insulin is already working — directly targeting V1's
   stack → overshoot → crash failure mode.
3. **Hypo-aware by construction.** The ML hypo-risk score and a recent-low penalty pull dosing back
   *before* a low, rather than only reacting after.
4. **It winds a meal down.** RECOVERING tapers as IOB acts, instead of V1 re-deciding from scratch
   and potentially re-dosing a meal that's already handled.

---

## What the festival showed (17–22 June, ~18–28k steps/day)

**Glycemia (6 days pooled, sensor artifact removed):**

| metric | value |
|---|---|
| mean glucose | 131 mg/dL (7.3 mmol/L) |
| TIR 70–180 | **82.7%** (Thu–Mon, the festival proper, 84–89%) |
| time < 70 | 3.2% |
| time < 54 | 0.8% |
| time > 180 | 14.1% |
| TDD | ~13–17 U/day (Mon 22 spiked to 22 — see below) |

Five active festival days held **84–89% TIR on very high activity** — a strong result for an
experimental closed loop under that much exercise.

**V5 vs V1 dosing — the honest version.** On the diverging cycles V5 withholds correction SMB at
highs (e.g. BG 240, RECOVERING: V5 0.75 U vs V1-would 1.65 U), and it does so **one-directionally
and consistently every day**. But the headline must be stated carefully:

- The "V5 ≈ half of V1" figure is **correction-SMB only**, on the cycles where they differ.
- **Basal (~6 U/day, ~40% of TDD) is identical under both** — it's the same engine basal.
- So in **total-daily-dose** terms the difference is **modest, not 2×.** V5 is meaningfully gentler
  on high-corrections, while overall insulin is broadly similar.

**Where the genuine lows came from** (and what fixes each — none of them carb logging):

- **Thu 18 — exercise crash (130 → 39).** A correction SMB fired into an already-falling,
  activity-driven BG. *Addressable by an active activity-load ISF factor* (raise ISF on high-step
  days → smaller correction). Currently shadow-only.
- **Sun 21 ~17:00 (→ 64).** **V5 dosed *more* than V1 here** (1.4 U vs V1-would 0.85 U): a CONFIRMED
  meal commit overshot on a rise that fizzled (158 → 178 → reversed). The one place V5's catch-up
  was too eager — *addressable by tuning the CONFIRMED commit / deceleration brake on a stalling
  rise*, not activity.
- **Sun 21 02:00 & Mon 07:00–10:00** were **not dosing faults** — an overnight carb-burn drift (loop
  already suspended), and a **pump-detachment** high+manual-rescue+exercise crash (the loop dosed 0
  the whole way down once reattached). And a chunk of Sunday night's apparent low was a **dying-sensor
  artifact** (false 3.7s; replacement sensor read 6.4 on engagement).

---

## Chart guide (`Boost-Festival-Summary-2026-06-17_22.pdf`)

1. **TIR by day** — stacked bands; festival days sit ~84–89% green.
2. **Daily insulin & mean** — TDD vs basal bars + mean-glucose line; shows TDD steady ~13–17 U and
   how much of it is shared basal.
3. **Activity vs time-low** — steps against % time < 70; the activity-heavy days are where the
   (dose-driven) lows cluster.
4. **V5 vs V1-would SMB** — per-day correction totals; V5 consistently below V1. *(SMB only — not
   TDD; basal is shared.)*
5. **6-day AGP** — glucose by time of day, median + IQR; the overnight band sits comfortably in range.
6. **Period summary** — pooled TIR donut + headline stats.

*Note on the Wed 17 column:* pre-festival baseline day (TIR 66%, mean 144) — this was the day the
DynISF velocity preference was found set to 0 (flat, over-sensitive ISF making the loop timid at
highs); corrected after.

---

## Bottom line

V5 keeps everything V1 does well and replaces the dosing decision with a meal-aware, confidence-scaled,
hypo-throttled state machine. On six real high-activity days it held **~83–89% TIR at a normal total
daily dose**, distinctly gentler on high-corrections than V1 would have been, with the genuine lows
traceable to specific, fixable causes (exercise-into-correction; one over-eager meal confirm) rather
than to the design. It remains **shadow / pre-alpha for other users** — V1 still doses; V5 logs what
it would do — which is how that gentler profile gets confirmed across more people before it ever
drives a pump.
