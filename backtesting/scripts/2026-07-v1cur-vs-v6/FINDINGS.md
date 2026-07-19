# V1 (as it runs in the V7-shadow build) vs V6 — confirm-meal dosing comparison

*2026-07-19. Follow-up to the ml-beta-vs-current V1 diff: the v7-shadow V1 adds two restraining guards
(post-rescue cap; cumulative SMB cap) + the v12 ML hypo-risk model over ml-beta. Does that changed V1
still out-perform V6 on the meal window? Semi-closed-loop confirm-meal replay, same method as
2026-07-v6-sim/sim_replay.py. Script: v1_vs_v6_replay.py (per-user parallel; JSON gitignored).*

## Verdict: V6 modestly BEATS the current V1 on the confirm shot (7/8 users).

| user | confirm meals | V6 TING% | V1 TING% | V6 tail | V1 tail | Δdose (V1−V6) |
|---|---|---|---|---|---|---|
| tim | 439 | 47.6 | 38.6 | 134 | 147 | −0.2U |
| C | 88 | 64.0 | 54.9 | 109 | 136 | −0.2U |
| H | 104 | 44.3 | 38.8 | 134 | 144 | −0.2U |
| D | 19 | 72.5 | 67.6 | 116 | 85 | +0.1U |
| B | 227 | 44.0 | 39.9 | 142 | 150 | −0.2U |
| F | 143 | 29.3 | 27.5 | 150 | 165 | −0.1U |
| A | 175 | 32.1 | 30.7 | 155 | 158 | −0.3U |
| E | 31 | 68.8 | 69.1 | 107 | 105 | 0.0U |

V6 wins TING in 7/8 (E tie) by ~+1.4 to +9 points; V1 sits on a higher post-meal tail. At the
confirmation point V1's UAM tiers dose ~0.1–0.3U LESS than V6's 1.8× confirm shot, so the V1
counterfactual gets less insulin and plateaus higher. (tim/D also show V1 crashing a touch MORE despite
lower mean dose — V1's UAM tiers are spikier meal-to-meal: bigger on some, smaller on others.)

## This does NOT contradict the earlier −7.5 TING (V1 better) finding
Different window. The −7.5 lived on the DESCENT/RECOVERY — V1's late recovery corrections that V6's
high-IOB brake suppresses ([[v6-vs-v1-meal-regression-2026-07-19]]). The two are consistent: **V6 wins
the confirm shot, V1 wins the recovery tail**; over the full meal window the recovery effect dominated
to give V1 the net edge. So the descent, not the confirm shot, remains the lever (plateau-nudge line).

## Architecture fact that makes this a faithful current-V1 comparison
OpenAPSBoostPlugin ~1345: ONE determine_basal call — the V1 DetermineBasalBoost — passed the RESOLVED
cumulativeSmbCap60Min (line 1360). Its rT.units IS the logged `v1_units`, computed live each cycle WITH
all of V1's guards (post-rescue cap, cumulative cap, v12 ML). The V5/V6 override replaces .units AFTER
(line 1505). So **the DB's v1_units already IS the in-build V1 dose** — no offline reconstruction needed,
and all the ml-beta→current changes are already baked in for current-build data.

### Discarded first attempt (documented so the trap isn't repeated)
I first tried to reconstruct current-V1 by re-applying the guards to logged V1 → tails of 250–300 (an
artifact). Wrong twice: (1) double-counted guards that are already in v1_units; (2) used the 1.5U
signature default for the cumulative cap when the real DoubleKey default is **10.0**, auto-config-derived
(candidates [1.5, 6.0]) — the recurring auto-config-≠-default + U200 trap. ISF winsorised 250, per-user
median throughout.

CAVEAT: earliest V6-era v1_units came from pre-2026-07-04 builds (no post-rescue cap), but confirm meals
almost never sit in a post-rescue window (BG high), so that guard barely moves confirm-meal v1_units.
