package app.aaps.plugins.aps.openAPSBoostV5

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * V5 Phase 1.a — meal_signal_score.
 *
 * Continuous 0-1 weighted combination of 6 signals indicating likelihood of an active meal.
 * Drives the MealHypothesis state machine's IDLE→OBSERVING and OBSERVING→CONFIRMED transitions.
 *
 * Replaces V4's binary G3 pre-UAM hold + meal-model-release + fast-carb-rebound triad with
 * one continuous score. The "binary cliff" failure modes (UAM_BOOST eligibility just-misses
 * — see 2026-05-05 incident) are eliminated by construction.
 *
 * Weights are HARDCODED constants calibrated by backtest #3 on the 19-user oref cohort
 * (`boost_v5_constants_calibration.md`). The user-facing settings surface contains NO
 * score-weight knobs — per V5's minimal-settings tenet, users have no basis to choose them.
 */

// Weight constants. Sum = 0.92 (< 1.0 acceptable; score is clipped to [0, 1] regardless).
// Calibrated 2026-05-06 per boost_v5_constants_calibration.md sweep results.
internal const val SCORE_WEIGHT_DELTA = 0.30
internal const val SCORE_WEIGHT_DELTA_ACCL = 0.16          // calibrated: 0.20 → 0.16 (-1.4pp false_conf)
internal const val SCORE_WEIGHT_ML_MEAL_LIKELY = 0.20
internal const val SCORE_WEIGHT_NOT_RECENTLY_LOW = 0.12    // calibrated: 0.15 → 0.12 (-1.3pp false_conf)
internal const val SCORE_WEIGHT_MEAL_TIME_OF_DAY = 0.10
internal const val SCORE_WEIGHT_NOT_EXERCISING = 0.04      // calibrated: 0.05 → 0.04

internal const val DELTA_NORMALIZE_HI_MGDL = 20.0          // delta saturates at 20 mg/dL/5min
internal const val DELTA_ACCL_NORMALIZE_HI_PCT = 30.0      // accl saturates at 30%

/**
 * Number of consecutive null-mlMealLikely cycles after which the score formula falls back
 * to a 5-weight version (mlMealLikely weight dropped, others scaled by 1/(1-W)).
 * Addresses the V3ML lazy-load bug noted in `boost_v3ml_production_validation.md`.
 */
internal const val ML_MEAL_RENORMALIZE_AFTER_CYCLES = 3

/** Rescale factor when the mlMealLikely weight is dropped after a long null streak. */
internal const val ML_MEAL_RENORMALIZE_FACTOR = 1.0 / (1.0 - SCORE_WEIGHT_ML_MEAL_LIKELY)

/** Per-component values that went into the final score. Emitted to NS for observability. */
data class ScoreComponents(
    val deltaTerm: Double,
    val deltaAcclTerm: Double,
    val mlMealLikelyTerm: Double,
    val notRecentlyLowTerm: Double,
    val mealTimeOfDayTerm: Double,
    val notExercisingTerm: Double,
)

data class ScoreResult(
    val score: Double,
    val components: ScoreComponents,
    val mlWeightsRenormalized: Boolean,
)

/**
 * Compute meal_signal_score for one cycle.
 *
 * @param delta BG delta over the last 5 minutes, mg/dL/5min.
 * @param deltaAccl acceleration of delta, percent (`(delta - shortAvgDelta) / max(|shortAvgDelta|, 2.0) × 100`).
 * @param mlMealLikely ML meal-likelihood model output ∈ [0, 1], or null if model not yet loaded.
 * @param recentLowBg minimum BG in the last 60 min, mg/dL.
 * @param hour hour of day, 0-23.
 * @param exerciseActive true if any exercise mode currently engaged in the activity classifier.
 * @param mlMealLikelyNullStreak count of consecutive prior cycles where mlMealLikely was null.
 *        Caller maintains this counter — the score function only reads it.
 */
