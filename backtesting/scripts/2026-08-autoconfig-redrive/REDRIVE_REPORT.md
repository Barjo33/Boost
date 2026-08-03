# Auto-config periodic re-derivation — replay report

Generated 2026-08-03 09:52 UTC · window 28d, step 28d · users A, B, C, D, E, F, H, tim

Data: local TimescaleDB (`boost_decisions`, `boost_cgm`, `boost_treatments`), refreshed to t=now. Knobs derived by `boost_autoconfig.py`, a verbatim port of `BoostV5AutoConfig.compute()` (selftest-checked, including Kotlin's half-up rounding).


## 1. Censoring — is the delivered-SMB distribution actually clipped?

| user | n_dosed | clip_all | clip_committed | clip_confirmed | p75_smb | p95_smb | op_ccap | op_fcap |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tim | 1160 | 0.042 | 0.214 | 0.165 | 0.2 | 1.035 | 1 | 3 |
| A | 1231 | 0.156 | 0.315 | 0.587 | 0.5 | 2.5 | 1 | 4 |
| B | 1952 | 0.174 | 0.193 | 0.348 | 0.35 | 1.5 | 0.7 | 3.5 |
| C | 988 | 0.131 | 0.299 | 0.45 | 0.5 | 1.415 | 1.22 | 2 |
| D | 364 | 0.115 | 0.128 | 0.545 | 0.4 | 1.42 | 0.5 | 1.5 |
| E | 1291 | 0.016 | 0.282 | 0.455 | 0.2 | 0.6 | 1.5 | 2.5 |
| F | 1663 | 0.029 | 0.093 | 0.175 | 0.3 | 1.052 | 1.5 | 4.5 |
| H | 875 | 0.026 | 0.158 | 0.25 | 0.35 | 1.1 | 1.5 | 4 |

`at_committed_cap` = share of delivered SMBs sitting at (>=98% of) the operative committedCap logged for that cycle. This is the precondition for the ratchet: the derivation's p75/p95 inputs are censored exactly to the extent this is non-zero.


## 2. Trajectory under repeated re-derivation


### aggression

| user | n_windows | n_changes | down | up | reversals | revert_rate | first | last | net | monotone_down |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A | 6 | 0 | 0 | 0 | 0 |  | 1 | 1 | 0 |  |
| B | 5 | 2 | 1 | 1 | 1 | 0.5 | 1 | 1 | 0 | 0.5 |
| C | 3 | 2 | 1 | 1 | 1 | 0.5 | 0.92 | 0.92 | 0 | 0.5 |
| D | 3 | 1 | 0 | 1 | 0 | 0 | 0.85 | 0.92 | 0.07 | 0 |
| E | 6 | 0 | 0 | 0 | 0 |  | 1 | 1 | 0 |  |
| F | 6 | 2 | 1 | 1 | 1 | 0.5 | 1 | 1 | 0 | 0.5 |
| H | 2 | 0 | 0 | 0 | 0 |  | 1 | 1 | 0 |  |
| tim | 6 | 3 | 2 | 1 | 1 | 0.333 | 1 | 0.85 | -0.15 | 0.667 |

Pooled net drift (first→last window): mean -0.010, bootstrap 95% CI [-0.056, +0.026] — overlaps zero.


### hypoCaution

| user | n_windows | n_changes | down | up | reversals | revert_rate | first | last | net | monotone_down |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A | 6 | 0 | 0 | 0 | 0 |  | 1 | 1 | 0 |  |
| B | 5 | 2 | 1 | 1 | 1 | 0.5 | 1 | 1 | 0 | 0.5 |
| C | 3 | 2 | 1 | 1 | 1 | 0.5 | 1.4 | 1.2 | -0.2 | 0.5 |
| D | 3 | 1 | 1 | 0 | 0 | 0 | 2 | 1.5 | -0.5 | 1 |
| E | 6 | 0 | 0 | 0 | 0 |  | 1 | 1 | 0 |  |
| F | 6 | 2 | 1 | 1 | 1 | 0.5 | 1 | 1 | 0 | 0.5 |
| H | 2 | 0 | 0 | 0 | 0 |  | 1 | 1 | 0 |  |
| tim | 6 | 4 | 2 | 2 | 2 | 0.5 | 1 | 1.7 | 0.7 | 0.5 |

Pooled net drift (first→last window): mean +0.000, bootstrap 95% CI [-0.188, +0.237] — overlaps zero.


### confirmedCap

| user | n_windows | n_changes | down | up | reversals | revert_rate | first | last | net | monotone_down |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A | 6 | 2 | 1 | 1 | 1 | 0.5 | 7.5 | 7.5 | 0 | 0.5 |
| B | 5 | 4 | 1 | 3 | 2 | 0.5 | 3.03 | 3.6 | 0.57 | 0.25 |
| C | 3 | 2 | 2 | 0 | 0 | 0 | 6.58 | 3.25 | -3.33 | 1 |
| D | 3 | 1 | 1 | 0 | 0 | 0 | 2 | 1.5 | -0.5 | 1 |
| E | 6 | 5 | 2 | 3 | 4 | 0.8 | 4.61 | 5.06 | 0.45 | 0.4 |
| F | 6 | 5 | 2 | 3 | 3 | 0.6 | 3 | 4.5 | 1.5 | 0.4 |
| H | 2 | 1 | 1 | 0 | 0 | 0 | 6 | 5.06 | -0.94 | 1 |
| tim | 6 | 2 | 1 | 1 | 0 | 0 | 2 | 2 | 0 | 0.5 |

Pooled net drift (first→last window): mean -0.281, bootstrap 95% CI [-1.309, +0.518] — overlaps zero.


### committedCap

| user | n_windows | n_changes | down | up | reversals | revert_rate | first | last | net | monotone_down |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A | 6 | 4 | 1 | 3 | 2 | 0.5 | 1.07 | 1.12 | 0.05 | 0.25 |
| B | 5 | 4 | 3 | 1 | 2 | 0.5 | 1.47 | 1.47 | 0 | 0.75 |
| C | 3 | 2 | 2 | 0 | 0 | 0 | 0.93 | 0.9 | -0.03 | 1 |
| D | 3 | 2 | 0 | 2 | 0 | 0 | 1.04 | 1.29 | 0.25 | 0 |
| E | 6 | 4 | 0 | 4 | 0 | 0 | 0.51 | 0.79 | 0.28 | 0 |
| F | 6 | 4 | 2 | 2 | 1 | 0.25 | 0.86 | 0.91 | 0.05 | 0.5 |
| H | 2 | 1 | 1 | 0 | 0 | 0 | 1.26 | 0.91 | -0.35 | 1 |
| tim | 6 | 5 | 3 | 2 | 3 | 0.6 | 0.74 | 0.34 | -0.4 | 0.6 |

Pooled net drift (first→last window): mean -0.019, bootstrap 95% CI [-0.185, +0.138] — overlaps zero.


### cumulative60

| user | n_windows | n_changes | down | up | reversals | revert_rate | first | last | net | monotone_down |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A | 6 | 4 | 2 | 2 | 2 | 0.5 | 9.6 | 9.7 | 0.1 | 0.5 |
| B | 5 | 4 | 1 | 3 | 2 | 0.5 | 6 | 6.5 | 0.5 | 0.25 |
| C | 3 | 2 | 2 | 0 | 0 | 0 | 8.4 | 5.1 | -3.3 | 1 |
| D | 3 | 0 | 0 | 0 | 0 |  | 4.1 | 4.1 | 0 |  |
| E | 6 | 5 | 2 | 3 | 4 | 0.8 | 5.6 | 6.6 | 1 | 0.4 |
| F | 6 | 5 | 2 | 3 | 3 | 0.6 | 4.7 | 6.3 | 1.6 | 0.4 |
| H | 2 | 1 | 1 | 0 | 0 | 0 | 8.5 | 6.9 | -1.6 | 1 |
| tim | 6 | 5 | 3 | 2 | 3 | 0.6 | 3.5 | 2.7 | -0.8 | 0.6 |

Pooled net drift (first→last window): mean -0.313, bootstrap 95% CI [-1.400, +0.625] — overlaps zero.


### primerCap

| user | n_windows | n_changes | down | up | reversals | revert_rate | first | last | net | monotone_down |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A | 6 | 4 | 1 | 3 | 2 | 0.5 | 0.54 | 0.56 | 0.02 | 0.25 |
| B | 5 | 4 | 3 | 1 | 2 | 0.5 | 0.59 | 0.59 | 0 | 0.75 |
| C | 3 | 1 | 1 | 0 | 0 | 0 | 0.37 | 0.36 | -0.01 | 1 |
| D | 3 | 2 | 0 | 2 | 0 | 0 | 0.26 | 0.52 | 0.26 | 0 |
| E | 6 | 4 | 0 | 4 | 0 | 0 | 0.26 | 0.4 | 0.14 | 0 |
| F | 6 | 4 | 2 | 2 | 1 | 0.25 | 0.34 | 0.36 | 0.02 | 0.5 |
| H | 2 | 1 | 1 | 0 | 0 | 0 | 0.6 | 0.36 | -0.24 | 1 |
| tim | 6 | 5 | 3 | 2 | 2 | 0.4 | 0.3 | 0.09 | -0.21 | 0.6 |

Pooled net drift (first→last window): mean -0.002, bootstrap 95% CI [-0.110, +0.101] — overlaps zero.


## 2b. Which term is actually binding each cap

| cap | tdd/40 | smb_p75 | manual_p90 | floor(1.5) | smb_p95 |
| --- | --- | --- | --- | --- | --- |
| committedCap = max(smb_p75, TDD/40) | 36 (97%) | 1 (3%) |  |  |  |
| confirmedCap = max(manual_p90, smb_p95) |  |  | 32 (86%) | 4 (11%) | 1 (3%) |

The ratchet can only bite through a term that reads Boost's own clipped output. Where TDD/40 or the 1.5 U floor binds instead, the derived cap is anchored to something the caps do not censor.


## 3. One ratchet step: delivered-sourced vs desired-sourced caps

| user | days | n_delivered | n_desired | p75_delivered | p75_desired | p95_delivered | p95_desired | tdd40 | ccap_delivered | ccap_desired | fcap_delivered | fcap_desired |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tim | 23 | 889 | 878 | 0.2 | 0.296 | 0.9 | 1.418 | 0.312 | 0.31 | 0.31 | 1.5 | 1.5 |
| A | 6 | 365 | 412 | 0.65 | 0.487 | 2.25 | 2.367 | 1.14 | 1.14 | 1.14 | 6 | 6 |
| B | 6 | 547 | 605 | 0.45 | 1.277 | 1.335 | 5.296 | 1.531 | 1.53 | 1.53 | 1.5 | 5.3 |
| C | 18 | 511 | 785 | 0.5 | 0.99 | 1.275 | 4.359 | 0.841 | 0.84 | 0.99 | 1.5 | 4.36 |
| D | 7 | 394 | 461 | 0.4 | 0.444 | 1.417 | 2.251 | 1.458 | 1.46 | 1.46 | 2 | 2.25 |
| E | 8 | 315 | 382 | 0.2 | 0.367 | 0.6 | 0.91 | 0.674 | 0.67 | 0.67 | 4.89 | 4.89 |
| F | 7 | 464 | 454 | 0.263 | 0.539 | 0.892 | 1.937 | 0.897 | 0.9 | 0.9 | 4.47 | 4.47 |
| H | 6 | 321 | 338 | 0.4 | 0.365 | 0.9 | 1.277 | 0.857 | 0.86 | 0.86 | 4.6 | 4.6 |

committedCap  from the uncapped desired shot minus from delivered SMBs: mean +0.019, 95% CI [+0.000, +0.056].

confirmedCap  from the uncapped desired shot minus from delivered SMBs: mean +0.864, 95% CI [+0.031, +1.900].

p95(SMB)      from the uncapped desired shot minus from delivered SMBs: mean +1.281, 95% CI [+0.462, +2.231].


`desired` = budget x actionMult x velocityFactor on cycles that ACTUALLY dosed — the same events as `delivered`, un-clipped. Windows are short (the dose-chain fields were only added 2026-07-10), so `days`/`n` are the honest sample here.


## 3b. Material change rate — changes that beat their own sampling band

| knob | users | changes | material | material_share | ci_lo | ci_hi |
| --- | --- | --- | --- | --- | --- | --- |
| aggression | 5 | 10 | 9 | 0.9 | 0.8 | 1 |
| hypoCaution | 5 | 11 | 5 | 0.455 | 0.2 | 0.75 |
| confirmedCap | 8 | 22 | 13 | 0.591 | 0.363 | 0.725 |
| committedCap | 8 | 26 | 17 | 0.654 | 0.375 | 0.844 |
| cumulative60 | 7 | 26 | 16 | 0.615 | 0.471 | 0.8 |
| primerCap | 8 | 25 | 8 | 0.32 | 0.125 | 0.713 |

The band is a day-block bootstrap (days are the resampling unit) over the SAME window, so it isolates sampling noise from real drift. A change inside the band is a number the same fortnight could have produced by chance.


## 4. A/A null — split-half re-derivation inside one window (secondary)

| knob | n | mean_abs_delta | ci_lo | ci_hi | pct_changed |
| --- | --- | --- | --- | --- | --- |
| aggression | 30 | 0.035 | 0.017 | 0.055 | 0.333 |
| hypoCaution | 30 | 0.21 | 0.103 | 0.337 | 0.367 |
| confirmedCap | 30 | 0.686 | 0.286 | 1.174 | 0.633 |
| committedCap | 30 | 0.086 | 0.053 | 0.125 | 0.933 |
| cumulative60 | 30 | 0.803 | 0.403 | 1.31 | 0.867 |
| primerCap | 30 | 0.068 | 0.045 | 0.094 | 0.833 |

Both halves come from the same window, so every non-zero delta here is sampling noise. NOTE the halves are HALF the analysis width, so this overstates the noise of a full 28-day derivation — §3b's bootstrap is the like-for-like null. At a 28-day analysis width the halves are 14 days, i.e. exactly the production LOOKBACK_DAYS, so this table doubles as the noise floor of the shipping one-shot derivation.


## 4b. Drift vs noise — is there anything to track?

| knob | mean_drift | mean_span | mean_noise_half | drift_over_noise | ci_lo | ci_hi | users_gt1 | n |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| aggression | 0.028 | 0.057 | 0.025 | 1.568 | 0 | 3.205 | 2 | 8 |
| hypoCaution | 0.175 | 0.312 | 0.16 | 1 | 0.107 | 1.894 | 2 | 8 |
| confirmedCap | 0.911 | 1.574 | 0.471 | 2.122 | 0.832 | 3.852 | 5 | 8 |
| committedCap | 0.176 | 0.213 | 0.067 | 2.72 | 1.152 | 4.588 | 5 | 8 |
| cumulative60 | 1.112 | 1.775 | 0.542 | 2.311 | 1.06 | 3.591 | 5 | 8 |
| primerCap | 0.112 | 0.129 | 0.056 | 2.311 | 0.857 | 3.814 | 4 | 8 |

`drift` = |last window − first window| (~5 months apart); `noise_half` = half-width of that knob's day-block bootstrap band. Ratio > 1 means the movement over five months is bigger than the noise of measuring it once — i.e. there is real drift for a re-derivation to track. Ratio < 1 means a re-run would mostly be chasing its own sampling error.


## 5. Harm pricing of committedCap lowerings

| user | t0 | old | new | baseline | u_removed | n_cycles | protective_share |
| --- | --- | --- | --- | --- | --- | --- | --- |
| tim | 2026-06-05 | 0.45 | 0.31 | 0.278 | 5.44 | 51 | 0.134 |
| B | 2026-07-03 | 1.49 | 1.47 | 0.128 | 2.28 | 114 | 0.123 |
| C | 2026-06-18 | 0.91 | 0.9 | 0.372 | 0.4 | 40 | 0.6 |
| F | 2026-06-05 | 1.02 | 0.99 | 0.15 | 0.16 | 6 | 0 |
| F | 2026-07-03 | 0.99 | 0.91 | 0.15 | 9.76 | 124 | 0.156 |
| H | 2026-06-29 | 1.26 | 0.91 | 0.098 | 20.965 | 78 | 0.137 |

Removed insulin's pre-low share minus the user's baseline pre-low share: mean -0.004, 95% CI [-0.098, +0.097]. Positive = the removed insulin was more often followed by a real low than the user's average unit, i.e. the lowering was protective.
