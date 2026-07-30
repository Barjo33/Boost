package app.aaps.core.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class LongNonKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    LocalProfileLastChange("local_profile_last_change", 0L),
    BtWatchdogLastBark("bt_watchdog_last", 0L),
    ActivePumpChangeTimestamp("active_pump_change_timestamp", 0L),
    LastCleanupRun("last_cleanup_run", 0L),

    // Health Connect HR ingest — high-water mark for incremental polling (epoch ms)
    ApsBoostHealthConnectLastSyncMs("boost_health_connect_last_sync_ms", 0L),

    // Install-time history-gap backfill (2026-07-30, see BoostHistorySync) — when the last request
    // was made. Enforces BoostHistorySync.RETRY_COOLDOWN_MS so a thin-history install cannot ask
    // NSClient for a re-download on every 5-minute loop cycle.
    ApsBoostHistorySyncLastAttemptMs("boost_history_sync_last_attempt_ms", 0L),
}

