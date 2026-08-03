# Pricing a re-anchored confirmedCap

Dose-chain propagation through the observed brake behaviour; window is the `velocityFactor` telemetry era (from 2026-07-10). Raises only — the arithmetic never reduces a delivered dose.


## TDD/12

| user | tdd | cap_old | cap_new | direction | days | n_confirm | n_raised | clip_rate_old | added_U_per_day | added_pct_tdd | held_U_per_day | held_pct_tdd | prelow_share_added | prelow_share_baseline | tbr70 | sev54 | raise_guard |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tim | 12.5 | 2.25 | 1.5 | lower | 23.59 | 76 | 8 | 0.14 | 0.08 | 0.66 | 0.08 | 0.66 | 0.31 | 0.31 | 6.07 | 1.5 | BLOCKED |
| A | 44.9 | 4 | 3.74 | lower | 6.98 | 21 | 0 | 0.29 | 0 | 0 | 0 | 0 |  | 0.16 | 0.71 | 0.04 | allowed |
| B | 61.3 | 3.5 | 5.11 | raise | 6.91 | 30 | 26 | 0.8 | 5.03 | 8.21 | 3.16 | 5.16 | 0.08 | 0.1 | 3.42 | 1.16 | BLOCKED |
| C | 32.9 | 2 | 2.74 | raise | 18.98 | 107 | 53 | 0.55 | 1.8 | 5.47 | 1.78 | 5.4 | 0.53 | 0.34 | 5.68 | 1.01 | BLOCKED |
| D | 57.6 | 1.5 | 4.8 | raise | 7.29 | 35 | 19 | 0.57 | 4.91 | 8.52 | 0.61 | 1.06 | 0.31 | 0.29 | 5.97 | 1.44 | BLOCKED |
| E | 25.9 | 2.5 | 2.16 | lower | 8.76 | 4 | 1 | 0.5 | 0.02 | 0.07 | 0.01 | 0.03 | 0 | 0.09 | 0.91 | 0 | allowed |
| F | 35.8 | 4.5 | 2.98 | lower | 7.86 | 20 | 0 | 0.5 | 0 | 0 | 0 | 0 |  | 0.09 | 0.88 | 0.09 | allowed |
| H | 34.7 | 4 | 2.89 | lower | 6.91 | 14 | 0 | 0.29 | 0 | 0 | 0 | 0 |  | 0.17 | 1.81 | 0 | allowed |

Holding the rolling-60-min budget at its CURRENT value instead of letting it rise with the cap turns the change into a pure redistribution: added insulin drops from 3.91 to 1.85 U/day across the raise users (3.9% of TDD), i.e. 53% of the extra is absorbed by the existing hourly budget.


## TDD/10

| user | tdd | cap_old | cap_new | direction | days | n_confirm | n_raised | clip_rate_old | added_U_per_day | added_pct_tdd | held_U_per_day | held_pct_tdd | prelow_share_added | prelow_share_baseline | tbr70 | sev54 | raise_guard |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tim | 12.5 | 2.25 | 1.5 | lower | 23.59 | 76 | 8 | 0.14 | 0.08 | 0.66 | 0.08 | 0.66 | 0.31 | 0.31 | 6.07 | 1.5 | BLOCKED |
| A | 44.9 | 4 | 4.49 | raise | 6.98 | 21 | 6 | 0.29 | 0.35 | 0.77 | 0.35 | 0.77 | 0 | 0.16 | 0.71 | 0.04 | allowed |
| B | 61.3 | 3.5 | 6.13 | raise | 6.91 | 30 | 26 | 0.8 | 6.59 | 10.75 | 3.37 | 5.49 | 0.09 | 0.1 | 3.42 | 1.16 | BLOCKED |
| C | 32.9 | 2 | 3.29 | raise | 18.98 | 107 | 53 | 0.55 | 2.61 | 7.93 | 2.56 | 7.77 | 0.53 | 0.34 | 5.68 | 1.01 | BLOCKED |
| D | 57.6 | 1.5 | 5.76 | raise | 7.29 | 35 | 19 | 0.57 | 5.69 | 9.87 | 0.61 | 1.06 | 0.29 | 0.29 | 5.97 | 1.44 | BLOCKED |
| E | 25.9 | 2.5 | 2.59 | raise | 8.76 | 4 | 2 | 0.5 | 0.03 | 0.11 | 0.01 | 0.03 | 0 | 0.09 | 0.91 | 0 | allowed |
| F | 35.8 | 4.5 | 3.58 | lower | 7.86 | 20 | 0 | 0.5 | 0 | 0 | 0 | 0 |  | 0.09 | 0.88 | 0.09 | allowed |
| H | 34.7 | 4 | 3.47 | lower | 6.91 | 14 | 2 | 0.29 | 0.14 | 0.39 | 0.14 | 0.39 | 0 | 0.17 | 1.81 | 0 | allowed |

