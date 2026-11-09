package app.aaps.plugins.aps.openAPSBoostV5

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The insulin-need watch (breadcrumb token ndw=). A daily check for a change in how much insulin the person is taking: the mean total daily dose of
 * the last [WINDOW_DAYS] complete days against the [BASELINE_DAYS] before them, flagged when it lies
 * more than [K_SD] standard deviations of single days from that month's mean.
 *
 * It reports and never doses. Insulin sensitivity is not identifiable from these records, because
 * food the loop is not told about absorbs any change in how well insulin works, so the notification
 * describes the change in dose and glucose and leaves the cause to the person.
 *
 * Measured offline over 45 users and 518 person-months, and 802 people and 6,862 person-months of the
 * JAEB Loop study, against sustained shifts of 15% or more. In the JAEB data it caught 63% of upward
 * shifts (60 to 65) and 41% of downward (38 to 44) at a median 9 days after the shift's start, and 54%
 * of upward notices fell within a shift against 13% of random days. The window and threshold were chosen
 * from a grid, which is why they were rerun on shifts of 10% and 20%. A notice's change was about half
 * still there the following week (42% to 53% across data sets and directions).
 *
 * Days are TddCalculator's: 24 hours from each local midnight, so they include profile basal (on a
 * clock-change day the hour lost or repeated is about 4% of a day, well inside the band). A day under
 * [GAP_FRACTION] of the median of the span is a gap in the record, not a dose. A change in the
 * profile's daily basal of more than [PROFILE_CHANGE] within the span would move the dose by itself,
 * whether it was a deliberate change or a new insulin concentration, so it is reported as that and
 * not as a change in need.
 */
object InsulinNeedWatch {

    const val WINDOW_DAYS = 5
    const val BASELINE_DAYS = 30
    const val MIN_BASELINE_DAYS = 20
    const val K_SD = 1.5
    const val GAP_FRACTION = 0.25
    const val PROFILE_CHANGE = 0.15
    const val COOLDOWN_DAYS = 14L

    /** One complete local day: its total daily dose and the profile's daily basal in force at its middle. */
    data class Day(val startMs: Long, val tddU: Double, val profileBasalU: Double?)

    enum class Status { FEW_DAYS, PROFILE_CHANGED, WITHIN, ABOVE, BELOW }

    data class Result(
        val status: Status,
        val recentMeanU: Double = Double.NaN,
        val baselineMeanU: Double = Double.NaN,
        val baselineSdU: Double = Double.NaN,
        val baselineDays: Int = 0,
        val windowStartMs: Long = 0L,
        val baselineStartMs: Long = 0L,
    ) {
        val z: Double get() = if (baselineSdU > 0) (recentMeanU - baselineMeanU) / baselineSdU else Double.NaN
        val percent: Double get() = if (baselineMeanU > 0) 100.0 * (recentMeanU - baselineMeanU) / baselineMeanU else Double.NaN

        /** Breadcrumb for the auto-config summary, e.g. ndw=ABOVE/z=2.3/p=+31/n=30. */
        fun token(): String = when (status) {
            Status.FEW_DAYS        -> "ndw=FEW_DAYS/n=$baselineDays"
            Status.PROFILE_CHANGED -> "ndw=PROFILE_CHANGED"
            else                   -> "ndw=${status.name}/z=${fmt1(z)}/p=${signed(percent)}/n=$baselineDays"
        }
    }

