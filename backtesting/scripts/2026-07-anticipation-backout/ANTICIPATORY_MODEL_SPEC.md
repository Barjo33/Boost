# Boost — the anticipatory layer: full specification (shadow-first)

*2026-07-20. The whole "learn the person, act ahead of their day" architecture. One learned per-person
EVENT-anticipator feeds a fixed, safe policy; every insulin-ADDING action is routed through the back-out
controller ([[BACKOUT_CONTROLLER_SPEC]]) so it is retractable; every insulin-REDUCING action is inherently
safe. Grounded in this session's measured ceilings, not aspiration. Nothing here doses until shadow +
two-test bar.*

## 0. The principle
A static loop is forever reactive (insulin always late) and forever average. This layer makes it
anticipatory and individual: it predicts the person's upcoming events and pre-positions the dose — but
because prediction is imperfect (meals only ~1-in-3), **every add-insulin action is retractable and
unwound if the event fails to confirm** (back-out controller). Anticipation needn't be accurate, only
retractable; the reducing-direction events are safe regardless. Learning changes PREDICTIONS (identified),
never a safety floor.

## 1. The event taxonomy (the centrepiece)
Each event: measured predictability (this session / prior probes), dose DIRECTION, action, and how safety
is enforced. Direction is everything — reducing is safe to act on; adding must be retractable + gated.

| Event | Predictability (source) | Direction | Anticipatory action | Safety enforcement |
|---|---|---|---|---|
| **Activity / exercise onset** | AUC **0.85** (habit prior, [[anticipation-probes]]) | REDUCE | lower temp / raise target ahead of the walk | inherently safe; restore if no walk |
| **Post-exercise delayed hypo** (recovery tail) | strong, universal (+2–3h, [[anticipation-probes]]) | REDUCE | pre-emptive zero-temp / target-up in the tail window | inherently safe; the single highest-value reducer |
| **Meal onset** | AUC **~0.6**, meal-time spread 0.84 (E07) | ADD | tiny retractable temp-basal pre-position | back-out; per-user regular-eater gate ONLY |
| **Dawn phenomenon** | frequent (~82%) but timing-loose ([[anticipation-probes]]) | ADD | gentle early-AM temp-basal, wide window | back-out; time-locked so confirm is easy |
| **Sleep onset / wake** | detector + learned window (already shipped) | posture | engage/lift night-mode; feeds all the above | real-time detection + one-sided clamp (07-20 fix) |
| **Sensitivity regime** (illness / cycle / alcohol) | slow, per-user; TDD-trend + Twin drift | scale | nudge the sensitivity prior (not a discrete dose) | offline/periodic; damper-only, never a floor |

**Design consequence of the ceilings:** the layer's confident wins are the REDUCERS (activity, recovery
tail) — high predictability AND safe direction. The ADDERS (meal, dawn) are low/loose and risky, so they
are small, retractable, and gated. We lead with the reducers.

## 2. The model (learned, per-person, pre-trained inference — rule #2 compliant)
- **Retargeted from nightscout-ml**: its temporal LSTM + person-pattern features, but predicting the EVENT,
  not the dose. Output = a calibrated probability per event over the next 15/30/45/60 min.
- **Features (habit + context, all past-only, no leakage):** time-of-day (cyclical), day-of-week/weekend,
  minutes-since-last-meal, minutes-since-wake, recent steps (5/15/30/60), HR + HRR, recent TDD at multiple
  timescales, sleep state, day-type. Glucose/IOB enter only as CONTEXT, never as the trigger (else it's
  reactive, not anticipatory).
