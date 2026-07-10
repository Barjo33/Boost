# Four-way CGM smoother benchmark -- results

Estimator quality ONLY. No TIR / dosing / BG-outcome claim is made.

Smoothers: persistence (baseline), exponential (AAPS today), tsunami-UKF (v7-shadow), v4-UKF (forward UKF + backward RTS + chi-squared outlier).


**v4 parity self-test:** PASS (9/9) -- reproduces the 9 behaviours of UnscentedKalmanFilterPluginTest.kt.

## Mode A -- SYNTHETIC (known ground truth)

Seeds: 20 x 3 days, sensor noise SD=6.0 mg/dL. 16999 valid samples (281 dropouts), 436 injected compression-artifact samples. Regenerate identically with `--seeds 20 --days 3`.

### Headline (lower = better)

| smoother | ground-truth RMSE (vs TRUE) | one-step RMSE (vs next raw) | GT %vs persist | 1-step %vs persist |
|----------|-----------------------------|-----------------------------|----------------|--------------------|
| persistence | 8.638 | 8.678 | +0.0% | +0.0% |
| exponential | 8.125 | 9.730 | +5.9% | -12.1% |
| tsunami | 8.996 | 9.747 | -4.2% | -12.3% |
| v4 | 6.121 | 9.415 | +29.1% | -8.5% |

- Ground-truth RMSE is the cleanest statement: how far the shipped smoothed curve sits from the *actual* glucose. The v4 curve includes its RTS backward pass.

### Artifact handling (injected compression dips)

`absorbed fraction` = how much of each artifact dip the smoother followed (0.0 = fully rejected/held at truth, 1.0 = tracked the false dip). Lower is safer.

| smoother | mean absorbed fraction | mean |err| at artifact (mg/dL) |
|----------|------------------------|-------------------------------|
| persistence | 1.000 | 36.94 |
| exponential | 0.903 | 30.06 |
| tsunami | 1.111 | 40.83 |
| v4 | 0.708 | 23.71 |

### Lag & jitter

Lag = signed tracking offset on |true slope|>2 windows (mg/dL; + = trails the move). Jitter = within-window variance on |true slope|<0.3 windows (mg/dL^2; lower = smoother).

| smoother | lag offset (mg/dL) | jitter var (mg/dL^2) | reversals |
|----------|--------------------|-----------------------|-----------|
| persistence | +0.61 | 45.35 | 3525 |
| exponential | +5.42 | 31.31 | 1821 |
| tsunami | +1.65 | 47.09 | 2924 |
| v4 | +0.58 | 13.32 | 1490 |

### v4-UKF vs tsunami-UKF (the head-to-head)

| metric | v4 | tsunami | v4 improvement |
|--------|----|---------|----------------|
| ground-truth RMSE | 6.121 | 8.996 | +32.0% |
| one-step RMSE | 9.415 | 9.747 | +3.4% |
| artifact absorbed | 0.708 | 1.111 | +36.3% |
| lag offset | +0.58 | +1.65 | +65.0% |
| jitter var | 13.32 | 47.09 | +71.7% |

## Mode B -- REAL CGM (no ground truth)

Source: local TimescaleDB boost_cgm (9 cohort series). Metrics available without truth: one-step-ahead predictive RMSE (vs next raw), lag (vs raw), jitter (vs raw stable windows). Cohort labels only.

### One-step-ahead predictive RMSE (pooled, mg/dL)

| smoother | one-step RMSE | %vs persistence |
|----------|---------------|-----------------|
| persistence | 5.878 | +0.0% |
| exponential | 6.154 | -4.7% |
| tsunami | 5.573 | +5.2% |
| v4 | 5.714 | +2.8% |

### Per-series one-step RMSE

| series | persistence | exponential | tsunami | v4 |
|--------|---|---|---|---|
| U1 | 5.547 | 5.872 | 5.163 | 5.320 |
| U2 | 5.483 | 5.214 | 4.449 | 4.646 |
| U3 | 6.182 | 6.389 | 6.096 | 6.011 |
| U4 | 4.819 | 5.508 | 4.978 | 5.071 |
| U5 | 3.279 | 3.606 | 3.109 | 3.100 |
| U6 | 7.088 | 7.227 | 6.315 | 6.339 |
| U7 | 7.295 | 7.625 | 7.152 | 7.399 |
| U8 | 4.273 | 4.635 | 4.034 | 4.063 |
| U9 | 7.360 | 7.825 | 7.287 | 7.692 |

### Lag & jitter (vs raw)

| smoother | lag offset (mg/dL) | jitter var (mg/dL^2) | reversals |
|----------|--------------------|-----------------------|-----------|
| persistence | +0.00 | 13.40 | 41623 |
| exponential | +4.36 | 17.42 | 28852 |
| tsunami | +1.25 | 14.09 | 44924 |
| v4 | -0.09 | 9.43 | 31739 |

