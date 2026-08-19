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
 * cumulative share delivered by the current five-minute bucket; at the day boundary that day is
 * normalised and folded into the persisted curve, so the cost is constant per cycle and no
 * historical recomputation is needed. Until a participant has days of their own, the population
 * shape carries it, and it is shrunk out as their own days accumulate.
 *
 * The shape is a share of a day. It is turned into expected units by scaling against the
 * participant's own seven-day dose, which is recomputed at start-up and once a day thereafter
 * rather than every cycle, since a seven-day average does not move within a day.
 *
 * State persisted as JSON:
 *   curve      288 cumulative fractions, one per five minutes from the anchor
 *   expected   the same curve expressed in units, rebuilt daily from the seven-day dose
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

        const val BUCKETS = 288                    // five-minute buckets in a day
        const val SHRINK_DAYS = 10.0               // days at which own and population weigh equally
        const val FRACTION_FLOOR = 0.10            // below this a projection divides by too little
        const val SANITY_LO = 0.5
        const val SANITY_HI = 2.0
        const val WEIGHT = 0.5                     // the projection's share of the blend
        const val MIN_DAY_UNITS = 1.0              // a day below this is not evidence of a shape

        /**
         * Population delivery curve, cumulative share of a day by five-minute bucket from the
         * quiet anchor. Built on the Mac from every participant in the record who carries both
         * boluses and basal, and checked against the wider bolus-only set of thirty-two, whose
         * shape it matches to within 0.044 in cumulative share. Seeds a participant with no days
         * of their own and is shrunk out as theirs accumulate.
         */
        val POPULATION_CURVE = doubleArrayOf(
        // 288 five-minute buckets, cumulative share of a day, from the quiet anchor
        0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00108, 0.00223, 0.00316, 0.00425, 0.00554, 0.00668, 0.00773,
        0.00922, 0.01039, 0.01163, 0.01269, 0.01405, 0.01552, 0.01664, 0.01829, 0.01978, 0.02139, 0.02264, 0.02405,
        0.02594, 0.02727, 0.02888, 0.03030, 0.03148, 0.03303, 0.03415, 0.03582, 0.03721, 0.03862, 0.04017, 0.04169,
        0.04348, 0.04491, 0.04650, 0.04800, 0.04967, 0.05136, 0.05293, 0.05451, 0.05613, 0.05788, 0.05962, 0.06191,
        0.06410, 0.06667, 0.06950, 0.07210, 0.07514, 0.07875, 0.08163, 0.08471, 0.08985, 0.09398, 0.09748, 0.10186,
        0.10643, 0.11002, 0.11370, 0.11682, 0.12091, 0.12496, 0.12862, 0.13376, 0.13769, 0.14164, 0.14615, 0.14965,
        0.15385, 0.15756, 0.16109, 0.16439, 0.16861, 0.17288, 0.17772, 0.18098, 0.18409, 0.18855, 0.19268, 0.19653,
        0.19940, 0.20358, 0.20793, 0.21026, 0.21447, 0.21810, 0.22247, 0.22642, 0.23030, 0.23474, 0.23877, 0.24189,
        0.24666, 0.25091, 0.25706, 0.26187, 0.26867, 0.27619, 0.28226, 0.28739, 0.29132, 0.29552, 0.30143, 0.30501,
        0.31177, 0.31711, 0.32122, 0.32548, 0.32864, 0.33314, 0.33637, 0.34041, 0.34554, 0.35104, 0.35511, 0.35865,
        0.36302, 0.36769, 0.37390, 0.37760, 0.38281, 0.38616, 0.39015, 0.39259, 0.39592, 0.39971, 0.40502, 0.40957,
        0.41370, 0.41899, 0.42266, 0.42684, 0.43124, 0.43616, 0.44024, 0.44387, 0.44801, 0.45298, 0.45837, 0.46448,
        0.46864, 0.47397, 0.47799, 0.48269, 0.48682, 0.49078, 0.49437, 0.49865, 0.51039, 0.51537, 0.51958, 0.52522,
        0.53371, 0.53962, 0.54341, 0.54737, 0.55085, 0.55532, 0.56014, 0.56460, 0.57008, 0.57561, 0.58040, 0.58455,
        0.58908, 0.59390, 0.59759, 0.60168, 0.60545, 0.60889, 0.61269, 0.61728, 0.62297, 0.62687, 0.63109, 0.63603,
        0.63960, 0.64315, 0.64758, 0.65268, 0.65708, 0.66060, 0.66386, 0.66790, 0.67263, 0.67738, 0.68170, 0.68532,
        0.68874, 0.69331, 0.69904, 0.70468, 0.70849, 0.71269, 0.71655, 0.72169, 0.72460, 0.72808, 0.73308, 0.73846,
        0.74316, 0.74687, 0.75235, 0.75794, 0.76216, 0.76833, 0.77308, 0.77814, 0.78319, 0.78794, 0.79181, 0.79546,
        0.80035, 0.80569, 0.81040, 0.81622, 0.82038, 0.82569, 0.83181, 0.83619, 0.84424, 0.84881, 0.85255, 0.85752,
        0.86325, 0.86979, 0.87441, 0.87811, 0.88064, 0.88393, 0.88784, 0.89106, 0.89630, 0.90147, 0.90558, 0.90980,
        0.91258, 0.91664, 0.92006, 0.92244, 0.92543, 0.92872, 0.93171, 0.93437, 0.93677, 0.93969, 0.94294, 0.94598,
        0.94886, 0.95120, 0.95361, 0.95600, 0.95824, 0.96075, 0.96310, 0.96484, 0.96697, 0.96863, 0.97070, 0.97232,
        0.97429, 0.97614, 0.97756, 0.97943, 0.98107, 0.98253, 0.98457, 0.98644, 0.98788, 0.98947, 0.99076, 0.99216,
        0.99320, 0.99430, 0.99552, 0.99655, 0.99753, 0.99868, 1.00000, 1.00000, 1.00000, 1.00000, 1.00000, 1.00000
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
        /** Units the curve expected by now, at the current calibration. */
        val expectedToday: Double,
        /** The seven-day dose the buckets are currently scaled against. */
        val calibratedTdd: Double,
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

    /** The curve expressed in units rather than shares, rebuilt when the day rolls. */
    private var expectedUnits: DoubleArray = DoubleArray(BUCKETS)
    private var calibratedTdd: Double = 0.0
    private var calibratedMs: Long = 0L

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
        calibrateIfDue(nowMs, tdd7D)
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
            " expected=${fmt(expectedUnits[bucket])}" +
            " bounded=${fmt(bounded)} 7D=${fmt(tdd7D)} → blend=${fmt(blend)}" +
            " curveDays=$curveDays anchor=${anchorHour}h"
        logInfo(line)
        return Result(blend, projection, fraction, deliveredSinceDayStart,
                      expectedUnits[bucket], calibratedTdd, curveDays, usedPrev, line)
    }

    /**
     * Turn the shape into expected units against the participant's own seven-day dose.
     *
     * Done at start-up and once a day thereafter rather than every cycle: a seven-day average
     * does not move within a day, and rebuilding 288 buckets on every pass would be work for
     * nothing. A change in the seven-day figure of more than a twentieth also triggers it, so a
     * participant whose requirement steps does not wait until the anchor to be measured against
     * the right scale.
     */
    private fun calibrateIfDue(nowMs: Long, tdd7D: Double) {
        val stale = calibratedMs == 0L || nowMs - calibratedMs >= DAY_MS
        val moved = calibratedTdd <= 0.0 ||
            kotlin.math.abs(tdd7D - calibratedTdd) / calibratedTdd > 0.05
        if (!stale && !moved) return
        for (i in 0 until BUCKETS) expectedUnits[i] = curve[i] * tdd7D
        calibratedTdd = tdd7D
        calibratedMs = nowMs
    }

    /** Units the curve expects to have been delivered by this bucket, at the current calibration. */
    fun expectedByBucket(bucket: Int): Double =
        expectedUnits[bucket.coerceIn(0, BUCKETS - 1)]

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
