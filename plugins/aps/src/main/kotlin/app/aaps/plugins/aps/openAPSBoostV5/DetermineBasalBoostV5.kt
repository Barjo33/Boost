package app.aaps.plugins.aps.openAPSBoostV5

import app.aaps.core.interfaces.logging.AAPSLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Boost V5 algorithm core — Observe-Confirm-Commit pipeline orchestrator.
 *
 * Status: PRE-ALPHA. Phase 1.a (meal_signal_score), 1.b (state machine), 1.c (AggressionBudget),
 * 2 (action multiplier), and 3 (safety gates) are all implemented in their respective files.
 * The orchestrator below stitches them into a single `decide()` entry point. The plugin's
 * `invoke()` wires inputs from oref / Boost services into [V5Inputs] and calls [decide];
 * that wiring lands in a follow-up task (#9: RT serialization), and feature-gating happens
 * via the plugin's `isEnabled()` returning false until shadow mode is approved.
 *
 * Architecture (mirrors `boost_v5_redesign_proposal.md` exactly):
 *
 *   Phase 1 — state estimation (no commitment):
 *     - mealSignalScore          (MealSignalScore.kt)
 *     - step + resetIfNeeded     (MealHypothesis.kt)
 *     - aggressionBudget         (AggressionBudget.kt)
 *
 *   Phase 2 — single decision rule:
 *     - mealActionMultiplier(state) × budget   (MealActionMultiplier.kt)
 *
 *   Phase 3 — ordered safety gates:
 *     - applyPhase3              (SafetyGates.kt)
 *
 * `baseInsulinReq` MUST come from Boost-flavoured oref calc (DynISF + 7D TDD with W8H pull-down +
 * TDD-anchored EMA sensitivity + autosens + hour-of-day ISF + TempTargets — all unchanged).
 * V5 contains zero sensitivity logic of its own. See `MIGRATION.md` and the AggressionBudget
 * KDoc for the full inheritance contract.
 */

/** All inputs V5 needs for one cycle. Caller assembles from oref + Boost services. */
data class V5Inputs(
    // Glucose status
    val delta: Double,
    val shortAvgDelta: Double,
    val deltaAccl: Double,
    val bg: Double,
    val eventualBg: Double,
    val targetBg: Double,
    val maxDelta: Double,
    val minGuardBg: Double,
    val minGuardThreshold: Double,
    /** Last ≥3 deltas (oldest → newest, including current). For [deltaDeclining]. */
    val deltaHistory: List<Double>,

    // IOB / dose context
    val iob: Double,
    val maxIob: Double,
    /** Boost-flavoured oref insulinReq for this cycle. NOT vanilla oref — see KDoc. */
    val baseInsulinReq: Double,
    val roundSmbTo: Double,
    val enableSmbPreChecks: Boolean,

    // ML model outputs
    val mlHypoRisk: Double?,
    val mlMealLikely: Double?,
    /** ML model invocation: re-run hypo risk at projected IOB. Null disables postActionRiskCheck. */
    val riskAtProjectedIob: ((projectedIob: Double) -> Double)? = null,

    // Cycle context
    val recentLowBg: Double,
    /** Cumulative BG rise over the last ~30 min, mg/dL. Derived from `shortAvgDelta * 6`. Fix 4 (2026-05-22). */
    val cumulativeRise30min: Double,
    val hour: Int,
    val exerciseActive: Boolean,
    val inPostExerciseWindow: Boolean,
    val sensorQualityOk: Boolean = true,

    // Reset triggers
    val profileSwitched: Boolean = false,
    val pumpDisconnected: Boolean = false,
    val loopSuspended: Boolean = false,
    val timeJumpMinutes: Double = 0.0,

    // User-facing knobs
    val aggressionUserKnob: Double = 1.0,
    val hypoCautionUserKnob: Double = 1.0,
)

/** Persisted V5 state read from RT at cycle start, written back at cycle end. */
data class V5PersistedState(
    val mealHypothesis: MealHypothesisState = MealHypothesisState(),
    val mlMealLikelyNullStreak: Int = 0,
)

/** Full per-cycle V5 output. Every field is reconstructable into the ~6 NS RT fields. */
data class V5Decision(
    val finalDose: Double,
    val score: Double,
    val scoreComponents: ScoreComponents,
    val mlWeightsRenormalized: Boolean,
    val mealHypothesis: MealHypothesis,
    val mealHypothesisAge: Int,
    val stateReset: Boolean,
    val aggressionBudget: AggressionBudgetResult,
    val actionMultiplier: Double,
    val insulinToDeliver: Double,
    val phase3: Phase3Result,
    val newPersistedState: V5PersistedState,
)

@Singleton
class DetermineBasalBoostV5 @Inject constructor(
    @Suppress("unused") private val aapsLogger: AAPSLogger,
) {
    /** Run one full V5 cycle. Pure function over inputs + prior state. */
    fun decide(inputs: V5Inputs, persisted: V5PersistedState): V5Decision {
        // Reset state machine if any reset condition fired (reboot equivalents)
        val (resetState, didReset) = resetIfNeeded(
            current = persisted.mealHypothesis,
            profileSwitched = inputs.profileSwitched,
            pumpDisconnected = inputs.pumpDisconnected,
            loopSuspended = inputs.loopSuspended,
            timeJumpMinutes = inputs.timeJumpMinutes,
        )

        // Phase 1.a — meal_signal_score
        val nextNullStreak =
            if (inputs.mlMealLikely == null) persisted.mlMealLikelyNullStreak + 1 else 0
        val scoreResult = mealSignalScore(
            delta = inputs.delta,
            deltaAccl = inputs.deltaAccl,
            mlMealLikely = inputs.mlMealLikely,
            recentLowBg = inputs.recentLowBg,
            hour = inputs.hour,
            exerciseActive = inputs.exerciseActive,
            cumulativeRise30min = inputs.cumulativeRise30min,
            mlMealLikelyNullStreak = nextNullStreak,
        )

        // Phase 1.b — state machine step
        val newHypothesisState = step(
            current = resetState,
            score = scoreResult.score,
            eventualBg = inputs.eventualBg,
            targetBg = inputs.targetBg,
            delta = inputs.delta,
            deltaAccl = inputs.deltaAccl,
            deltaDeclining = deltaDeclining(inputs.deltaHistory, windowCycles = 2),
        )

        // Phase 1.c — AggressionBudget
        val budget = aggressionBudget(
            baseInsulinReq = inputs.baseInsulinReq,
            mlHypoRisk = inputs.mlHypoRisk,
            inPostExerciseWindow = inputs.inPostExerciseWindow,
            hypoCautionUserKnob = inputs.hypoCautionUserKnob,
        )

        // Phase 2 — single decision rule
        val actionMult = mealActionMultiplier(newHypothesisState.state, inputs.aggressionUserKnob)
        val insulinToDeliver = budget.budget * actionMult

        // Phase 3 — ordered safety gates
        val phase3 = applyPhase3(Phase3Inputs(
            insulinToDeliver = insulinToDeliver,
            enableSmbPreChecks = inputs.enableSmbPreChecks,
            minGuardBg = inputs.minGuardBg,
            minGuardThreshold = inputs.minGuardThreshold,
            maxDelta = inputs.maxDelta,
            bg = inputs.bg,
            iob = inputs.iob,
            maxIob = inputs.maxIob,
            deltaAccl = inputs.deltaAccl,
            baseInsulinReq = inputs.baseInsulinReq,
            roundSmbTo = inputs.roundSmbTo,
            sensorQualityOk = inputs.sensorQualityOk,
            riskAtProjectedIob = inputs.riskAtProjectedIob,
            mlHypoRisk = inputs.mlHypoRisk,
        ))

        return V5Decision(
            finalDose = phase3.finalDose,
            score = scoreResult.score,
            scoreComponents = scoreResult.components,
            mlWeightsRenormalized = scoreResult.mlWeightsRenormalized,
            mealHypothesis = newHypothesisState.state,
            mealHypothesisAge = newHypothesisState.ageCycles,
            stateReset = didReset,
            aggressionBudget = budget,
            actionMultiplier = actionMult,
            insulinToDeliver = insulinToDeliver,
            phase3 = phase3,
            newPersistedState = V5PersistedState(
                mealHypothesis = newHypothesisState,
                mlMealLikelyNullStreak = nextNullStreak,
            ),
        )
    }
}
