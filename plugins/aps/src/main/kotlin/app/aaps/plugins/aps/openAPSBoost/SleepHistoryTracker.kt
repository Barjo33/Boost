package app.aaps.plugins.aps.openAPSBoost

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * SleepHistoryTracker — rolling 28-day record of sleep onset and wake times, with
 * circular-mean aggregation so PRE_SLEEP can fire `preSleepLeadMin` before the user's
 * *learned* habitual sleep onset (not just the configured nightStart fallback).
 *
 * Two events drive the record:
 *   AWAKE → SLEEPING       → record `sleepStartMs` (the current "session")
 *   SLEEPING → AWAKE       → record `wakeMs`, close the session, persist
 *
 * Storage shape (StringKey `ApsBoostSleepHistory`):
 *   {
 *     "sessions": [
 *       { "sleepStartMs": ..., "wakeMs": ... }, ...
 *     ],
 *     "openSleepStartMs": <ms or null>     // current session that hasn't woken yet
 *   }
 *
 * Trim policy: keep only sessions whose `sleepStartMs` is within the last 28 days.
 *
 * Aggregation:
 *   - Convert sleepStartMs and wakeMs to local clock-minute-of-day [0..1439]
 *   - Compute *circular* mean (vector sum on unit circle, atan2 back) to handle
 *     wrap-around correctly: avg of (22:00, 02:00) = 00:00, not 12:00
 *   - Return null if fewer than [minSessionsForLearned] sessions present
 */
object SleepHistoryTracker {

    private const val WINDOW_DAYS = 28L
    private const val WINDOW_MS = WINDOW_DAYS * 24L * 60L * 60L * 1000L
    private const val MIN_SESSIONS_FOR_LEARNED = 7

    /** A closed sleep session. */
    data class Session(val sleepStartMs: Long, val wakeMs: Long)

    /**
     * Carrier struct persisted to SharedPreferences.
     *
     * @param sessions          Closed sessions within rolling window
     * @param openSleepStartMs  The current session (entered SLEEPING but not yet woken). Null when no session in progress.
     */
    data class History(
        var sessions: MutableList<Session> = mutableListOf(),
        var openSleepStartMs: Long? = null
    ) {
        fun serialize(): String {
            val arr = JSONArray()
            for (s in sessions) {
                arr.put(JSONObject().put("sleepStartMs", s.sleepStartMs).put("wakeMs", s.wakeMs))
            }
            return JSONObject()
                .put("sessions", arr)
                .put("openSleepStartMs", openSleepStartMs ?: JSONObject.NULL)
                .toString()
        }

        companion object {
            fun deserialize(raw: String): History {
                if (raw.isBlank()) return History()
                return try {
                    val j = JSONObject(raw)
                    val arr = j.optJSONArray("sessions") ?: JSONArray()
                    val list = mutableListOf<Session>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(Session(o.getLong("sleepStartMs"), o.getLong("wakeMs")))
                    }
                    val openVal = j.opt("openSleepStartMs")
                    val open = if (openVal == null || openVal == JSONObject.NULL) null else (openVal as Number).toLong()
                    History(list, open)
                } catch (e: Exception) {
                    History()
                }
            }
        }
    }

    /**
     * Aggregate metrics from the rolling history.
     *
     * @param sleepStartMinAvg  Circular-mean clock-minute of sleep onset (0..1439), null if insufficient data
     * @param wakeMinAvg        Circular-mean clock-minute of wake (0..1439), null if insufficient data
     * @param sleepDurationMinAvg  Mean sleep duration in minutes, null if insufficient data
     * @param sessionCount      Number of closed sessions inside the 28-day window
     */
    data class Aggregate(
        val sleepStartMinAvg: Int?,
        val wakeMinAvg: Int?,
        val sleepDurationMinAvg: Int?,
        val sessionCount: Int
    )

    /** Called by the plugin when SleepStateDetector transitions AWAKE→SLEEPING. */
    fun onSleepStart(h: History, sleepStartMs: Long): History {
        return h.copy(openSleepStartMs = sleepStartMs)
    }

    /**
     * Called by the plugin when SleepStateDetector transitions SLEEPING→AWAKE. Closes the
     * open session, appends to history, and trims old sessions outside the rolling window.
     * Returns the updated history (caller persists).
     */
    fun onWake(h: History, wakeMs: Long): History {
        val open = h.openSleepStartMs ?: return h.copy()      // no open session; nothing to do
        val newSessions = h.sessions.toMutableList()
        newSessions.add(Session(open, wakeMs))
        // Trim sessions whose start is older than the rolling window
        val cutoff = wakeMs - WINDOW_MS
        newSessions.removeAll { it.sleepStartMs < cutoff }
        return History(newSessions, openSleepStartMs = null)
    }

    /** Compute aggregates over the rolling window. */
    fun aggregate(h: History, localOffsetMs: Long): Aggregate {
        if (h.sessions.size < MIN_SESSIONS_FOR_LEARNED) {
            return Aggregate(null, null, null, h.sessions.size)
        }
        val sleepStartMin = h.sessions.map { msToMinOfDay(it.sleepStartMs, localOffsetMs) }
        val wakeMin = h.sessions.map { msToMinOfDay(it.wakeMs, localOffsetMs) }
        val durations = h.sessions.map { ((it.wakeMs - it.sleepStartMs) / 60_000L).toInt() }
        return Aggregate(
            sleepStartMinAvg = circularMean(sleepStartMin),
            wakeMinAvg = circularMean(wakeMin),
            sleepDurationMinAvg = if (durations.isNotEmpty()) durations.sum() / durations.size else null,
            sessionCount = h.sessions.size
        )
    }

    /**
     * Convert an absolute UTC millis instant to local clock minute-of-day [0..1439].
     *
     * @param utcMs        UTC instant
     * @param localOffsetMs  Local-time offset from UTC in ms (e.g. BST = +1h = 3,600,000)
     */
    fun msToMinOfDay(utcMs: Long, localOffsetMs: Long): Int {
        val localMs = utcMs + localOffsetMs
        val msPerDay = 24L * 60L * 60L * 1000L
        val msIntoDay = ((localMs % msPerDay) + msPerDay) % msPerDay
        return (msIntoDay / 60_000L).toInt()
    }

    /**
     * Circular mean of a list of minute-of-day values, handling wrap-around correctly.
     * Returns the mean clock-minute (0..1439). Each value is mapped to an angle on the
     * unit circle, vectors summed, and the resulting angle converted back to a minute.
     *
     * Example: circularMean([1320, 1410, 60, 120]) ≈ 0 (≈ midnight), NOT 727 (12:07).
     */
    fun circularMean(minutes: List<Int>): Int? {
        if (minutes.isEmpty()) return null
        var sx = 0.0
        var sy = 0.0
        for (m in minutes) {
            val angle = 2.0 * Math.PI * m / 1440.0
            sx += cos(angle)
            sy += sin(angle)
        }
        val mean = atan2(sy, sx)
        val normalized = (mean + 2.0 * Math.PI) % (2.0 * Math.PI)
        return ((normalized / (2.0 * Math.PI)) * 1440.0).toInt() % 1440
    }
}
