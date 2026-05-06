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
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.plugins.aps.OpenAPSFragment
import app.aaps.plugins.aps.R
import org.json.JSONObject
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
 * V4 retention audit results are recorded in the V5 redesign proposal and migration doc.
 */
@Singleton
open class OpenAPSBoostV5Plugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val config: Config,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
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
    ) {
        try {
            val priorState = stateStore.load()
            val inputs = buildInputs(rT, glucoseStatus, iobArray, oapsProfile, pumpBolusStep)
            val decision = determineBasalBoostV5.decide(inputs, priorState)
            stateStore.save(decision.newPersistedState)
            val rtJson = v5DecisionToRtJson(decision)
            aapsLogger.info(LTag.APS, "BoostV5_RT: ${rtJson} actual_v441_smb=${rT.units ?: 0.0} actual_v441_insulinReq=${rT.insulinReq ?: 0.0}")
        } catch (e: Throwable) {
            // Never let V5 break V4.4.1. Log and continue.
            aapsLogger.error(LTag.APS, "BoostV5 shadow error", e)
        }
    }

    private fun buildInputs(
        rT: RT,
        gs: GlucoseStatus,
        iobArray: Array<IobTotal>,
        opb: OapsProfileBoost,
        pumpBolusStep: Double,
    ): V5Inputs {
        // delta_accl with V3's denominator floor — `max(|shortAvgDelta|, 2.0)` — carried over
        // verbatim from V3 input preprocessing.
        val deltaAccl = 100.0 * (gs.delta - gs.shortAvgDelta) / max(abs(gs.shortAvgDelta), 2.0)

        // 3-cycle delta history from glucose status. longAvgDelta is a longer-window average
        // so it's a reasonable proxy for "two cycles ago"; combined with shortAvgDelta and
        // current delta, deltaDeclining can reliably check the 2-cycle decline pattern.
        val deltaHistory = listOf(gs.longAvgDelta, gs.shortAvgDelta, gs.delta)

        // baseInsulinReq directly from V4.4.1's computed value. V4.4.1 used the Boost-flavoured
        // formula `(min(minPredBG, eventualBG) - target_bg) / future_sens` with DynISF +
        // 7D-only TDD + EMA sensitivity all baked in. V5 trusts this number.
        val baseInsulinReq = (rT.insulinReq ?: 0.0).coerceAtLeast(0.0)

        val iob = iobArray.firstOrNull()?.iob ?: 0.0

        val hour = LocalTime.now(ZoneId.systemDefault()).hour

        // V4.4.1's own `enableSMB` / pre-checks decision is reflected in `rT.units`. If V4.4.1
        // chose to deliver an SMB, pre-checks passed. If V4.4.1 set `units = 0` AND there was
        // a non-zero insulinReq, some gate fired.
        val enableSmbPreChecks = (rT.units ?: 0.0) > 0.0 || (rT.insulinReq ?: 0.0) <= 0.0

        return V5Inputs(
            delta = gs.delta,
            shortAvgDelta = gs.shortAvgDelta,
            deltaAccl = deltaAccl,
            bg = gs.glucose,
            eventualBg = rT.eventualBG ?: gs.glucose,
            targetBg = opb.target_bg,
            maxDelta = abs(gs.delta),
            // minGuardBg: minimum across V4.4.1's IOB/UAM/ZT prediction lists. V4.4.1's own
            // hard gate uses the same-shaped value internally (line 1055 of DetermineBasalBoostV3MLG3).
            minGuardBg = listOfNotNull(rT.predBGs?.IOB, rT.predBGs?.UAM, rT.predBGs?.ZT)
                .flatten().minOrNull()?.toDouble() ?: gs.glucose,
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
            hour = hour,
            exerciseActive = opb.v5_exerciseActive,
            inPostExerciseWindow = opb.v5_inPostExerciseWindow,
            sensorQualityOk = true,            // V0: BG quality already vetted by V4.4.1's checks
            profileSwitched = false,           // V0: profile-switch detection deferred (low-impact path)
            pumpDisconnected = false,
            loopSuspended = false,
            timeJumpMinutes = 0.0,
            aggressionUserKnob = aggressionKnob,
            hypoCautionUserKnob = hypoCautionKnob,
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
