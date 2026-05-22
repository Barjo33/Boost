package app.aaps.plugins.aps.openAPSBoostV5

import kotlin.math.max

/**
 * V5 Phase 1.b — MealHypothesis state machine.
 *
 * Persisted across cycles via RT fields (`mealHypothesis: String?`, `mealHypothesisAge: Int?`,
 * and from 2026-05-15 `maxScoreInObserving: Double?`). V4 had no equivalent — meal recognition
 * was implicit in tier-ladder selection. V5 makes the hypothesis a first-class state the
 * algorithm reasons about, then commits doses against.
 *
 * Five states: IDLE → OBSERVING → CONFIRMED → COMMITTED → RECOVERING → IDLE.
 *
 * Transition thresholds are HARDCODED and calibrated against shadow data
 * (`boost_v5_constants_calibration.md` and the 2026-05-15 V5-fixes review). They are NOT
 * user-facing knobs.
 *
 * State persistence is non-trivial (state from prior cycles influences this cycle's decision),
 * so all reset paths are explicit: see [resetIfNeeded].
 *
 * ## 2026-05-15 fixes applied
 *
 * Shadow data review on 2026-05-15 (`boost_2026-05-14_evening_excursion.md`) showed CONFIRMED
 * fired only 4 times across 8 days because the OBSERVING → CONFIRMED predicate required ALL
 * three conditions to hold ON THE SAME CYCLE — but score is volatile cycle-to-cycle and peaked
 * 1–2 cycles before the age gate (CONFIRM_MIN_OBSERVING_AGE=2) opened. By the time age=2 the
 * score had already retreated.
 *
 * Fix: track the max score observed during the current OBSERVING run and use it for the
 * eligibility check, not the instantaneous score. This preserves the age hysteresis (which
 * matters for noise rejection) while allowing brief score peaks to drive the transition.
 *
 * CONFIRM_SCORE also lowered from 0.66 → 0.55 per the recalibration plan (the original
 * calibration was against an idealised cohort; p95 of observed scores in production is 0.532).
 *
 * ## 2026-05-22 Fix 5 — eventualBG peak-tracking
 *
 * 7d validation backtest against the 2026-05-15 evening meal showed Fix 4 (sustainedRise score
 * component) alone wasn't enough: V5's score peak rose from 0.600 to 0.689 with Fix 4, well
 * above the 0.55 threshold, but CONFIRMED still didn't fire. The third predicate condition —
 * `eventualBg > targetBg + 50` — was checked snapshot-only on each cycle, and during the slow
 * meal window the eventualBG forecast peaked at +42 above target (just below the +50 gate)
 * while the high-score window kept retreating elsewhere.
 *
 * Same shape as the score wobble Fix 1 addressed: a value that crosses the bar briefly during
 * OBSERVING but isn't sitting above the bar on the exact cycle the predicate is evaluated.
 *
 * Fix: peak-track `(eventualBg - targetBg)` across the OBSERVING run and check the max against
 * the threshold, mirroring Fix 1's max-score logic. With peak-tracking, the threshold is also
 * lowered 50 → 30 because peak-over-window is naturally higher than snapshot — the original
 * calibration was for snapshot reads.
 */

enum class MealHypothesis { IDLE, OBSERVING, CONFIRMED, COMMITTED, RECOVERING }

/**
 * Persisted state. The plugin reads this from RT each cycle and writes it back after [step].
 *
 * @property maxScoreInObserving peak meal_signal_score observed during the current OBSERVING run.
 *   Reset to 0.0 whenever state transitions OUT of OBSERVING. Allows CONFIRMED to fire on
 *   accumulated evidence rather than instantaneous score — added 2026-05-15 to fix the
 *   transient-peak-misses-age-gate issue described in the class docstring.
 * @property maxEventualBgOffsetInObserving peak `(eventualBg - targetBg)` observed during the
 *   current OBSERVING run, mg/dL. Reset to 0.0 on state transitions out of OBSERVING. Fix 5
 *   (2026-05-22) — same shape as maxScoreInObserving, applied to the eventualBG offset that the
 *   CONFIRMED predicate also requires.
 */
