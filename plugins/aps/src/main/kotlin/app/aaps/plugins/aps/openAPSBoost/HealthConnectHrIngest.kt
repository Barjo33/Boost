package app.aaps.plugins.aps.openAPSBoost

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.aaps.core.data.model.HR
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HealthConnectHrIngest — reads HeartRateRecord from Android Health Connect on a polling
 * cadence and persists into AAPS's HR table via PersistenceLayer.insertOrUpdateHeartRate.
 *
 * Designed to bridge the Garmin overnight gap: Garmin Connect on the phone keeps syncing
 * HR to Health Connect even when the watch's Connect IQ data field isn't actively
 * pulling glucose. This service taps that stream so Boost sleep detection sees continuous
 * HR throughout the night.
 *
 * Polling strategy:
 *   - Caller invokes [syncIfDue] every Boost cycle (~5 min)
 *   - Internal throttle skips runs within [pollIntervalMin] (default 5 min, settable
 *     via IntKey.ApsBoostHealthConnectPollMin) of the last successful sync
 *   - Each run requests records from [lastSyncedMs] onwards, dedupes against existing
 *     timestamps (PersistenceLayer's insertOrUpdate is idempotent on (timestamp,device)
 *     in practice — we set a stable timestamp + duration from the HC record)
 *
 * Failsafe behaviour:
 *   - If Health Connect SDK isn't available on this device, [isAvailable] is false and
 *     [syncIfDue] is a no-op
 *   - If the user has not granted READ_HEART_RATE, reads return empty silently — logged
 *     once per app start
 *   - Coroutine errors are caught, logged, and never propagate to the calling plugin cycle
 */
@Singleton
class HealthConnectHrIngest @Inject constructor(
    private val context: Context,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val disposable = CompositeDisposable()

    @Volatile private var client: HealthConnectClient? = null
    @Volatile private var inFlight = false
    @Volatile private var lastSyncRunMs: Long = 0L
    @Volatile private var permissionWarned = false

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun getOrInitClient(): HealthConnectClient? {
        client?.let { return it }
        if (!isAvailable) return null
        return try {
            val c = HealthConnectClient.getOrCreate(context)
            client = c
            c
        } catch (t: Throwable) {
            aapsLogger.error(LTag.APS, "HealthConnectHrIngest: client init failed: ${t.message}")
            null
        }
    }

    /**
     * Invoke from the Boost plugin's invoke() each cycle. Cheap when not due; spawns an
     * async sync when due. Never throws.
     */
    fun syncIfDue() {
        if (!preferences.get(BooleanKey.ApsBoostHealthConnectHrEnabled)) return
        val intervalMs = preferences.get(IntKey.ApsBoostHealthConnectPollMin).coerceAtLeast(1) * 60_000L
        val now = System.currentTimeMillis()
        if (now - lastSyncRunMs < intervalMs) return
        if (inFlight) return
        val hc = getOrInitClient() ?: return
        inFlight = true
        lastSyncRunMs = now
        scope.launch {
            try {
                syncOnce(hc, now)
            } catch (t: Throwable) {
                aapsLogger.error(LTag.APS, "HealthConnectHrIngest: sync failed: ${t.message}")
            } finally {
                inFlight = false
            }
        }
    }

    /**
     * Read all HeartRateRecord samples since the persisted [LongNonKey.ApsBoostHealthConnectLastSyncMs]
     * (or the last hour on first run), persist via PersistenceLayer, advance the marker.
     */
    private suspend fun syncOnce(hc: HealthConnectClient, nowMs: Long) {
        // Check permission once and remember the answer for this session
        val granted = try {
            val grantedPerms = hc.permissionController.getGrantedPermissions()
            HealthPermission.getReadPermission(HeartRateRecord::class) in grantedPerms
        } catch (t: Throwable) {
            aapsLogger.error(LTag.APS, "HealthConnectHrIngest: permission check failed: ${t.message}")
            false
        }
        if (!granted) {
            if (!permissionWarned) {
                aapsLogger.warn(LTag.APS, "HealthConnectHrIngest: READ_HEART_RATE not granted — open AAPS settings → Boost → HR sources → Health Connect to grant.")
                permissionWarned = true
            }
            return
        }

        val lastSyncedMs = preferences.get(LongNonKey.ApsBoostHealthConnectLastSyncMs)
        // First run: pull last 60 min so the first ingest doesn't dump 24h of data
        val sinceMs = if (lastSyncedMs == 0L) nowMs - 60 * 60_000L else lastSyncedMs

        val request = ReadRecordsRequest(
            recordType = HeartRateRecord::class,
            timeRangeFilter = TimeRangeFilter.between(
                Instant.ofEpochMilli(sinceMs),
                Instant.ofEpochMilli(nowMs)
            )
        )
        val resp = hc.readRecords(request)
        if (resp.records.isEmpty()) {
            aapsLogger.debug(LTag.APS, "HealthConnectHrIngest: no new HR records in [${sinceMs}..${nowMs}]")
            preferences.put(LongNonKey.ApsBoostHealthConnectLastSyncMs, nowMs)
            return
        }

        var maxSampleMs = sinceMs
        var inserted = 0
        // HeartRateRecord groups multiple samples per record (a "session" of HR ticks).
        // Each sample has a time and a beats-per-minute. Convert each into an HR row.
        for (record in resp.records) {
            val device = record.metadata.device?.let { d ->
                listOfNotNull(d.manufacturer, d.model).joinToString(" ").trim().ifEmpty { "HealthConnect" }
            } ?: "HealthConnect"
            for (sample in record.samples) {
                val sampleMs = sample.time.toEpochMilli()
                if (sampleMs <= sinceMs) continue
                val hr = HR(
                    timestamp = sampleMs,
                    duration = 60_000L,                       // HC samples are point-in-time; treat as 1-min weight
                    beatsPerMinute = sample.beatsPerMinute.toDouble(),
                    device = device,
                    isValid = true
                )
                disposable += persistenceLayer.insertOrUpdateHeartRate(hr).subscribe(
                    { inserted++ },
                    { e -> aapsLogger.warn(LTag.APS, "HealthConnectHrIngest: persist failed: ${e.message}") }
                )
                if (sampleMs > maxSampleMs) maxSampleMs = sampleMs
            }
        }
        preferences.put(LongNonKey.ApsBoostHealthConnectLastSyncMs, maxSampleMs)
        aapsLogger.info(LTag.APS, "HealthConnectHrIngest: ingested up to ${maxSampleMs} ms (queued $inserted, records ${resp.records.size})")
    }

    /** Force a sync regardless of throttle — e.g. from a settings "test now" button. Returns immediately; result via logs. */
    fun forceSync() {
        lastSyncRunMs = 0L
        syncIfDue()
    }

    /** Synchronous one-shot check primarily for diagnostics. */
    fun isPermissionGranted(): Boolean = try {
        val hc = getOrInitClient() ?: return false
        runBlocking {
            val grantedPerms = hc.permissionController.getGrantedPermissions()
            HealthPermission.getReadPermission(HeartRateRecord::class) in grantedPerms
        }
    } catch (t: Throwable) { false }
}
