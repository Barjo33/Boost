package app.aaps.plugins.aps.openAPSBoost

import org.json.JSONArray
import org.json.JSONObject

/**
 * DailyStepHistoryTracker — Boost activity-load SHADOW (2026-06-16).
 *
 * Rolling per-day step history (single-source — see [HealthConnectStepsIngest]) → personal
 * baseline (median) → deviation-based shadow factors:
 *   - **activity-load** (recent volume ABOVE baseline) → would-RAISE ISF (more sensitive),
 *     front-loaded over ~24–48h. The post-exercise sensitivity window (lit: 24–48h glycogen
 *     resynthesis).
 *   - **inactivity** (recent volume BELOW baseline) → would-LOWER ISF (more insulin). Smaller +
 *     more conservative because add-insulin is the unsafe direction.
 *
 * SHADOW ONLY: the plugin LOGS [ShadowFactors] to NS; nothing applies them to dosing. Governing
 * principle ([[boost_activity_load_sensitivity_design]]): learn personal baseline, act on
 * deviation; clinical absolutes stay fixed. For a deviation ratio, single-source CONSISTENCY
 * matters more than absolute step accuracy.
 */
object DailyStepHistoryTracker {

    const val WINDOW_DAYS = 28L
    private const val DAY_MS = 86_400_000L
    const val MIN_DAYS_FOR_BASELINE = 7          // cold-start guard — no factor until enough normal days
    const val ACTIVITY_MAX_ISF_PCT = 15.0        // cap on would-raise-ISF at extreme excess (walking-modest)
    const val INACTIVITY_MAX_ISF_PCT = 8.0       // smaller — add-insulin (unsafe) direction
    const val ACTIVITY_RATIO_FULL = 2.0          // ratio ≥ this saturates the activity factor (2× baseline)
    const val INACTIVITY_RATIO_FULL = 0.4        // ratio ≤ this saturates the inactivity factor (≤40%)

    /** Local epoch-day index from a UTC instant + local offset (so day boundaries are the user's). */
    fun dayIndex(utcMs: Long, localOffsetMs: Long): Long = (utcMs + localOffsetMs) / DAY_MS

    // ── Intraday activity-load (2026-06-19, SHADOW). Acute same-day sensitivity: today's cumulative
    // steps vs the user's typical pace by this hour. Complements the next-day (24-48h) factor above.
    // Fraction of a day's steps typically completed by the END of each local hour (generic diurnal
    // curve; not personalised in v1). Index = hour 0..23. ~0 overnight, rising through the day.
    private val DIURNAL_FRACTION = doubleArrayOf(
        0.00, 0.00, 0.00, 0.00, 0.00, 0.01, 0.03, 0.07, 0.13, 0.20, 0.28, 0.36,
        0.44, 0.52, 0.59, 0.66, 0.73, 0.80, 0.86, 0.91, 0.95, 0.98, 0.99, 1.00
    )

    data class IntradayFactor(val ratio: Double?, val wouldDeltaIsfPct: Double, val expectedByNow: Int)

    /**
     * Intraday "running hot?" factor: today's cumulative [stepsToday] vs expected-by-now
     * ([baseline] × the diurnal fraction for [hour]). RAISE-ONLY (acute exercise → more sensitive →
     * +ISF), capped at [ACTIVITY_MAX_ISF_PCT]; below-pace returns 0 (the next-day factor owns the
     * lower-activity direction). Null baseline → no factor. ~6 ops; no I/O.
     */
    fun intradayFactor(stepsToday: Int, baseline: Int?, hour: Int): IntradayFactor {
        if (baseline == null || baseline <= 0) return IntradayFactor(null, 0.0, 0)
        val frac = DIURNAL_FRACTION[hour.coerceIn(0, 23)].coerceAtLeast(0.02)
        val expected = baseline * frac
        val ratio = stepsToday / expected
        val pct = (((ratio - 1.0) / (ACTIVITY_RATIO_FULL - 1.0)).coerceIn(0.0, 1.0)) * ACTIVITY_MAX_ISF_PCT
        return IntradayFactor(ratio, pct, expected.toInt())
    }

