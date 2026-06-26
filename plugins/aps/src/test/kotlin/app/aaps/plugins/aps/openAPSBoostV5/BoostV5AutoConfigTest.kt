package app.aaps.plugins.aps.openAPSBoostV5

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for the V5 auto-config calculator: conservative, transparent derivation of V5 knobs from a
 * user's last-N-day V1 history. Pure-function tests on [BoostV5AutoConfig.compute].
 */
class BoostV5AutoConfigTest {

    private fun profile(
        days: Int = 14, bg: Int = 3500, tdd: Double = 40.0,
        manual: List<Double> = listOf(3.0, 4.0, 5.0, 6.0),
        smb: List<Double> = listOf(0.2, 0.3, 0.4, 0.6, 0.8),
        tbr70: Double = 3.0, sev54: Double = 0.4, meanBg: Double = 130.0,
        maxIob: Double = 8.0, maxBolus: Double = 10.0
    ) = BoostV5AutoConfig.V1Profile(days, bg, tdd, manual, smb, tbr70, sev54, meanBg, maxIob, maxBolus)

    @Test fun `insufficient days returns null`() {
        assertThat(BoostV5AutoConfig.compute(profile(days = 5))).isNull()
    }

    @Test fun `insufficient bg readings returns null`() {
        assertThat(BoostV5AutoConfig.compute(profile(bg = 800))).isNull()
    }

    @Test fun `in-target user gets neutral aggression and no extra caution`() {
        val s = BoostV5AutoConfig.compute(profile(tbr70 = 2.5, sev54 = 0.2))!!
        assertThat(s.aggression).isEqualTo(1.0)
        assertThat(s.hypoCaution).isEqualTo(1.0)
        assertThat(s.fastCarbConfirm).isTrue()
    }

    @Test fun `hypo-prone user gets gentler aggression, higher caution, fast-carb off`() {
        val s = BoostV5AutoConfig.compute(profile(tbr70 = 8.0, sev54 = 2.5))!!
        assertThat(s.aggression).isEqualTo(0.85)
        assertThat(s.hypoCaution).isGreaterThan(1.0)
        assertThat(s.fastCarbConfirm).isFalse()
    }

    @Test fun `aggression is never auto-raised above neutral`() {
        // Even a pristine, never-low user does not get aggression > 1.0 on day one.
        val s = BoostV5AutoConfig.compute(profile(tbr70 = 0.5, sev54 = 0.0))!!
        assertThat(s.aggression).isAtMost(1.0)
    }

    @Test fun `caps derive from dose distribution and clamp to ranges`() {
        val s = BoostV5AutoConfig.compute(profile(manual = listOf(2.0, 3.0, 4.0, 5.0), smb = listOf(0.3, 0.5, 0.7)))!!
        assertThat(s.confirmedCapU).isAtLeast(1.5)
        assertThat(s.confirmedCapU).isAtMost(7.5)
        assertThat(s.committedCapU).isAtLeast(0.25)
        assertThat(s.committedCapU).isAtMost(2.5)
        assertThat(s.cumulativeSmbCap60MinU).isAtLeast(1.0)
        assertThat(s.cumulativeSmbCap60MinU).isAtMost(5.0)
        // cumulative cap is never below a single confirm shot (it must allow ≥1 confirm)
        assertThat(s.cumulativeSmbCap60MinU).isAtLeast(s.confirmedCapU - 1e-9)
    }

    @Test fun `confirmed cap covers a big-meal bolus user`() {
        val big = BoostV5AutoConfig.compute(profile(manual = listOf(5.0, 7.0, 9.0, 11.0)))!!
        val small = BoostV5AutoConfig.compute(profile(manual = listOf(1.0, 1.5, 2.0)))!!
        assertThat(big.confirmedCapU).isGreaterThan(small.confirmedCapU)
    }

    @Test fun `cumulative cap is never below a single confirmed shot, even for a big-meal user`() {
        // Big eater: confirmedCap clamps to its 7.5 ceiling. The hourly cumulative budget must not
        // saturate below that (was clamped to 5.0 before the 2026-06-26 fix).
        val s = BoostV5AutoConfig.compute(profile(manual = listOf(5.0, 7.0, 9.0, 11.0)))!!
        assertThat(s.confirmedCapU).isEqualTo(7.5)
        assertThat(s.cumulativeSmbCap60MinU).isAtLeast(s.confirmedCapU - 1e-9)
    }

    @Test fun `maxIob and bolus cap are carried and clamped`() {
        val s = BoostV5AutoConfig.compute(profile(maxIob = 15.0, maxBolus = 12.0))!!
        assertThat(s.maxIobU).isEqualTo(12.0)   // clamped to key max
        assertThat(s.bolusCapU).isEqualTo(10.0) // clamped to key max
    }

    @Test fun `percentile interpolates`() {
        val v = listOf(1.0, 2.0, 3.0, 4.0)
        assertThat(BoostV5AutoConfig.percentile(v, 0.0)).isEqualTo(1.0)
        assertThat(BoostV5AutoConfig.percentile(v, 100.0)).isEqualTo(4.0)
        assertThat(BoostV5AutoConfig.percentile(v, 50.0)).isWithin(1e-9).of(2.5)
        assertThat(BoostV5AutoConfig.percentile(emptyList(), 90.0)).isEqualTo(0.0)
    }

    @Test fun `rationale explains every setting`() {
        val s = BoostV5AutoConfig.compute(profile())!!
        assertThat(s.rationale).isNotEmpty()
        assertThat(s.rationale.any { it.contains("HypoCaution") }).isTrue()
        assertThat(s.rationale.any { it.contains("Aggression") }).isTrue()
    }
}
