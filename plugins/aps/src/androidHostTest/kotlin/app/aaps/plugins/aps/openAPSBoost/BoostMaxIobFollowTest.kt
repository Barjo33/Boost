package app.aaps.plugins.aps.openAPSBoost

import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.openAPSBoostV5.BoostV5AutoConfig
import app.aaps.plugins.aps.openAPSBoostV5.BoostV5AutoConfigApply
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Boost max IOB uses max IOB unless the person set a lower value. Before, a value
 * nobody chose (the factory 1.0, or a copy of max IOB auto-config wrote once) stayed fixed: at 1.0
 * every Boost tier stopped at 1 U of IOB, and a copy went stale when max IOB changed. The default is
 * now the key maximum, 12 U, which both engines hold to max IOB.
 */
class BoostMaxIobFollowTest {

    private val mark = BoostV5AutoConfigApply.BOOST_MAX_IOB_FOLLOW_MARK

    private fun prefs(stored: Double?, ledgerJson: String = "", migrated: Boolean = false): Preferences = mock<Preferences>().also {
        whenever(it.get(BooleanComposedKey.BoostV5AutoConfigResolved, mark)).thenReturn(migrated)
        whenever(it.getIfExists(DoubleKey.ApsBoostMaxIob)).thenReturn(stored)
        whenever(it.getIfExists(StringKey.ApsBoostV5AutoConfigWritten)).thenReturn(ledgerJson)
    }

    @Test fun `the default is the key maximum, which max IOB bounds`() {
        assertThat(DoubleKey.ApsBoostMaxIob.defaultValue).isEqualTo(12.0)
        assertThat(DoubleKey.ApsBoostMaxIob.max).isEqualTo(12.0)
        assertThat(minOf(DoubleKey.ApsBoostMaxIob.defaultValue, 8.0)).isEqualTo(8.0)
        // V1's final clamp holds a Boost dose to max IOB whatever Boost max IOB says.
        assertThat(boostSmbWithinMaxIob(2.0, 8.0, 7.5, 0.05)).isEqualTo(0.5)
    }

    @Test fun `only values nobody chose move`() {
        assertThat(BoostV5AutoConfigApply.boostMaxIobShouldFollow(1.0, null, 8.0)).isTrue()      // old factory default
        assertThat(BoostV5AutoConfigApply.boostMaxIobShouldFollow(6.0, 6.0, 8.0)).isTrue()      // auto-config's ledger copy
        assertThat(BoostV5AutoConfigApply.boostMaxIobShouldFollow(8.0, null, 8.0)).isTrue()     // copy written before the ledger
        assertThat(BoostV5AutoConfigApply.boostMaxIobShouldFollow(12.0, null, 15.0)).isFalse()  // copy clamped to 12: already the default
        assertThat(BoostV5AutoConfigApply.boostMaxIobShouldFollow(5.5, 8.0, 8.0)).isFalse()     // person changed it after auto-config
        assertThat(BoostV5AutoConfigApply.boostMaxIobShouldFollow(5.5, null, 8.0)).isFalse()    // person set it
        assertThat(BoostV5AutoConfigApply.boostMaxIobShouldFollow(null, null, 8.0)).isFalse()   // nothing stored: already the default
    }

    @Test fun `migration writes the default once and marks itself`() {
        val p = prefs(stored = 1.0)
        assertThat(BoostV5AutoConfigApply.migrateBoostMaxIobToFollow(p, 8.0)).isEqualTo(1.0)
        verify(p).put(DoubleKey.ApsBoostMaxIob, 12.0)
        verify(p).put(BooleanComposedKey.BoostV5AutoConfigResolved, mark, value = true)
    }

    @Test fun `migration keeps a value the person set and still marks itself`() {
        val p = prefs(stored = 5.5)
        assertThat(BoostV5AutoConfigApply.migrateBoostMaxIobToFollow(p, 8.0)).isNull()
        verify(p, never()).put(eq(DoubleKey.ApsBoostMaxIob), any<Double>())
        verify(p).put(BooleanComposedKey.BoostV5AutoConfigResolved, mark, value = true)
    }

    @Test fun `migration reads the ledger copy`() {
        val p = prefs(stored = 6.0, ledgerJson = """{"boost_max_iob":6.0}""")
        assertThat(BoostV5AutoConfigApply.migrateBoostMaxIobToFollow(p, 8.0)).isEqualTo(6.0)
        verify(p).put(DoubleKey.ApsBoostMaxIob, 12.0)
    }

    @Test fun `a second run changes nothing`() {
        val p = prefs(stored = 1.0, migrated = true)
        assertThat(BoostV5AutoConfigApply.migrateBoostMaxIobToFollow(p, 8.0)).isNull()
        verify(p, never()).put(eq(DoubleKey.ApsBoostMaxIob), any<Double>())
    }

    @Test fun `auto-config no longer writes Boost max IOB`() {
        assertThat(BoostV5AutoConfigApply.managedDoubleKeys).doesNotContain(DoubleKey.ApsBoostMaxIob)
        val p = BoostV5AutoConfig.V1Profile(
            daysWithData = 14, bgReadingCount = 4000, tddMedianU = 40.0, manualBolusesU = listOf(4.0, 5.0, 6.0),
            smbAmountsU = listOf(0.3, 0.5, 0.8), tbrBelow70Pct = 2.0, timeBelow54Pct = 0.3, meanGlucoseMgdl = 130.0,
            currentMaxBolusU = 10.0
        )
        val s = BoostV5AutoConfig.compute(p)!!
        assertThat(BoostV5AutoConfigApply.managedDoubleKnobs(s).map { it.first }).doesNotContain(DoubleKey.ApsBoostMaxIob)
    }
}
