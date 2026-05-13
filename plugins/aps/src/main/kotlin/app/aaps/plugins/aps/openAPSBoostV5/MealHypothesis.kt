package app.aaps.plugins.aps.openAPSBoostV5

/**
 * V5 Phase 1.b — MealHypothesis state machine.
 *
 * Persisted across cycles via two RT fields (`mealHypothesis: String?`, `mealHypothesisAge: Int?`).
 * V4 had no equivalent — meal recognition was implicit in tier-ladder selection. V5 makes the
 * hypothesis a first-class state the algorithm reasons about, then commits doses against.
 *
 * Five states: IDLE → OBSERVING → CONFIRMED → COMMITTED → RECOVERING → IDLE.
 *
 * Transition thresholds are HARDCODED and calibrated by backtest #3
 * (`boost_v5_constants_calibration.md`). They are NOT user-facing knobs.
 *
 * State persistence is non-trivial (state from prior cycles influences this cycle's decision),
 * so all reset paths are explicit: see [resetIfNeeded].
 */

enum class MealHypothesis { IDLE, OBSERVING, CONFIRMED, COMMITTED, RECOVERING }

/** Persisted state. The plugin reads this from RT each cycle and writes it back after [step]. */
data class MealHypothesisState(
    val state: MealHypothesis = MealHypothesis.IDLE,
    val ageCycles: Int = 0,
)

// Calibrated transition thresholds (HARDCODED). Per boost_v5_constants_calibration.md.
internal const val ENTER_OBSERVING_SCORE = 0.44                 // calibrated: 0.40 → 0.44
internal const val CONFIRM_SCORE = 0.66                         // calibrated: 0.60 → 0.66 (most impactful: -3.6pp false_conf)
internal const val CONFIRM_EVENTUAL_BG_OFFSET_MGDL = 50.0       // eventualBG > target + 50 to confirm
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
    val (state, age) = current

    return when (state) {
        MealHypothesis.IDLE ->
            if (score >= ENTER_OBSERVING_SCORE) MealHypothesisState(MealHypothesis.OBSERVING, 0)
            else MealHypothesisState(state, age + 1)

        MealHypothesis.OBSERVING -> {
            val confirmEligible = age >= CONFIRM_MIN_OBSERVING_AGE &&
                score >= CONFIRM_SCORE &&
                eventualBg > targetBg + CONFIRM_EVENTUAL_BG_OFFSET_MGDL
            when {
                confirmEligible -> MealHypothesisState(MealHypothesis.CONFIRMED, 0)
                score < FALL_BACK_TO_IDLE_SCORE && age >= FALL_BACK_TO_IDLE_AGE ->
                    MealHypothesisState(MealHypothesis.IDLE, 0)
                else -> MealHypothesisState(state, age + 1)
            }
        }

        MealHypothesis.CONFIRMED ->
            if (age >= CONFIRMED_TO_COMMITTED_AGE) MealHypothesisState(MealHypothesis.COMMITTED, 0)
            else MealHypothesisState(state, age + 1)

        MealHypothesis.COMMITTED -> {
            // BOTH conditions required to back off — prevents flicker on transient deceleration
            // mid-rise (e.g. CGM noise). V4's tier ladder had this flicker problem.
            val backOff = deltaAccl < RECOVERING_DECEL_THRESHOLD && deltaDeclining
            if (backOff) MealHypothesisState(MealHypothesis.RECOVERING, 0)
            else MealHypothesisState(state, age + 1)
        }

        MealHypothesis.RECOVERING ->
            // EITHER condition exits to IDLE (more permissive than entry — easier to leave RECOVERING)
            if (delta < 0 || score < RECOVERING_TO_IDLE_SCORE) MealHypothesisState(MealHypothesis.IDLE, 0)
            else MealHypothesisState(state, age + 1)
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
        return Pair(MealHypothesisState(MealHypothesis.IDLE, 0), true)
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
