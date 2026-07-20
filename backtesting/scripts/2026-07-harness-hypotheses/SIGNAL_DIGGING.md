# Signal digging on the GBM forecaster (2026-07-20) — the well is nearly dry for short-horizon prediction

*Full history, 9 users, 220k samples, BG+30 target, GroupKFold by user, bootstrap CIs. Question: with the
data we have, what signals reduce the GBM's error?*

## What the GBM is
Base GBM RMSE **21.7 mg/dL @30 min**. Importances: recent 5-min delta > current BG > IOB > time-of-day >
15-min delta > steps. It's a momentum + level + IOB + circadian model — the recent trajectory does most
of the work.

## Where it still fails (the residual map)
| regime | RMSE | share |
|---|---|---|
| rising (Δ15>+15) | **31.9** | 12% |
| high (>180) | 30.8 | 9% |
| meal-state | 25.9 | 6% |
| active (steps60>200) | 25.4 | 14% |
| falling | 24.5 | 12% |
| low (<80) | 23.8 | 9% |
| flat | 18.8 | 72% |
| overnight (0–6h) | **16.9** | 25% |

The error concentrates in RISING / HIGH / MEAL / ACTIVE regimes and is smallest overnight/flat. I.e. the
30-min forecast is hard exactly when something is happening — a meal or exercise — which is driven by
UNOBSERVED inputs (unannounced carbs, exercise intensity). Overnight/flat is nearly solved.

## Candidate signals — ΔRMSE when ADDED to base (95% CI)
| signal added | ΔRMSE [95% CI] | verdict |
|---|---|---|
| **acceleration** (Δ curvature) | **−0.164 [−0.179, −0.150]** | HELPS (the only real one) |
| volatility (SD30, Δ30) | −0.056 [−0.068, −0.041] | helps a little |
| heart rate (avg, HRR%) | −0.010 [−0.019, −0.001] | trivial |
| steps30 | −0.008 | trivial |
| IOB decomposition (activity/bolus/basal) | +0.111 | HURTS |
| sensitivity (DynISF + TDD 1d/7d/ratio) | +0.173 | HURTS |
| carbs + meal-state | +0.016 | hurts |
| ml_meal_likely + ml_hypo_risk | +0.010 | hurts |

## The honest conclusion
**For 30-min BG prediction the available signals are nearly exhausted.** The base momentum+IOB+circadian
model is near the data's information ceiling. The ONLY clean new signal is **acceleration** (curvature),
worth ~0.16 mg/dL — cheap, add it. Everything we'd *hope* carries information — the physiological IOB
decomposition, the sensitivity/TDD regime, the meal-state, the ML meal/hypo signals — actually **HURTS**
the short-horizon forecast: they're too slow (TDD/DynISF move over days), too noisy, or redundant with the
trajectory. The big residual (rising/meal regimes) is dominated by **unannounced meals + exercise
intensity, which are not in the data** (no meal announcements is the premise) → largely IRREDUCIBLE.

## Two threads this opens (not yet tested)
1. **Longer horizons (+90/+120):** momentum decays, so the SLOW signals (sensitivity regime, TDD,
   circadian) that hurt at +30 may finally help where the regime matters more than the trajectory. The
   "signals hurt" result is horizon-specific and worth re-testing at 2–3h.
2. **The real opportunity may be DETECTION, not prediction:** the residual lives in meals we can't predict
   — but could a signal DETECT the meal *earlier* (shrink the reaction lag)? That reframes the search from
   "predict BG" to "flag the unannounced meal sooner", which is what the loop actually needs.
