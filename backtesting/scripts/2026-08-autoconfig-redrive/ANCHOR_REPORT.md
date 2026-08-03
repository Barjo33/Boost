# Sizing confirmedCap without manual boluses — anchor study

Per user, over the whole `boostv5_state` era (shadow + live). All quantities in U.


## Candidates and references

| user | n_manual | n_smb | n_conf | tdd | op_cap | R1_desired_conf_p90 | R2_episode_p90 | R3_op_cap | cur_manual_p90 | cur_p95_all_smb | conf_p90 | conf_p95 | large_p90 | allbolus_p90 | tdd_over_8 | tdd_over_10 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tim | 230 | 19607 | 339 | 14.1 | 3 | 2.86 | 3.9 | 3 | 3 | 1 | 2.25 | 2.86 | 1.01 | 0.75 | 1.76 | 1.41 |
| A | 1431 | 17565 | 312 | 43.6 | 4 | 6.44 | 9.4 | 4 | 8 | 2.15 | 4.85 | 6 | 2.2 | 2.15 | 5.45 | 4.36 |
| B | 413 | 26430 | 390 | 56.7 | 3.25 | 12.42 | 9.07 | 3.25 | 4 | 2.15 | 3.5 | 3.7 | 2.2 | 1.7 | 7.09 | 5.67 |
| C | 173 | 3903 | 299 | 33.8 | 2 | 5.93 | 6.16 | 2 | 5.42 | 2.2 | 2 | 2.3 | 2.31 | 1.8 | 4.22 | 3.38 |
| D | 1862 | 16713 | 179 | 53 | 2.5 | 6.37 | 4.66 | 2.5 | 2 | 1.2 | 2.5 | 3.21 | 1.2 | 1.1 | 6.62 | 5.3 |
| E | 1097 | 16861 | 27 | 29.7 | 2.5 | 5.73 | 4.75 | 2.5 | 4.55 | 0.45 | 2.5 | 2.5 | 0.45 | 0.55 | 3.71 | 2.97 |
| F | 1338 | 16066 | 251 | 37.1 | 4.5 | 7.13 | 5.58 | 4.5 | 5 | 1.7 | 3 | 3.7 | 1.75 | 1.7 | 4.64 | 3.71 |
| H | 1443 | 12441 | 56 | 36.4 | 4 | 8.02 | 6.03 | 4 | 6 | 1.45 | 4 | 4.96 | 1.55 | 2 | 4.55 | 3.64 |

`cur_*` are what auto-config uses today. `R1` is the uncapped shot the engine wanted; `R2` is what a whole meal cost; `R3` is the cap the user actually runs.


## How each candidate compares to the references (ratio, pooled across users)

| candidate | R1 | R2 | R3 |
| --- | --- | --- | --- |
| cur_manual_p90 | 0.77 [0.56, 0.97] | 0.87 [0.62, 0.91] | 1.37 [1.16, 1.96] |
| cur_p95_all_smb | 0.21 [0.17, 0.31] | 0.25 [0.20, 0.29] | 0.43 [0.35, 0.70] |
| conf_p90 | 0.43 [0.38, 0.62] | 0.53 [0.44, 0.58] | 1.00 [0.84, 1.07] |
| conf_p95 | 0.51 [0.43, 0.76] | 0.65 [0.50, 0.71] | 1.14 [1.00, 1.27] |
| large_p90 | 0.22 [0.18, 0.32] | 0.26 [0.20, 0.30] | 0.43 [0.35, 0.73] |
| allbolus_p90 | 0.24 [0.17, 0.28] | 0.23 [0.19, 0.28] | 0.47 [0.34, 0.61] |
| tdd_over_8 | 0.65 [0.62, 0.82] | 0.77 [0.63, 0.99] | 1.42 [1.14, 2.02] |
| tdd_over_10 | 0.52 [0.49, 0.65] | 0.61 [0.51, 0.80] | 1.14 [0.90, 1.62] |

Ratio 1.00 = the candidate lands on that reference. Bracketed range is a bootstrap 95% CI of the mean over users (n=8, so it is wide by construction).


## Censoring — is the candidate reading its own cap back?

| user | clip_conf_p90 | clip_p95_smb |
| --- | --- | --- |
| tim | 0.327 | 0.005 |
| A | 1 | 0.028 |
| B | 1 | 0.147 |
| C | 1 | 0.668 |
| D | 1 | 0.001 |
| E | 1 | 0.003 |
| F | 0.179 | 0.002 |
| H | 0.889 | 0.055 |

Share of each candidate's top contributing doses sitting at (>=98% of) the live cap.


## The hands-free case

| user | n_manual | cur_manual_p90 | cur_p95_all_smb | conf_p90 | R1_desired_conf_p90 | R2_episode_p90 | R3_op_cap |
| --- | --- | --- | --- | --- | --- | --- | --- |
| C | 173 | 5.42 | 2.2 | 2 | 5.93 | 6.16 | 2 |
| tim | 230 | 3 | 1 | 2.25 | 2.86 | 3.9 | 3 |

The users with the fewest manual boluses are where the current anchor has to fall back to `p95(all SMBs)`. Compare that column to what the engine actually delivers at a confirm.
