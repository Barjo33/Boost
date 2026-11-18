package app.aaps.plugins.aps.openAPSBoostV5

import app.aaps.core.keys.DoubleKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The committed cap setting goes up to 5.0 U (6.1.2 and 6.3.2: 0.0 to 2.5, default 0.5), and auto-config
 * now uses the same range at onboarding and in the weekly re-derivation. The derivation is
 * max(SMB p75, TDD/40), so it passes the old 2.5 only above a median TDD of 100 U or an SMB 75th
 * percentile above 2.5 U. The primer is a fraction of the committed cap and keeps its own maximum of 2.5.
 */
class RedriveCommittedCapCeilingTest {

    private val CCAP = DoubleKey.ApsBoostV5CommittedCapU
    private val PRIMER = DoubleKey.ApsBoostV5PrimerCapU

    private fun suggestion(tdd: Double) = BoostV5AutoConfig.compute(
        BoostV5AutoConfig.V1Profile(
            daysWithData = 28, bgReadingCount = 6000, tddMedianU = tdd,
            manualBolusesU = List(20) { 4.0 }, smbAmountsU = List(200) { 0.3 },
            tbrBelow70Pct = 1.0, timeBelow54Pct = 0.2, meanGlucoseMgdl = 130.0,
            currentMaxBolusU = 2.5))!!

    private class Store(vararg initial: Pair<DoubleKey, Double>) {
        val stored = mutableMapOf(*initial)
        val baseline = mutableMapOf<DoubleKey, Double>()
        val pending = mutableMapOf<DoubleKey, Double>()
        val written = mutableMapOf<DoubleKey, Double>()
        fun run(s: BoostV5AutoConfig.V5Suggestion) {
            BoostV5AutoConfigApply.redrive(
                s, 1.0, 0.2,
                storedValue = { stored[it] }, baselineValue = { baseline[it] }, pendingValue = { pending[it] },
                put = { k, v -> stored[k] = v },
                setBaseline = { k, v -> baseline[k] = v },
                setPending = { k, v -> if (v == null) pending.remove(k) else pending[k] = v },
                lastWritten = { written[it] }, recordWritten = { k, v -> written[k] = v })
        }
    }

    @Test fun `onboarding derives the committed cap up to the setting's 5_0`() {
        assertThat(CCAP.max).isEqualTo(5.0)
        assertThat(CCAP.defaultValue).isEqualTo(0.5)
        assertThat(suggestion(80.0).committedCapU).isEqualTo(2.0)
        assertThat(suggestion(160.0).committedCapU).isEqualTo(4.0)
        assertThat(suggestion(240.0).committedCapU).isEqualTo(5.0)
    }

    @Test fun `the primer stays within its own 2_5 when the committed cap is above it`() {
        val s = suggestion(200.0)                     // committed cap 5.0; any primer fraction above 0.5 would pass 2.5
        assertThat(s.committedCapU).isEqualTo(5.0)
        assertThat(s.primerCapU).isEqualTo(DoubleKey.ApsBoostV5PrimerCapU.max)
    }

    @Test fun `re-derivation raises the committed cap past 2_5 and stops at 5_0`() {
        val st = Store(CCAP to 2.5)
        st.run(suggestion(100.0))                     // baseline: derived 2.5
        repeat(3) { st.run(suggestion(160.0)) }       // derived 4.0
        assertThat(st.stored[CCAP]!!).isGreaterThan(2.5)
        repeat(3) { st.run(suggestion(400.0)) }       // derived 5.0, and movement that would carry it further
        assertThat(st.stored[CCAP]!!).isAtMost(5.0)
    }

    @Test fun `the primer follows a rising committed cap at the policy fraction, not the clamped ratio`() {
        val s = suggestion(200.0)                     // committed 5.0, primer held to 2.5: the ratio is no longer the fraction
        val start = Math.round(2.5 * s.primerFrac * 100) / 100.0
        val st = Store(CCAP to 2.5, PRIMER to start)
        st.written[PRIMER] = start
        st.run(suggestion(100.0))                     // baseline: derived committed cap 2.5
        st.run(s)                                     // committed cap rises by the step limit
        val cap = st.stored[CCAP]!!
        assertThat(cap).isGreaterThan(2.5)
        assertThat(st.stored[PRIMER]!!).isAtLeast(start)
        assertThat(st.stored[PRIMER]!!).isWithin(0.011).of(minOf(cap * s.primerFrac, PRIMER.max))
    }
}
