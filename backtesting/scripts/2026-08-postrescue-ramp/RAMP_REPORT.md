# Post-rescue rebound guard — is the BG ramp opening too fast?

Cohort tim, A, B, C, D, E, F, H; V6 era; 418 post-rescue windows, 6160 cycles.


Shipped ramp: `<120 → 0.30`, `120–170 → linear 0.30→1.00`, `≥170 → guard does not apply`. The scale reads BG only — not window age, not rate of rise.


## 1. Traverse — how long does protection last?

| user | windows | med_len_min | med_min_to_120 | med_min_to_170 | pct_reaching_170 | insulin |
| --- | --- | --- | --- | --- | --- | --- |
| A | 37 | 60.55 | 44.97 | 45.24 | 0.05 | 59.8 |
| B | 44 | 70.1 | 40.23 | 50.12 | 0.09 | 107.4 |
| C | 93 | 69.55 | 49.92 | 60.47 | 0.03 | 143.7 |
| D | 71 | 74.62 | 45.05 |  | 0 | 101.05 |
| E | 29 | 70.02 | 65.02 |  | 0 | 38.5 |
| F | 34 | 72.17 | 65.08 | 125.13 | 0.03 | 44.05 |
| H | 21 | 55.6 | 20.18 |  | 0 | 18.65 |
| tim | 89 | 60.17 | 47.71 | 54.96 | 0.11 | 63.7 |

Across all 418 windows: BG reaches 120 a median 46 min after the window opens and 170 a median 53 min after (mean 95% CI [46, 65]). 5% of windows reach 170 at all.


## 2 & 3. Exposure and pricing by band

| band | cycles | dosed_cycles | insulin_U | pct_of_window_insulin | mean_dose | prelow_share | baseline | vs_baseline |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| a. <120 (scale .30) | 5662 | 509 | 343.567 | 59.56 | 0.675 | 0.276 | 0.192 | 0.084 |
| b. 120-145 (.30-.65) | 340 | 189 | 156.329 | 27.101 | 0.827 | 0.271 | 0.192 | 0.079 |
| c. 145-170 (.65-1.0) | 108 | 68 | 56.15 | 9.734 | 0.826 | 0.139 | 0.192 | -0.053 |
| d. >=170 (guard OFF) | 50 | 33 | 20.8 | 3.606 | 0.63 | 0.5 | 0.192 | 0.308 |

`prelow_share` = share of that band's insulin delivered within 3 h of a real <70; `baseline` is the cohort mean over ALL delivered insulin. Positive `vs_baseline` means insulin in that band lands ahead of a low more often than the user's average unit.


### Per-user, so no single person carries the pooled figure

| user | a. <120 (scale .30) | b. 120-145 (.30-.65) | c. 145-170 (.65-1.0) | d. >=170 (guard OFF) |
| --- | --- | --- | --- | --- |
| A | -0.016 | 0.068 | 0.106 | 0.806 |
| B | -0.115 | -0.098 | -0.133 | -0.133 |
| C | 0.083 | 0.133 | -0.005 | -0.302 |
| D | 0.079 | 0.073 | -0.331 |  |
| E | 0.102 | -0.087 | -0.087 |  |
| F | -0.087 | -0.126 | -0.203 |  |
| H | 0.448 | 0.303 | -0.108 |  |
| tim | 0.151 | 0.209 | 0.132 | 0.266 |

Each cell is that user's band pre-low share minus their own baseline.

- **a. <120 (scale .30)**: mean +0.081 over 8 users, 95% CI [-0.020, +0.208]
- **b. 120-145 (.30-.65)**: mean +0.060 over 8 users, 95% CI [-0.040, +0.160]
- **c. 145-170 (.65-1.0)**: mean -0.079 over 8 users, 95% CI [-0.181, +0.021]
- **d. >=170 (guard OFF)**: mean +0.159 over 4 users, 95% CI [-0.218, +0.571]


## 4. Candidate ramps

Each priced by the insulin it removes relative to the shipped ramp, and by how much of that removed insulin sat ahead of a real low. `pre_smb` (the dose before scaling) is only logged when the guard applied, so removal above 170 is computed from the delivered dose, which is what the guard would have scaled.

| policy | U_removed | pct_of_window_insulin | removed_prelow_share | per_user_vs_baseline | ci_lo | ci_hi | n_users | verdict |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| flat 0.30 whole window | 64.351 | 11.156 | 0.235 | -0.015 | -0.144 | 0.137 | 8 | unproven |
| cap ramp at 0.60 | 18.347 | 3.181 | 0.302 | -0.005 | -0.175 | 0.207 | 8 | unproven |
| extend ramp to 220 | 29.069 | 5.039 | 0.178 | -0.057 | -0.144 | 0.036 | 8 | unproven |

`per_user_vs_baseline` is the mean over users of (removed insulin's pre-low share minus that user's own baseline share), with a cluster bootstrap 95% CI over users. A CI overlapping zero means the policy is **unproven** — it is not demonstrably removing the units that were about to cause a low rather than units at random.


### Per-user

| user | cap ramp at 0.60 | extend ramp to 220 | flat 0.30 whole window |
| --- | --- | --- | --- |
| A | 0.64 | 0.084 | 0.395 |
| B | -0.133 | -0.128 | -0.128 |
| C | -0.036 | -0.015 | -0.015 |
| D | -0.331 | -0.265 | -0.265 |
| E | -0.087 | -0.087 | -0.087 |
| F | -0.203 | -0.165 | -0.166 |
| H | -0.108 | -0.064 | -0.064 |
| tim | 0.221 | 0.182 | 0.208 |