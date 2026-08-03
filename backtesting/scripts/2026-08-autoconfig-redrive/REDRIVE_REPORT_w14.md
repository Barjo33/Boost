# Auto-config periodic re-derivation — replay report

Generated 2026-08-03 09:52 UTC · window 14d, step 14d · users A, B, C, D, E, F, H, tim

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
| A | 11 | 0 | 0 | 0 | 0 |  | 1 | 1 | 0 |  |
| B | 9 | 5 | 2 | 3 | 3 | 0.6 | 1 | 1 | 0 | 0.4 |
| C | 6 | 5 | 3 | 2 | 3 | 0.6 | 0.92 | 0.85 | -0.07 | 0.6 |
| D | 3 | 1 | 0 | 1 | 0 | 0 | 0.85 | 0.92 | 0.07 | 0 |
| E | 11 | 0 | 0 | 0 | 0 |  | 1 | 1 | 0 |  |
| F | 11 | 2 | 1 | 1 | 1 | 0.5 | 1 | 1 | 0 | 0.5 |
| H | 4 | 0 | 0 | 0 | 0 |  | 1 | 1 | 0 |  |
| tim | 11 | 6 | 3 | 3 | 3 | 0.5 | 0.92 | 0.85 | -0.07 | 0.5 |

Pooled net drift (first→last window): mean -0.009, bootstrap 95% CI [-0.035, +0.018] — overlaps zero.


### hypoCaution

| user | n_windows | n_changes | down | up | reversals | revert_rate | first | last | net | monotone_down |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A | 11 | 0 | 0 | 0 | 0 |  | 1 | 1 | 0 |  |
| B | 9 | 5 | 3 | 2 | 3 | 0.6 | 1 | 1 | 0 | 0.6 |
| C | 6 | 5 | 2 | 3 | 3 | 0.6 | 1.1 | 1.7 | 0.6 | 0.4 |
| D | 3 | 2 | 2 | 0 | 0 | 0 | 2 | 1.2 | -0.8 | 1 |
| E | 11 | 0 | 0 | 0 | 0 |  | 1 | 1 | 0 |  |
| F | 11 | 2 | 1 | 1 | 1 | 0.5 | 1 | 1 | 0 | 0.5 |
| H | 4 | 0 | 0 | 0 | 0 |  | 1 | 1 | 0 |  |
| tim | 11 | 7 | 4 | 3 | 4 | 0.571 | 1.5 | 1.6 | 0.1 | 0.571 |

Pooled net drift (first→last window): mean -0.013, bootstrap 95% CI [-0.287, +0.225] — overlaps zero.


### confirmedCap

| user | n_windows | n_changes | down | up | reversals | revert_rate | first | last | net | monotone_down |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A | 11 | 4 | 2 | 2 | 1 | 0.25 | 7.5 | 7.5 | 0 | 0.5 |
| B | 9 | 8 | 4 | 4 | 7 | 0.875 | 3 | 3 | 0 | 0.5 |
| C | 6 | 5 | 4 | 1 | 2 | 0.4 | 6.15 | 1.6 | -4.55 | 0.8 |
| D | 3 | 0 | 0 | 0 | 0 |  | 1.5 | 1.5 | 0 |  |
| E | 11 | 10 | 4 | 6 | 4 | 0.4 | 4.53 | 5.07 | 0.54 | 0.4 |
| F | 11 | 7 | 3 | 4 | 2 | 0.286 | 3 | 4.43 | 1.43 | 0.429 |
| H | 4 | 1 | 1 | 0 | 0 | 0 | 6 | 4.6 | -1.4 | 1 |
| tim | 11 | 2 | 1 | 1 | 1 | 0.5 | 1.5 | 1.5 | 0 | 0.5 |

Pooled net drift (first→last window): mean -0.498, bootstrap 95% CI [-1.771, +0.492] — overlaps zero.


### committedCap

