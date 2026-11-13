package app.aaps.plugins.aps.openAPSBoostV5

import app.aaps.core.keys.DoubleKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The committed cap setting goes up to 5.0 U, where it stopped at 2.5 U (6.1.2: 0.0 to 2.5, default 0.5).
 * Auto-config still sets it no higher than 2.5 at onboarding, and the weekly re-derivation must not
 * carry it past 2.5 either: it scales the current value by the movement of the derivation, so without
 * a ceiling a value at 2.5 with a rising TDD would be raised to 3.13 and on.
 */
class RedriveCommittedCapCeilingTest {

    private val CCAP = DoubleKey.ApsBoostV5CommittedCapU

    private class Store(vararg initial: Pair<DoubleKey, Double>) {
        val stored = mutableMapOf(*initial)
        val baseline = mutableMapOf<DoubleKey, Double>()
        val pending = mutableMapOf<DoubleKey, Double>()
        val written = mutableMapOf<DoubleKey, Double>()
        fun run(tdd: Double) {
            val s = BoostV5AutoConfig.compute(
                BoostV5AutoConfig.V1Profile(
                    daysWithData = 28, bgReadingCount = 6000, tddMedianU = tdd,
                    manualBolusesU = List(20) { 4.0 }, smbAmountsU = List(200) { 0.3 },
                    tbrBelow70Pct = 1.0, timeBelow54Pct = 0.2, meanGlucoseMgdl = 130.0,
                    currentMaxBolusU = 2.5))!!
            BoostV5AutoConfigApply.redrive(
                s, 1.0, 0.2,
                storedValue = { stored[it] }, baselineValue = { baseline[it] }, pendingValue = { pending[it] },
                put = { k, v -> stored[k] = v },
                setBaseline = { k, v -> baseline[k] = v },
                setPending = { k, v -> if (v == null) pending.remove(k) else pending[k] = v },
                lastWritten = { written[it] }, recordWritten = { k, v -> written[k] = v })
        }
    }

    @Test fun `the setting allows 5_0 and auto-config stops at 2_5`() {
        assertThat(CCAP.max).isEqualTo(5.0)
        assertThat(CCAP.defaultValue).isEqualTo(0.5)
        assertThat(BoostV5AutoConfig.COMMITTED_CAP_AUTO_MAX).isEqualTo(2.5)
    }

    @Test fun `re-derivation does not raise the committed cap past 2_5`() {
        val st = Store(CCAP to 2.5)
        st.run(tdd = 80.0)                 // baseline: derived committed cap TDD/40 = 2.0
        repeat(3) { st.run(tdd = 100.0) }  // derived 2.5: a 1.25 movement that would propose 3.13
        assertThat(st.stored[CCAP]!!).isAtMost(2.5)
    }

    @Test fun `re-derivation still raises a lower committed cap up to 2_5`() {
        val st = Store(CCAP to 1.6)
        st.run(tdd = 64.0)                 // baseline 1.6
        repeat(3) { st.run(tdd = 100.0) }  // derived 2.5
        assertThat(st.stored[CCAP]!!).isGreaterThan(1.6)
        assertThat(st.stored[CCAP]!!).isAtMost(2.5)
    }

    @Test fun `a value the person set above 2_5 is not raised by auto-config`() {
        val st = Store(CCAP to 3.0)
        st.run(tdd = 80.0)
        repeat(3) { st.run(tdd = 100.0) }
        assertThat(st.stored[CCAP]!!).isAtMost(3.0)
    }
}
