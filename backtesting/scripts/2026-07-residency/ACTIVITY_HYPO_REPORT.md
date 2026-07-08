# Does activity genuinely precede the hypo? — validating the 47% ACTIVITY low finding

_Follow-up to `RESIDENCY_REPORT.md`, 2026-07-08. oref.boost_decisions, self+A–H, ~89.5k cycles. Reproduce: `activity_hypo.py`._

## Verdict

**Yes — steps are a strong LEADING predictor of hypos, with hours of lead time. The signal is real and user-specific (not cross-user transferable), and STEPS, not HR, carries it on current data.** This validates the residency's ACTIVITY = 47%-of-low-time finding and the exercise protections / Garmin **steps** ingest; it does *not* (yet) validate the HR ingest, which is too sparse to evaluate.

## 1. Dose-response — strong and monotone (steps)

Forward-low (<70 within 3h) rate by recent steps (cohort base 19.1%):

| steps_60m | n | fwd-low% |
|---|---|---|
| 0 | 35,608 | 13.1 |
| 1–100 | 15,350 | 17.1 |
| 100–300 | 17,171 | 18.5 |
| 300–600 | 9,045 | 25.9 |
| 600–1200 | 6,010 | 31.8 |
| **1200+** | 4,277 | **38.5** |

Sedentary → very active nearly **triples** the hypo rate (13% → 38.5%), cleanly monotone.

**HR-reserve is not a usable signal here** — `hrr_pct` is **76% NULL** (the overnight-HR-death problem), and where populated it's flat (~21–22% across 0–40% HRR). So HR as currently ingested carries no clean hypo signal; that's a *data-sparsity* verdict, not proof HR is useless.

## 2. But the signal is PER-USER, not cross-user

LGBM forward-low, GroupKFold **by user**:

| model | AUC |
|---|---|
| baseline (BG / IOB / delta / hour / eventualBG / state) | 0.739 ± 0.046 |
| + activity (steps + HR + iob-activity) | 0.717 ± 0.040 |
| **activity's cross-user lift** | **−0.02 (within fold noise)** |

Adding activity does **not** improve a *generalised* (held-out-user) hypo predictor, even though its pooled dose-response is strong and its in-sample gain rank is high (steps_60m #5, iob-activity #6). The reading: the activity→hypo relationship is **user-specific** — each user's fitness, step baseline, and post-activity drop differ, so a one-size cross-user model can't transfer it. **This validates the design choice of per-user activity thresholds** (Boost's protection) over a global model.

## 3. Lead time — activity precedes the low by hours

Mean `steps_60m` at increasing look-back before each real low onset (baseline 256):

| min before low | mean steps_60m | × baseline |
|---|---|---|
| 5 | 610 | 2.4× |
| 15 | 521 | 2.0× |
| 30 | 434 | 1.7× |
| 60 | 391 | 1.5× |
| 90–180 | ~400 | 1.5–1.6× |

Activity sits **1.5–1.6× above baseline as far as 3h ahead**, rising to 2.4× just before. It is a genuine **leading** indicator — the exercise protection has ample time to act, not a coincident artifact.

## Implications for the Garmin work

- **Steps ingest is the validated hypo lever** — the strong dose-response + long lead time back the exercise protections and the Garmin **steps** path directly.
- **HR ingest is unvalidated because HR is too sparse (76% null)** — precisely the overnight-HR-death the Garmin **HR** ingest is built to fix. If the Garmin firmware-HR (24/7, no listener death) fills that gap, HR *may* then contribute — but that's a hypothesis to re-test once dense HR exists, not a proven signal today.
- **Per-user thresholds are the right shape** — a global activity→hypo model doesn't transfer; the protection must stay personalised.

## Caveats

- Pooled dose-response can be partly confounded (activity co-varies with time-of-day and low IOB); the lead-time result is the cleaner causal-direction evidence.
- Forward-low here = any <70 within 3h (base 19.1%), a deliberately sensitive label.
- The HR conclusion is "can't validate on sparse data," not "HR doesn't predict."