| user | n_windows | n_changes | down | up | reversals | revert_rate | first | last | net | monotone_down |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A | 11 | 9 | 4 | 5 | 6 | 0.667 | 1.07 | 1.13 | 0.06 | 0.444 |
| B | 9 | 7 | 4 | 3 | 3 | 0.429 | 1.47 | 1.58 | 0.11 | 0.571 |
| C | 6 | 4 | 2 | 2 | 3 | 0.75 | 0.93 | 0.88 | -0.05 | 0.5 |
| D | 3 | 2 | 1 | 1 | 1 | 0.5 | 1.26 | 1.28 | 0.02 | 0.5 |
| E | 11 | 10 | 3 | 7 | 5 | 0.5 | 0.51 | 0.71 | 0.2 | 0.3 |
| F | 11 | 9 | 5 | 4 | 5 | 0.556 | 0.86 | 0.89 | 0.03 | 0.556 |
| H | 4 | 3 | 2 | 1 | 1 | 0.333 | 1.25 | 0.85 | -0.4 | 0.667 |
| tim | 11 | 10 | 4 | 6 | 5 | 0.5 | 0.74 | 0.31 | -0.43 | 0.4 |

Pooled net drift (first→last window): mean -0.058, bootstrap 95% CI [-0.216, +0.080] — overlaps zero.


### cumulative60

| user | n_windows | n_changes | down | up | reversals | revert_rate | first | last | net | monotone_down |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A | 11 | 9 | 5 | 4 | 4 | 0.444 | 9.6 | 9.8 | 0.2 | 0.556 |
| B | 9 | 8 | 4 | 4 | 7 | 0.875 | 5.9 | 6.2 | 0.3 | 0.5 |
| C | 6 | 4 | 3 | 1 | 2 | 0.5 | 8 | 3.4 | -4.6 | 0.75 |
| D | 3 | 1 | 0 | 1 | 0 | 0 | 4 | 4.1 | 0.1 | 0 |
| E | 11 | 10 | 4 | 6 | 5 | 0.5 | 5.6 | 6.5 | 0.9 | 0.4 |
| F | 11 | 10 | 4 | 6 | 5 | 0.5 | 4.7 | 6.2 | 1.5 | 0.4 |
| H | 4 | 2 | 2 | 0 | 0 | 0 | 8.5 | 6.3 | -2.2 | 1 |
| tim | 11 | 9 | 4 | 5 | 5 | 0.556 | 3 | 2.1 | -0.9 | 0.444 |

Pooled net drift (first→last window): mean -0.587, bootstrap 95% CI [-1.950, +0.562] — overlaps zero.


### primerCap

| user | n_windows | n_changes | down | up | reversals | revert_rate | first | last | net | monotone_down |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A | 11 | 9 | 5 | 4 | 6 | 0.667 | 0.54 | 0.56 | 0.02 | 0.556 |
| B | 9 | 7 | 3 | 4 | 5 | 0.714 | 0.59 | 0.6 | 0.01 | 0.429 |
| C | 6 | 4 | 2 | 2 | 3 | 0.75 | 0.37 | 0.22 | -0.15 | 0.5 |
| D | 3 | 2 | 0 | 2 | 0 | 0 | 0.32 | 0.51 | 0.19 | 0 |
| E | 11 | 9 | 2 | 7 | 3 | 0.333 | 0.26 | 0.36 | 0.1 | 0.222 |
| F | 11 | 8 | 3 | 5 | 4 | 0.5 | 0.34 | 0.45 | 0.11 | 0.375 |
| H | 4 | 3 | 2 | 1 | 2 | 0.667 | 0.6 | 0.34 | -0.26 | 0.667 |
| tim | 11 | 10 | 7 | 3 | 4 | 0.4 | 0.3 | 0.08 | -0.22 | 0.7 |

Pooled net drift (first→last window): mean -0.025, bootstrap 95% CI [-0.135, +0.079] — overlaps zero.


