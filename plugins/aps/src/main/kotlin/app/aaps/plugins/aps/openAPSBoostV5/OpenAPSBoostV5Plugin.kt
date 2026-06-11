package app.aaps.plugins.aps.openAPSBoostV5

import android.content.Context
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.OapsProfileBoost
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.plugins.aps.OpenAPSFragment
import app.aaps.plugins.aps.R
import org.json.JSONObject
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

/**
 * Boost V5 — Observe-Confirm-Commit dosing pipeline.
 *
 * Status: PRE-ALPHA, shadow-mode only. Hidden from the plugin list (`showInList { false }`)
 * until the test plan's Layer 1–3 acceptance gates pass and the observability check is verified.
 * Do NOT enable for active dosing on a user pump.
 *
 * Architecture (see `boost_v5_redesign_proposal.md`):
 *   Phase 1 — state estimation (meal_signal_score → MealHypothesis state machine → AggressionBudget)
 *   Phase 2 — single decision rule (aggression_budget × meal_action_multiplier)
 *   Phase 3 — ordered safety gates (hard gates → soft gates ordered → final clamp)
 *
 * Design tenets:
 *   - Minimal user settings: ≤3 user-facing knobs; ~14–15 internal constants frozen at release.
 *   - Sensitivity inheritance: baseInsulinReq is Boost-flavoured oref (DynISF + 7D TDD with W8H
 *     pull-down + TDD-anchored EMA sensitivity + autosens + hour-of-day ISF + TempTargets).
 *     V5 contains NO sensitivity logic of its own.
 *   - State persistence: MealHypothesis persists across cycles. All reset paths explicit
 *     (reboot, pump disconnect, loop suspend, profile switch, time jump > 30 min).
 *
 * Reference documents in this directory:
 *   - `MIGRATION.md`  — V4.4.1 → V5 mechanism mapping (where did Tier 5 go?)
 *   - `V1_VS_V5.md`   — V1 → V5 side-by-side comparison (architecture, settings, safety,
 *                       brake stacking, observability). The authoritative answer to
 *                       "what does V5 do differently from the original Boost?"
 *
 * V4 retention audit results are recorded in the V5 redesign proposal and migration doc.
 */
