package app.aaps.plugins.aps.openAPSBoost

import app.aaps.plugins.aps.openAPSBoost.OpenAPSBoostPlugin.Companion.effectiveNightStartMin
import app.aaps.plugins.aps.openAPSBoost.SleepStateDetector.SleepState
import app.aaps.plugins.aps.openAPSBoost.SleepStateDetector.State
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * A user with no HR feed. The no-HR fallback qualified as soon as candidacy opened 90 min before
 * nightStart, and the pre-window hold kept it, so an evening at the table was SLEEPING. Without a
 * feed the HR rules do not apply and sleep starts only from nightStart. Night 20:00 to 07:00, no HR
 * readings, no steps, five-minute cycles from 18:00.
 */
class SleepNoHrFeedTest {

    private val MIN = 60_000L
    private val T0 = 1_000_000_000_000L
    private val START_MIN = 18 * 60

    private fun minuteAt(now: Long) = ((START_MIN + (now - T0) / MIN) % 1440).toInt()

    /** Minute of day of the first SLEEPING cycle between 18:00 and 21:00, or null. */
    private fun firstSleepingMinute(hrFeedPresent: Boolean): Int? {
        var state = State()
        for (c in 0 until 36) {
            val now = T0 + c * 5 * MIN
            val r = SleepStateDetector.evaluate(
                state, SleepStateDetector.Inputs(
                    nowMs = now, minuteOfDay = minuteAt(now), hrReadings = emptyList(),
                    hrResting = 60, stepsLast15Min = 0, mlMealLikely = null,
                    nightStartMin = 20 * 60, nightEndMin = 7 * 60, stepsToday = 3000,
                    cycleSpacingMinutes = 5.0, hrFeedPresent = hrFeedPresent
                )
            )
            state = r.newState
            if (state.state == SleepState.SLEEPING) return minuteAt(now)
        }
        return null
    }

    @Test fun `without an HR feed sleep does not start before nightStart`() {
        val first = firstSleepingMinute(hrFeedPresent = false)
        assertThat(first).isNotNull()
        assertThat(first!!).isAtLeast(20 * 60)
        assertThat(first).isAtMost(20 * 60 + 15)       // the 10-min hysteresis after nightStart
    }

    @Test fun `with an HR feed in drought the early candidacy window still applies`() {
        // a watch that has gone quiet keeps the 90-min early window, as before
        assertThat(firstSleepingMinute(hrFeedPresent = true)!!).isLessThan(20 * 60)
    }

    @Test fun `without an HR feed a learned early night start does not move it earlier`() {
        // sessions that began on the clock fallback near 18:40 against a configured 20:00
        assertThat(effectiveNightStartMin(learned = 18 * 60 + 40, configured = 20 * 60, bandMin = 90, hrFeedPresent = false))
            .isEqualTo(20 * 60)
        // later than configured is still learned, and with a feed the band stays symmetric
        assertThat(effectiveNightStartMin(learned = 21 * 60, configured = 20 * 60, bandMin = 90, hrFeedPresent = false))
            .isEqualTo(21 * 60)
        assertThat(effectiveNightStartMin(learned = 18 * 60 + 40, configured = 20 * 60, bandMin = 90, hrFeedPresent = true))
            .isEqualTo(18 * 60 + 40)
    }
}
