package app.aaps.ui.compose.overview.boost

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.BoostTier
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/** What the Boost overview and widget show from the last loop result, by its age. */
internal class BoostOverviewDataProviderTest {

    private val now = 100_000_000L

    private fun provider(lastRunAgeMs: Long, rt: RT, nsRt: RT? = null): BoostOverviewDataProvider {
        val request = mock<APSResult> {
            on { rawData() } doReturn rt
            on { variableSens } doReturn rt.variable_sens
        }
        val lastRun = Loop.LastRun().apply {
            this.request = request
            lastAPSRun = now - lastRunAgeMs
        }
        val loop = mock<Loop> { on { this.lastRun } doReturn lastRun }
        val openAPSData = ProcessedDeviceStatusData.OpenAPSData().apply { suggested = nsRt }
        val deviceStatus = mock<ProcessedDeviceStatusData> { on { this.openAPSData } doReturn openAPSData }
        val ads = mock<AutosensDataStore>()
        val iobCobCalculator = mock<IobCobCalculator> { on { this.ads } doReturn ads }
        val dateUtil = mock<DateUtil> { on { now() } doReturn now }
        return BoostOverviewDataProvider(loop, deviceStatus, iobCobCalculator, mock<TddCalculator>(), mock<PersistenceLayer>(), dateUtil)
    }

    private fun boostRt() = RT(
        runningDynamicIsf = true,
        reason = StringBuilder("Tier 2 - High Boost"),
        variable_sens = 45.0,
        deltaAcceleration = 12.5,
        boostProfileSwitch = 130
    )

    @Test
    fun `a recent result is shown, with delta acceleration and profile percent read from the RT`() = runTest {
        val status = provider(lastRunAgeMs = 5 * 60_000L, rt = boostRt()).getBoostStatus()
        assertThat(status.tier).isEqualTo(BoostTier.HIGH_BOOST)
        assertThat(status.dynIsfValue).isEqualTo(45.0)
        assertThat(status.deltaAccl).isEqualTo(12.5)
        assertThat(status.profilePercentage).isEqualTo(130)
    }

    @Test
    fun `a result older than 15 minutes is not shown as current`() = runTest {
        val status = provider(lastRunAgeMs = 40 * 60_000L, rt = boostRt()).getBoostStatus()
        assertThat(status.tier).isEqualTo(BoostTier.INACTIVE)
        assertThat(status.tierLabel).isEqualTo(BoostOverviewDataProvider.NO_RECENT_RESULT)
        assertThat(status.dynIsfValue).isEqualTo(0.0)
        assertThat(status.isV5Active).isFalse()
    }

    @Test
    fun `an old Nightscout result is not shown as current either`() = runTest {
        val nsRt = boostRt().apply { timestamp = now - 40 * 60_000L }
        val status = provider(lastRunAgeMs = 40 * 60_000L, rt = boostRt(), nsRt = nsRt).getBoostStatus()
        assertThat(status.tier).isEqualTo(BoostTier.INACTIVE)
    }
}