fun mealSignalScore(
    delta: Double,
    deltaAccl: Double,
    mlMealLikely: Double?,
    recentLowBg: Double,
    hour: Int,
    exerciseActive: Boolean,
    mlMealLikelyNullStreak: Int = 0,
): ScoreResult {
    val deltaTerm = clipNormalize(delta, 0.0, DELTA_NORMALIZE_HI_MGDL)
    val deltaAcclTerm = clipNormalize(deltaAccl, 0.0, DELTA_ACCL_NORMALIZE_HI_PCT)
    val notRecentlyLowTerm = notRecentlyLowPenalty(recentLowBg)
    val mealTimeOfDayTerm = mealTimeOfDayBump(hour)
    val notExercisingTerm = if (exerciseActive) 0.0 else 1.0

    val renormalize = mlMealLikely == null && mlMealLikelyNullStreak >= ML_MEAL_RENORMALIZE_AFTER_CYCLES
    val mlMealLikelyTerm = mlMealLikely ?: 0.0

    val rawScore = if (renormalize) {
        // Drop ml_meal_likely weight; rescale the remaining 5 to compensate so that the score
        // ceiling stays the same as when ML is available. Without this, a multi-cycle ML outage
        // would silently lower the score and freeze V5 in IDLE.
        ML_MEAL_RENORMALIZE_FACTOR * (
            SCORE_WEIGHT_DELTA * deltaTerm +
            SCORE_WEIGHT_DELTA_ACCL * deltaAcclTerm +
            SCORE_WEIGHT_NOT_RECENTLY_LOW * notRecentlyLowTerm +
            SCORE_WEIGHT_MEAL_TIME_OF_DAY * mealTimeOfDayTerm +
            SCORE_WEIGHT_NOT_EXERCISING * notExercisingTerm
        )
    } else {
        SCORE_WEIGHT_DELTA * deltaTerm +
        SCORE_WEIGHT_DELTA_ACCL * deltaAcclTerm +
        SCORE_WEIGHT_ML_MEAL_LIKELY * mlMealLikelyTerm +
        SCORE_WEIGHT_NOT_RECENTLY_LOW * notRecentlyLowTerm +
        SCORE_WEIGHT_MEAL_TIME_OF_DAY * mealTimeOfDayTerm +
        SCORE_WEIGHT_NOT_EXERCISING * notExercisingTerm
    }

    val score = max(0.0, min(1.0, rawScore))

    return ScoreResult(
        score = score,
        components = ScoreComponents(
            deltaTerm = deltaTerm,
            deltaAcclTerm = deltaAcclTerm,
            mlMealLikelyTerm = mlMealLikelyTerm,
            notRecentlyLowTerm = notRecentlyLowTerm,
            mealTimeOfDayTerm = mealTimeOfDayTerm,
            notExercisingTerm = notExercisingTerm,
        ),
        mlWeightsRenormalized = renormalize,
    )
}

private fun clipNormalize(value: Double, lo: Double, hi: Double): Double {
    if (hi <= lo) return 0.0
    return max(0.0, min(1.0, (value - lo) / (hi - lo)))
}

/**
 * Continuous penalty for recent-low BG. Returns 1.0 if recentLowBg ≥ 100 mg/dL, 0.0 at ≤ 70,
 * linear between.
 *
 * Replaces V4's binary G3-hold floor (`recentLowBG ≥ 70` as a binary gate). The continuous
 * version eliminates the cliff at 70 that caused unhelpful tier transitions when BG hovered
 * around 70.
 */
private fun notRecentlyLowPenalty(recentLowBg: Double): Double =
    clipNormalize(recentLowBg, 70.0, 100.0)

/**
 * Smooth peaks at typical meal hours (08:00, 13:00, 19:00). Gaussian-shaped with width 2h.
 *
 * NOTE: This is a meal-LIKELIHOOD signal — it raises the prior that a rise during typical
 * meal hours is a meal. It is NOT a dose amplifier. V5 has no time-of-day dose amplifier;
 * dawn coverage is owned by the AAPS profile (hour-of-day basal rates / hour-of-day ISF).
 */
private fun mealTimeOfDayBump(hour: Int): Double {
    val centres = intArrayOf(8, 13, 19)
    val width = 2.0
    var maxBump = 0.0
    for (centre in centres) {
        val diff = (hour - centre).toDouble()
        val bump = exp(-(diff * diff) / (2.0 * width * width))
        if (bump > maxBump) maxBump = bump
    }
    return maxBump
}
