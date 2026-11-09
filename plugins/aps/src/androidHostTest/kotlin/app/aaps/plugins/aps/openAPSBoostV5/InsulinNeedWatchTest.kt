package app.aaps.plugins.aps.openAPSBoostV5

import app.aaps.plugins.aps.openAPSBoostV5.InsulinNeedWatch.Day
import app.aaps.plugins.aps.openAPSBoostV5.InsulinNeedWatch.Night
import app.aaps.plugins.aps.openAPSBoostV5.InsulinNeedWatch.Status
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * The insulin-need watch. A 38-day series of daily insulin (a rescaled example, units of insulin) pins the case the rule was chosen on;
 * the rest pin the gap, profile-change and cooldown behaviour a notification depends on.
 */
class InsulinNeedWatchTest {

    private val dayMs = 24L * 60 * 60 * 1000
    private val t0 = 1_788_652_800_000L      // a midnight; only differences matter

    /** 6 September to 13 October 2026. */
    private val october = listOf(
        35.7, 44.1, 35.3, 32.5, 30.7, 49.2, 47.9, 42.6, 41.7, 29.7, 36.5, 34.8, 45.2, 36.3, 38.2, 47.1, 36.5, 42.6, 44.0,
        28.6, 35.6, 40.2, 40.8, 40.8, 41.0, 33.4, 44.3, 39.9, 37.1, 44.3, 43.5, 33.1, 43.6, 59.6, 41.2, 54.5, 50.6, 39.4
    )

    private fun days(tdds: List<Double>, basal: (Int) -> Double? = { 8.0 }) =
        tdds.mapIndexed { i, x -> Day(t0 + i * dayMs, x, basal(i)) }

    @Test fun `a sustained rise is flagged on 12 October and not on 11 October`() {
        // index 36 is 12 October, index 35 is 11 October
        val on12 = InsulinNeedWatch.evaluate(days(october.take(37)))
        assertThat(on12.status).isEqualTo(Status.ABOVE)
        assertThat(on12.recentMeanU).isWithin(0.05).of((43.6 + 59.6 + 41.2 + 54.5 + 50.6) / 5)
        assertThat(on12.baselineDays).isEqualTo(30)
        assertThat(on12.z).isGreaterThan(1.5)
        assertThat(InsulinNeedWatch.evaluate(days(october.take(36))).status).isEqualTo(Status.WITHIN)
    }

    @Test fun `a steady month is within and a sustained fall is below`() {
        val steady = List(35) { 30.0 + (it % 5) - 2 }
        assertThat(InsulinNeedWatch.evaluate(days(steady)).status).isEqualTo(Status.WITHIN)
        val falling = steady.dropLast(5) + listOf(22.0, 21.0, 23.0, 22.0, 24.0)
        assertThat(InsulinNeedWatch.evaluate(days(falling)).status).isEqualTo(Status.BELOW)
    }

    @Test fun `too short a month gives no verdict`() {
        val r = InsulinNeedWatch.evaluate(days(List(15) { 30.0 + (it % 3) }))
        assertThat(r.status).isEqualTo(Status.FEW_DAYS)
    }

    @Test fun `a near-empty day is a gap in the record, not a dose`() {
        val base = List(30) { 30.0 + (it % 5) - 2 }
        // a gap in the window leaves four usable days: no verdict rather than a BELOW
        assertThat(InsulinNeedWatch.evaluate(days(base + listOf(30.0, 31.0, 2.0, 31.0, 30.0))).status).isEqualTo(Status.FEW_DAYS)
        // a gap in the month is left out, so it does not widen the spread and hide a real rise
        val withGap = base.toMutableList().also { it[10] = 1.5 } + listOf(40.0, 41.0, 42.0, 41.0, 40.0)
        val r = InsulinNeedWatch.evaluate(days(withGap))
        assertThat(r.baselineDays).isEqualTo(29)
        assertThat(r.status).isEqualTo(Status.ABOVE)
    }

