package app.aaps.plugins.aps.openAPSBoostV5

import app.aaps.core.interfaces.logging.AAPSLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Boost V5 algorithm core — Observe-Confirm-Commit pipeline.
 *
 * Status: PRE-ALPHA scaffold. No phase is implemented yet; methods throw NotImplementedError
 * until their dedicated implementation tasks land. The structure below mirrors the proposal
 * exactly so future contributors can fill in one phase at a time without restructuring.
 *
 * Phase 1 — state estimation (no commitment):
 *   - mealSignalScore: continuous 0–1 weighted combination of 6 signals
 *   - mealHypothesisStep: state machine transition (IDLE / OBSERVING / CONFIRMED / COMMITTED / RECOVERING)
 *   - aggressionBudget: baseInsulinReq × mlHypoRiskScale × postExerciseRecoveryModifier, floored at 0.30 × baseInsulinReq
 *
 * Phase 2 — decision (single rule):
 *   - dose = aggression_budget × meal_action_multiplier(mealHypothesis)
 *
 * Phase 3 — safety gates (ordered, none can increase dose):
 *   - HARD: enableSmbPreChecks, minGuardBG, maxDelta, maxIOB clamp
 *   - SOFT (ordered): iobHeadroomBrake → postActionRiskCheck → decelerationBrake → sensorQualityCheck
 *   - FINAL: round, dynamicSpikeCap, max(0)
 *
 * `baseInsulinReq` is Boost-flavoured oref (DynISF + 7D TDD + EMA sensitivity, all inherited).
 * V5 contains no sensitivity logic of its own. See `boost_v5_redesign_proposal.md` for the
 * full architecture and the V4 retention audit.
 */
@Singleton
class DetermineBasalBoostV5 @Inject constructor(
    @Suppress("unused") private val aapsLogger: AAPSLogger
)