    /**
     * [days] are complete days, oldest first. Missing days may simply be absent. [lastDayStartMs] is
     * the start of yesterday, so that a missing yesterday leaves the window short rather than moving
     * it back a day. Returns the status of the [WINDOW_DAYS] days ending yesterday against the
     * [BASELINE_DAYS] before them.
     */
    fun evaluate(days: List<Day>, lastDayStartMs: Long = days.lastOrNull()?.startMs ?: 0L): Result {
        if (days.size < WINDOW_DAYS) return Result(Status.FEW_DAYS)
        val dayMs = 24L * 60 * 60 * 1000
        val lastStart = lastDayStartMs
        val windowStart = lastStart - (WINDOW_DAYS - 1) * dayMs
        val baselineStart = windowStart - BASELINE_DAYS * dayMs
        val span = days.filter { it.startMs >= baselineStart - dayMs / 2 }
        val median = percentile(span.map { it.tddU }.filter { it > 0 }, 50.0)
        val usable = span.filter { it.tddU > 0 && it.tddU >= GAP_FRACTION * median }
        // Window days are matched to their expected start within half a day, which absorbs DST.
        val window = usable.filter { it.startMs > windowStart - dayMs / 2 }
        val baseline = usable.filter { it.startMs <= windowStart - dayMs / 2 }
        if (window.size < WINDOW_DAYS || baseline.size < MIN_BASELINE_DAYS)
            return Result(Status.FEW_DAYS, baselineDays = baseline.size)

        val basals = span.mapNotNull { it.profileBasalU }.filter { it > 0 }
        if (basals.isNotEmpty()) {
            val ref = percentile(baseline.mapNotNull { it.profileBasalU }.filter { it > 0 }.ifEmpty { basals }, 50.0)
            if (span.any { (it.profileBasalU ?: ref) > 0 && abs((it.profileBasalU ?: ref) - ref) > PROFILE_CHANGE * ref })
                return Result(Status.PROFILE_CHANGED, baselineDays = baseline.size)
        }

        val recent = window.map { it.tddU }.average()
        val mean = baseline.map { it.tddU }.average()
        val sd = sqrt(baseline.sumOf { (it.tddU - mean) * (it.tddU - mean) } / (baseline.size - 1))
        val status = when {
            sd <= 0.0                  -> Status.WITHIN
            recent - mean > K_SD * sd  -> Status.ABOVE
            mean - recent > K_SD * sd  -> Status.BELOW
            else                       -> Status.WITHIN
        }
        return Result(status, recent, mean, sd, baseline.size, windowStart, baselineStart)
    }

    // The night watch (breadcrumb token ndn=), shadow only: it is logged and never notifies.
    // Insulin from 00:00 to 06:00 over the last NIGHT_WINDOW nights against the NIGHT_BASELINE before,
    // flagged when it is more than NIGHT_K_SD SDs of single nights above that mean and every window
    // night's mean glucose is above the median of the baseline nights. Measured offline, with 2.05 SD
    // chosen to match the whole-day watch's false-notice rate: on JAEB Loop the next week's nights
    // stayed 7.3 mg/dL (6.2 to 8.3) higher after a notice. Excluding or clipping nights did not
    // improve it.
    const val NIGHT_WINDOW = 3
    const val NIGHT_BASELINE = 14
    const val NIGHT_MIN_BASELINE = 10
    const val NIGHT_K_SD = 2.05
    const val NIGHT_HOURS = 6
    /** Five-minute slots with a glucose reading needed for a night's mean, of 72 (as offline). */
    const val NIGHT_MIN_BG_SLOTS = 58

    /** One night: its insulin and mean glucose, null when the record is missing or too thin. */
    data class Night(val startMs: Long, val insulinU: Double?, val meanBgMgdl: Double?)

    data class NightResult(
        val status: Status,
        val recentMeanU: Double = Double.NaN,
        val baselineMeanU: Double = Double.NaN,
        val baselineSdU: Double = Double.NaN,
        val baselineNights: Int = 0,
        val glucoseAbove: Boolean = false,
    ) {
        val z: Double get() = if (baselineSdU > 0) (recentMeanU - baselineMeanU) / baselineSdU else Double.NaN
        val percent: Double get() = if (baselineMeanU > 0) 100.0 * (recentMeanU - baselineMeanU) / baselineMeanU else Double.NaN

        /** e.g. ndn=ABOVE/z=2.3/p=+31/g=1/n=14. z and g are kept on WITHIN so the shadow can be rescored offline. */
        fun token(): String = when (status) {
            Status.FEW_DAYS        -> "ndn=FEW_DAYS/n=$baselineNights"
            Status.PROFILE_CHANGED -> "ndn=PROFILE_CHANGED"
            else                   -> "ndn=${status.name}/z=${fmt1(z)}/p=${signed(percent)}/g=${if (glucoseAbove) 1 else 0}/n=$baselineNights"
        }
    }