@Singleton
open class OpenAPSBoostV5Plugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val config: Config,
    private val preferences: Preferences,
    private val determineBasalBoostV5: DetermineBasalBoostV5,
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.APS)
        .fragmentClass(OpenAPSFragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_generic_icon)
        .pluginName(R.string.openaps_boost_v5)
        .shortName(R.string.boost_v5_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .preferencesVisibleInSimpleMode(false)
        .showInList { false }
        .description(R.string.description_boost_v5),
    aapsLogger, rh
), APS, PluginConstraints {

    override var lastAPSRun: Long = 0
    override val algorithm = APSResult.Algorithm.BOOST
    override var lastAPSResult: APSResult? = null

    /** State persistence across cycles. Initialised lazily on first access. */
    private val stateStore: V5StateStore by lazy { V5StateStore(preferences) }

    override fun isEnabled(): Boolean = false

    override fun invoke(initiator: String, tempBasalFallback: Boolean) {
        // V5 plugin is hidden (showInList { false }) and not user-selectable as the active APS.
        // Shadow execution happens via [runShadow], which V4.4.1 (V3MLG3) calls at the end of
        // its own invoke() with V4.4.1's gathered inputs. invoke() here is a no-op for that
        // reason — see runShadow() for the actual shadow entry point.
        aapsLogger.debug(LTag.APS, "BoostV5 invoke: not selectable; shadow runs via V3MLG3 callback.")
    }

    /**
     * Sidecar shadow runner. Called by V4.4.1 (`OpenAPSBoostV3MLG3Plugin.invoke()`) with the
     * inputs and result V4.4.1 just produced. V5 sees exactly what V4.4.1 saw — no duplication
     * of input gathering, no risk of input drift between the two algorithms.
     *
     * V5 reads:
     *  - V4.4.1's RT for `eventualBG`, `insulinReq` (used as `baseInsulinReq`), `mlHypoRisk`,
     *    `mlMealLikely` — V4.4.1 already ran the ML predictions.
     *  - GlucoseStatus for `delta`, `shortAvgDelta`, `longAvgDelta`, `glucose`.
     *  - OapsProfileBoost for `target_bg`, `boost_maxIOB`, `recentLowBG`, `lgsThreshold`,
     *    and the `v5_*` activity fields V4.4.1 fills (exerciseActive, inPostExerciseWindow).
     *
     * V5 computes:
     *  - `delta_accl` from delta + shortAvgDelta with V3's denominator floor.
     *  - The 3-cycle deltaHistory from longAvgDelta / shortAvgDelta / delta.
     *  - Its own meal_signal_score, MealHypothesis transition, AggressionBudget, action
     *    multiplier, Phase 3 gates via [determineBasalBoostV5.decide].
     *
     * Output: V5 RT JSON logged via aapsLogger at INFO with prefix "BoostV5_RT:" — Tim greps
     * logs to compare V5 shadow decisions against V4.4.1's actual delivery.
     *
     * Safety: any exception is caught and logged; V5 never propagates an error to V4.4.1.
     */
    fun runShadow(
        rT: RT,
        glucoseStatus: GlucoseStatus,
        iobArray: Array<IobTotal>,
        oapsProfile: OapsProfileBoost,
        pumpBolusStep: Double,
        activeMode: Boolean = false,
        microBolusAllowed: Boolean = true,
        flatBGsDetected: Boolean = false,
    ): V5Decision? {
        return try {
            val priorState = stateStore.load()
            val inputs = buildInputs(rT, glucoseStatus, iobArray, oapsProfile, pumpBolusStep, activeMode, microBolusAllowed, flatBGsDetected)
            val decision = determineBasalBoostV5.decide(inputs, priorState)
            stateStore.save(decision.newPersistedState)

            // Mutate V4.4.1's rT to attach V5 fields. The same rT instance is referenced by
            // V4.4.1's DetermineBasalResult.result and gets serialised via RT.serialize() when
            // LoopPlugin uploads NS deviceStatus — V5's fields ride along automatically.
            // V5's runShadow runs BEFORE V4.4.1 fires EventAPSCalculationFinished so any
            // listener sees the populated rT.
            rT.boostV5_score = decision.score
            rT.boostV5_state = decision.mealHypothesis.name
            rT.boostV5_age = decision.mealHypothesisAge
            rT.boostV5_budget = decision.aggressionBudget.budget
            rT.boostV5_actionMult = decision.actionMultiplier
            rT.boostV5_finalDose = decision.finalDose
            rT.boostV5_gateReduction = formatGateReduction(decision)

            val rtJson = v5DecisionToRtJson(decision)
            aapsLogger.info(LTag.APS, "BoostV5_RT: ${rtJson} actual_smb=${rT.units ?: 0.0} actual_insulinReq=${rT.insulinReq ?: 0.0} activeMode=$activeMode")
            decision
        } catch (e: Throwable) {
            // Never let V5 break V4.4.1. Log and continue. Null → caller leaves V1's dose intact.
            aapsLogger.error(LTag.APS, "BoostV5 shadow error", e)
            null
        }
    }

    /**
     * Short-horizon minGuardBG for V5's hard safety gate (2026-05-15 fix).
     *
     * V4.4.x's `rT.minGuardBG` is `min()` taken over the full 4-hour prediction horizon. The
     * IOB-only forecast tail regularly dips to absurd lows (39 mg/dL is common) even when the
     * next 30 minutes is fine — reading this value caused V5's `HARD:min_guard_bg` to fire on
     * **50.4%** of cycles in the shadow window 2026-05-07 → 2026-05-15 (per
     * `boost_2026-05-14_evening_excursion.md` and the 5-fixes review).
     *
     * The hard gate is supposed to mean "imminent hypo, do not dose". 30 minutes is the
     * appropriate window — a basal cutoff issued now can plausibly prevent a hypo 30 min out;
     * the 4h-tail forecast is not actionable.
     *
     * Returns the min over the next 30 min (6 prediction points) of all available prediction
     * series, or null if no prediction array is available (caller falls back to V4.4.x's
     * `rT.minGuardBG`, then to current BG).
     */
    private fun shortHorizonMinGuard(rT: RT): Double? {
        val pred = rT.predBGs ?: return null
        val series = listOfNotNull(pred.IOB, pred.UAM, pred.ZT, pred.COB)
        if (series.isEmpty()) return null
        // Take min over the first 6 points (30 min at 5-min cycles) of every series, then
        // take the min across all series. Returns the worst-case 30-min-horizon prediction.
        val mins = series.mapNotNull { it.take(6).minOrNull()?.toDouble() }
        return mins.minOrNull()
    }

    /** Same compact summary used in the log line and in `v5DecisionToRtJson`'s gate string. */
    private fun formatGateReduction(decision: V5Decision): String {
        val parts = mutableListOf<String>()
        decision.phase3.reductions.iobHeadroomBrake.takeIf { it < 1.0 }?.let { parts.add("iobHeadroom:${"%.2f".format(java.util.Locale.US, it)}") }
        decision.phase3.reductions.postActionRiskCheck.takeIf { it < 1.0 }?.let { parts.add("postAction:${"%.2f".format(java.util.Locale.US, it)}") }
        decision.phase3.reductions.decelerationBrake.takeIf { it < 1.0 }?.let { parts.add("decel:${"%.2f".format(java.util.Locale.US, it)}") }
        decision.phase3.reductions.sensorQualityCheck.takeIf { it < 1.0 }?.let { parts.add("sensor:${"%.2f".format(java.util.Locale.US, it)}") }
        decision.phase3.reductions.hardGateFired?.let { parts.add("HARD:$it") }
        if (decision.phase3.reductions.maxIobClampApplied) parts.add("maxIOB")
        if (decision.phase3.reductions.dynamicSpikeCapped) parts.add("spike")
        return parts.joinToString(",").ifEmpty { "none" }
    }

    private fun buildInputs(
        rT: RT,
        gs: GlucoseStatus,
        iobArray: Array<IobTotal>,
        opb: OapsProfileBoost,
        pumpBolusStep: Double,
        activeMode: Boolean,
        microBolusAllowed: Boolean,
        flatBGsDetected: Boolean,
    ): V5Inputs {
        // delta_accl with V3's denominator floor — `max(|shortAvgDelta|, 2.0)` — carried over
        // verbatim from V3 input preprocessing.
        val deltaAccl = 100.0 * (gs.delta - gs.shortAvgDelta) / max(abs(gs.shortAvgDelta), 2.0)

        // 3-cycle delta history from glucose status. longAvgDelta is a longer-window average
        // so it's a reasonable proxy for "two cycles ago"; combined with shortAvgDelta and
        // current delta, deltaDeclining can reliably check the 2-cycle decline pattern.
        val deltaHistory = listOf(gs.longAvgDelta, gs.shortAvgDelta, gs.delta)

        // Fix 4 (2026-05-22): cumulative rise over ~30 min for slow-meal detection. shortAvgDelta
        // is per-5-min-cycle averaged over the 2.5–17.5 min lookback window (DeltaCalculator).
        // Multiplying by 6 projects 30 min of accumulated rise at the current cycle's rate.
        // Clamped non-negative — falling BG produces no sustained-rise signal.
        val cumulativeRise30min = max(0.0, gs.shortAvgDelta * 6.0)

        // baseInsulinReq directly from V4.4.1's computed value. V4.4.1 used the Boost-flavoured
        // formula `(min(minPredBG, eventualBG) - target_bg) / future_sens` with DynISF +
        // 7D-only TDD + EMA sensitivity all baked in. V5 trusts this number.
        val baseInsulinReq = (rT.insulinReq ?: 0.0).coerceAtLeast(0.0)

        val iob = iobArray.firstOrNull()?.iob ?: 0.0

        val hour = LocalTime.now(ZoneId.systemDefault()).hour

        // V0 SHADOW MODE: enableSmbPreChecks is permissive — V5 makes its own decision and the
        // operator compares against V4.4.1's actual delivery. Earlier code derived this from
        // `(units > 0) OR (insulinReq <= 0)`, but that returns false when V4.4.1 had a small
        // insulinReq that rounded to units=0 (e.g. insulinReq=0.01U with roundSMBTo=0.05). In
        // shadow we want V5's decision visible regardless. V5's other hard gates (minGuardBg
        // via rT.minGuardBG, maxIOB clamp, maxDelta) already cover safety. When V5 graduates
        // to alpha (active APS), this becomes a real V5-side enableSMB check.
        // ACTIVE-DOSING ALPHA (2026-06-11): gate on V1's real SMB permission (microBolusAllowed) so
        // V5 can only dose on cycles V1 itself permits an SMB — V1 is the outer safety envelope.
        // Shadow mode (activeMode=false) keeps the permissive value so shadow telemetry is unchanged.
        val enableSmbPreChecks = if (activeMode) microBolusAllowed else true

        return V5Inputs(
            delta = gs.delta,
            shortAvgDelta = gs.shortAvgDelta,
            deltaAccl = deltaAccl,
            bg = gs.glucose,
            eventualBg = rT.eventualBG ?: gs.glucose,
            targetBg = opb.target_bg,
            maxDelta = abs(gs.delta),
            // minGuardBg: V4.4.1's smart-selected predicted-low (COB/UAM/IOB-blended per the rules
            // at DetermineBasalBoostV3MLG3.kt:799-808). Reading rT.minGuardBG directly avoids the
            // bug from a previous attempt that did `min(predBGs.IOB+UAM+ZT)` over the full prediction
            // horizon — that picked up the IOB-only forecast tail (e.g. 39 mg/dL) and fired the V5
            // hard gate every cycle even when V4.4.1's own minGuardBG was 92 mg/dL (well above 80).
            minGuardBg = shortHorizonMinGuard(rT) ?: rT.minGuardBG ?: gs.glucose,
            minGuardThreshold = opb.lgsThreshold?.toDouble() ?: 80.0,
            deltaHistory = deltaHistory,
            iob = iob,
            maxIob = opb.boost_maxIOB,
            baseInsulinReq = baseInsulinReq,
            roundSmbTo = pumpBolusStep,
            enableSmbPreChecks = enableSmbPreChecks,
            mlHypoRisk = rT.mlHypoRisk,
            mlMealLikely = rT.mlMealLikely,
            riskAtProjectedIob = null,         // Phase 3 postActionRiskCheck disabled in V0; V4.4.1's
                                               // postSmbScale already runs against rT.units; V5 doesn't
                                               // re-run the model in shadow.
            recentLowBg = opb.recentLowBG,
            cumulativeRise30min = cumulativeRise30min,
            hour = hour,
            exerciseActive = opb.v5_exerciseActive,
            inPostExerciseWindow = opb.v5_inPostExerciseWindow,
            sensorQualityOk = if (activeMode) !flatBGsDetected else true,
            profileSwitched = false,           // deferred reset trigger (microBolusAllowed gates actual dosing)
            pumpDisconnected = false,
            loopSuspended = false,
            timeJumpMinutes = 0.0,
            aggressionUserKnob = aggressionKnob,
            hypoCautionUserKnob = hypoCautionKnob,
            confirmedCapU = preferences.get(DoubleKey.ApsBoostV5ConfirmedCapU),
            committedCapU = preferences.get(DoubleKey.ApsBoostV5CommittedCapU),
        )
    }

    override fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatus? = null

    override fun configuration(): JSONObject = JSONObject()

    override fun applyConfiguration(configuration: JSONObject) {}

    /** V5's three (and only three) user-facing knobs, per the minimal-settings tenet. */
    val aggressionKnob: Double get() = preferences.get(DoubleKey.ApsBoostV5Aggression)
    val hypoCautionKnob: Double get() = preferences.get(DoubleKey.ApsBoostV5HypoCaution)

    /**
     * Sensitivity knob — reserved. Per the V5 proposal Decision #4, this is the optional knob
     * that ships ONLY if backtest bimodality justifies it. Currently fixed at 1.0 by exposing
     * a degenerate range; it's wired into prefs so the UI surfaces it as "reserved" rather
     * than introducing a new key later.
     */
    @Suppress("unused")
    val sensitivityKnob: Double get() = preferences.get(DoubleKey.ApsBoostV5Sensitivity)

    override fun addPreferenceScreen(
        preferenceManager: PreferenceManager,
        parent: PreferenceScreen,
        context: Context,
        requiredKey: String?,
    ) {
        if (requiredKey != null && requiredKey != "openapsboostv5_settings") return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "openapsboostv5_settings"
            title = rh.gs(R.string.openaps_boost_v5)
            initialExpandedChildrenCount = 0

            addPreference(AdaptiveDoublePreference(
                ctx = context,
                doubleKey = DoubleKey.ApsBoostV5Aggression,
                dialogMessage = R.string.boost_v5_aggression_summary,
                title = R.string.boost_v5_aggression_title,
            ))
            addPreference(AdaptiveDoublePreference(
                ctx = context,
                doubleKey = DoubleKey.ApsBoostV5HypoCaution,
                dialogMessage = R.string.boost_v5_hypo_caution_summary,
                title = R.string.boost_v5_hypo_caution_title,
            ))
            addPreference(AdaptiveDoublePreference(
                ctx = context,
                doubleKey = DoubleKey.ApsBoostV5Sensitivity,
                dialogMessage = R.string.boost_v5_sensitivity_summary,
                title = R.string.boost_v5_sensitivity_title,
            ))
        }
    }
}
