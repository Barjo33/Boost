# Pre-registered protocol — CGM cadence: one-minute vs five-minute

**Status:** PRE-REGISTERED (analysis plan fixed before data collection)
**Registered:** 2026-08-09
**Version:** 1.0
**Applies to:** `v7-shadow-1m-test` @ `c625390d5a` (one-minute arms) and `Boost-V7-shadow` @ `cf8a77ad09`.

> Pre-registration discipline: the hypotheses, arms, endpoints, sample size, stopping rules and
> analysis model below are fixed **before** any trial data is collected. Deviations are recorded in
> the amendment log, dated, with reason.

---

## 1. Background & rationale

The one-minute programme (`backtesting/scripts/2026-07-onemin/`) established what a faster feed does
and does not buy. It buys **latency, not bandwidth**: interstitial lag is ~3.8 min, which destroys
most sub-five-minute content, and a real-era comparison found the two cadences differ by a single
scale factor flat over 5–120 min with no noise floor either side. Prediction gained nothing
(lift 9.14 vs 9.18). Rate of change was estimated *worse* at one minute. The measured latency gain
was about **two minutes** — the grid wait — and the programme's conclusion was that this is
actionable for alarms, not for AID.

Against that, one signal survived: faster feeds help **fast falls**, where two minutes of latency is
a larger share of the time available to react.

This trial exists because that conclusion was reached offline, on observational cuts, and the
identification constraint means no backtest can produce the counterfactual trajectory. The prior is
therefore a **null on aggregate glycaemia**, and the protocol is written so that a null is a clean,
reportable result rather than a failure.

## 2. The intervention is compound, by decision

**The one-minute arm changes two things at once, and this is deliberate.**

`ApsMaxSmbFrequency` (`smbinterval`) is left at its default of **3 minutes in every arm** — the
setting is not touched. But the loop cycles on new CGM data, so the binding constraint differs:

| arm | loop cycle | effective minimum SMB spacing |
|---|---|---|
| one-minute | 1 min | **3 min** (the setting binds) |
| five-minute | 5 min | **5 min** (the cadence binds) |

So the one-minute arm has up to 67% more dosing opportunities than the five-minute arm, from cadence
alone. This was identified before the trial and the decision taken (2026-08-09) is to **treat the
higher dosing frequency as part of the intervention** rather than to suppress it by raising
`smbinterval` to 5 on the one-minute arm.

The consequence is stated here so it is never mistaken for a cadence-only result: **this trial
estimates the effect of running a one-minute feed as a user would actually run it**, sensing and
dosing frequency together. It does **not** identify the sensing contribution alone. Any result,
positive or null, must be reported as the compound effect. A follow-on trial holding `smbinterval`
at 5 on both arms would be required to separate them, and is out of scope here.

## 3. Hypotheses

- **H1 (primary, safety-directional):** One-minute does not increase TBR<70 relative to the same
  sensor run at five minutes.
- **H2 (primary, mechanism):** One-minute reduces the depth and duration of excursions **following a
  fast fall**, the one place the offline work left a live signal.
- **H3 (secondary):** No difference in TIR or TING. Stated as expected-null; see §6, this trial
  cannot settle TIR either way.
- **H0:** No within-user difference on any endpoint.

## 4. Design

Three arms, but **only one contrast is properly identified** and the protocol says so up front.

| arm | sensor hardware | loop cadence |
|---|---|---|
| **A** | one-minute-capable | 1 min |
| **B** | **the same** one-minute-capable sensor, series used at 5 min | 5 min |
| **C** | native five-minute sensor | 5 min |

- **A vs B — PRIMARY.** Same physical sensor, same wear session, same person; differs only in
  cadence (and the dosing frequency that rides with it, §2). Randomised **day by day inside each
  sensor session**, so calendar, site, illness and eating pattern are held as close as they can be.
  This is the contrast the trial is powered and analysed for.
- **B vs C — SECONDARY.** Same cadence, different hardware. Isolates the sensor, not the cadence.
  Necessarily period-level, because it requires a different device on the body, so it is confounded
  with calendar and is **hypothesis-generating only**.
- **A vs C — REPORTED, NOT INTERPRETED.** Confounded by both hardware and cadence. Included because
  it is the comparison a reader will ask for; it settles nothing.

The code supports arm B exactly: on a feed at or above two-minute spacing the native series *is* the
bucketed series (`cadence >= 2.0 -> bucketedDataNative = bucketedData`), so B is byte-identical in
behaviour to a native five-minute run of the same build.

**Randomisation:** seeded PRNG keyed on `(participantId, date)`, balanced in blocks of 6 days so the
arms stay even within each week. Not alternating days — that aliases with weekday effects.

**Blinding:** none, and not achievable. The participant is the developer. Recorded as a limitation.

## 5. Population

Single subject (the developer), within-user. n=1 by design: cross-user cadence comparison would need
hardware the cohort does not have, and this project's position is that within-subject beats
between-subject at this scale.

## 6. Sample size & power — read this before choosing a duration

Computed from the participant's own 178 complete days (`scripts/2026-08-cadence-trial/cadence_power.py`),
using between-day SD with a first-order autocorrelation inflation, two-sided α=0.05, 80% power:

| outcome | baseline | between-day SD | 14 d/arm | 28 d/arm | 56 d/arm |
|---|---|---|---|---|---|
| TIR 70–180 | 85.2% | 8.7 pp | 10.2 pp | 7.2 pp | 5.1 pp |
| TING 63–140 | 68.7% | 12.0 pp | 14.6 pp | 10.4 pp | 7.3 pp |
| TBR <70 | 4.7% | 3.9 pp | 4.9 pp | 3.5 pp | 2.5 pp |
| TBR <54 | 1.0% | 1.4 pp | 1.8 pp | 1.2 pp | 0.9 pp |

**TIR cannot be settled by this trial.** At 28 days per arm the smallest detectable difference is
7.2 pp on an 85.2% baseline — an effect that would take TIR to 92%, which no one expects from a
two-minute latency gain. Reporting "no significant TIR difference" from this design would be
uninformative, so TIR is **demoted to a secondary, expected-null endpoint** and the primary
endpoints are the safety and mechanism ones, where the per-day event count is higher than one
number per day.

**Planned duration: 56 days per arm** (a 112-day A/B phase), which is the point at which TBR<70 is
detectable at about half the baseline rate. Shorter phases are permitted but the analysis must
report the MDE alongside the estimate and must not claim a null.

## 7. Safety & stopping rules (pre-specified)

Absolute floors bind regardless of any statistic, and can only tighten:

- **TBR<54 > 1%** over any rolling 14 days on an arm → that arm stops immediately.
- **TBR<70 > 4%** over any rolling 14 days on an arm → that arm stops immediately.
- Any single event below 54 mg/dL lasting > 30 min attributable to a dosing decision → pause,
  review the cycle logs before resuming.

**⚠ The baseline already breaches the TBR<70 floor.** Measured over the last 28 / 90 / 180 days,
TBR<70 is **4.4% / 4.3% / 4.7%** and TBR<54 is **0.8% / 0.9% / 1.0%**. The stopping rule above will
therefore trigger on the participant's ordinary control, in either arm, for reasons that have nothing
to do with cadence. Three consequences, all of which must be settled before day 1:

1. Starting the trial in this state means an arm is likely to be stopped by a condition that predates
   the trial, wasting the phase and producing an uninterpretable result.
2. The correct order of work is to bring TBR<70 under the floor **first**, and only then run a
   cadence experiment on top of a compliant baseline.
3. If the trial is nevertheless run now, the pre-registered position must be that these thresholds
   are **not** trial stopping rules but standing safety limits already in force, and a separate,
   explicitly relative deterioration rule is added: an arm stops if its rolling 14-day TBR<70 exceeds
   the pre-trial baseline by more than 1.5 pp. This is recorded as an addition to, never a
   replacement for, the absolutes.

## 8. Data capture

- Both builds log per-cycle to Nightscout and are extracted into `boost_decisions` by the standard
  extractor; the arm is recovered from the detected cadence, not from a manual label.
- Arm assignment, sensor serial and phase are recorded per day in the trial registry.
- `detectedCadenceMinutes` is logged per cycle so the realised cadence can be checked against the
  intended arm. **Any day whose realised median cadence disagrees with its assignment is excluded
  before analysis**, and the count of such days is reported.

## 9. Endpoints

**Primary**
1. TBR<70, per day, arm A vs arm B.
2. Fast-fall recovery: for every descent steeper than −3 mg/dL/5min sustained 15 min, the nadir
   reached and the minutes spent below 70 in the following 2 h. Event-level, so n is events not days.

**Secondary** — TBR<54; TIR; TING; CV; total daily insulin; SMB count per day (this will differ by
construction, §2, and is reported as a manipulation check rather than an outcome).

## 10. Analysis plan (fixed)

- Day-level outcomes, arm A vs arm B, difference of means with a **bootstrap 95% CI resampling whole
  days** (days, not readings — consecutive readings are not independent).
- Event-level endpoints: mixed comparison clustered by day, bootstrap over days.
- Every effect size reported with its CI and an explicit "distinguishable from baseline?" verdict.
  Where the interval covers zero the result is reported as **unproven**, with the MDE from §6 quoted
  alongside so the reader can tell "no effect" from "no power".
- Confidence tier labelled on every claim (SOLID / PROVISIONAL / SPECULATIVE).
- No subgroup analysis is pre-specified. Any that is run post hoc is labelled exploratory.

## 11. Decision rule

- **Adopt one-minute** only if TBR<70 is non-inferior *and* the fast-fall endpoint improves, both
  with intervals excluding the null, at the planned duration.
- **Reject** if TBR<70 worsens beyond the §7 rule.
- **Null** — the expected outcome — is written up as such, and closes the question of whether a
  faster feed is worth pursuing for AID. That is a useful result and the programme's offline work
  already points at it.

## 12. Limitations

Single subject, unblinded, compound intervention (§2), TIR underpowered (§6), arm C confounded with
hardware and calendar (§4), and a baseline that does not currently meet the safety floor (§7).

---

## Amendment log

| date | version | change | reason |
|---|---|---|---|
| 2026-08-09 | 1.0 | Initial registration | — |