    @Test fun `a profile change is reported as that, not as a change in need`() {
        // undiluted U200 halves the pump units of the profile and of every dose
        val tdds = List(30) { 30.0 + (it % 5) - 2 } + listOf(16.0, 15.0, 15.5, 16.0, 15.0)
        val r = InsulinNeedWatch.evaluate(days(tdds) { i -> if (i < 30) 16.0 else 8.0 })
        assertThat(r.status).isEqualTo(Status.PROFILE_CHANGED)
        // the same days with an unchanged profile would have been read as a fall
        assertThat(InsulinNeedWatch.evaluate(days(tdds)).status).isEqualTo(Status.BELOW)
        // a missing profile does not count as a change
        assertThat(InsulinNeedWatch.evaluate(days(tdds) { null }).status).isEqualTo(Status.BELOW)
    }

    @Test fun `a missing yesterday leaves the window short instead of moving it back`() {
        val d = days(october.take(37))
        val yesterday = d.last().startMs
        // drop 12 October: the window is still 8 to 12 October, now with four days, so no verdict
        assertThat(InsulinNeedWatch.evaluate(d.dropLast(1), yesterday).status).isEqualTo(Status.FEW_DAYS)
        assertThat(InsulinNeedWatch.evaluate(d, yesterday).status).isEqualTo(Status.ABOVE)
    }

    @Test fun `a temporary percentage switch is divided out, a permanent one is kept`() {
        // 16 U a day profile under a 70% exercise switch stores 11.2 U
        assertThat(InsulinNeedWatch.profileDailyBasal(11.2, 70, 2 * 60 * 60 * 1000L)).isWithin(1e-9).of(16.0)
        assertThat(InsulinNeedWatch.profileDailyBasal(19.2, 120, 0L)).isWithin(1e-9).of(19.2)
    }

    @Test fun `a direction is not repeated within 14 days, the other direction is`() {
        val above = InsulinNeedWatch.evaluate(days(october.take(37)))
        val now = t0 + 40 * dayMs
        assertThat(InsulinNeedWatch.shouldNotify(above, 0L, 0L, now)).isTrue()
        assertThat(InsulinNeedWatch.shouldNotify(above, now - 13 * dayMs, 0L, now)).isFalse()
        assertThat(InsulinNeedWatch.shouldNotify(above, now - 14 * dayMs, 0L, now)).isTrue()
        assertThat(InsulinNeedWatch.shouldNotify(above, 0L, now - dayMs, now)).isTrue()
        val within = InsulinNeedWatch.evaluate(days(october.take(36)))
        assertThat(InsulinNeedWatch.shouldNotify(within, 0L, 0L, now)).isFalse()
    }

    @Test fun `the breadcrumb token carries the verdict`() {
        assertThat(InsulinNeedWatch.evaluate(days(october.take(37))).token()).matches("ndw=ABOVE/z=\\d\\.\\d/p=\\+\\d+/n=30")
        assertThat(InsulinNeedWatch.evaluate(days(List(10) { 30.0 })).token()).startsWith("ndw=FEW_DAYS")
    }

    // Night watch: 14 baseline nights alternating 8.0 and 8.5 U (mean 8.25, SD 0.2594) at 118 and 122 mg/dL.
    private val baseIns = List(14) { if (it % 2 == 0) 8.0 else 8.5 }
    private val baseBg = List(14) { if (it % 2 == 0) 118.0 else 122.0 }
    private val baseSd = sqrt(baseIns.sumOf { (it - 8.25) * (it - 8.25) } / 13)

    private fun nights(windowIns: List<Double?>, windowBg: List<Double?>, ins: List<Double?> = baseIns, bg: List<Double?> = baseBg) =
        (ins + windowIns).zip(bg + windowBg).mapIndexed { i, (u, g) -> Night(t0 + i * dayMs, u, g) }

    @Test fun `night insulin above the band with glucose above the usual nights is ABOVE`() {
        val r = InsulinNeedWatch.evaluateNights(nights(List(3) { 10.0 }, List(3) { 140.0 }))
        assertThat(r.status).isEqualTo(Status.ABOVE)
        assertThat(r.glucoseAbove).isTrue()
        assertThat(r.baselineNights).isEqualTo(14)
        assertThat(r.token()).matches("ndn=ABOVE/z=\\d+\\.\\d/p=\\+21/g=1/n=14")
    }

