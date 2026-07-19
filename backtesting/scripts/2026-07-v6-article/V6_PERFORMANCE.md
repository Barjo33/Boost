# Boost V6 — cohort performance + dosing mechanism (2026-07-19)

Backing analysis for the V6 article. Script: `v6_analysis.py` (DB `oref.boost_decisions`, refreshed
to t=now first). Cohort = 7 V6-active users (`boostv5_active`), 11–24 days each, through 2026-07-19.
Honest by construction — see the split between **descriptive outcomes** and **clean mechanism** below.

## Outcomes over the V6-active era — DESCRIPTIVE, not a causal effect

Median across users (within-subject summary; the cohort is small + self-selected):

| metric | median | IQR |
|---|---|---|
| TIR 70–180 | 84.4% | 81.2–91.6 |
| TING 63–140 | 66.1% | 61.3–73.2 |
| TAR >180 | 13.4% | 4.9–16.4 |
| TBR <70 | 1.8% | 1.2–3.5 |
| TBR <54 | 0.2% | 0.1–0.7 |
| glucose CV | 29.3% | 25.0–34.7 |

Per-user spread is large (TIR 77–99%) and tracks CV almost perfectly — consistent with the frontier
finding that tight-range time is a **variability** problem (~1.3pp TING per 1% CV), not a dose-harder
one. **These are outcomes WHILE on V6, not a measured V6 effect:** no glucose simulator ⇒ no
counterfactual; the cross-user Boost-vs-oref advantage dissolved under a matched baseline (+2.9pp raw
→ +1.2pp adjusted, permutation p≈0.27); the within-user RCT has not been run.

## Dosing mechanism — CLEAN (same-cycle `boostv5_finaldose` vs `v1_units`)

Every cycle V6 records the dose base oref would have given. Comparing them, same user/cycle/glucose,
needs no counterfactual. V6 **changes the dose on ~1 cycle in 11** (median: amplify 5.0%, restrain
3.5%, identical 91.4%), net **+11%** insulin/day. Where it spends that insulin:

- **By glucose band (pooled, U/1000 cyc):** low <90 **+1.5**, in-band 90–140 +9.8, **mild-high
  140–180 +20.5**, high >180 +11.3. → concentrates on the addressable mild-high band; near-absent at lows.
- **By state:** IDLE +4.2, OBSERVING +4.8, **CONFIRMED +181.9** → almost all intervention lands once a
  meal is confirmed (front-loads ~40× harder than any other state).
- **By time of day:** overnight +32.4 vs day +9–11 U/1000 cyc.

## Safety

Dosing +11% more, low exposure stayed inside consensus targets across all 7 users (TBR<70 median
1.8% vs <4%; TBR<54 0.2% vs <1%). The mechanism is visible: extra insulin goes to 140–180, not into
recovering lows.

## Overnight — descriptively strong, but confounded

Overnight (00–06) median TIR 96% / TING 88% vs daytime 81% / 60%, and V6 is most active overnight.
**Not attributable to V6:** overnight is fasting (no meals to mishandle); every loop does better
there. Separating it needs a within-user randomised night (not run).

## What can / can't be claimed

- Mechanism (how V6 differs from oref): **solid** — clean same-cycle comparison.
- Safety (lows held while dosing more): **well-supported** — observed fact of the era.
- Outcome improvement: **not established** — no counterfactual, confounded cross-user comparison.
- Generality: **limited** — 7 self-selected users, hypothesis-generating.

Article (private artifact): rendered from `boost-v6-article.html` (scratchpad; not committed —
carries the same aggregates). Anonymised P1–P7; cross-user figures use within-user ratios / pooled
patterns to avoid mixing insulin concentrations (U100 vs U200).