data class MealHypothesisState(
    val state: MealHypothesis = MealHypothesis.IDLE,
    val ageCycles: Int = 0,
    val maxScoreInObserving: Double = 0.0,
    val maxEventualBgOffsetInObserving: Double = 0.0,
)

// Calibrated transition thresholds (HARDCODED).
// Per boost_v5_constants_calibration.md, with 2026-05-15 revisions noted inline.
internal const val ENTER_OBSERVING_SCORE = 0.44                 // calibrated: 0.40 → 0.44
internal const val CONFIRM_SCORE = 0.55                         // 2026-05-15: 0.66 → 0.55 (was at p99 of observed scores; lowered with peak-score tracking)
internal const val CONFIRM_EVENTUAL_BG_OFFSET_MGDL = 30.0       // 2026-05-22: 50.0 → 30.0 (Fix 5 — paired with peak-offset tracking)
internal const val CONFIRM_MIN_OBSERVING_AGE = 2                // hysteresis: must observe ≥2 cycles before confirming
internal const val FALL_BACK_TO_IDLE_SCORE = 0.36               // calibrated: 0.30 → 0.36
internal const val FALL_BACK_TO_IDLE_AGE = 2                    // hysteresis: ≥2 cycles below threshold to fall back
internal const val CONFIRMED_TO_COMMITTED_AGE = 1               // CONFIRMED is a single-cycle commit; then COMMITTED
internal const val RECOVERING_DECEL_THRESHOLD = -5.0            // delta_accl < -5 enters RECOVERING (with delta declining)
internal const val RECOVERING_TO_IDLE_SCORE = 0.18              // calibrated: 0.20 → 0.18

/** Time-jump threshold (minutes) for forcing IDLE on clock changes (e.g. timezone switch). */
internal const val TIME_JUMP_RESET_MINUTES = 30.0

/**
 * Single-step transition. Pure function; no side effects. Caller threads state across cycles.
 *
 * @param current state from the previous cycle.
 * @param score `meal_signal_score` from this cycle's Phase 1.a.
 * @param eventualBg oref's eventualBG projection, mg/dL.
 * @param targetBg current target BG, mg/dL.
 * @param delta this cycle's BG delta, mg/dL/5min.
 * @param deltaAccl this cycle's delta_accl, percent.
 * @param deltaDeclining whether delta has been monotonically declining over the last ≥2 cycles
 *   (computed by the caller from delta history). COMMITTED → RECOVERING requires both
 *   `delta_accl < -5` AND this declining-2-cycles condition.
 */