## 2b. Which term is actually binding each cap

| cap | tdd/40 | smb_p75 | manual_p90 | floor(1.5) | smb_p95 |
| --- | --- | --- | --- | --- | --- |
| committedCap = max(smb_p75, TDD/40) | 64 (97%) | 2 (3%) |  |  |  |
| confirmedCap = max(manual_p90, smb_p95) |  |  | 45 (68%) | 13 (20%) | 8 (12%) |

The ratchet can only bite through a term that reads Boost's own clipped output. Where TDD/40 or the 1.5 U floor binds instead, the derived cap is anchored to something the caps do not censor.


## 3. One ratchet step: delivered-sourced vs desired-sourced caps

| user | days | n_delivered | n_desired | p75_delivered | p75_desired | p95_delivered | p95_desired | tdd40 | ccap_delivered | ccap_desired | fcap_delivered | fcap_desired |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tim | 14 | 483 | 493 | 0.25 | 0.24 | 1 | 1.571 | 0.312 | 0.31 | 0.31 | 1.5 | 1.57 |
| A | 6 | 365 | 412 | 0.65 | 0.487 | 2.25 | 2.367 | 1.14 | 1.14 | 1.14 | 6 | 6 |
| B | 6 | 547 | 605 | 0.45 | 1.277 | 1.335 | 5.296 | 1.531 | 1.53 | 1.53 | 1.5 | 5.3 |
| C | 14 | 404 | 622 | 0.463 | 1.086 | 1.527 | 4.881 | 0.9 | 0.9 | 1.09 | 1.53 | 4.88 |
| D | 7 | 394 | 461 | 0.4 | 0.444 | 1.417 | 2.251 | 1.458 | 1.46 | 1.46 | 2 | 2.25 |
| E | 8 | 315 | 382 | 0.2 | 0.367 | 0.6 | 0.91 | 0.674 | 0.67 | 0.67 | 4.89 | 4.89 |
| F | 7 | 464 | 454 | 0.263 | 0.539 | 0.892 | 1.937 | 0.897 | 0.9 | 0.9 | 4.47 | 4.47 |
| H | 6 | 321 | 338 | 0.4 | 0.365 | 0.9 | 1.277 | 0.857 | 0.86 | 0.86 | 4.6 | 4.6 |

committedCap  from the uncapped desired shot minus from delivered SMBs: mean +0.024, 95% CI [+0.000, +0.071].

confirmedCap  from the uncapped desired shot minus from delivered SMBs: mean +0.934, 95% CI [+0.031, +2.150].

p95(SMB)      from the uncapped desired shot minus from delivered SMBs: mean +1.321, 95% CI [+0.466, +2.273].


`desired` = budget x actionMult x velocityFactor on cycles that ACTUALLY dosed — the same events as `delivered`, un-clipped. Windows are short (the dose-chain fields were only added 2026-07-10), so `days`/`n` are the honest sample here.


## 3b. Material change rate — changes that beat their own sampling band

| knob | users | changes | material | material_share | ci_lo | ci_hi |
| --- | --- | --- | --- | --- | --- | --- |
| aggression | 5 | 19 | 16 | 0.842 | 0.36 | 0.967 |
| hypoCaution | 5 | 21 | 11 | 0.524 | 0.243 | 0.723 |
| confirmedCap | 7 | 37 | 20 | 0.541 | 0.432 | 0.815 |
| committedCap | 8 | 54 | 25 | 0.463 | 0.338 | 0.573 |
| cumulative60 | 8 | 53 | 24 | 0.453 | 0.246 | 0.571 |
| primerCap | 8 | 52 | 24 | 0.462 | 0.388 | 0.575 |

The band is a day-block bootstrap (days are the resampling unit) over the SAME window, so it isolates sampling noise from real drift. A change inside the band is a number the same fortnight could have produced by chance.


