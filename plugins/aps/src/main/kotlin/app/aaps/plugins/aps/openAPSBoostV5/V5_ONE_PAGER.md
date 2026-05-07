# Boost V5 — at a glance

**What it is**

Boost V5 is a clean-slate redesign of the Boost dosing decision. Instead of
the eight-tier if-else ladder that V1 through V4.4.1 all share, V5 uses a
short three-phase pipeline that asks "is this a meal?" once, commits to a
single decision, and applies safety damping in a defined order. It's a
parallel plugin alongside the existing variants — V1, V2, V3, V3ML, V3MLG3
(V4.4.1) and V5 all coexist; the user picks which one is active. V5 is
currently shadow-only and not user-selectable.


**Why we did it**

A few specific patterns prompted the rewrite. On 5 May, V4.4.1 saw a real
meal climbing but the UAM_BOOST tier's conditions just-missed (`uamBoost1`
at 1.15 against a 1.20 threshold) — the algorithm fell through to a slower
tier and didn't deliver SMB for 41 minutes while BG climbed. That's a
binary-threshold problem, and it's the kind of thing that's hard to fix
inside a tier ladder without making the existing tiers more complex.

There were others. The brake stack (`mlRiskScale`, `postSmbScale`,
`fastCarbScale`) has no overall floor, so under stacked high-risk it can
push doses to ~5 % of what oref calculated. On 6 May, Activity mode
persisted for 35 minutes after a walk into a meal and BG peaked at 232.
The Boost preferences screen has 60+ entries, ~30 of them Boost-specific
knobs the user is expected to tune themselves.

V5's design tenet is that users shouldn't be confronted with hundreds of
internal knobs. Constants get calibrated once at release; user-facing
settings only exist for things where per-user variation genuinely helps.


**What V5 changes**

V5 carries an explicit "meal hypothesis" across cycles: IDLE → OBSERVING
(small test dose, 30 % of normal) → CONFIRMED (catch-up dose, 180 % of
normal) → COMMITTED (sustain) → RECOVERING (back off as IOB takes effect)
→ IDLE. The whole thing is driven by a continuous 0–1 score — six weighted
signals including BG delta, acceleration, the ML meal-likelihood model, a
recent-low penalty and time of day. No binary cliffs; just-miss patterns
accumulate score over time and reach the right state.

Safety composition has a hard floor: the dose cannot fall below 30 % of
oref's calculated need before action multipliers are applied. The two
remaining damping multipliers are graduated, so they smooth rather than
stack to zero.

V5 also introduces ML hypo-risk damping (V1 has no ML at all) and a
graduated IOB headroom brake replacing V1's hard Tier 7 cap.


**What stays the same**

This is the part that often surprises people. V5 only redesigns the
dosing decision — the layer below `determineBasal`. The plugin code that
shapes the inputs (sleep-in window, inactivity scaling, exercise
classification, post-exercise recovery detection, dynISF velocity, time
window, HR zones, profile, ISF, autosens, TempTargets) all keeps working
unchanged. V5 reads the result, it doesn't rebuild it.

So if you're running V4.4.1 today, your sleep-in, activity %, post-exercise
recovery hours, dynISF velocity, max IOB, autosens, TempTarget handling —
none of that changes. The only settings you stop using are the dose-sizing
dials that lived inside `determineBasal`: `boost_insulin_req_pct`,
`boost_scale`, `boost_percent_scale_factor`, `boost_bolus_cap`, the
per-tier toggles. V5 has its own internal logic instead.


**What's new for the user**

Three new dials, calibrated defaults:

- **Aggression** (0.7–1.3, default 1.0): scales the catch-up dose at the
  CONFIRMED moment.
- **Hypo Caution** (1.0–2.0, default 1.0): strengthens the brake when the
  ML hypo-risk model thinks a low is coming. Raise it for hypo unawareness
  or recent severe lows.
- **Sensitivity** (reserved): currently inert; may ship if backtesting
  justifies it.

That's the entire user-facing surface V5 adds.


**Where we are**

V5 is **PRE-ALPHA and shadow-only**. It's hidden from the plugin list and
cannot be selected as the active APS algorithm. When V4.4.1 runs, V5 runs
alongside as a sidecar — sees the same inputs, makes its own parallel
decision, and writes that decision to the AAPS log and the Nightscout
deviceStatus (`boostV5_score`, `boostV5_state`, `boostV5_age`,
`boostV5_budget`, `boostV5_actionMult`, `boostV5_finalDose`,
`boostV5_gateReduction`). V5 does not deliver insulin.

A daily comparison script tracks where V5 and V4.4.1 disagreed for review.
V5 will only become user-selectable after the test plan's acceptance gates
pass on real shadow-mode data. There is no clinical superiority claim of
V5 over V1 or V4.4.1 yet.
