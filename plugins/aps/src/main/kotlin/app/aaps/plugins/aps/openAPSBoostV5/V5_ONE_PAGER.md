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

Honestly, the real reason was that I didn't want to keep baking more
complex layers into the existing code. By V4.4.1 the Boost core was
around 1,500 lines with eight tier formulas and eleven different
modulators on top, and each new safety mechanism — the IOB-cap
reinstatement, the hypo-risk model, the G3 hold, the G3 release fix — had
to be threaded through the existing structure as another multiplicative
brake or another tier-eligibility gate. The V4.5 design queue had a dozen
more items waiting. The next layer would have made the code harder to
reason about and harder to modify safely.

The pipeline map I put together on 2 May made it concrete: the fast-carb
heuristic and the meal-likelihood model trying to detect the same thing
differently, `mlTierDowngrade` and `mlRiskScale` double-braking on the
same metric, the brake stack having no overall floor and being able to
drive doses to ~5 % of oref's calculated need. The kind of thing where
patching one symptom creates another. Specific incidents — the 5 May
just-miss that left BG climbing for 41 minutes, the 6 May walk-into-a-meal
where Activity mode persisted 35 min and BG peaked at 232 — were real but
they're symptoms of the layering, not separate problems.

So instead of layer #12, V5 is a redesign. The minimal-settings tenet
fell out of that — constants get calibrated once at release, user-facing
settings only exist where per-user variation genuinely helps.


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
