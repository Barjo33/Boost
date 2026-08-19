package app.aaps.plugins.aps.openAPSBoost

import kotlin.math.max
import kotlin.math.min

/**
 * Silent shadow of a volume-weighted total-daily-dose blend.
 *
 * The shipped blend projects a day from the last eight hours by multiplying by three, which is
 * exact only if any eight hours holds a third of the day's insulin. Measured across nine
 * participants the share runs from about half the daily rate in the small hours to 1.4 times in
 * the afternoon, so the term reads the clock as much as the person: its downward trigger fires on
 * 60 to 97 per cent of small-hours cycles and 0 to 16 per cent of afternoon ones.
 *
 * This computes an alternative and logs it. Half the seven-day average, and half a projection of
 * today: the insulin delivered since the day's quiet anchor, read against the share of a day the
 * participant's own delivery curve says has passed by now.
 *
 * IT DOES NOT DOSE. The candidate was evaluated against four pre-registered targets and failed one
 * of them, detection lag across daily shifts, so it is not a replacement and is not offered as
 * one. It is here to accumulate the on-device curve and the paired estimates a within-person trial
 * would need, which is the only route by which it could become a dosing change.
 *
 * The curve is learned by observation rather than by querying history. Each cycle records the
 * cumulative share delivered by the current half hour; at the day boundary that day is normalised
 * and folded into the persisted curve, so the cost is constant per cycle and no historical
 * recomputation is needed. Until a participant has days of their own, the population shape carries
 * it, and it is shrunk out as their own days accumulate.
 *
 * State persisted as JSON:
 *   curve      48 cumulative fractions, one per half hour from the anchor
 *   curveDays  how many of the participant's own days the curve rests on
 *   anchorHour the participant's quiet hour, where the day is cut
 *   dayStartMs the anchor instant of the day in progress
 *   dayBuckets the cumulative delivery observed in the day so far, by half hour
 *   prevTotal  the previous day's completed total
 */
