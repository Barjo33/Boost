package app.aaps.plugins.aps.openAPSBoostV5

import android.content.Context
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.GlucoseStatus
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
import javax.inject.Inject
import javax.inject.Singleton

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
    private val determineBasalBoostV5: DetermineBasalBoostV5
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
        // Plugin is PRE-ALPHA. invoke() is intentionally a NO-OP at the AAPS level — but the
        // shadow-runner pathway below is the entry point future shadow-mode wiring will use:
        //
        //   1. Build a V5Inputs from oref/Boost services (currently UNWIRED — see TODOs).
        //   2. shadowRun() — runs Phase 1/2/3, persists state, builds RT JSON.
        //   3. Future: emit RT JSON to ProcessedDeviceStatusData so it appears in NS deviceStatus.
        //
        // Until step 1 is wired (deferred dedicated task), invoke() does nothing. This avoids
        // emitting partial/incorrect shadow data while the integration is in progress.
        aapsLogger.debug(LTag.APS, "BoostV5 invoke: PRE-ALPHA, shadow-runner not yet wired to AAPS inputs.")
    }

    /**
     * Run V5 against an already-built [V5Inputs], persist the new state, and return both the
     * full decision and a JSON blob suitable for NS deviceStatus emission.
     *
     * This is the integration seam: when [invoke] starts wiring real AAPS inputs (TODO: read
     * GlucoseStatus, ProcessedDeviceStatusData, IobCobCalculator, ML models, Boost exercise
     * state, profile.maxIOB, etc.), it should build V5Inputs and call this method. Tests can
     * invoke shadowRun directly with synthetic inputs to validate end-to-end behaviour.
     */
    fun shadowRun(inputs: V5Inputs): Pair<V5Decision, JSONObject> {
        val priorState = stateStore.load()
        val decision = determineBasalBoostV5.decide(inputs, priorState)
        stateStore.save(decision.newPersistedState)
        val rtJson = v5DecisionToRtJson(decision)
        aapsLogger.debug(LTag.APS, "BoostV5 shadow: state=${decision.mealHypothesis} dose=${decision.finalDose}")
        return Pair(decision, rtJson)
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
