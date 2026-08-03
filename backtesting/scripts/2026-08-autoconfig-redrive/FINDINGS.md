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
censored by Boost's caps. It holds in the V6-era windows (`boostv5_active` share > 0.7) as well as
across the record.

> **Correction (same day).** An earlier version of this section treated the pre-V6 windows as an
> exogenous baseline because they predate the migration. That is wrong: **V1 is Boost**, so every
> window here is Boost's own output under some generation's dose ceilings, and the censoring concern
> spans the whole history rather than only the last window per user. It does not change the
> binding-term measurement — which term binds is independent of what generated the SMBs — but it
> removes the "clean baseline" reading and raises a separate migration question, answered in the
> second addendum.

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

# Second addendum — what the migration actually inherits from V1

Every user in this cohort was already running Boost before V6. So the question is not "how does
auto-config read a foreign algorithm's history" but "how does it read an **earlier Boost
generation's** history". Study: `v1_migration_check.py` → `V1_MIGRATION_REPORT.md`.

## V1's meal doses are not hiding from the percentile

V1 tiers its meal response in telemetry (`UAM_BOOST`, `UAM_HIGH_BOOST`, `PERCENT_SCALE`,
`ACCELERATION` vs plain `REGULAR_OREF1`), so the obvious worry is that `p95(all SMBs)` blurs them
away. It does not:

**p90(V1 meal-tier shots) ÷ p95(all V1 shots) = 1.04** (range 0.88–1.67, CI 0.99–1.29).

V1's meal shots are the same size as its ordinary ones. There is nothing for a tier-aware percentile
to recover, because V1 did not concentrate a meal into a large shot in the first place.

## The defect is architectural, not statistical

V1 spreads a meal response across many moderate SMBs; V6 concentrates it into one CONFIRMED shot plus
holds. Same meal, different shape:

| | median | range | CI |
|---|---|---|---|
| p90(V6 CONFIRMED shot) ÷ p95(all V1 shots) | 2.12 | 0.80–3.33 | 1.69–2.71 |
| p90(V6 **desired** confirm shot) ÷ p95(all V1 shots) | **4.64** | 2.37–7.63 | 3.59–6.15 |
| p90(V6 meal-episode total) ÷ p90(V1 meal-episode total) | **0.88** | 0.67–1.22 | 0.77–1.00 |

The first row is censored by V6's own cap, so the second is the honest figure: **V6 wants a single
shot roughly 4.6× the size of V1's 95th-percentile shot.** And the third row is the control that
makes it safe to say so — **a meal costs the same or slightly less under V6** (0.88, CI upper bound
touching 1). The insulin is not increasing; it is being redistributed from many shots into one plus
holds.

So `confirmedCap = max(p90 manual, p95 all SMBs)` sizes a V6 per-shot cap from a V1 per-shot
distribution, and under-sizes it several-fold — for **every** migrating user, not only hands-free
ones. That is a third independent argument against the current anchor, arriving from the migration
side rather than the hands-free side.

## It also strengthens the TDD proposal

TDD/10 ÷ p90(V6 desired confirm shot) = **0.60**, range 0.46–0.67, CI 0.53–0.64 — the tightest
cross-user ratio measured anywhere in this work. A TDD anchor sits at a stable fraction of what the
engine wants across all eight users, which is exactly the property a migration default needs. The
divisor is then a policy choice about how far below the engine's own appetite the cap should sit:
TDD/10 sits at ~60% of it; TDD/6 would sit on it.

**Confidence: PROVISIONAL.** n = 8. The V6 episode totals are themselves capped, so 0.88 is a lower
bound; the direction (V6 does not cost more per meal) is what the row is being used for.

---

# Third addendum — pricing the re-anchor

Study: `reanchor_pricing.py` → `REANCHOR_PRICING.md`. Propagates a different cap through the
*observed* dose chain (`min(raw, capNew) × brakeFactor`, brake factor taken from the logged
`doseAfterBrakes / doseAfterCaps`). Window is the `velocityFactor` telemetry era — 7 days for most
users, 19–24 for tim and C. Thin, and the load-bearing caveat on everything below.

## Today's caps have no consistent relationship to anything

As a share of TDD the operative `confirmedCap` runs from **2.6% (D) to 18.0% (tim)** — a 6.9× spread,
median 9.3%. That dispersion is the anchor problem stated in one number. `TDD/10` puts everyone at
10%, which is close to the current median: **this is a re-anchoring, not a loosening.** It lowers the
cap for tim, F and H, and raises it for A, B, C, D and E.

## The value lands exactly where the raise-guard blocks it

| user | cap → | clip rate now | added U/day (level) | added U/day (shape) | raise-guard |
|---|---|---|---|---|---|
| B | 3.5 → 6.13 | **0.80** | 6.59 | 3.37 | **BLOCKED** |
| D | 1.5 → 5.76 | 0.57 | 5.69 | 0.61 | **BLOCKED** |
| C | 2.0 → 3.29 | 0.55 | 2.61 | 2.56 | **BLOCKED** |
| A | 4.0 → 4.49 | 0.29 | 0.35 | 0.35 | allowed |
| E | 2.5 → 2.59 | 0.50 (n=4) | 0.03 | 0.01 | allowed |

The three users whose confirm shots are pinned to the cap most often — the ones the re-anchor exists
for — all trip the guard. The two it would reach gain **0.19 U/day between them (0.4% of TDD)**, which
is nothing. Among those two the added insulin's pre-low share is 0.124 *below* their baseline
(CI −0.160, −0.088), i.e. favourably targeted, but that rests on eight raised cycles and is not
evidence of anything.

This is not a defect in the guard. It is the recurring shape of the whole problem: the users who want
more insulin at meals are the users who run more lows.

## The way through is to make it a shape change, not a level change

Today's apply layer recomputes `cumulativeCap60 = confirmedCap + 2 × committedCap`, so raising the
per-shot cap raises the hourly budget with it and the extra is genuinely new insulin. Holding
`cumulativeCap60` at its current value instead lets the confirm shot grow while the hour's total
cannot — the extra has to come out of the holds that would have followed:

**55% of the added insulin is absorbed** across the raise users (3.05 → 1.38 U/day). Per user it
varies with how tightly their hourly budget already binds: D 5.69 → 0.61 (89% absorbed), B 6.59 →
3.37 (49%), C 2.61 → 2.56 (2%, his hourly budget rarely binds).

That is the difference between the early-dosing audit's two categories: MOVED insulin priced
harm-neutral, NEW insulin priced +15pp. A shape-only re-anchor is mostly the former.

## Where that leaves it

The proposal is now specific: **`confirmedCap = clamp(TDD/10, 1.5, 7.5)` with `cumulativeCap60`
decoupled from it rather than recomputed as `conf + 2 × comm`.** The open question is a policy one I
can't settle from data: the raise-guard currently fires on any dose-cap raise, so it would block
B/C/D even under the shape-only form, where the hourly total does not increase. Whether a shape-only
change should face the same guard as a level change is a judgement about what the guard is for.

**Confidence: PROVISIONAL, and weaker than the earlier sections.** Seven-day windows for five of eight
users; TBR percentages computed over that same short window rather than a trailing 14 days, so the
guard verdicts are indicative; the brake propagation assumes the brake stack attenuates a larger
pre-brake dose by the same factor it attenuated the observed one, which holds if the brakes are pure
multipliers and not if any of them are thresholded on dose size.

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
