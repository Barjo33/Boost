# Should auto-config re-derive on a schedule? — replay findings

**2026-08-03.** Scripts: `boost_autoconfig.py` (verbatim Python port of `BoostV5AutoConfig.compute()`,
selftested), `redrive_replay.py`. Reports: `REDRIVE_REPORT.md` (28-day window, the proposal) and
`REDRIVE_REPORT_w14.md` (14-day, production's current `LOOKBACK_DAYS`). Data: local TimescaleDB
refreshed to t=now, 8 users, plus a new `boost_treatments` table (manual-vs-SMB bolus split, which
`boost_decisions` never carried — wired into the extractor as `boost_treatments.py`).

---

## The hypothesis this was built to test — and its fate

**Predicted:** `confirmedCap = max(manual p90, SMB p95)` and `committedCap = max(SMB p75, TDD/40)`
read the delivered-SMB distribution, which Boost itself clips at those very caps. Re-running the
derivation would therefore ratchet the caps monotonically down, with no restoring force.

**Found: the mechanism is real, but it is almost never on the binding path.**

| cap | binding term across 37 derived windows |
|---|---|
| `committedCap` | **TDD/40 in 36/37 (97%)**, SMB p75 in 1 |
| `confirmedCap` | **manual-bolus p90 in 32/37 (86%)**, the 1.5 U floor in 4, SMB p95 in 1 |

Δ`committedCap` correlates **0.996** with Δ TDD. Δ`confirmedCap` correlates **0.93** with Δ manual-bolus
p90. Repeated re-derivation is a TDD tracker and a meal-bolus-habit tracker — both exogenous, neither
censored by Boost's caps. This holds specifically in the six windows that are genuinely Boost-era
(`boostv5_active` share > 0.7), which is where the self-reference would have to bite.

The ratchet claim was **SOLID as algebra and wrong as a prediction about this cohort.** It stays on
the record as the failure mode to re-check whenever an anchor weakens (below), not as a reason not
to re-derive.

## Where the mechanism does show up

When the SMB term *does* bind, the censoring bias is large and in the predicted direction. Deriving
from the uncapped desired shot (`budget × actionMult × velocityFactor`, restricted to cycles that
actually dosed) instead of the delivered SMB gives:

- `confirmedCap` **+0.86 U** higher on average (95% CI +0.03, +1.90)
- p95(SMB) **+1.28 U** higher (95% CI +0.46, +2.23)
- `committedCap` +0.02 U (CI +0.00, +0.06) — nil, exactly as the TDD/40 anchoring predicts

For two users the gap is the whole cap: B 1.5 → 5.3 U, C 1.5 → 4.36 U.

**The exposed profile is the user who doesn't announce meals.** tim runs a median of 9.5 manual
boluses per 28-day window — sitting on the `MIN_MANUAL_BOLUS_SAMPLES = 10` gate. His `confirmedCap`
flips 1.5 ↔ 2.0 between windows because the sample-count gate opens and closes, not because anything
about his dosing changed. With no manual anchor his cap falls to the 1.5 U floor, and the desired-dose
source doesn't rescue him either (his desired p95 is 1.42, still under the floor).

## Is there anything worth tracking?

`drift` = |last window − first window|, ~5 months apart. `noise` = half-width of that window's own
day-block bootstrap band (days are the resampling unit; CGM within a day is far too autocorrelated
for an iid bootstrap).

| knob | drift ÷ noise (28d) | 95% CI | verdict |
|---|---|---|---|
| `committedCap` | **2.72** | 1.15 – 4.59 | real drift — worth tracking |
| `cumulative60` | **2.33** | 1.06 – 3.63 | real drift — worth tracking |
| `primerCap` | 2.31 | 0.86 – 3.81 | overlaps 1 |
| `confirmedCap` | 2.13 | 0.83 – 3.86 | overlaps 1 |
| `aggression` | 1.57 | 0.00 – 3.21 | overlaps 1 — noise |
| `hypoCaution` | 1.00 | 0.11 – 1.89 | overlaps 1 — noise |

The knobs with real five-month drift are exactly the TDD-anchored ones. The glycaemic knobs
(`hypoCaution`, `aggression`) do not move more than the noise of measuring them — which is the
online-slider conclusion arriving a second time by a different route.

## What a naive re-run would cost

| | 28-day window | 14-day window (production's `LOOKBACK_DAYS`) |
|---|---|---|
| cap changes per user per 6 months | 8.5 | 18.0 |
| share of changes beating their own noise band | 59–65% | 46–54% |
| `confirmedCap` drift ÷ noise | 2.13 | 1.45 |

Window width is the single biggest lever: 28 days roughly halves the churn and materially raises the
share of changes that mean something. Per-user revert-within-two-windows ran 0–80%, several users at
50% — the same churn signature that killed the online cap-stepper, though here it is driven by
sampling noise rather than by an outcome-triggered controller.

Harm price of the six `committedCap` lowerings that actually occurred: removed insulin's pre-low
share minus the user's own baseline pre-low share = **−0.004 (95% CI −0.098, +0.097)** — neutral. The
lowerings neither prevented lows nor caused them. n = 6; provisional.

## The finding that isn't about re-derivation at all

The split-half A/A runs two independent 14-day derivations inside each 28-day window — 14 days being
exactly what the shipping one-shot auto-config uses. Between two halves of the *same* window, with
the same person and no drift to find:

| knob | mean abs. difference | halves that differ at all |
|---|---|---|
| `confirmedCap` | **0.69 U** (CI 0.30 – 1.17) | 63% |
| `cumulative60` | 0.80 U (CI 0.41 – 1.29) | 87% |
| `hypoCaution` | 0.21 (CI 0.10 – 0.34) | 37% |
| `committedCap` | 0.09 U (CI 0.05 – 0.12) | 93% |

So the **currently shipping** day-1 derivation carries roughly ±0.7 U of sampling noise on
`confirmedCap`. That is a fact about what every migrating user already gets, independent of whether
anything periodic ever ships, and it argues for widening `LOOKBACK_DAYS` on its own.

## Design implications — revising the earlier sketch

1. **Drop the "derive caps from the pre-cap desired dose" fix from the critical path.** The anchors
   already do that job in 97%/86% of windows. Keep the idea only for the no-announce profile, and
   note it doesn't rescue that profile either (the floor binds first).
2. **Re-derive on 28+ days, not 14.** This is the load-bearing parameter, and it improves the
   *existing* one-shot derivation too.
3. **Re-derive the TDD-anchored knobs; freeze the glycaemic ones.** `committedCap` / `cumulative60`
   have drift that beats their noise. `hypoCaution` and `aggression` do not — leave them one-shot.
4. **Deadband from the measured noise floor**, so a re-run cannot fire on sampling error. The
   bootstrap half-widths are directly usable as on-device constants: `committedCap` 0.07 U,
   `primerCap` 0.06 U, `hypoCaution` 0.16, `confirmedCap` 0.47 U, `cumulative60` 0.54 U,
   `aggression` 0.025.
5. **Fix the `n >= 10` cliff.** For a low-announce user the manual-bolus gate toggling is itself a
   source of cap churn. Hysteresis on the gate, or a longer window so the count is reliably met.
6. **Keep the asymmetry unchanged**: tightenings apply, raises stay behind the TBR/`<54` raise-guard,
   user-tuned knobs are never touched.
7. **Log which term bound each cap.** One string per derivation makes the anchor assumption
   monitorable on-device instead of re-litigable only by backtest.

## Limitations

- TDD telemetry only exists from ~Feb/Mar 2026, so the trajectory is 3–6 windows per user over five
  months, not years.
- Only about one window per user is genuinely Boost-era; the anchor result holds there, but on six
  windows.
- The dose-chain fields (`velocityFactor`) date from 2026-07-10, so the desired-vs-delivered
  comparison rests on 6–23 days per user.
- No counterfactual BG is claimed anywhere. Cap changes are priced against observed lows only.

**Confidence.** Anchor result: SOLID for this history, PROVISIONAL as a claim about the Boost era.
Drift-vs-noise, churn, and the split-half noise floor: PROVISIONAL (n = 8 users, bootstrap CIs given).
Harm price: PROVISIONAL (n = 6 lowerings). The ratchet mechanism itself: SOLID as algebra, refuted as
the dominant effect here.

**What would flip the verdict:** a user with no manual boluses *and* a falling TDD — both anchors
weakening together — or a cohort that stops announcing meals after migration, which puts the SMB p95
term back on the binding path. Implication 7 exists to catch exactly that.