class BoostVwaTddShadow(
    private val loadState: () -> String,
    private val saveState: (String) -> Unit,
    private val logInfo: (String) -> Unit = {},
) {

    companion object {

        const val BUCKETS = 48                     // half hours in a day
        const val SHRINK_DAYS = 10.0               // days at which own and population weigh equally
        const val FRACTION_FLOOR = 0.10            // below this a projection divides by too little
        const val SANITY_LO = 0.5
        const val SANITY_HI = 2.0
        const val WEIGHT = 0.5                     // the projection's share of the blend
        const val MIN_DAY_UNITS = 1.0              // a day below this is not evidence of a shape

        /**
         * Population delivery curve, cumulative fraction by half hour from the quiet anchor,
         * measured over nine participants and 178 days each. Seeds a participant who has no
         * days of their own and is shrunk out as they accumulate.
         */
        val POPULATION_CURVE = doubleArrayOf(
            0.0000, 0.0059, 0.0135, 0.0245, 0.0346, 0.0435, 0.0556, 0.0655,
            0.0811, 0.1002, 0.1188, 0.1423, 0.1654, 0.1836, 0.2076, 0.2312,
            0.2592, 0.2869, 0.3153, 0.3368, 0.3624, 0.3924, 0.4173, 0.4410,
            0.4679, 0.4929, 0.5345, 0.5572, 0.5891, 0.6060, 0.6372, 0.6615,
            0.6839, 0.7083, 0.7320, 0.7603, 0.7899, 0.8199, 0.8536, 0.8731,
            0.8968, 0.9172, 0.9396, 0.9549, 0.9693, 0.9807, 0.9916, 1.0000
        )

        const val DEFAULT_ANCHOR_HOUR = 3          // the cohort's quiet hour, until one is learned
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val BUCKET_MS = DAY_MS / BUCKETS
    }

    data class Result(
        /** The blend this shadow proposes. Logged; never dosed. */
        val vwaBlend: Double,
        /** The projection of today's total on its own, before blending with the seven-day term. */
        val projection: Double,
        /** Share of the day the curve says has passed, so a reader can see why a cycle abstained. */
        val dayFraction: Double,
        /** Units delivered since the day's anchor. */
        val deliveredToday: Double,
        /** How many of the participant's own days the curve rests on. */
        val curveDays: Int,
        /** True where too little of the day has passed and the previous day carried the estimate. */
        val usedPreviousDay: Boolean,
        val debugLine: String,
    )

    private var curve: DoubleArray = POPULATION_CURVE.copyOf()
    private var curveDays: Int = 0
    private var anchorHour: Int = DEFAULT_ANCHOR_HOUR
    private var dayStartMs: Long = 0L
    private var dayBuckets: DoubleArray = DoubleArray(BUCKETS)
    private var prevTotal: Double = 0.0
    private var loaded = false

    /**
     * @param nowMs             wall clock for this cycle
     * @param deliveredSinceDayStart  insulin delivered since the day's anchor, in units
     * @param tdd7D             the seven-day average the shipped blend already computes
     * @return null when the inputs cannot support an estimate, which the caller logs as an
     *         abstention rather than substituting a value
     */
    fun compute(nowMs: Long, deliveredSinceDayStart: Double?, tdd7D: Double?): Result? {
        if (deliveredSinceDayStart == null || tdd7D == null || tdd7D <= 0.0) return null
        if (deliveredSinceDayStart < 0.0) return null
        ensureLoaded()

        rollDayIfNeeded(nowMs)
        val elapsed = nowMs - dayStartMs
        val bucket = min(BUCKETS - 1, max(0, (elapsed / BUCKET_MS).toInt()))
        dayBuckets[bucket] = max(dayBuckets[bucket], deliveredSinceDayStart)

        val fraction = curve[bucket]
        val usedPrev: Boolean
        val projection: Double
        if (fraction >= FRACTION_FLOOR) {
            projection = deliveredSinceDayStart / fraction
            usedPrev = false
        } else if (prevTotal > 0.0) {
            // Too little of the day has passed to divide by. Reverting to the seven-day term
            // here would discard what yesterday established at every anchor, and a day that ran
            // heavy is the best evidence available about the one starting.
            projection = prevTotal
            usedPrev = true
        } else {
            projection = tdd7D
            usedPrev = true
        }

        val bounded = min(max(projection, SANITY_LO * tdd7D), SANITY_HI * tdd7D)
        val blend = (1.0 - WEIGHT) * tdd7D + WEIGHT * bounded
        persist()

        val line = "VwaTdd: day=${fmt(fraction)} deliv=${fmt(deliveredSinceDayStart)}" +
            " proj=${fmt(projection)}${if (usedPrev) "(prev)" else ""}" +
            " bounded=${fmt(bounded)} 7D=${fmt(tdd7D)} → blend=${fmt(blend)}" +
            " curveDays=$curveDays anchor=${anchorHour}h"
        logInfo(line)
        return Result(blend, projection, fraction, deliveredSinceDayStart, curveDays,
                      usedPrev, line)
    }

    private fun rollDayIfNeeded(nowMs: Long) {
        if (dayStartMs == 0L) {
            dayStartMs = anchorFor(nowMs)
            dayBuckets = DoubleArray(BUCKETS)
            return
        }
        if (nowMs - dayStartMs < DAY_MS) return
        foldDayIntoCurve()
        dayStartMs = anchorFor(nowMs)
        dayBuckets = DoubleArray(BUCKETS)
    }

    /** Normalise the completed day and shrink it into the curve by how many days it rests on. */
    private fun foldDayIntoCurve() {
        // The day's total is the largest cumulative figure seen, not the last bucket's. A cycle
        // need not land in the final half hour, and requiring one meant the curve never learned.
        var total = 0.0
        for (v in dayBuckets) total = max(total, v)
        if (total < MIN_DAY_UNITS) return
        val observed = DoubleArray(BUCKETS)
        var running = 0.0
        for (i in 0 until BUCKETS) {
            running = max(running, dayBuckets[i])          // the series is cumulative already
            observed[i] = (running / total).coerceIn(0.0, 1.0)
        }
        val n = curveDays + 1
        val w = n / (n + SHRINK_DAYS)
        var prev = 0.0
        for (i in 0 until BUCKETS) {
            val blended = w * observed[i] + (1.0 - w) * curve[i]
            prev = max(prev, blended)                       // a cumulative curve cannot fall
            curve[i] = prev.coerceIn(0.0, 1.0)
        }
        val last = curve[BUCKETS - 1]
        if (last > 0.0) for (i in 0 until BUCKETS) curve[i] = curve[i] / last
        curveDays = n
        prevTotal = total
    }

    private fun anchorFor(nowMs: Long): Long {
        val dayIndex = Math.floorDiv(nowMs - anchorHour * 60L * 60 * 1000, DAY_MS)
        return dayIndex * DAY_MS + anchorHour * 60L * 60 * 1000
    }

    private fun fmt(v: Double) = String.format("%.2f", v)

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            val raw = loadState()
            if (raw.isBlank()) return
            val o = org.json.JSONObject(raw)
            o.optJSONArray("curve")?.let { arr ->
                if (arr.length() == BUCKETS) {
                    for (i in 0 until BUCKETS) curve[i] = arr.optDouble(i, curve[i])
                }
            }
            curveDays = o.optInt("curveDays", 0)
            anchorHour = o.optInt("anchorHour", DEFAULT_ANCHOR_HOUR)
            dayStartMs = o.optLong("dayStartMs", 0L)
            prevTotal = o.optDouble("prevTotal", 0.0)
            o.optJSONArray("dayBuckets")?.let { arr ->
                if (arr.length() == BUCKETS) {
                    for (i in 0 until BUCKETS) dayBuckets[i] = arr.optDouble(i, 0.0)
                }
            }
        } catch (e: Exception) {
            logInfo("VwaTdd: state load failed (${e.message}); starting from the population curve")
        }
    }

    private fun persist() {
        try {
            val o = org.json.JSONObject()
                .put("curve", org.json.JSONArray(curve.toList()))
                .put("curveDays", curveDays)
                .put("anchorHour", anchorHour)
                .put("dayStartMs", dayStartMs)
                .put("prevTotal", prevTotal)
                .put("dayBuckets", org.json.JSONArray(dayBuckets.toList()))
            saveState(o.toString())
        } catch (e: Exception) {
            logInfo("VwaTdd: state persist failed (${e.message})")
        }
    }

    /** The instant the current day was cut at, so a caller can ask how much of it has passed. */
    fun dayAnchorMs(nowMs: Long): Long {
        ensureLoaded()
        return anchorFor(nowMs)
    }

    /** Testing seam: the curve currently in force. */
    fun curveSnapshot(): DoubleArray = curve.copyOf()
}
