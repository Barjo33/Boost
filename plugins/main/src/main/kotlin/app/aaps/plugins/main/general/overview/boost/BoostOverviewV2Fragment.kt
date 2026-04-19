package app.aaps.plugins.main.general.overview.boost

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.recyclerview.widget.LinearLayoutManager
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.WarnColors
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAcceptOpenLoopChange
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.rx.events.EventEffectiveProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventExtendedBolusChange
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventNewOpenLoopNotification
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.rx.events.EventRunningModeChange
import app.aaps.core.interfaces.rx.events.EventScale
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.rx.events.EventTempTargetChange
import app.aaps.core.interfaces.rx.events.EventWearUpdateTiles
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewCalcProgress
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewGraph
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewIobCob
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewSensitivity
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.round
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.ui.UIRunnable
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.BoostOverviewV2FragmentBinding
import app.aaps.plugins.main.general.overview.graphData.GraphData
import app.aaps.plugins.main.general.overview.notifications.NotificationStore
import app.aaps.plugins.main.general.overview.notifications.events.EventUpdateOverviewNotification
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider

/**
 * BoostOverviewV2Fragment — Alternative dark-theme home screen for Boost.
 *
 * Modern card-based layout with stat pills, integrated time range selector,
 * and streamlined bottom action buttons. Uses the same data sources and
 * helpers as BoostOverviewFragment.
 */
class BoostOverviewV2Fragment : DaggerFragment(), View.OnClickListener {

    // --- Injection (same dependencies as BoostOverviewFragment) ---

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var loop: Loop
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var notificationStore: NotificationStore
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var config: Config
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var overviewMenus: OverviewMenus
    @Inject lateinit var trendCalculator: TrendCalculator
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var lastBgData: LastBgData
    @Inject lateinit var automation: Automation
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var graphDataProvider: Provider<GraphData>
    @Inject lateinit var boostHelper: BoostOverviewHelper
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var dexcomBoyda: DexcomBoyda
    @Inject lateinit var xDripSource: XDripSource
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var warnColors: WarnColors

    // --- State ---

    private val disposable = CompositeDisposable()
    private var handler = Handler(HandlerThread("BoostOverviewV2Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable
    private var _binding: BoostOverviewV2FragmentBinding? = null
    private val binding get() = _binding!!

    private var axisWidth: Int = 0
    private var lastUserAction = ""

    @Volatile private var lastBoostStatus: BoostOverviewHelper.BoostStatus = BoostOverviewHelper.BoostStatus()

    private var scheduledGuiUpdate: Runnable? = null

    // Time range button references for highlight management
    private val rangeButtons: List<Pair<Int, Int>> by lazy {
        listOf(3 to R.id.v2_range_3h, 6 to R.id.v2_range_6h, 12 to R.id.v2_range_12h, 24 to R.id.v2_range_24h)
    }

    // --- Lifecycle ---

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        BoostOverviewV2FragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Resolve theme colours for BgBobbleView
        binding.v2BgBobble.resolveThemeColors(rh)

        // Graph axis width
        axisWidth = when {
            resources.displayMetrics.densityDpi <= 120 -> 3
            resources.displayMetrics.densityDpi <= 160 -> 10
            resources.displayMetrics.densityDpi <= 320 -> 35
            resources.displayMetrics.densityDpi <= 420 -> 50
            resources.displayMetrics.densityDpi <= 560 -> 70
            else                                       -> 80
        }

        // Graph styling — dark theme colours
        val gridColor = Color.parseColor("#1a1d28")
        binding.v2BgGraph.gridLabelRenderer?.gridColor = gridColor
        binding.v2BgGraph.gridLabelRenderer?.reloadStyles()
        binding.v2BgGraph.gridLabelRenderer?.labelVerticalWidth = axisWidth
        binding.v2BgGraph.gridLabelRenderer?.horizontalLabelsColor = Color.parseColor("#444444")
        binding.v2BgGraph.gridLabelRenderer?.verticalLabelsColor = Color.parseColor("#444444")

        binding.v2IobGraph.gridLabelRenderer?.gridColor = gridColor
        binding.v2IobGraph.gridLabelRenderer?.reloadStyles()
        binding.v2IobGraph.gridLabelRenderer?.isHorizontalLabelsVisible = false
        binding.v2IobGraph.gridLabelRenderer?.labelVerticalWidth = axisWidth
        binding.v2IobGraph.gridLabelRenderer?.numVerticalLabels = 3
        binding.v2IobGraph.gridLabelRenderer?.verticalLabelsColor = Color.parseColor("#444444")

        binding.v2Notifications.setHasFixedSize(false)
        binding.v2Notifications.layoutManager = LinearLayoutManager(view.context)

        // Chart menu (scale button hidden — V2 uses inline time range buttons instead)
        overviewMenus.setupChartMenu(binding.v2ChartMenuButton, binding.v2ScaleButton)

        // Time range buttons
        binding.v2Range3h.setOnClickListener { setRange(3) }
        binding.v2Range6h.setOnClickListener { setRange(6) }
        binding.v2Range12h.setOnClickListener { setRange(12) }
        binding.v2Range24h.setOnClickListener { setRange(24) }

        // Long-press BG graph to cycle range
        binding.v2BgGraph.setOnLongClickListener {
            overviewData.rangeToDisplay += 6
            overviewData.rangeToDisplay = if (overviewData.rangeToDisplay > 24) 6 else overviewData.rangeToDisplay
            preferences.put(IntNonKey.RangeToDisplay, overviewData.rangeToDisplay)
            rxBus.send(EventPreferenceChange(IntNonKey.RangeToDisplay.key))
            updateRangeButtons()
            false
        }

        // Tap BG graph to open Treatments
        binding.v2BgGraph.setOnClickListener {
            context?.let { ctx ->
                startActivity(android.content.Intent(ctx, uiInteraction.treatmentsActivity))
            }
        }

        // Stat pill click listeners
        binding.v2PillIob.setOnClickListener(this)
        binding.v2PillDynisf.setOnClickListener(this)
        binding.v2PillTdd.setOnClickListener(this)
        binding.v2PillProfile.setOnClickListener(this)

        // Bottom action buttons
        binding.v2BtnInsulin.setOnClickListener(this)
        binding.v2BtnCalculator.setOnClickListener(this)

        // Target value tap -> temp target dialog
        binding.v2TargetValue.setOnClickListener(this)

        // Exercise label tap -> activity details
        binding.v2ExerciseLabel.setOnClickListener(this)

        // Pump status bar tap
        binding.v2PumpStatusLayout.setOnClickListener(this)

        // AID status tap -> loop dialog
        binding.v2AidStatus.setOnClickListener(this)
        binding.v2AidDot.setOnClickListener(this)

        // Hidden standard buttons layout — wire up for compatibility
        binding.v2ButtonsLayout.acceptTempButton.setOnClickListener(this)
        binding.v2ButtonsLayout.quickWizardButton.setOnClickListener(this)

        updateRangeButtons()
    }

    override fun onResume() {
        super.onResume()

        // Progress bar
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewCalcProgress::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateCalcProgress() }, fabricPrivacy::logException)