Among users the raise-guard would actually let through (n=2): added insulin 0.19 U/day (0.4% of TDD); the added insulin's pre-low share minus baseline = -0.124 [-0.160, -0.088]. Positive = the extra insulin lands disproportionately before real lows.


Holding the rolling-60-min budget at its CURRENT value instead of letting it rise with the cap turns the change into a pure redistribution: added insulin drops from 3.05 to 1.38 U/day across the raise users (3.0% of TDD), i.e. 55% of the extra is absorbed by the existing hourly budget.


## TDD/8

| user | tdd | cap_old | cap_new | direction | days | n_confirm | n_raised | clip_rate_old | added_U_per_day | added_pct_tdd | held_U_per_day | held_pct_tdd | prelow_share_added | prelow_share_baseline | tbr70 | sev54 | raise_guard |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tim | 12.5 | 2.25 | 1.56 | lower | 23.59 | 76 | 8 | 0.14 | 0.09 | 0.69 | 0.09 | 0.69 | 0.31 | 0.31 | 6.07 | 1.5 | BLOCKED |
| A | 44.9 | 4 | 5.61 | raise | 6.98 | 21 | 6 | 0.29 | 0.93 | 2.08 | 0.93 | 2.08 | 0 | 0.16 | 0.71 | 0.04 | allowed |
| B | 61.3 | 3.5 | 7.5 | raise | 6.91 | 30 | 26 | 0.8 | 8.17 | 13.33 | 3.38 | 5.51 | 0.09 | 0.1 | 3.42 | 1.16 | BLOCKED |
| C | 32.9 | 2 | 4.11 | raise | 18.98 | 107 | 53 | 0.55 | 3.59 | 10.91 | 3.23 | 9.81 | 0.51 | 0.34 | 5.68 | 1.01 | BLOCKED |
| D | 57.6 | 1.5 | 7.2 | raise | 7.29 | 35 | 19 | 0.57 | 6.28 | 10.9 | 0.61 | 1.06 | 0.26 | 0.29 | 5.97 | 1.44 | BLOCKED |
| E | 25.9 | 2.5 | 3.24 | raise | 8.76 | 4 | 2 | 0.5 | 0.1 | 0.4 | 0.01 | 0.03 | 0 | 0.09 | 0.91 | 0 | allowed |
| F | 35.8 | 4.5 | 4.47 | lower | 7.86 | 20 | 1 | 0.5 | 0.04 | 0.11 | 0.04 | 0.11 | 1 | 0.09 | 0.88 | 0.09 | allowed |
| H | 34.7 | 4 | 4.34 | raise | 6.91 | 14 | 4 | 0.29 | 0.48 | 1.4 | 0.48 | 1.4 | 0.1 | 0.17 | 1.81 | 0 | allowed |

Among users the raise-guard would actually let through (n=3): added insulin 0.51 U/day (1.3% of TDD); the added insulin's pre-low share minus baseline = -0.106 [-0.160, -0.071]. Positive = the extra insulin lands disproportionately before real lows.


Holding the rolling-60-min budget at its CURRENT value instead of letting it rise with the cap turns the change into a pure redistribution: added insulin drops from 3.26 to 1.44 U/day across the raise users (3.3% of TDD), i.e. 56% of the extra is absorbed by the existing hourly budget.


## Who the change actually reaches

| user | TDD/10 | TDD/12 | TDD/8 | cap_old | raise_guard | clip_rate_old |
| --- | --- | --- | --- | --- | --- | --- |
| A | 4.49 | 3.74 | 5.61 | 4 | allowed | 0.29 |
| B | 6.13 | 5.11 | 7.5 | 3.5 | BLOCKED | 0.8 |
| C | 3.29 | 2.74 | 4.11 | 2 | BLOCKED | 0.55 |
| D | 5.76 | 4.8 | 7.2 | 1.5 | BLOCKED | 0.57 |
| E | 2.59 | 2.16 | 3.24 | 2.5 | allowed | 0.5 |
| F | 3.58 | 2.98 | 4.47 | 4.5 | allowed | 0.5 |
| H | 3.47 | 2.89 | 4.34 | 4 | allowed | 0.29 |
| tim | 1.5 | 1.5 | 1.56 | 2.25 | BLOCKED | 0.14 |

`clip_rate_old` is how often the confirm shot is currently pinned at the cap — the engagement rate. A re-anchor does nothing for a user whose cap never binds.
