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

---

# Addendum — sizing `confirmedCap` for a hands-free user

Raised the same day: Boost targets zero manual bolusing, so anchoring `confirmedCap` on
`p90(manual boluses)` is anchoring on a behaviour the system exists to remove. For an
older-Boost migrant or any hands-free setup the term is absent by design and the fallback
`p95(all SMBs)` takes over. Study: `anchor_study.py` → `ANCHOR_REPORT.md`.

## The fallback is not sized for the job

`p95(all SMBs)` comes out at **0.43× the cap the user actually runs** (95% CI 0.35–0.70)
and **0.21× the shot the engine actually wanted** (R1). The reason is the population, not
the percentile: IDLE / OBSERVING / COMMITTED micro-doses are the large majority of SMBs, so
the 95th percentile of the pooled distribution still sits inside the micro-dose mass. For
tim — the closest thing the cohort has to a hands-free user — it returns **1.0 U** against
CONFIRMED shots whose p90 is 2.25 U and a cap he set himself at 3.0 U.

## "Obviously large boluses" — right target, wrong selector

Selecting by size instead of type does **not** fix it. `p90(SMBs above the user's own
median)` scores **0.43× the operative cap** — indistinguishable from the current fallback,
because the above-median SMB pool is still mostly holds. `p90(all boluses, type-blind)`
is 0.47×. Size-based selection cannot separate a confirm shot from a hold, because the
distributions overlap.

Selecting by **state** does separate them: `p90(CONFIRMED-state shots)` scores 1.00× the
operative cap (CI 0.84–1.07).

## But every delivered-dose statistic is censored by the cap it would set

That 1.00 is largely tautological. The top CONFIRMED shots sit at the live cap for six of
eight users (`clip_conf_p90` = 1.00 for A/B/C/D/E, 0.89 for H). For the two users whose
confirm shots are *not* pinned to the cap — tim (0.33) and F (0.18) — `conf_p90` is only
**0.67–0.75× the cap they actually run**. So anchoring on CONFIRMED shots would shrink a
well-configured user's cap by about a third, and for everyone else it just reads their own
cap back. This is the ratchet from the main study, now on the binding path: moving the
anchor onto delivered doses is exactly what would arm it.

## What is left once you exclude the censored signals

Only two quantities live outside the cap's own feedback loop: **manual boluses** and **TDD**.
Hands-free removes the first. That leaves TDD as the only uncensored anchor available — and
it is already how `committedCap` is anchored (TDD/40).

| candidate | ÷ operative cap | uncensored? | needs announcing? |
|---|---|---|---|
| `p95(all SMBs)` — current fallback | 0.43 [0.35, 0.70] | yes | no |
| `p90(large SMBs)` — size-selected | 0.43 [0.35, 0.73] | yes | no |
| `p90(CONFIRMED shots)` — state-selected | 1.00 [0.84, 1.07] | **no** | no |
| `p90(manual bolus)` — current primary | 1.37 [1.16, 1.96] | yes | **yes** |
| **TDD/10** | **1.14 [0.90, 1.62]** | **yes** | **no** |
| TDD/8 | 1.42 [1.14, 2.02] | yes | no |

**TDD/10 is the only candidate that is both uncensored and available hands-free**, and its
ratio to the caps people actually run overlaps 1. It is also dimensionally consistent with
the rest of the derivation: `confirmedCap = TDD/10` is exactly 4× `committedCap = TDD/40`.

## The manual anchor is also mis-specified for announcing users

`p90(manual bolus)` sits at **0.87× the whole-meal episode cost** but **1.37× the cap users
actually run** (CI 1.16–1.96, excludes 1). That is the tell: a manual bolus measures the
*meal*, while `confirmedCap` bounds one *shot* in a sequence that Boost deliberately splits
across a confirm plus holds. So the current anchor over-sizes the cap even for the users it
was designed for — it is not merely unavailable hands-free, it is measuring the wrong thing.

## Proposal (untested — this is a spec, not a result)

Replace the manual/SMB-p95 pair with the exogenous anchor:

    confirmedCap = clamp(TDD/10, 1.5, 7.5)

keeping `p90(manual bolus)` only as an optional additional `max` term for announcing users,
and only if a within-user trial shows the 1.37× over-sizing is wanted rather than tolerated.

Per-user effect against today's derived value: A 7.50→4.36, E 5.06→2.97, H 5.06→3.64,
F 4.50→3.71, tim 2.00→1.41 (all reductions); C 3.25→3.38, B 3.60→5.67, **D 1.50→5.30**.

D is the warning. He is the most hypo-prone user in the cohort (TBR<70 ~9–10%) with a high
TDD, and a pure TDD anchor is blind to glycaemia. The existing raise-guard already blocks
this — a dose-cap raise is held whenever 14-day TBR<70 > 4% or <54 ≥ 1% — and D trips it by
a wide margin. That guard is load-bearing for this proposal, not incidental to it.

**Confidence: PROVISIONAL.** n = 8 users, one era, CIs wide and several overlapping 1. The
censoring stratification rests on two users. Nothing here is a reason to change a shipped
cap without a pre-registered within-user trial.

---

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