fun step(
    current: MealHypothesisState,
    score: Double,
    eventualBg: Double,
    targetBg: Double,
    delta: Double,
    deltaAccl: Double,
    deltaDeclining: Boolean,
): MealHypothesisState {
    val (state, age, maxScore, maxOffset) = current
    val currentOffset = eventualBg - targetBg

    return when (state) {
        MealHypothesis.IDLE ->
            if (score >= ENTER_OBSERVING_SCORE)
                // seed both peaks with the entry-cycle values so the OBSERVING run starts from
                // observed truth rather than 0
                MealHypothesisState(MealHypothesis.OBSERVING, 0, score, currentOffset)
            else MealHypothesisState(state, age + 1, 0.0, 0.0)

        MealHypothesis.OBSERVING -> {
            // 2026-05-15 Fix 1: track running max-score in this OBSERVING run, use it for the
            // CONFIRMED eligibility check (not the instantaneous score). Score is volatile;
            // peaks 1–2 cycles before the age gate opens. Tracking the max lets a brief
            // high-score cycle drive the transition once age conditions are met.
            //
            // 2026-05-22 Fix 5: same treatment for (eventualBg - targetBg). The eventualBG
            // forecast also moves cycle-to-cycle and can be high during the meal-rise window
            // but retreat by the time score + age conditions align. Peak-track it too.
            val newMaxScore = max(maxScore, score)
            val newMaxOffset = max(maxOffset, currentOffset)
            val confirmEligible = age >= CONFIRM_MIN_OBSERVING_AGE &&
                newMaxScore >= CONFIRM_SCORE &&
                newMaxOffset >= CONFIRM_EVENTUAL_BG_OFFSET_MGDL
            when {
                confirmEligible -> MealHypothesisState(MealHypothesis.CONFIRMED, 0, 0.0, 0.0)
                score < FALL_BACK_TO_IDLE_SCORE && age >= FALL_BACK_TO_IDLE_AGE ->
                    MealHypothesisState(MealHypothesis.IDLE, 0, 0.0, 0.0)
                else -> MealHypothesisState(state, age + 1, newMaxScore, newMaxOffset)
            }
        }

        MealHypothesis.CONFIRMED ->
            if (age >= CONFIRMED_TO_COMMITTED_AGE)
                MealHypothesisState(MealHypothesis.COMMITTED, 0, 0.0, 0.0)
            else MealHypothesisState(state, age + 1, 0.0, 0.0)

        MealHypothesis.COMMITTED -> {
            // BOTH conditions required to back off — prevents flicker on transient deceleration
            // mid-rise (e.g. CGM noise). V4's tier ladder had this flicker problem.
            val backOff = deltaAccl < RECOVERING_DECEL_THRESHOLD && deltaDeclining
            if (backOff) MealHypothesisState(MealHypothesis.RECOVERING, 0, 0.0, 0.0)
            else MealHypothesisState(state, age + 1, 0.0, 0.0)
        }

        MealHypothesis.RECOVERING ->
            // EITHER condition exits to IDLE (more permissive than entry — easier to leave RECOVERING)
            if (delta < 0 || score < RECOVERING_TO_IDLE_SCORE)
                MealHypothesisState(MealHypothesis.IDLE, 0, 0.0, 0.0)
            else MealHypothesisState(state, age + 1, 0.0, 0.0)
    }
}

/**
 * Force IDLE on conditions where prior state shouldn't carry over. All five paths are explicit
 * because silently inheriting a stale meal hypothesis could cause unsafe dosing on resume.
 *
 * Returns Pair(newState, didReset).
 *
 * @param current the (possibly-stale) state from RT or last cycle.
 * @param profileSwitched true if active profile changed since last cycle.
 * @param pumpDisconnected true if pump is currently disconnected.
 * @param loopSuspended true if user has suspended the loop.
 * @param timeJumpMinutes absolute minutes between expected and actual cycle time
 *   (e.g. 60 if device clock jumped due to timezone change).
 */
fun resetIfNeeded(
    current: MealHypothesisState,
    profileSwitched: Boolean = false,
    pumpDisconnected: Boolean = false,
    loopSuspended: Boolean = false,
    timeJumpMinutes: Double = 0.0,
): Pair<MealHypothesisState, Boolean> {
    if (profileSwitched || pumpDisconnected || loopSuspended || timeJumpMinutes > TIME_JUMP_RESET_MINUTES) {
        return Pair(MealHypothesisState(MealHypothesis.IDLE, 0, 0.0, 0.0), true)
    }
    return Pair(current, false)
}

/**
 * Helper: compute whether delta has been declining over the last `windowCycles` cycles.
 * Used as input to [step] for the COMMITTED → RECOVERING transition.
 *
 * @param deltaHistory ordered oldest → newest delta values, including the current cycle.
 *   For "declining over last 2 cycles" pass at least 3 deltas.
 */
fun deltaDeclining(deltaHistory: List<Double>, windowCycles: Int = 2): Boolean {
    if (deltaHistory.size < windowCycles + 1) return false
    val tail = deltaHistory.takeLast(windowCycles + 1)
    for (i in 0 until tail.size - 1) {
        if (tail[i] <= tail[i + 1]) return false
    }
    return true
}