    @Test fun `the night threshold sits at 2_05 SD`() {
        fun at(k: Double) = InsulinNeedWatch.evaluateNights(nights(List(3) { 8.25 + k * baseSd }, List(3) { 140.0 })).status
        assertThat(at(2.0)).isEqualTo(Status.WITHIN)
        assertThat(at(2.1)).isEqualTo(Status.ABOVE)
    }

    @Test fun `a night rise without glucose above the usual nights stays WITHIN and says so`() {
        val r = InsulinNeedWatch.evaluateNights(nights(List(3) { 10.0 }, listOf(140.0, 119.0, 140.0)))
        assertThat(r.status).isEqualTo(Status.WITHIN)
        assertThat(r.token()).contains("/g=0/")
        // a window night with too few readings cannot pass the gate either
        assertThat(InsulinNeedWatch.evaluateNights(nights(List(3) { 10.0 }, listOf(140.0, null, 140.0))).status).isEqualTo(Status.WITHIN)
    }

    @Test fun `high night glucose with usual night insulin stays WITHIN`() {
        assertThat(InsulinNeedWatch.evaluateNights(nights(List(3) { 8.3 }, List(3) { 200.0 })).status).isEqualTo(Status.WITHIN)
    }

    @Test fun `a missing window night or a thin baseline is FEW_DAYS`() {
        assertThat(InsulinNeedWatch.evaluateNights(nights(listOf(10.0, null, 10.0), List(3) { 140.0 })).status).isEqualTo(Status.FEW_DAYS)
        val thin = baseIns.mapIndexed { i, x -> if (i < 5) null else x }
        val r = InsulinNeedWatch.evaluateNights(nights(List(3) { 10.0 }, List(3) { 140.0 }, ins = thin))
        assertThat(r.status).isEqualTo(Status.FEW_DAYS)
        assertThat(r.token()).isEqualTo("ndn=FEW_DAYS/n=9")
        assertThat(InsulinNeedWatch.evaluateNights(nights(List(3) { 10.0 }, List(3) { 140.0 }).take(12)).status).isEqualTo(Status.FEW_DAYS)
    }

    @Test fun `night glucose needs the same coverage on a one-minute and a five-minute feed`() {
        val min = 60_000L
        val five = (0 until 72).map { t0 + it * 5 * min to 120.0 }
        val one = (0 until 360).map { t0 + it * min to 120.0 }
        assertThat(InsulinNeedWatch.nightMeanBg(five, t0)).isEqualTo(120.0)
        assertThat(InsulinNeedWatch.nightMeanBg(one, t0)).isEqualTo(120.0)
        // the first 15 slots (75 minutes) missing leaves 57 of 72 on either feed
        assertThat(InsulinNeedWatch.nightMeanBg(five.drop(15), t0)).isNull()
        assertThat(InsulinNeedWatch.nightMeanBg(one.drop(75), t0)).isNull()
        assertThat(InsulinNeedWatch.nightMeanBg(five.drop(14), t0)).isEqualTo(120.0)
        assertThat(InsulinNeedWatch.nightMeanBg(one.drop(70), t0)).isEqualTo(120.0)
        // sensor-error readings neither count as coverage nor enter the mean
        assertThat(InsulinNeedWatch.nightMeanBg(five.mapIndexed { i, p -> if (i < 15) p.first to 38.0 else p }, t0)).isNull()
        assertThat(InsulinNeedWatch.nightMeanBg(five.mapIndexed { i, p -> if (i < 5) p.first to 38.0 else p }, t0)).isEqualTo(120.0)
    }

    @Test fun `a profile change is reported instead of a night verdict`() {
        val r = InsulinNeedWatch.evaluateNights(nights(List(3) { 10.0 }, List(3) { 140.0 }), profileChanged = true)
        assertThat(r.token()).isEqualTo("ndn=PROFILE_CHANGED")
    }
}
