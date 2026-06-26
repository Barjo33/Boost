package app.aaps.plugins.aps.openAPSBoostV5

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * 2026-06-16 fast-carb fast-path (2026-06-26: OBSERVING-only; cold IDLE no longer fast-confirms).
 * Single-cycle OBSERVING → CONFIRMED when the rise is sharp
 * (delta ≥ 8) AND accelerating (deltaAccl ≥ 15) AND score corroborates (≥ 0.60) AND awake AND not
 * exercising AND the toggle is on — replay-validated to recover the ~15-min confirm latency that
 * crashed the 2026-06-16 fast carb, without firing on sleep/compression. Pure-function tests on step().
 */
class MealHypothesisFastConfirmTest {

    private fun obs(age: Int = 0, committed: Boolean = false) =
        MealHypothesisState(MealHypothesis.OBSERVING, age, 0.5, 10.0, committed)
    private fun idle() = MealHypothesisState(MealHypothesis.IDLE, 0, 0.0, 0.0, false)

    // strong fast-carb signals
    private val D = 12.0; private val A = 30.0; private val S = 0.7

    @Test fun `fast-confirm fires from OBSERVING in one cycle`() {
        val r = step(obs(age = 0), score = S, eventualBg = 150.0, targetBg = 100.0,
            delta = D, deltaAccl = A, deltaDeclining = false,
            asleep = false, exerciseActive = false, fastConfirmEnabled = true)
        assertThat(r.state).isEqualTo(MealHypothesis.CONFIRMED)
        assertThat(r.committedInSession).isTrue()
    }

    @Test fun `does NOT fast-confirm from a cold IDLE (one beat first) but confirms next cycle`() {
        // 2026-06-26: a cold IDLE single-cycle spike is a transient/compression signature. From IDLE
        // a fast-carb signal enters OBSERVING (one beat), then fast-confirms on the next cycle.
        val first = step(idle(), score = S, eventualBg = 150.0, targetBg = 100.0,
            delta = D, deltaAccl = A, deltaDeclining = false,
            asleep = false, exerciseActive = false, fastConfirmEnabled = true)
        assertThat(first.state).isEqualTo(MealHypothesis.OBSERVING)
        // still-sharp next cycle from OBSERVING → fast-confirm (lead time preserved, one cycle later)
        val second = step(first, score = S, eventualBg = 150.0, targetBg = 100.0,
            delta = D, deltaAccl = A, deltaDeclining = false,
            asleep = false, exerciseActive = false, fastConfirmEnabled = true)
        assertThat(second.state).isEqualTo(MealHypothesis.CONFIRMED)
    }

    @Test fun `does NOT fire while asleep (compression guard)`() {
        val r = step(obs(), S, 150.0, 100.0, D, A, false, asleep = true, exerciseActive = false, fastConfirmEnabled = true)
        assertThat(r.state).isNotEqualTo(MealHypothesis.CONFIRMED)
    }

    @Test fun `does NOT fire while exercising`() {
        val r = step(obs(), S, 150.0, 100.0, D, A, false, asleep = false, exerciseActive = true, fastConfirmEnabled = true)
        assertThat(r.state).isNotEqualTo(MealHypothesis.CONFIRMED)
    }

    @Test fun `does NOT fire when score does not corroborate`() {
        val r = step(obs(), score = 0.45, eventualBg = 150.0, targetBg = 100.0, delta = D, deltaAccl = A,
            deltaDeclining = false, asleep = false, exerciseActive = false, fastConfirmEnabled = true)
        assertThat(r.state).isNotEqualTo(MealHypothesis.CONFIRMED)
    }

    @Test fun `does NOT fire on a slow rise even if accelerating`() {
        val r = step(obs(), S, 150.0, 100.0, delta = 4.0, deltaAccl = A, deltaDeclining = false,
            asleep = false, exerciseActive = false, fastConfirmEnabled = true)
        assertThat(r.state).isNotEqualTo(MealHypothesis.CONFIRMED)
    }

    @Test fun `respects Fix-6 single-confirm guard (no re-confirm in same session)`() {
        val r = step(obs(age = 1, committed = true), S, 150.0, 100.0, D, A, false,
            asleep = false, exerciseActive = false, fastConfirmEnabled = true)
        assertThat(r.state).isNotEqualTo(MealHypothesis.CONFIRMED)
    }

    @Test fun `disabled toggle = unchanged behaviour (young OBSERVING stays OBSERVING)`() {
        // fast signals but toggle off; age 0 < CONFIRM_MIN_OBSERVING_AGE so normal path won't confirm
        val r = step(obs(age = 0), S, 150.0, 100.0, D, A, false,
            asleep = false, exerciseActive = false, fastConfirmEnabled = false)
        assertThat(r.state).isEqualTo(MealHypothesis.OBSERVING)
    }

    @Test fun `default args (no fast-path params) preserve legacy behaviour`() {
        // Existing call sites that don't pass the new args must behave exactly as before.
        val r = step(idle(), score = 0.5, eventualBg = 150.0, targetBg = 100.0, delta = D, deltaAccl = A, deltaDeclining = false)
        assertThat(r.state).isEqualTo(MealHypothesis.OBSERVING)   // score≥ENTER_OBSERVING → OBSERVING, not fast-confirm
    }
}
