# KAIROS Twin vs V6 — unannounced-meal detection (identifiable)

**Question.** How well does the Twin handle unannounced meals compared to the shipped V6
state machine? Specifically the two *identifiable* legs (no counterfactual needed):
detection **timing** and rising-cycle **attribution**.

**Design.** Replay the validated offline EnKF over each user's historical CGM (45 d, 7
users: tim, F, H, B, E, A, C — the cohort with ≥20 confirms) to recover the latent
glucose-appearance state `Ra`. Meal onsets are defined **from CGM alone** (≥30 mg/dL rise
over 45 min from an 80–170 start, deduped to 60 min) so neither detector defines its own
ground truth. Latency = minutes from that objective onset until each detector first fires
in a −15…+60 min window. Detectors compared **at equal false-alarm rate** (false alarm =
firing on a non-meal rising cycle, Δ>3 mg/dL/5 min, outside any onset window). Twin `Ra` is
a change-point detector (rise ≥ jump above its own trailing-30 min median), swept across
thresholds to trace its ROC; V6 is its single shipped confirm gate. Per-person `Gb` only;
all other params at population priors (a change-point on `Ra`'s own baseline is robust to
per-user SI/TDD bias). Filter fit (Gi vs CGM RMSE) 2.0–4.5 mg/dL across users — the model
tracks. Scripts: `twin_vs_v6_detection.py`, `twin_vs_v6_roc.py`. Raw traces stay in
scratchpad; only aggregates here.

## Result 1 — detection timing: a wash (observability wall)

| detector | sensitivity | false-alarm | median latency from onset |
|---|---|---|---|
| **V6 confirm gate** (shipped) | 0.767 | 0.116 | **20.0 min** |
| **Twin `Ra`** @ V6-matched FA (jump 0.8) | 0.805 | 0.105 | **20.0 min** |

At equal false-alarm rate the Twin catches **+3.8 pp** more meals (80.5% vs 76.7%) — a
marginally better ROC — but at the **same 20 min latency**. And latency is **flat at 20 min
across the Twin's entire ROC** (jump 0.4→3.0), i.e. tightening or loosening the Twin
detector does not move *when* it fires, only *how many* it catches:

```
 jump  sens   fa     lat_med
 0.4   0.92   0.170   20 min
 0.8   0.81   0.105   20 min   <- matched to V6 FA
 1.2   0.63   0.062   20 min
 2.0   0.33   0.017   20 min
 3.0   0.12   0.003   15 min
```

**Interpretation.** The 20 min floor is not a property of either detector — it is the
**interstitial lag plus the time for an unannounced meal to emerge from CGM noise**. Both
detectors sit downstream of the same lagged signal, so neither can fire before the meal is
distinguishable, and a better filter does not move that floor. This is the observability /
identification wall, quantified: **you cannot detect an undeclared carb earlier than the
CGM reveals it, and the Twin does not.**

## Result 2 — attribution: the Twin is the more *conservative* meal-caller

On rising cycles (Δ>3 mg/dL/5 min, pooled), how the two label the rise (Twin `Ra` change-
point at jump 1.2 vs V6 state ∈ {CONFIRMED, COMMITTED}):

| both say meal | Twin-only | V6-only | neither |
|---|---|---|---|
| 14.7% | 5.3% | **15.4%** | 64.7% |

V6 commits to a rise as a meal on ~30% of rising cycles; the Twin on ~20%. V6 flags **~3×
as many rises the Twin's physiology attributes to non-appearance** (its `Ra` stays flat →
the rise is explained by falling insulin action / rebound / noise). Some of those V6-only
calls are the meals the Twin misses (the sensitivity gap in Result 1); the rest are the
rebound/sensitivity rises the Twin declines to treat as meals. This analysis does not
separate those two sub-populations, so it is suggestive, not proof, of a rebound-avoidance
edge — but it is directionally consistent with the physiology (the model separates `Ra`
from `X`) and with why V6 needs its heuristic guards.

## Verdict

**The Twin does not beat V6 at *detecting* unannounced meals — that race is an
observability wall both hit at ~20 min.** Its measurable edges are narrow: a slightly
better sensitivity/false-alarm trade-off (+3.8 pp at equal FA) and a more conservative,
physiology-grounded read of which rises are actually meals. The genuine advantage the Twin
brings to unannounced meals is **not in this analysis at all** — it is the calibrated
forecast band and appearance-vs-sensitivity attribution that let the *response* be sized
under honest uncertainty (validated separately: 60 min band coverage 87%, rising-cycle
forecast RMSE ~1.5× better than oref). That is a **tail/recovery** improvement, not a
peak-prediction one, and it remains a policy question — unvalidatable offline.