    /** One completed local day's step total from the chosen single source. */
    data class DailyTotal(val dayIndex: Long, val steps: Int, val source: String)

    data class History(var days: MutableMap<Long, DailyTotal> = linkedMapOf()) {
        fun serialize(): String {
            val arr = JSONArray()
            for (d in days.values) arr.put(JSONObject().put("d", d.dayIndex).put("s", d.steps).put("src", d.source))
            return JSONObject().put("days", arr).toString()
        }

        companion object {
            fun deserialize(raw: String): History {
                if (raw.isBlank()) return History()
                return try {
                    val arr = JSONObject(raw).optJSONArray("days") ?: JSONArray()
                    val m = linkedMapOf<Long, DailyTotal>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val d = o.getLong("d")
                        m[d] = DailyTotal(d, o.getInt("s"), o.optString("src", ""))
                    }
                    History(m)
                } catch (e: Exception) {
                    History()
                }
            }
        }
    }

    /**
     * Merge newly-read **completed**-day totals (dayIndex < [todayIndex]; today is partial and
     * excluded) and trim to the rolling window. Returns a new History (caller persists on change).
     */
    fun merge(h: History, totals: List<DailyTotal>, todayIndex: Long): History {
        val m = LinkedHashMap(h.days)
        for (t in totals) if (t.dayIndex in (todayIndex - WINDOW_DAYS) until todayIndex) m[t.dayIndex] = t
        val cutoff = todayIndex - WINDOW_DAYS
        m.keys.filter { it < cutoff }.toList().forEach { m.remove(it) }
        return History(m)
    }

    /**
     * Median daily steps over completed days, EXCLUDING the most recent [excludeRecent] days (so a
     * high/low recent stretch doesn't contaminate the baseline it's being measured against).
     * Returns null until [MIN_DAYS_FOR_BASELINE] qualifying days exist.
     */
    fun baseline(h: History, todayIndex: Long, excludeRecent: Int = 2): Int? {
        val vals = h.days.values.filter { it.dayIndex < todayIndex - excludeRecent }.map { it.steps }.sorted()
        if (vals.size < MIN_DAYS_FOR_BASELINE) return null
        return vals[vals.size / 2]
    }

    data class ShadowFactors(
        val baselineSteps: Int?,
        val lastDaySteps: Int?,
        val ratio: Double?,            // decay-weighted recent load ÷ baseline
        val wouldDeltaIsfPct: Double,  // signed: + = raise ISF (activity), − = lower ISF (inactivity); 0 if no data
        val note: String
    )

    /**
     * Shadow factor for [todayIndex] from the completed days before it. Recent load = decay-weighted
     * excess of the last two completed days (yesterday weight 1.0, day-before 0.5 → front-loaded,
     * ~24–48h). Above baseline → +ISF%, below → −ISF% (smaller). Never applied — logged only.
     */
    fun shadowFactors(h: History, todayIndex: Long): ShadowFactors {
        val base = baseline(h, todayIndex)
        val last = h.days[todayIndex - 1]?.steps
        if (base == null || base <= 0 || last == null)
            return ShadowFactors(base, last, null, 0.0, "insufficient-history")
        val y = h.days[todayIndex - 1]?.steps?.toDouble() ?: base.toDouble()
        val y2 = h.days[todayIndex - 2]?.steps?.toDouble() ?: base.toDouble()
        val weightedLoad = (y * 1.0 + y2 * 0.5) / 1.5
        val ratio = weightedLoad / base
        val pct: Double
        val note: String
        if (ratio >= 1.0) {
            val f = ((ratio - 1.0) / (ACTIVITY_RATIO_FULL - 1.0)).coerceIn(0.0, 1.0)
            pct = ACTIVITY_MAX_ISF_PCT * f
            note = "activity-load"
        } else {
            val f = ((1.0 - ratio) / (1.0 - INACTIVITY_RATIO_FULL)).coerceIn(0.0, 1.0)
            pct = -INACTIVITY_MAX_ISF_PCT * f
            note = "inactivity"
        }
        return ShadowFactors(base, last, ratio, pct, note)
    }
}
