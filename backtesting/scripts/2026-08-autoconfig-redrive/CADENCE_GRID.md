# Auto-config re-derivation cadence — window x step grid

Real history, 8 users. Effective lag = W/2 + S/2 (arithmetic). Everything else measured.


## The caps (committedCap, confirmedCap, cumulative60) — the knobs with real drift

| window | step | lag_days | changes_per_6mo | median_abs_delta | path_over_net | material_share | after_deadband_per_6mo |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 14 | 7 | 10.5 | 15.855 | 0.361 | 5.003 | 0.409 | 6.09 |
| 14 | 14 | 14 | 8.811 | 0.553 | 3.241 | 0.472 | 4.104 |
| 21 | 7 | 14 | 13.835 | 0.371 | 3.741 | 0.233 | 3.715 |
| 28 | 7 | 17.5 | 13.416 | 0.239 | 3.846 | 0.218 | 2.894 |
| 28 | 14 | 21 | 7.142 | 0.344 | 2.675 | 0.366 | 2.398 |
| 28 | 28 | 28 | 4.138 | 0.553 | 3 | 0.634 | 1.79 |

`changes_per_6mo` and `after_deadband_per_6mo` are per knob per user. `path_over_net` is the median over user-knobs of (total distance travelled ÷ net distance covered): 1.0 would mean every move was progress.


## Per knob, at the proposed 14/7 against the alternatives


### aggression (deadband 0.025)

| window | step | changes_per_6mo | path_over_net | after_deadband_per_6mo |
| --- | --- | --- | --- | --- |
| 14 | 7 | 4.678 | 5.286 | 4.678 |
| 14 | 14 | 3.991 | 5.571 | 3.991 |
| 21 | 7 | 4.326 | 3 | 4.326 |
| 28 | 7 | 3.034 | 2.867 | 3.034 |
| 28 | 14 | 2.464 | 2.867 | 2.464 |
| 28 | 28 | 1.955 | 1.467 | 1.955 |

### hypoCaution (deadband 0.16)

| window | step | changes_per_6mo | path_over_net | after_deadband_per_6mo |
| --- | --- | --- | --- | --- |
| 14 | 7 | 6.996 | 13.333 | 5.463 |
| 14 | 14 | 4.683 | 1.667 | 3.868 |
| 21 | 7 | 6.029 | 5 | 4.4 |
| 28 | 7 | 5.48 | 4.3 | 3.414 |
| 28 | 14 | 2.898 | 5 | 2.627 |
| 28 | 28 | 2.111 | 3 | 1.8 |

### confirmedCap (deadband 0.47)

| window | step | changes_per_6mo | path_over_net | after_deadband_per_6mo |
| --- | --- | --- | --- | --- |
| 14 | 7 | 12.512 | 4.868 | 5.563 |
| 14 | 14 | 6.621 | 2.835 | 3.584 |
| 21 | 7 | 10.903 | 3.881 | 3.955 |
| 28 | 7 | 10.321 | 3.733 | 3.326 |
| 28 | 14 | 5.536 | 2.233 | 2.394 |
| 28 | 28 | 3.724 | 3.467 | 1.955 |

### committedCap (deadband 0.067)

| window | step | changes_per_6mo | path_over_net | after_deadband_per_6mo |
| --- | --- | --- | --- | --- |
| 14 | 7 | 18.719 | 4.241 | 7.848 |
| 14 | 14 | 10.291 | 5.2 | 4.485 |
| 21 | 7 | 16.586 | 6.245 | 3.483 |
| 28 | 7 | 15.873 | 3.823 | 2.606 |
| 28 | 14 | 8.698 | 2.1 | 2.731 |
| 28 | 28 | 4.376 | 1.375 | 1.645 |

### cumulative60 (deadband 0.54)

| window | step | changes_per_6mo | path_over_net | after_deadband_per_6mo |
| --- | --- | --- | --- | --- |
| 14 | 7 | 16.333 | 5.669 | 4.86 |
| 14 | 14 | 9.522 | 2.778 | 4.242 |
| 21 | 7 | 14.015 | 3.5 | 3.708 |
| 28 | 7 | 14.055 | 4.5 | 2.749 |
| 28 | 14 | 7.193 | 3.25 | 2.068 |
| 28 | 28 | 4.314 | 3.188 | 1.769 |

### primerCap (deadband 0.056)

| window | step | changes_per_6mo | path_over_net | after_deadband_per_6mo |
| --- | --- | --- | --- | --- |
| 14 | 7 | 17.345 | 3.625 | 6.728 |
| 14 | 14 | 9.995 | 2.336 | 4.345 |
| 21 | 7 | 15.056 | 3.5 | 3.313 |
| 28 | 7 | 12.191 | 7.767 | 2.081 |
| 28 | 14 | 7.709 | 1.429 | 1.455 |
| 28 | 28 | 4.066 | 1.19 | 0.869 |

## Bootstrap CIs on the headline contrast (caps only)


**changes_per_6mo**
- 14/14: mean 8.81 [7.43, 10.09] (n=24 user-knobs)
- 14/7: mean 15.85 [12.99, 18.46] (n=24 user-knobs)
- 21/7: mean 13.83 [11.36, 16.08] (n=24 user-knobs)
- 28/7: mean 13.42 [11.14, 15.57] (n=24 user-knobs)
- 28/14: mean 7.14 [5.70, 8.45] (n=24 user-knobs)
- 28/28: mean 4.14 [3.41, 4.73] (n=21 user-knobs)

**path_over_net**
- 14/14: mean 8.46 [3.68, 14.90] (n=20 user-knobs)
- 14/7: mean 12.53 [5.48, 22.28] (n=18 user-knobs)
- 21/7: mean 9.96 [4.84, 16.97] (n=21 user-knobs)
- 28/7: mean 9.58 [4.17, 17.39] (n=23 user-knobs)
- 28/14: mean 5.21 [2.55, 9.04] (n=20 user-knobs)
- 28/28: mean 4.93 [2.21, 9.21] (n=17 user-knobs)

**after_deadband_per_6mo**
- 14/14: mean 4.10 [2.96, 5.29] (n=24 user-knobs)
- 14/7: mean 6.09 [4.43, 7.84] (n=24 user-knobs)
- 21/7: mean 3.72 [2.75, 4.73] (n=24 user-knobs)
- 28/7: mean 2.89 [1.90, 4.02] (n=24 user-knobs)
- 28/14: mean 2.40 [1.78, 3.02] (n=24 user-knobs)
- 28/28: mean 1.79 [1.32, 2.28] (n=21 user-knobs)