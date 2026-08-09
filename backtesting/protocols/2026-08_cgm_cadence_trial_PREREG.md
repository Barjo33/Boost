# Pre-registered protocol — CGM cadence: one-minute vs five-minute

**Status:** PRE-REGISTERED (analysis plan fixed before data collection)
**Registered:** 2026-08-09
**Version:** 1.3
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

Three arms, **one binary**, two switches. All arms run the same APK, so no difference between them
can come from the build.

| arm | sensor | loop | `ApsLoopAtNativeCadence` | `smbinterval` | effective SMB spacing |
|---|---|---|---|---|---|
| **A** | 5 min | 5 min | off | 3 | 5 min (loop binds) |
| **B** | 1 min | 5 min | off | 3 | 5 min (loop binds) |
| **C** | 1 min | 1 min | **on** | 3 | 3 min (setting binds) |

Arm B is what the build does with a one-minute sensor and the switch off: the loop trigger reads the
five-minute bucketed series, so decisions happen every five minutes, while the decision itself is
computed from the one-minute native series — deltas, the smoother and the ML features all see
one-minute data. Arm C moves the trigger onto the native series so a decision happens every minute.

**Contrasts, and what each identifies:**

- **A vs B — sensing cadence, alone.** Dosing opportunity is identical (both loop at five minutes,
  and `smbinterval` at 3 never binds because the loop binds first). The only difference is whether
  the decision is computed from one-minute or five-minute data. **This is the question the offline
  programme could not answer.**
- **B vs C — dosing frequency, alone.** Sensing is identical, one-minute in both. The only
  difference is how often a decision is taken, and with it whether `smbinterval` binds at 3.
- **A vs C — both together.** The combination a user would actually run. Reported, and now
  decomposable into the two contrasts above rather than confounded.

This supersedes the compound-intervention position in §2: because arm B exists, sensing and dosing
frequency are separately identified and neither result has to be reported as a bundle.

**Randomisation.** A and B share hardware only in the sense that both are five-minute loops; A needs
a five-minute sensor and B and C need a one-minute sensor. B vs C is therefore randomisable day by
day within a single wear session, by toggling one preference — that contrast is the cleanly
randomised one. A requires different hardware and so enters at period level, confounded with
calendar, and A vs B is interpreted with that caveat.

Seeded PRNG keyed on `(participantId, date)`, balanced in blocks of 6 days.

**Blinding:** none, and not achievable. Recorded as a limitation.

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

**Baseline status — amended 2026-08-09 (v1.2).** Version 1.0 recorded a TBR<70 breach and deferred
the trial until a clean pre-trial baseline was demonstrated. **That deferral was wrong and is
withdrawn.** The arms run concurrently under day-randomisation, so each arm's comparator is the other
arm in the same period, not a historical window. A still-settling TDD scaling is common to all three
arms — same person, same profile, same derivation — and randomisation cancels any factor that varies
with time rather than with assignment. A run-in period therefore buys nothing for the comparison,
and the earlier claim that it was "the thing that makes the relative rule work" was simply incorrect.

What a still-moving TDD model does cost is **precision, not validity**: it inflates between-day
variance and so widens the minimum detectable difference in §6. Block randomisation in blocks of 6
protects against a trend aliasing with assignment. The only pre-trial requirement retained is a
check that the TDD scaling has settled, which the record already supports — the derived ratio has
held at 1.00 across the transition and since.

For context rather than as a gate, the switch from U200 to the same analogue at U100 strength on
2026-08-05 accounts for the breach recorded in v1.0. Recorded units per day roughly doubled, as
diluting to half strength requires, and the transition day itself carried TBR<54 of 4.2% against
0.0% on each of the days since:

| period | complete days | TIR | TBR<70 | TBR<54 |
|---|---|---|---|---|
| pre-switch | 89 | 84.5% | 4.1% | 0.8% |
| transition (05–06 Aug) | 2 | 85.4% | 4.9% | 2.4% |
| stabilised (from 07 Aug) | 2 | 92.1% | 1.9% | 0.0% |

Partial days are excluded throughout: an in-progress day of good control reads as a perfect one.

**Stopping rules, corrected.** The absolute floors above stand unchanged and bind on their own
terms. The relative rule is measured **between concurrent arms, not against a historical baseline**:
an arm stops if its rolling 14-day TBR<70 exceeds the *other arm's same-period* rate by more than
1.5 pp. This is an addition to the absolute floors, never a replacement for them.

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

## 11a. Resolved — how each arm is built

Closed 2026-08-09. Loop rate is set by which glucose series the trigger reads, not by a setting and
not by the sensor alone: `InvokeLoopWorker` takes its timestamp from `ads.actualBg()`, which reads
`bucketedData`, and both bucketing paths step at five minutes whatever the sensor reports. A
one-minute sensor therefore yields one-minute data at a five-minute loop with no change at all —
that is arm B. Arm C required an enabler (`ApsLoopAtNativeCadence`, default off) that moves the
trigger onto the native series. The throttle proposed in v1.2 was unnecessary and was not built.

## 12. Limitations

Single subject, unblinded, compound intervention (§2), TIR underpowered (§6), arm C confounded with
hardware and calendar (§4), and a baseline that does not currently meet the safety floor (§7).

---

## Amendment log

| date | version | change | reason |
|---|---|---|---|
| 2026-08-09 | 1.0 | Initial registration | — |
| 2026-08-09 | 1.1 | (superseded by 1.2) Baseline breach in §7 attributed to the U200->U100 change; day 1 deferred until 12 complete stabilised days demonstrate the floor is met | Transition dated 05 Aug; units/day roughly doubled and the transition day carried TBR<54 4.2%. Two stabilised days show 1.9% / 0.0% but cannot demonstrate compliance |
| 2026-08-09 | 1.2 | Pre-trial run-in withdrawn; relative stopping rule re-specified against the concurrent arm rather than a historical baseline | Arms run concurrently under randomisation, so a common time-varying factor such as a settling TDD model cancels in the contrast. A run-in adds nothing to validity and the v1.1 deferral was incorrect. Arm definitions pending a decision on the loop-interval throttle (see OPEN ITEM) |
| 2026-08-09 | 1.3 | Arms re-specified to A=5min/5min, B=1min sensor/5min loop, C=1min/1min, all on one binary with ApsLoopAtNativeCadence as the switch; OPEN ITEM closed | The loop trigger reads the five-minute bucketed series, so a one-minute sensor alone gives one-minute sensing at a five-minute loop — that is arm B, and it needed no code. Arm C needed an enabler, not the throttle proposed in 1.2. With B present, sensing and dosing frequency are separately identified and §2's compound framing no longer applies |