        // Granular event-driven updates
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewIobCob::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateIobAndBoost() }, fabricPrivacy::logException)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewGraph::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGraph() }, fabricPrivacy::logException)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewNotification::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateNotification() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventBucketedDataCreated::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                boostHelper.invalidate()
                updateBg()
            }, fabricPrivacy::logException)

        // Full refresh events
        disposable += rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                boostHelper.invalidate()
                if (it.now) refreshAll() else scheduleUpdateGUI()
            }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventEffectiveProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .delay(30, TimeUnit.MILLISECONDS, aapsSchedulers.main)
            .subscribe({
                context?.let { ctx -> overviewData.pumpStatus = it.getStatus(ctx) }
                updatePumpStatus()
            }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNewOpenLoopNotification::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAcceptOpenLoopChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempTargetChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateHeroRight() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventRunningModeChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                updateAidStatus()
                runOnUiThread { processButtonsVisibility() }
            }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ processButtonsVisibility() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventScale::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                overviewData.rangeToDisplay = it.hours
                preferences.put(IntNonKey.RangeToDisplay, it.hours)
                rxBus.send(EventPreferenceChange(IntNonKey.RangeToDisplay.key))
                updateRangeButtons()
            }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewSensitivity::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)

        refreshLoop = Runnable { refreshAll(); handler.postDelayed(refreshLoop, 60_000L) }
        handler.postDelayed(refreshLoop, 60_000L)
        handler.post { refreshAll() }
        updatePumpStatus()
        updateCalcProgress()
        popupBolusDialogIfRunning(onClick = false)
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
        scheduledGuiUpdate?.let { handler.removeCallbacks(it) }
        scheduledGuiUpdate = null
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.v2BgGraph?.let { graph ->
            graph.setOnLongClickListener(null)
            graph.setOnClickListener(null)
            detachAllSeries(graph)
        }
        _binding?.v2IobGraph?.let { graph ->
            detachAllSeries(graph)
        }
        _binding = null
    }

    private fun detachAllSeries(graph: com.jjoe64.graphview.GraphView) {
        graph.removeAllSeries()
        val allSeries = listOf(
            overviewData.bucketedGraphSeries, overviewData.bgReadingGraphSeries,
            overviewData.predictionsGraphSeries, overviewData.baseBasalGraphSeries,
            overviewData.tempBasalGraphSeries, overviewData.basalLineGraphSeries,
            overviewData.absoluteBasalGraphSeries, overviewData.temporaryTargetSeries,
            overviewData.runningModesSeries, overviewData.activitySeries,
            overviewData.activityPredictionSeries, overviewData.epsSeries,
            overviewData.treatmentsSeries, overviewData.therapyEventSeries,
            overviewData.iobSeries, overviewData.absIobSeries,
            overviewData.iobPredictions1Series, overviewData.minusBgiSeries,
            overviewData.minusBgiHistSeries, overviewData.cobSeries,
            overviewData.cobMinFailOverSeries, overviewData.deviationsSeries,
            overviewData.ratioSeries, overviewData.varSensSeries,
            overviewData.dsMaxSeries, overviewData.dsMinSeries,
            overviewData.heartRateGraphSeries, overviewData.stepsCountGraphSeries
        )
        for (s in allSeries) {
            (s as? com.jjoe64.graphview.series.Series<*>)?.onGraphViewDetached(graph)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    // --- Coalesced GUI update ---

    private fun scheduleUpdateGUI() {
        class UpdateRunnable : Runnable {
            override fun run() {
                refreshAll()
                scheduledGuiUpdate = null
            }
        }
        scheduledGuiUpdate?.let { handler.removeCallbacks(it) }
        scheduledGuiUpdate = UpdateRunnable()
        scheduledGuiUpdate?.let { handler.postDelayed(it, 500) }
    }

    // --- Progress bar ---

    private fun updateCalcProgress() {
        _binding ?: return
        binding.v2ProgressBar.visibility = (overviewData.calcProgressPct != 100).toVisibility()
        binding.v2ProgressBar.progress = overviewData.calcProgressPct
    }

    // --- Time range buttons ---

    private fun setRange(hours: Int) {
        overviewData.rangeToDisplay = hours
        preferences.put(IntNonKey.RangeToDisplay, hours)
        rxBus.send(EventPreferenceChange(IntNonKey.RangeToDisplay.key))
        updateRangeButtons()
    }

    private fun updateRangeButtons() {
        runOnUiThread {
            _binding ?: return@runOnUiThread
            val current = overviewData.rangeToDisplay
            val accentColor = Color.parseColor("#00d4ff")
            val dimColor = Color.parseColor("#aaaaaa")
            val activeBg = Color.parseColor("#1a00d4ff")
            val inactiveBg = Color.TRANSPARENT

            for ((hours, _) in rangeButtons) {
                val tv: TextView = when (hours) {
                    3 -> binding.v2Range3h
                    6 -> binding.v2Range6h
                    12 -> binding.v2Range12h
                    24 -> binding.v2Range24h
                    else -> continue
                }
                if (hours == current) {
                    tv.setTextColor(accentColor)
                    tv.setBackgroundColor(activeBg)
                } else {
                    tv.setTextColor(dimColor)
                    tv.setBackgroundColor(inactiveBg)
                }
            }
        }
    }

    // --- Refresh ---

    private fun refreshAll() {
        if (!config.appInitialized || !isAdded) return
        val cachedStatus = boostHelper.getBoostStatus()
        lastBoostStatus = cachedStatus
        updateBg()
        updateIobAndBoost(cachedStatus)
        updatePumpInfo(cachedStatus)
        updateHeroRight(cachedStatus)
        updateProfile()
        runOnUiThread {
            _binding ?: return@runOnUiThread
            processButtonsVisibility()
            updateGraph()
            updateNotification()
            updateRangeButtons()
            updateAidStatus()
        }
    }

    // --- BG Bobble ---

    @SuppressLint("SetTextI18n")
    private fun updateBg() {
        if (!isAdded) return
        val lastBg = lastBgData.lastBg()
        val isActual = lastBgData.isActualBg()
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        val trendArrow = trendCalculator.getTrendArrow(iobCobCalculator.ads)
        val trendDesc = trendCalculator.getTrendDescription(iobCobCalculator.ads)
        val (trendAngle, chevronRot) = BgBobbleView.trendToAngles(trendArrow)

        runOnUiThread {
            _binding ?: return@runOnUiThread
            binding.v2BgBobble.bgValueMgdl = lastBg?.recalculated ?: 0.0
            binding.v2BgBobble.bgDisplayText = profileUtil.fromMgdlToStringInUnits(lastBg?.recalculated)
            binding.v2BgBobble.unitsLabel = if (profileFunction.getUnits() == GlucoseUnit.MGDL) "mg/dL" else "mmol/L"
            binding.v2BgBobble.isActualBg = isActual
            binding.v2BgBobble.trendAngleDeg = trendAngle
            binding.v2BgBobble.chevronRotateDeg = chevronRot

            if (glucoseStatus != null) {
                binding.v2DeltaText.text = "${profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.delta)} \u00B7 ${trendDesc ?: ""}"
            } else {
                binding.v2DeltaText.text = ""
            }

            binding.v2TimeAgo.text = dateUtil.minOrSecAgo(rh, lastBg?.timestamp)

            // TalkBack
            val bgStr = profileUtil.fromMgdlToStringInUnits(lastBg?.recalculated)
            val unitsStr = binding.v2BgBobble.unitsLabel
            val trendStr = trendDesc ?: ""
            val ageStr = binding.v2TimeAgo.text
            binding.v2BgBobble.contentDescription = "Blood glucose $bgStr $unitsStr, $trendStr, $ageStr"
        }
    }

    // --- AID Status (top-right indicator) ---

    private fun updateAidStatus() {
        runOnUiThread {
            _binding ?: return@runOnUiThread
            val pump = activePlugin.activePump
            if (!pump.pumpDescription.isTempBasalCapable) {
                binding.v2AidStatus.text = "NO AID"
                binding.v2AidDot.setBackgroundColor(Color.parseColor("#ff5252"))
                return@runOnUiThread
            }
            when (loop.runningMode) {
                RM.Mode.CLOSED_LOOP, RM.Mode.CLOSED_LOOP_LGS -> {
                    binding.v2AidStatus.text = "AID ON"
                    binding.v2AidDot.setBackgroundColor(Color.parseColor("#4caf50"))
                }
                RM.Mode.OPEN_LOOP -> {
                    binding.v2AidStatus.text = "OPEN"
                    binding.v2AidDot.setBackgroundColor(Color.parseColor("#ffb300"))
                }
                RM.Mode.SUSPENDED_BY_USER, RM.Mode.SUSPENDED_BY_PUMP, RM.Mode.SUSPENDED_BY_DST -> {
                    binding.v2AidStatus.text = "PAUSED"
                    binding.v2AidDot.setBackgroundColor(Color.parseColor("#ff5252"))
                }
                RM.Mode.DISCONNECTED_PUMP -> {
                    binding.v2AidStatus.text = "DISC"
                    binding.v2AidDot.setBackgroundColor(Color.parseColor("#ff5252"))
                }
                RM.Mode.DISABLED_LOOP -> {
                    binding.v2AidStatus.text = "OFF"
                    binding.v2AidDot.setBackgroundColor(Color.parseColor("#444444"))
                }
                RM.Mode.SUPER_BOLUS -> {
                    binding.v2AidStatus.text = "S.BOLUS"
                    binding.v2AidDot.setBackgroundColor(Color.parseColor("#ffb300"))
                }
                else -> {
                    binding.v2AidStatus.text = "AID ON"
                    binding.v2AidDot.setBackgroundColor(Color.parseColor("#4caf50"))
                }
            }
        }
    }

    // --- IOB + Boost stat pills ---

    @SuppressLint("SetTextI18n")
    private fun updateIobAndBoost(cached: BoostOverviewHelper.BoostStatus? = null) {
        if (!isAdded) return
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        val totalIob = bolusIob.iob + basalIob.basaliob
        val boostStatus = cached ?: boostHelper.getBoostStatus()
        lastBoostStatus = boostStatus

        runOnUiThread {
            _binding ?: return@runOnUiThread
            // IOB pill
            binding.v2PillIobValue.text = String.format(Locale.getDefault(), "%.2fu", totalIob)

            // DynISF pill
            val dynRaw = boostStatus.dynIsfValue
            val dynDisp = if (dynRaw > 0) {
                val converted = profileUtil.fromMgdlToUnits(dynRaw)
                if (profileFunction.getUnits() == GlucoseUnit.MGDL) String.format(Locale.getDefault(), "%.0f", converted)
                else String.format(Locale.getDefault(), "%.1f", converted)
            } else "---"
            binding.v2PillDynisfValue.text = dynDisp

            // TDD pill
            val tddDisplay = when {
                boostStatus.tddWeighted > 0  -> boostStatus.tddWeighted
                boostStatus.tddFromDebug > 0 -> boostStatus.tddFromDebug
                else                          -> boostStatus.tdd7d
            }
            binding.v2PillTddValue.text = if (tddDisplay > 0) String.format(Locale.getDefault(), "%.1fu", tddDisplay) else "---"

            // Profile pill — shows tier label + profile percentage
            val tierShort = boostStatus.tierLabel
                .replace(Regex("""^Tier\s+\d+\s*-\s*"""), "")
                .take(12)
            binding.v2PillProfileTier.text = tierShort
            binding.v2PillProfileTier.setTextColor(boostStatus.tier.colorHex.toInt())
            binding.v2PillProfilePct.text = "${boostStatus.profilePercentage}%"

            // TalkBack
            binding.v2PillIob.contentDescription = "Insulin on board: ${String.format(Locale.getDefault(), "%.2f", totalIob)} units"
            val unitsStr = if (profileFunction.getUnits() == GlucoseUnit.MGDL) "mg/dL per unit" else "mmol/L per unit"
            binding.v2PillDynisf.contentDescription = "Dynamic ISF: $dynDisp $unitsStr"
            binding.v2PillTdd.contentDescription = "Total daily dose: ${if (tddDisplay > 0) String.format(Locale.getDefault(), "%.1f units", tddDisplay) else "unavailable"}"
            binding.v2PillProfile.contentDescription = "Boost tier: $tierShort, profile ${boostStatus.profilePercentage} percent"
        }
    }

    // --- Profile ---

    private fun updateProfile() {
        if (!isAdded) return
        val profile = profileFunction.getProfile()
        val isModified = (profile as? ProfileSealed.EPS)?.let {
            it.value.originalPercentage != 100 || it.value.originalTimeshift != 0L || it.value.originalDuration != 0L
        } ?: false
        val pct = (profile as? ProfileSealed.EPS)?.value?.originalPercentage ?: 100

        runOnUiThread {
            _binding ?: return@runOnUiThread
            // Profile pill already shows boost tier; the pct reflects the profile percentage
            binding.v2PillProfilePct.text = "${pct}%"
            val color = if (isModified) Color.parseColor("#ffb300") else Color.parseColor("#00d4ff")
            binding.v2PillProfilePct.setTextColor(color)
        }
    }

    // --- Pump info (left hero column) ---

    @SuppressLint("SetTextI18n")
    private fun updatePumpInfo(cached: BoostOverviewHelper.BoostStatus? = null) {
        if (!isAdded) return
        val pump = activePlugin.activePump
        val boostStatus = cached ?: boostHelper.getBoostStatus()
        runOnUiThread {
            _binding ?: return@runOnUiThread
            val res = pump.reservoirLevel
            val maxReading = pump.pumpDescription.maxResorvoirReading.toDouble()
            if (pump.pumpDescription.isPatchPump && res >= maxReading) {
                binding.v2PumpReservoir.text = "${decimalFormatter.to0Decimal(maxReading)}+U"
            } else if (res > 0) {
                binding.v2PumpReservoir.text = "${decimalFormatter.to0Decimal(res)}U"
                warnColors.setColorInverse(binding.v2PumpReservoir, res,
                    preferences.get(IntKey.OverviewResWarning), preferences.get(IntKey.OverviewResCritical))
            } else {
                binding.v2PumpReservoir.text = "---"
            }

            val bat = pump.batteryLevel
            binding.v2PumpBattery.text = if (bat != null) "${bat}%" else "---"

            val cStr = formatAgeDaysHours(boostStatus.cannulaAgeDays)
            val sStr = formatAgeDaysHours(boostStatus.sensorAgeDays)
            binding.v2PumpCannulaAge.text = cStr
            binding.v2PumpSensorAge.text = sStr

            // Apply warning colours
            val cannulaTE = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)
            val sensorTE = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)
            if (cannulaTE != null) {
                warnColors.setColorByAge(binding.v2PumpCannulaAge, cannulaTE,
                    preferences.get(IntKey.OverviewCageWarning), preferences.get(IntKey.OverviewCageCritical))
            }
            if (sensorTE != null) {
                warnColors.setColorByAge(binding.v2PumpSensorAge, sensorTE,
                    preferences.get(IntKey.OverviewSageWarning), preferences.get(IntKey.OverviewSageCritical))
            }

            // TalkBack
            val resDesc = if (res > 0) "${decimalFormatter.to0Decimal(res)} units" else "unknown"
            val batDesc = if (bat != null) "$bat percent" else "unknown"
            binding.v2PumpSection.contentDescription = "Pump: reservoir $resDesc, battery $batDesc, cannula $cStr, sensor $sStr"
        }
    }

    // --- Hero right column (Target, Exercise) ---

    @SuppressLint("SetTextI18n")
    private fun updateHeroRight(cached: BoostOverviewHelper.BoostStatus? = null) {
        if (!isAdded) return
        val bs = cached ?: boostHelper.getBoostStatus()

        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())
        val hasTempTarget = tempTarget != null
        val profileTargetMgdl = profileFunction.getProfile()?.getTargetMgdl() ?: 0.0
        val targetMgdl: Double = when {
            hasTempTarget -> tempTarget!!.lowTarget
            bs.targetBgMgdl > 0 && profileTargetMgdl > 0 &&
                kotlin.math.abs(profileTargetMgdl - bs.targetBgMgdl) > 0.01 -> bs.targetBgMgdl
            else -> profileTargetMgdl.takeIf { it > 0 } ?: 100.0
        }
        val targetStr = profileUtil.fromMgdlToStringInUnits(targetMgdl)

        runOnUiThread {
            _binding ?: return@runOnUiThread
            binding.v2TargetValue.text = targetStr
            binding.v2TargetValue.setTextColor(
                when {
                    hasTempTarget -> Color.parseColor("#ffb300")
                    else          -> Color.parseColor("#e8eaf0")
                }
            )

            binding.v2ExerciseLabel.text = bs.activityDetail
            val actColor = when (bs.activityMode) {
                BoostOverviewHelper.ActivityMode.ACTIVE    -> Color.parseColor("#ffb300")
                BoostOverviewHelper.ActivityMode.INACTIVE  -> Color.parseColor("#60a5fa")
                BoostOverviewHelper.ActivityMode.SLEEP_IN  -> Color.parseColor("#c084fc")
                BoostOverviewHelper.ActivityMode.BOOST_OFF -> Color.parseColor("#444444")
                else                                       -> Color.parseColor("#aaaaaa")
            }
            binding.v2ExerciseLabel.setTextColor(actColor)
        }
    }

    // --- Pump status bar ---

    private fun updatePumpStatus() {
        runOnUiThread {
            _binding ?: return@runOnUiThread
            val status = overviewData.pumpStatus
            if (status.isEmpty()) binding.v2PumpStatusLayout.visibility = View.GONE
            else { binding.v2PumpStatus.text = status; binding.v2PumpStatusLayout.visibility = View.VISIBLE }
        }
    }

    // --- Graph ---

    private fun updateGraph() {
        _binding ?: return
        val ctx = context ?: return
        val pump = activePlugin.activePump
        val graphData = graphDataProvider.get().with(binding.v2BgGraph, overviewData)
        val menuChartSettings = overviewMenus.setting
        if (menuChartSettings.isEmpty()) return

        graphData.addInRangeArea(overviewData.fromTime, overviewData.endTime,
            preferences.get(UnitDoubleKey.OverviewLowMark), preferences.get(UnitDoubleKey.OverviewHighMark))
        graphData.addBgReadings(menuChartSettings[0][OverviewMenus.CharType.PRE.ordinal], ctx)
        graphData.addBucketedData()
        graphData.addTreatments(ctx)
        graphData.addEps(ctx, 0.95)
        if (menuChartSettings[0][OverviewMenus.CharType.TREAT.ordinal]) graphData.addTherapyEvents()
        if (menuChartSettings[0][OverviewMenus.CharType.ACT.ordinal]) graphData.addActivity(0.8)
        if ((pump.pumpDescription.isTempBasalCapable || config.AAPSCLIENT) && menuChartSettings[0][OverviewMenus.CharType.BAS.ordinal])
            graphData.addBasals()
        graphData.addTargetLine()
        graphData.addRunningModes()
        graphData.addNowLine(dateUtil.now())
        graphData.setNumVerticalLabels()
        graphData.formatAxis(overviewData.fromTime, overviewData.endTime)
        graphData.performUpdate()

        // ── V2 Dark Theme Restyle ──
        // The standard GraphData series use the AAPS theme colours (green bands,
        // cyan bars). Override them after performUpdate() to match the V2 design.
        val bgGraph = binding.v2BgGraph
        bgGraph.viewport.backgroundColor = Color.parseColor("#0a0c10")
        bgGraph.gridLabelRenderer?.gridColor = Color.parseColor("#1a1d28")
        bgGraph.gridLabelRenderer?.horizontalLabelsColor = Color.parseColor("#333333")
        bgGraph.gridLabelRenderer?.verticalLabelsColor = Color.parseColor("#333333")
        // Restyle series: iterate and adjust colours
        for (i in 0 until bgGraph.series.size) {
            val series = bgGraph.series[i]
            when (series) {
                is com.jjoe64.graphview.series.AreaGraphSeries<*> -> {
                    // In-range area — make it subtle accent tint instead of bright green
                    series.color = 0  // no border
                    series.backgroundColor = Color.parseColor("#0c00d4ff")  // very subtle cyan tint
                }
                is com.jjoe64.graphview.series.BarGraphSeries<*> -> {
                    // Basal bars — change from cyan to orange, reduce opacity
                    series.color = Color.parseColor("#59fb923c")  // semi-transparent orange
                }
            }
        }

        // IOB graph
        val iobGraphData = graphDataProvider.get().with(binding.v2IobGraph, overviewData)
        iobGraphData.addIob(true, 1.0)
        iobGraphData.addNowLine(dateUtil.now())
        iobGraphData.formatAxis(overviewData.fromTime, overviewData.endTime)
        iobGraphData.performUpdate()

        // IOB graph V2 restyle
        val iobGraph = binding.v2IobGraph
        iobGraph.viewport.backgroundColor = Color.parseColor("#090b0f")
        iobGraph.gridLabelRenderer?.gridColor = Color.parseColor("#1a1d28")
        iobGraph.gridLabelRenderer?.verticalLabelsColor = Color.parseColor("#333333")
        // Restyle IOB series to blue
        for (i in 0 until iobGraph.series.size) {
            val series = iobGraph.series[i]
            when (series) {
                is com.jjoe64.graphview.series.AreaGraphSeries<*> -> {
                    series.backgroundColor = Color.parseColor("#4060a5fa")  // blue gradient fill
                }
                is com.jjoe64.graphview.series.LineGraphSeries<*> -> {
                    series.color = Color.parseColor("#60a5fa")  // blue line
                    series.thickness = 4
                }
            }
        }

        // Sensitivity graph
        val profile = profileFunction.getProfile()
        val bucketedData = iobCobCalculator.ads.getBucketedDataTableCopy()
        if (bucketedData != null && bucketedData.size > 20 && profile != null) {
            binding.v2SensitivityGraphContainer.visibility = View.VISIBLE
            val sensGraph = binding.v2SensitivityGraph

            data class DevPoint(val time: Long, val deviation: Double, val absBgi: Double, val hasCob: Boolean)
            val devPoints = mutableListOf<DevPoint>()
            val effectiveIsf = lastBoostStatus.variableSens.takeIf { it > 0 }
                ?: profile.getIsfMgdl("SensGraph")

            for (i in 1 until bucketedData.size) {
                val bg = bucketedData[i].recalculated
                val prevBg = bucketedData[i - 1].recalculated
                val time = bucketedData[i].timestamp
                if (bg <= 39 || prevBg <= 39) continue

                val actualDelta = bg - prevBg
                val iobData = iobCobCalculator.calculateFromTreatmentsAndTemps(time, profile)
                val bgi = -iobData.activity * effectiveIsf * 5.0
                val deviation = actualDelta - bgi
                val cobInfo = iobCobCalculator.getCobInfo("SensGraph")
                val hasCob = cobInfo.displayCob != null && cobInfo.displayCob!! > 0.5
                devPoints.add(DevPoint(time, deviation, kotlin.math.abs(bgi), hasCob))
            }

            val windowMs = 3600_000L
            val dataPoints = mutableListOf<com.jjoe64.graphview.series.DataPoint>()
            val refPoints = mutableListOf<com.jjoe64.graphview.series.DataPoint>()
            for (pt in devPoints) {
                if (pt.time < overviewData.fromTime || pt.time > overviewData.endTime) continue
                var sumDev = 0.0; var sumAbsBgi = 0.0; var nClean = 0; var nTotal = 0
                for (wp in devPoints) {
                    if (wp.time > pt.time) break
                    if (wp.time < pt.time - windowMs) continue
                    nTotal++
                    sumAbsBgi += wp.absBgi
                    if (!wp.hasCob) { sumDev += wp.deviation; nClean++ }
                }
                val ratio = if (nClean >= 3 && nTotal > 0) {
                    val meanAbsBgi = sumAbsBgi / nTotal
                    if (meanAbsBgi > 0.5) (1.0 + (sumDev / nClean) / meanAbsBgi).coerceIn(0.5, 1.5) else 1.0
                } else 1.0
                dataPoints.add(com.jjoe64.graphview.series.DataPoint(pt.time.toDouble(), ratio))
                refPoints.add(com.jjoe64.graphview.series.DataPoint(pt.time.toDouble(), 1.0))
            }

            sensGraph.removeAllSeries()
            if (dataPoints.size > 2) {
                val refSeries = com.jjoe64.graphview.series.LineGraphSeries(refPoints.toTypedArray())
                refSeries.color = Color.GRAY
                refSeries.thickness = 2
                sensGraph.addSeries(refSeries)

                val capHighPoints = refPoints.map { com.jjoe64.graphview.series.DataPoint(it.x, 1.15) }.toTypedArray()
                val capLowPoints = refPoints.map { com.jjoe64.graphview.series.DataPoint(it.x, 0.85) }.toTypedArray()
                val capHighSeries = com.jjoe64.graphview.series.LineGraphSeries(capHighPoints)
                capHighSeries.color = Color.argb(80, 255, 0, 0)
                capHighSeries.thickness = 1
                sensGraph.addSeries(capHighSeries)
                val capLowSeries = com.jjoe64.graphview.series.LineGraphSeries(capLowPoints)
                capLowSeries.color = Color.argb(80, 255, 0, 0)
                capLowSeries.thickness = 1
                sensGraph.addSeries(capLowSeries)

                val sensSeries = com.jjoe64.graphview.series.LineGraphSeries(dataPoints.toTypedArray())
                sensSeries.color = Color.parseColor("#e8eaf0")
                sensSeries.thickness = 4
                sensGraph.addSeries(sensSeries)

                sensGraph.viewport.isXAxisBoundsManual = true
                sensGraph.viewport.setMinX(overviewData.fromTime.toDouble())
                sensGraph.viewport.setMaxX(overviewData.endTime.toDouble())
                sensGraph.viewport.isYAxisBoundsManual = true
                sensGraph.viewport.setMinY(0.7)
                sensGraph.viewport.setMaxY(1.3)
                sensGraph.gridLabelRenderer?.isHorizontalLabelsVisible = false
                sensGraph.gridLabelRenderer?.numVerticalLabels = 3
                sensGraph.gridLabelRenderer?.labelVerticalWidth = axisWidth
                sensGraph.gridLabelRenderer?.gridColor = Color.parseColor("#1a1d28")
                sensGraph.gridLabelRenderer?.verticalLabelsColor = Color.parseColor("#444444")

                val nowSeries = com.jjoe64.graphview.series.LineGraphSeries(arrayOf(
                    com.jjoe64.graphview.series.DataPoint(dateUtil.now().toDouble(), 0.7),
                    com.jjoe64.graphview.series.DataPoint(dateUtil.now().toDouble(), 1.3)
                ))
                nowSeries.color = Color.parseColor("#e8eaf0")
                nowSeries.thickness = 1
                sensGraph.addSeries(nowSeries)
            }

            val currentDevRatio = lastBoostStatus.deviationSensRatio
            val labelText = if (currentDevRatio != null && lastBoostStatus.deviationSensSource != "none") {
                "Sensitivity \u00D7${String.format(Locale.getDefault(), "%.2f", currentDevRatio)} (${lastBoostStatus.deviationSensSource})"
            } else {
                val latestRatio = dataPoints.lastOrNull()?.y ?: 1.0
                "Sensitivity \u00D7${String.format(Locale.getDefault(), "%.2f", latestRatio)} (preview)"
            }
            binding.v2SensitivityGraphLabel.text = labelText
        } else {
            binding.v2SensitivityGraphContainer.visibility = View.GONE
        }

        // TalkBack
        val hours = overviewData.rangeToDisplay
        binding.v2BgGraph.contentDescription = "Blood glucose graph, ${hours} hour view"
        binding.v2IobGraph.contentDescription = "Insulin on board graph, ${hours} hour view"
    }

    // --- Notifications ---

    private fun updateNotification() {
        _binding ?: return
        binding.v2Notifications.let { notificationStore.updateNotifications(it) }
    }

    // --- Button visibility (hidden standard buttons for accept temp, quick wizard) ---

    @SuppressLint("SetTextI18n")
    private fun processButtonsVisibility() {
        _binding ?: return
        val profile = profileFunction.getProfile()
        val pump = activePlugin.activePump
        val actualBG = iobCobCalculator.ads.actualBg()

        // Accept temp button (shown in hidden buttons layout when open loop has pending change)
        val lastRun = loop.lastRun
        if (lastRun?.request?.isChangeRequested == true && loop.runningMode != RM.Mode.DISCONNECTED_PUMP && loop.runningMode == RM.Mode.OPEN_LOOP) {
            binding.v2ButtonsLayout.acceptTempButton.visibility = View.VISIBLE
            binding.v2ButtonsLayout.root.visibility = View.VISIBLE
            binding.v2ButtonsLayout.acceptTempButton.text = "${rh.gs(R.string.set_basal_question)}\n${lastRun.constraintsProcessed?.resultAsString()}"
        } else {
            binding.v2ButtonsLayout.acceptTempButton.visibility = View.GONE
        }

        // Quick wizard
        val quickWizardEntry = quickWizard.getActive()
        if (quickWizardEntry != null && actualBG != null && profile != null) {
            binding.v2ButtonsLayout.quickWizardButton.visibility = View.VISIBLE
            binding.v2ButtonsLayout.root.visibility = View.VISIBLE
            val wizard = quickWizardEntry.doCalc(profile, profileFunction.getProfileName(), actualBG)
            binding.v2ButtonsLayout.quickWizardButton.text = quickWizardEntry.buttonText() + "\n" +
                rh.gs(app.aaps.core.objects.R.string.format_carbs, quickWizardEntry.carbs()) +
                " " + rh.gs(app.aaps.core.ui.R.string.format_insulin_units, wizard.calculatedTotalInsulin)
            if (wizard.calculatedTotalInsulin <= 0) binding.v2ButtonsLayout.quickWizardButton.visibility = View.GONE
        } else {
            binding.v2ButtonsLayout.quickWizardButton.visibility = View.GONE
        }

        // Hide standard buttons that V2 replaces with its own UI
        binding.v2ButtonsLayout.treatmentButton.visibility = View.GONE
        binding.v2ButtonsLayout.wizardButton.visibility = View.GONE
        binding.v2ButtonsLayout.insulinButton.visibility = View.GONE
        binding.v2ButtonsLayout.carbsButton.visibility = View.GONE
        binding.v2ButtonsLayout.calibrationButton.visibility = View.GONE
        binding.v2ButtonsLayout.cgmButton.visibility = View.GONE

        // Show the standard buttons layout only if accept temp or quick wizard is visible
        val anyVisible = binding.v2ButtonsLayout.acceptTempButton.visibility == View.VISIBLE ||
            binding.v2ButtonsLayout.quickWizardButton.visibility == View.VISIBLE
        binding.v2ButtonsLayout.root.visibility = anyVisible.toVisibility()
    }

    // --- Click handlers ---

    @SuppressLint("SetTextI18n")
    override fun onClick(v: View) {
        if (childFragmentManager.isStateSaved) return
        activity?.let { a ->
            when (v.id) {
                // Stat pill clicks
                R.id.v2_pill_iob -> {
                    val bi = iobCobCalculator.calculateIobFromBolus().round()
                    val ba = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
                    OKDialog.show(a, rh.gs(app.aaps.core.ui.R.string.iob),
                        "Total: ${String.format(Locale.getDefault(), "%.2f", bi.iob + ba.basaliob)}U\nBolus: ${String.format(Locale.getDefault(), "%.2f", bi.iob)}U\nBasal: ${String.format(Locale.getDefault(), "%.2f", ba.basaliob)}U")
                }
                R.id.v2_pill_dynisf -> {
                    val bs = lastBoostStatus
                    val units = profileFunction.getUnits()
                    val unitsStr = if (units == GlucoseUnit.MGDL) "mg/dL" else "mmol/L"
                    val details = StringBuilder()
                    val dynConverted = if (bs.variableSens > 0) profileUtil.fromMgdlToUnits(bs.variableSens) else 0.0
                    val dynFmt = if (units == GlucoseUnit.MGDL) "%.0f" else "%.1f"
                    details.append("Dynamic ISF: ${if (bs.variableSens > 0) String.format(Locale.getDefault(), dynFmt, dynConverted) else "---"} $unitsStr/U")
                    details.append("\n\nAlgorithm inputs:")
                    val bgConverted = if (bs.lastBg > 0) profileUtil.fromMgdlToStringInUnits(bs.lastBg) else "---"
                    details.append("\n  BG: $bgConverted $unitsStr")
                    details.append("\n  TDD (weighted): ${if (bs.tddWeighted > 0) String.format(Locale.getDefault(), "%.1f", bs.tddWeighted) else "---"}")
                    if (bs.insulinDivisor > 0) details.append("\n  Insulin divisor: ${bs.insulinDivisor}")
                    details.append("\n\nTDD 7d avg: ${String.format(Locale.getDefault(), "%.1f", bs.tdd7d)}")
                    details.append("\nTDD 24h: ${String.format(Locale.getDefault(), "%.1f", bs.tdd24h)}")
                    OKDialog.show(a, "Dynamic ISF", details.toString())
                }
                R.id.v2_pill_tdd -> {
                    val bs = lastBoostStatus
                    OKDialog.show(a, "Total Daily Dose",
                        "oapsProfile.TDD: ${if (bs.tddWeighted > 0) String.format(Locale.getDefault(), "%.1f", bs.tddWeighted) else "(not set)"}\n" +
                            "TDD from debug: ${if (bs.tddFromDebug > 0) String.format(Locale.getDefault(), "%.1f", bs.tddFromDebug) else "(not found)"}\n" +
                            "TDD 7d avg: ${String.format(Locale.getDefault(), "%.1f", bs.tdd7d)}\n" +
                            "TDD 24h: ${String.format(Locale.getDefault(), "%.1f", bs.tdd24h)}\n\n" +
                            "--- Script Debug ---\n${bs.scriptDebugText.ifEmpty { "(no debug output)" }}")
                }
                R.id.v2_pill_profile -> {
                    val bs = lastBoostStatus
                    val fcLine = if (bs.fastCarbProtection) "Fast Carb Protection active\n\n" else ""
                    OKDialog.show(a, "Boost Decision",
                        "${fcLine}Current tier: ${bs.tierLabel}\n\nReason: ${bs.tierReason}\n\nDelta accel: ${String.format(Locale.getDefault(), "%.1f", bs.deltaAccl)}")
                }

                // Target value tap -> temp target dialog
                R.id.v2_target_value -> {
                    protectionCheck.queryProtection(a, ProtectionCheck.Protection.BOLUS,
                        UIRunnable { if (isAdded) uiInteraction.runTempTargetDialog(childFragmentManager) })
                }

                // Exercise label tap -> activity details
                R.id.v2_exercise_label -> {
                    val bs = lastBoostStatus
                    val units = profileFunction.getUnits()
                    val unitsLabel = if (units == GlucoseUnit.MGDL) "mg/dL" else "mmol/L"
                    val exerciseTargetStr = if (bs.targetBgMgdl > 0)
                        "${profileUtil.fromMgdlToStringInUnits(bs.targetBgMgdl)} $unitsLabel"
                    else "---"
                    OKDialog.show(a, "Exercise / Activity Mode",
                        "Current: ${bs.activityDetail}\n\n" +
                            "Exercise target: $exerciseTargetStr\n\n" +
                            "Profile: ${bs.profilePercentage}%\n\n" +
                            "--- Script Debug ---\n${bs.scriptDebugText.ifEmpty { "(no debug output)" }}")
                }

                // Bottom action buttons
                R.id.v2_btn_insulin -> {
                    protectionCheck.queryProtection(a, ProtectionCheck.Protection.BOLUS,
                        UIRunnable { if (isAdded) uiInteraction.runInsulinDialog(childFragmentManager) })
                }
                R.id.v2_btn_calculator -> {
                    protectionCheck.queryProtection(a, ProtectionCheck.Protection.BOLUS,
                        UIRunnable { if (isAdded) uiInteraction.runWizardDialog(childFragmentManager) })
                }

                // AID status tap -> loop dialog
                R.id.v2_aid_status, R.id.v2_aid_dot -> {
                    protectionCheck.queryProtection(a, ProtectionCheck.Protection.BOLUS, UIRunnable {
                        if (isAdded) uiInteraction.runLoopDialog(childFragmentManager, 1)
                    })
                }

                // Standard hidden buttons
                R.id.accept_temp_button -> {
                    protectionCheck.queryProtection(a, ProtectionCheck.Protection.BOLUS, UIRunnable {
                        if (isAdded) {
                            val lastRun = loop.lastRun
                            loop.invoke("Accept temp button", false)
                            if (lastRun?.lastAPSRun != null && lastRun.constraintsProcessed?.isChangeRequested == true) {
                                protectionCheck.queryProtection(a, ProtectionCheck.Protection.BOLUS, UIRunnable {
                                    uel.log(Action.ACCEPTS_TEMP_BASAL, Sources.Overview)
                                    binding.v2ButtonsLayout.acceptTempButton.visibility = View.GONE
                                })
                            }
                        }
                    })
                }
                R.id.quick_wizard_button -> {
                    protectionCheck.queryProtection(a, ProtectionCheck.Protection.BOLUS,
                        UIRunnable { if (isAdded) onClickQuickWizard() })
                }

                // Pump status bar tap
                R.id.v2_pump_status_layout -> {
                    popupBolusDialogIfRunning(onClick = true)
                }
            }
        }
    }

    private fun onClickQuickWizard() {
        context?.let { ctx ->
            startActivity(android.content.Intent(ctx, uiInteraction.quickWizardListActivity))
        }
    }

    fun popupBolusDialogIfRunning(onClick: Boolean) {
        if (commandQueue.bolusInQueue()) {
            if (!BolusProgressData.bolusEnded && (!BolusProgressData.isSMB || onClick)) {
                activity?.let { activity ->
                    protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                        if (isAdded)
                            uiInteraction.runBolusProgressDialog(childFragmentManager)
                    })
                }
            }
        }
    }

    private fun formatAgeDaysHours(ageDays: Double): String {
        if (ageDays < 0) return "?"
        val totalHours = (ageDays * 24).toLong()
        val days = totalHours / 24
        val hours = totalHours % 24
        return "${days}d ${hours}h"
    }
}
