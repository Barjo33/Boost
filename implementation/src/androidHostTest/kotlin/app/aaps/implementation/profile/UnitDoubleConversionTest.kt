package app.aaps.implementation.profile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.unitDoubleToMgdl
import app.aaps.core.interfaces.profile.unitDoubleToUnits
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.utils.DecimalFormatterImpl
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

/**
 * The night-mode BG offset is a difference, stored in mg/dL. Magnitude detection read its 27 mg/dL
 * default as 27 mmol/L, and the dose path then disabled SMBs below target + 486 mg/dL all night.
 */
class UnitDoubleConversionTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences

    private lateinit var sut: ProfileUtilImpl
    private val offset = UnitDoubleKey.ApsBoostNightModeBgOffset

    @BeforeEach
    fun setup() {
        sut = ProfileUtilImpl(preferences, DecimalFormatterImpl(rh), rh)
    }

    private fun units(u: GlucoseUnit) = whenever(preferences.get(StringKey.GeneralUnits)).thenReturn(u.asText)

    @Test
    fun `offset of 27 mg per dL reads as 1_5 mmol and 27 mg per dL, never 27 mmol`() {
        units(GlucoseUnit.MMOL)
        assertThat(sut.unitDoubleToUnits(offset, 27.0)).isWithin(0.01).of(1.5)
        units(GlucoseUnit.MGDL)
        assertThat(sut.unitDoubleToUnits(offset, 27.0)).isWithin(1e-9).of(27.0)
    }

    @Test
    fun `entered offset is stored in mg per dL by the user's units`() {
        units(GlucoseUnit.MMOL)
        assertThat(sut.unitDoubleToMgdl(offset, 1.5)).isWithin(0.1).of(27.0)
        units(GlucoseUnit.MGDL)
        assertThat(sut.unitDoubleToMgdl(offset, 27.0)).isWithin(1e-9).of(27.0)
    }

    @Test
    fun `offset stored times 18 by the old entry path reads back as entered`() {
        units(GlucoseUnit.MGDL)
        assertThat(sut.unitDoubleToUnits(offset, 27.0 * 18.01559)).isWithin(0.01).of(27.0)
        assertThat(sut.unitDoubleToUnits(offset, 90.0)).isWithin(1e-9).of(90.0)
    }

    @Test
    fun `glucose keys keep magnitude detection`() {
        units(GlucoseUnit.MMOL)
        assertThat(sut.unitDoubleToUnits(UnitDoubleKey.ApsBoostDynIsfBgCap, 210.0)).isWithin(0.01).of(11.66)
        assertThat(sut.unitDoubleToUnits(UnitDoubleKey.ApsBoostDynIsfBgCap, 11.66)).isWithin(1e-9).of(11.66)
        assertThat(sut.unitDoubleToMgdl(UnitDoubleKey.ApsBoostDynIsfBgCap, 11.66)).isWithin(0.1).of(210.0)
    }
}