- **Per-person**: starts from a population prior, moves toward the individual as their days accrue
  (periodic offline retrain — "robust statistics computed periodically" per rule #2). Upgrades Boost's
  existing `mlMealLikely`; adds `mlActivityLikely`, `mlRecoveryLowLikely`, `mlDawnLikely`.
- **Calibration is mandatory** (a probability that means what it says): monitor predicted-vs-observed event
  rates per event/user; a mis-calibrated head is disabled until recalibrated. The action scales with the
  calibrated probability, so calibration IS safety here.

## 3. The action policy (fixed, not learned): confidence × reversibility → posture
For each event with probability p and per-user calibrated action size A:
```
posture_delta = A * clip(p, 0, 1) * direction_gain
```
- **Reducers** (activity, recovery-tail): act at modest p (e.g. p>0.4) — worst case is mildly high. The
  action is a temp reduction / target raise, auto-restored when p decays or the window passes.
- **Adders** (meal, dawn): act only at higher p (e.g. p>0.6) AND only for gated users; deliver via the
  back-out controller (retractable temp-basal, confirm-or-unwind). Size scales with p so a marginal call
  barely doses.
- Multiple events compose by SUMMING postures, then clamping — but a reducer always dominates an adder
  when both fire (never add insulin into an anticipated low).

## 4. The safety spine (unifying, non-negotiable)
1. **Every add-insulin anticipation → the back-out controller** (temp-basal not SMB; Twin-Ra/BG confirm
   within deadline; zero-temp+protect on non-confirm; low-trip). Validated confirm AUC 0.83–0.87 (E08).
2. **Every reduce-insulin anticipation → auto-restore** when the event doesn't materialise (symmetric,
   trivially safe — restoring normal basal can't cause a low).
3. **Hard floors underneath everything** (TBR kill-switch, minGuardBG, post-rescue) — the anticipation
   layer can never override them; they are the backstop.
4. **Calibrated-confidence gating**: no action above what p supports; a de-calibrated head is inert.
5. **Reducer-dominates-adder** compositional rule.

## 5. Per-user auto-config gating
- **Activity + recovery-tail reducers: ON for most** (safe direction, high predictability). Gated OFF only
  where a user shows no activity signal.
- **Meal pre-position: ON only for meal-time-REGULAR users** (E07 F-types — low meal-time entropy) AND
  TBR-clean (strict cut). Everyone else: meal anticipation stays OFF (reactive dosing governs).
- **Dawn: ON where a consistent dawn rise is detected** per-user.
- All derived by `BoostV5AutoConfig` from the user's own history; insulin-adding heads carry the stricter
  gate ([[feedback-autoconfig-managed-switches]]).

## 6. Shadow-first + the two-test bar
Ship as SHADOW: each event head logs its probability, the would-be posture, and (via the back-out shadow)
the confirm/back-out outcome — delivering nothing. Bank per event/user: fire rate, calibration (predicted
vs observed), confirm-vs-back-out split, and on reducers whether the anticipated low actually came. Only
heads that clear absolute TBR gates + relative pricing + a within-user trial go live, one event at a time,
reducers first.

## 7. Honest ceilings (what the data already says — don't oversell)
- **Reducers are the real prize**: activity (0.85) + recovery-tail (universal) are both predictable AND
  safe → biggest, safest wins. Build these first.
- **Meal anticipation is weak** (0.6, variable eaters) → small, gated, retractable; expect a modest gain on
  the regular-eater minority, near-zero cost elsewhere (thanks to back-out).
- **Dawn** is frequent but loosely timed → wide-window gentle add, easy to confirm (time-locked).
- The layer's value is asymmetric: it mostly REMOVES insulin ahead of anticipated lows (the session's
  recurring harm) and only cautiously ADDS ahead of well-predicted, confirmable rises.

## 8. Build order
1. Back-out controller shadow ([[BACKOUT_CONTROLLER_SPEC]]) — the foundation, reuses the running Twin.
2. Event-anticipator model (retarget nightscout-ml) → shadow the four probability heads + calibration.
3. Wire reducers (activity, recovery-tail) through auto-restore; meal/dawn adders through back-out.
4. Per-user auto-config gates. Then two-test bar, reducers first.
```
Config keys (future): ApsBoostAnticipation{Backout,Activity,RecoveryLow,MealPreposition,Dawn}
```
