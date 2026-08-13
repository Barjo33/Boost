# Per-user configuration, and four attempts to tune it online

## Hypothesis

Participants differ enough that a single set of dosing knobs cannot serve them, which the attribution
study demonstrated when cap clipping ranged from nothing to 59 per cent of one participant's time
above range while brake suppression ranged from 11 to 47. The question was how the per-person values
should be arrived at.

Two answers were available. Derive them once from the person's own history and hold them, or adjust
them continuously against outcomes. The second is more appealing, adapts to drift, and is what most
people assume an adaptive system should do.

## Investigation

The derivation approach was built first and validated on a migration cohort, then extended to a
periodic re-derivation on a fixed cadence.

The online approach was tested four separate times, on two caps and two sliders, each as a controller
that raises or lowers the knob in response to observed outcomes. Each was replayed against the record
and evaluated on whether its adjustments persisted or reverted.

## Methods

Recorded under `backtesting/scripts/2026-07-cap-stepper/`, `2026-07-slider-controller/` and
`2026-08-autoconfig-redrive/`. The migration validation covered seven participants. A controller was
judged by its revert rate, meaning the share of adjustments subsequently undone, on the reasoning
that a controller which raises and then lowers the same knob is responding to noise rather than to
drift.

## Results

Derivation works. On the migration cohort three participants were rescued from caps that were
clipping them and one was tightened protectively, and it shipped with five amendments after the
backtest exposed problems with historical factory defaults, cumulative clamping, resolved values, a
minimum sample size and a raise guard keyed to time below range.

All four online controllers failed, in both directions and for both kinds of knob. Raising the
committed cap online reverted 43 per cent of the time across a sweep from 33 to 50 per cent, and
produced about four raises in six weeks. Raising the confirmed cap online almost never bound, with
one to five raises in six weeks and all the reverts coming from a single participant. An aggression
slider raised on highs reverted 45 per cent of the time and is mis-targeted, since the attribution
study shows highs are a sizing and timing problem. A hypoglycaemia-caution slider raised on lows had
a good-to-wrong ratio of 0.74, flat, and ratchets to its maximum.

The static per-user equivalent of the last of those is well targeted. Removed insulin sitting before
a low runs at 28 to 32 per cent for hypoglycaemia-prone participants against 1 to 6 per cent for
well-controlled ones, so a fixed value keyed to the participant's time below range does the work the
slider was attempting.

## Discussion

The four failures converge on the same policy, which is the reason to record them together. Never
raise aggression automatically, and key hypoglycaemia caution to measured time below range. That
policy was derived once from the migration work and then re-derived four times by controllers that
were trying to find something better and did not.

The mechanism behind the failures is worth stating. A knob adjusted against outcomes is being fitted
to data the knob itself generated, on a sample of a few events per week, against outcomes that are
dominated by meals and activity rather than by the knob. The revert rates are the signature: a
controller responding to signal moves and stays, and one responding to noise moves and comes back.

This is also the clearest instance of the programme's separation between what is learned and what
ships. The derivation is statistical, it uses robust order statistics over a person's history, and it
runs offline on a schedule. What the dose path sees is a number. Nothing in the loop learns, and
these four experiments are the empirical argument for that architecture rather than merely a
philosophical preference.

The periodic re-derivation that followed is deliberately conservative. It tracks the movement of each
knob's underlying driver rather than its absolute value, requires a move to exceed that knob's own
measured noise band, and holds any raise to a dose cap behind the same time-below-range guard. In
practice the ratchet binds in about one window in thirty seven, which is what makes re-derivation
safe to run automatically.
