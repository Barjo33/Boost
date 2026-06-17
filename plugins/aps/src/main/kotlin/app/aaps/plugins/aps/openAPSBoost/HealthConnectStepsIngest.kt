package app.aaps.plugins.aps.openAPSBoost

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.openAPSBoost.DailyStepHistoryTracker.DailyTotal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HealthConnectStepsIngest — Boost activity-load SHADOW (2026-06-16). Mirrors [HealthConnectHrIngest].
 *
 * Reads StepsRecord from Health Connect, filters to a **single source** (the dominant `dataOrigin`
 * over the window — HC aggregates steps from every app that writes them and double-counts; for a
 * deviation-from-baseline metric, single-source CONSISTENCY beats blended accuracy), buckets into
 * per-LOCAL-day totals, and exposes them for the plugin to fold into [DailyStepHistoryTracker].
 *
 * Reads a full [DailyStepHistoryTracker.WINDOW_DAYS] window each poll, so the baseline is
 * **backfilled from HC history immediately** (no cold start). Hourly throttle (daily totals don't
 * need 5-min cadence). SHADOW — produces data only; nothing here touches dosing.
 */
@Singleton
class HealthConnectStepsIngest @Inject constructor(
    private val context: Context,
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    @Volatile private var client: HealthConnectClient? = null
    @Volatile private var inFlight = false
    @Volatile private var lastSyncRunMs = 0L
    @Volatile private var permissionWarned = false

    private val windowMs = DailyStepHistoryTracker.WINDOW_DAYS * 24L * 60L * 60_000L
    private val pollIntervalMs = 60 * 60_000L   // hourly is ample for daily totals

    /** Single-source per-completed-day totals from the most recent sync (empty until first sync). */
    @Volatile var latestDailyTotals: List<DailyTotal> = emptyList()
        private set
    @Volatile var chosenSource: String? = null
        private set
    /** All step-writing source packages seen in the last window (for diagnostics / NS visibility). */
    @Volatile var availableSources: List<String> = emptyList()
        private set

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun getOrInitClient(): HealthConnectClient? {
        client?.let { return it }
        if (!isAvailable) return null
        return try {
            HealthConnectClient.getOrCreate(context).also { client = it }
        } catch (t: Throwable) {
            aapsLogger.error(LTag.APS, "HealthConnectStepsIngest: client init failed: ${t.message}")
            null
        }
    }

    /** Call each Boost cycle. Cheap when not due; spawns an async sync when due. Never throws. */
    fun syncIfDue() {
        if (!preferences.get(BooleanKey.ApsBoostActivityShadowEnabled)) return
        val now = System.currentTimeMillis()
        if (now - lastSyncRunMs < pollIntervalMs) return
        if (inFlight) return
        val hc = getOrInitClient() ?: return
        inFlight = true
        lastSyncRunMs = now
        scope.launch {
            try {
                syncOnce(hc, now)
            } catch (t: Throwable) {
                aapsLogger.error(LTag.APS, "HealthConnectStepsIngest: sync failed: ${t.message}")
            } finally {
                inFlight = false
            }
        }
    }

    private suspend fun syncOnce(hc: HealthConnectClient, nowMs: Long) {
        val granted = try {
            HealthPermission.getReadPermission(StepsRecord::class) in hc.permissionController.getGrantedPermissions()
        } catch (t: Throwable) {
            aapsLogger.error(LTag.APS, "HealthConnectStepsIngest: permission check failed: ${t.message}")
            false
        }
        if (!granted) {
            if (!permissionWarned) {
                aapsLogger.warn(LTag.APS, "HealthConnectStepsIngest: READ_STEPS not granted — grant Health Connect step access (Boost → HC) for the activity shadow.")
                permissionWarned = true
            }
            return
        }
        val sinceMs = nowMs - windowMs
        val offsetMs = ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds * 1000L
        val resp = hc.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(Instant.ofEpochMilli(sinceMs), Instant.ofEpochMilli(nowMs))
            )
        )
        // Sum per (source package, local day); pick the dominant source; emit only its per-day totals.
        val perSourceDay = HashMap<String, HashMap<Long, Long>>()
        val perSourceTotal = HashMap<String, Long>()
        for (r in resp.records) {
            val src = r.metadata.dataOrigin.packageName.ifBlank { "unknown" }
            val day = DailyStepHistoryTracker.dayIndex(r.startTime.toEpochMilli(), offsetMs)
            perSourceDay.getOrPut(src) { HashMap() }.merge(day, r.count) { a, b -> a + b }
            perSourceTotal.merge(src, r.count) { a, b -> a + b }
        }
        availableSources = perSourceTotal.entries.sortedByDescending { it.value }.map { it.key }
        val chosen = chooseSource(perSourceTotal)
        if (chosen == null) {
            latestDailyTotals = emptyList()
            chosenSource = null
            aapsLogger.info(LTag.APS, "HealthConnectStepsIngest: no step records in window")
            return
        }
        latestDailyTotals = perSourceDay[chosen]!!
            .map { (day, steps) -> DailyTotal(day, steps.toInt(), chosen) }
            .sortedBy { it.dayIndex }
        chosenSource = chosen
        aapsLogger.info(LTag.APS, "HealthConnectStepsIngest: sources=${perSourceTotal.keys} chose=$chosen days=${latestDailyTotals.size}")
    }

    /** Force a sync regardless of throttle — e.g. a settings "test now" button. */
    fun forceSync() {
        lastSyncRunMs = 0L
        syncIfDue()
    }

    companion object {
        /**
         * Single-source selection preference, highest priority first (package-name substrings).
         * The first priority source that actually wrote steps in the window wins; only if none of
         * them are present do we fall back to the source with the most steps (normally the phone's
         * own pedometer). Garmin (watch) is preferred as the most reliable continuous step source;
         * the phone is the fallback for when the watch isn't worn or isn't syncing.
         *   Garmin Connect = com.garmin.android.apps.connectmobile
         */
        val SOURCE_PRIORITY = listOf("garmin")

        /** Pick the step source: highest-priority present-with-data, else dominant-by-total ("phone"). */
        internal fun chooseSource(perSourceTotal: Map<String, Long>): String? {
            val withData = perSourceTotal.filterValues { it > 0 }
            if (withData.isEmpty()) return null
            for (pref in SOURCE_PRIORITY) {
                withData.keys
                    .filter { it.contains(pref, ignoreCase = true) }
                    .maxByOrNull { withData.getValue(it) }
                    ?.let { return it }
            }
            return withData.maxByOrNull { it.value }!!.key   // fallback: dominant source = the phone
        }
    }
}