    /**
     * A night's mean glucose from native-cadence readings (time, mg/dL), or null under
     * [NIGHT_MIN_BG_SLOTS] five-minute slots with a reading, so that a one-minute feed needs the same
     * coverage as a five-minute one. Readings below 39 mg/dL are sensor errors and are dropped.
     */
    fun nightMeanBg(readings: List<Pair<Long, Double>>, startMs: Long): Double? {
        val ok = readings.filter { it.second >= 39.0 && it.first >= startMs }
        val slots = ok.map { (it.first - startMs) / (5L * 60 * 1000) }.distinct().size
        return if (slots >= NIGHT_MIN_BG_SLOTS) ok.map { it.second }.average() else null
    }

    /**
     * [nights] are the last [NIGHT_WINDOW] + [NIGHT_BASELINE] nights, oldest first, one entry per
     * night with nulls for missing data. [profileChanged] is the whole-day watch's PROFILE_CHANGED,
     * since a new basal profile moves night insulin by itself. Upward only: the rule is for rising need.
     */
    fun evaluateNights(nights: List<Night>, profileChanged: Boolean = false): NightResult {
        if (nights.size < NIGHT_WINDOW + NIGHT_MIN_BASELINE) return NightResult(Status.FEW_DAYS)
        val window = nights.takeLast(NIGHT_WINDOW)
        val baseline = nights.dropLast(NIGHT_WINDOW).takeLast(NIGHT_BASELINE)
        val base = baseline.mapNotNull { it.insulinU }
        if (window.any { it.insulinU == null } || base.size < NIGHT_MIN_BASELINE)
            return NightResult(Status.FEW_DAYS, baselineNights = base.size)
        if (profileChanged) return NightResult(Status.PROFILE_CHANGED, baselineNights = base.size)
        val recent = window.map { it.insulinU!! }.average()
        val mean = base.average()
        val sd = sqrt(base.sumOf { (it - mean) * (it - mean) } / (base.size - 1))
        val baseBg = baseline.mapNotNull { it.meanBgMgdl }
        val windowBg = window.map { it.meanBgMgdl }
        val glucoseAbove = baseBg.size >= NIGHT_MIN_BASELINE && windowBg.all { it != null } &&
            percentile(baseBg, 50.0).let { med -> windowBg.all { it!! > med } }
        val status = if (sd > 0 && recent - mean > NIGHT_K_SD * sd && glucoseAbove) Status.ABOVE else Status.WITHIN
        return NightResult(status, recent, mean, sd, base.size, glucoseAbove)
    }

    /**
     * The profile's own daily basal from an effective profile switch. Its stored blocks already carry
     * the switch's percentage, so a temporary switch (an exercise 70% or a sick-day 120%) is divided
     * back out: it is a short-term choice, not a new profile. A permanent percentage is kept, since it
     * changes the dose the person takes from then on.
     */
    fun profileDailyBasal(storedBasalSumU: Double, originalPercentage: Int, originalDurationMs: Long): Double =
        if (originalDurationMs > 0 && originalPercentage > 0) storedBasalSumU * 100.0 / originalPercentage else storedBasalSumU

    /** Whether to notify: the status is outside the band and the same direction has not been notified within [COOLDOWN_DAYS]. */
    fun shouldNotify(result: Result, lastAboveMs: Long, lastBelowMs: Long, now: Long): Boolean {
        val cooldownMs = COOLDOWN_DAYS * 24L * 60 * 60 * 1000
        return when (result.status) {
            Status.ABOVE -> lastAboveMs == 0L || now - lastAboveMs >= cooldownMs
            Status.BELOW -> lastBelowMs == 0L || now - lastBelowMs >= cooldownMs
            else         -> false
        }
    }

    private fun percentile(xs: List<Double>, p: Double): Double {
        if (xs.isEmpty()) return 0.0
        val s = xs.sorted()
        val r = p / 100.0 * (s.size - 1)
        val lo = r.toInt()
        val hi = minOf(lo + 1, s.size - 1)
        return s[lo] + (s[hi] - s[lo]) * (r - lo)
    }

    private fun fmt1(x: Double) = if (x.isNaN()) "NA" else "%.1f".format(java.util.Locale.ROOT, x)
    private fun signed(x: Double) = if (x.isNaN()) "NA" else "%+.0f".format(java.util.Locale.ROOT, x)
}