## 4. A/A null — split-half re-derivation inside one window (secondary)

| knob | n | mean_abs_delta | ci_lo | ci_hi | pct_changed |
| --- | --- | --- | --- | --- | --- |
| aggression | 62 | 0.038 | 0.025 | 0.052 | 0.371 |
| hypoCaution | 62 | 0.192 | 0.121 | 0.269 | 0.435 |
| confirmedCap | 62 | 0.5 | 0.296 | 0.747 | 0.645 |
| committedCap | 62 | 0.086 | 0.065 | 0.109 | 0.935 |
| cumulative60 | 62 | 0.613 | 0.402 | 0.869 | 0.855 |
| primerCap | 62 | 0.06 | 0.045 | 0.076 | 0.823 |

Both halves come from the same window, so every non-zero delta here is sampling noise. NOTE the halves are HALF the analysis width, so this overstates the noise of a full 14-day derivation — §3b's bootstrap is the like-for-like null. At a 28-day analysis width the halves are 14 days, i.e. exactly the production LOOKBACK_DAYS, so this table doubles as the noise floor of the shipping one-shot derivation.


## 4b. Drift vs noise — is there anything to track?

| knob | mean_drift | mean_span | mean_noise_half | drift_over_noise | ci_lo | ci_hi | users_gt1 | n |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| aggression | 0.026 | 0.075 | 0.03 | 0.769 | 0.187 | 1.43 | 3 | 8 |
| hypoCaution | 0.188 | 0.438 | 0.191 | 0.755 | 0.05 | 1.644 | 2 | 8 |
| confirmedCap | 0.99 | 2.186 | 0.646 | 1.454 | 0.331 | 2.677 | 4 | 8 |
| committedCap | 0.163 | 0.275 | 0.071 | 2.585 | 0.946 | 4.511 | 4 | 8 |
| cumulative60 | 1.337 | 2.538 | 0.728 | 2.148 | 1.057 | 3.229 | 5 | 8 |
| primerCap | 0.133 | 0.19 | 0.062 | 2.671 | 1.328 | 4.149 | 6 | 8 |

`drift` = |last window − first window| (~5 months apart); `noise_half` = half-width of that knob's day-block bootstrap band. Ratio > 1 means the movement over five months is bigger than the noise of measuring it once — i.e. there is real drift for a re-derivation to track. Ratio < 1 means a re-run would mostly be chasing its own sampling error.


## 5. Harm pricing of committedCap lowerings

| user | t0 | old | new | baseline | u_removed | n_cycles | protective_share |
| --- | --- | --- | --- | --- | --- | --- | --- |
| tim | 2026-07-17 | 0.38 | 0.31 | 0.278 | 9.2 | 137 | 0.351 |
| B | 2026-07-03 | 1.57 | 1.32 | 0.128 | 16.1 | 74 | 0.207 |
| C | 2026-07-16 | 0.9 | 0.88 | 0.372 | 2.9 | 145 | 0.366 |
| D | 2026-07-17 | 1.32 | 1.28 | 0.223 | 0.3 | 9 | 0 |
| E | 2026-07-17 | 0.84 | 0.71 | 0.109 | 5.37 | 47 | 0.128 |
| F | 2026-07-03 | 0.99 | 0.91 | 0.15 | 4.28 | 54 | 0.131 |
| F | 2026-07-17 | 0.91 | 0.89 | 0.15 | 1.45 | 75 | 0.179 |
| H | 2026-06-29 | 1.26 | 1.07 | 0.098 | 6.225 | 36 | 0.064 |
| H | 2026-07-13 | 1.07 | 0.85 | 0.098 | 6.54 | 35 | 0.229 |

Removed insulin's pre-low share minus the user's baseline pre-low share: mean +0.006, 95% CI [-0.062, +0.059]. Positive = the removed insulin was more often followed by a real low than the user's average unit, i.e. the lowering was protective.
