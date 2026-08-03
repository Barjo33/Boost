# What auto-config inherits from the user's previous Boost

Every cohort user ran Boost before V6 (V1 IS Boost). V1's meal responses are tiered in telemetry: UAM_BOOST, UAM_HIGH_BOOST, PERCENT_SCALE, ACCELERATION vs plain REGULAR_OREF1.


## Per-shot and per-episode sizing, V1 era vs V6 era

| user | n_v1 | n_boosted | v1_p95_all | v1_p90_boosted | v1_episode_p90 | v6_conf_p90 | v6_desired_p90 | v6_episode_p90 | tdd_over_10 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tim | 14843 | 1435 | 1 | 1.1 | 3.6 | 2.25 | 2.86 | 3.2 | 1.68 |
| A | 12654 | 1007 | 2.05 | 2.5 | 6.04 | 4.85 | 6.44 | 7.35 | 4.26 |
| B | 10081 | 667 | 1.75 | 1.75 | 9.02 | 3.5 | 12.42 | 7.88 | 5.91 |
| C | 1243 | 361 | 2.5 | 2.5 | 6.79 | 2 | 5.93 | 4.58 | 3.99 |
| D | 12547 | 716 | 1.25 | 1.35 | 3.45 | 2.5 | 6.37 | 3.39 | 3.97 |
| E | 6650 | 335 | 0.75 | 1.25 | 2.9 | 2.5 | 5.73 | 2.64 | 2.62 |
| F | 12974 | 1466 | 1.7 | 1.5 | 8.05 | 3 | 7.13 | 5.67 | 4.03 |
| H | 3261 | 898 | 1.25 | 1.25 | 5.55 | 4 | 8.02 | 4.25 | 5.11 |

## Ratios

- **tier visibility**: median 1.04, range 0.88–1.67, mean 95% CI [0.99, 1.29]  
  _p90(V1 meal-tier shots) / p95(all V1 shots) — how much bigger V1's meal shots are than the blind percentile auto-config takes_
- **concentration (delivered)**: median 2.12, range 0.80–3.33, mean 95% CI [1.69, 2.71]  
  _p90(V6 CONFIRMED shot) / p95(all V1 shots) — V6 is censored by its own cap, so this is a LOWER bound_
- **concentration (uncapped)**: median 4.64, range 2.37–7.63, mean 95% CI [3.59, 6.15]  
  _p90(V6 desired confirm shot) / p95(all V1 shots) — the honest figure_
- **episode totals**: median 0.88, range 0.67–1.22, mean 95% CI [0.77, 1.00]  
  _p90(V6 meal-episode total) / p90(V1 meal-episode total) — does a meal cost the same under both architectures?_
- **TDD/10 vs need**: median 0.60, range 0.46–0.67, mean 95% CI [0.53, 0.64]  
  _TDD/10 / p90(V6 desired confirm shot) — cross-user stability of the TDD anchor_