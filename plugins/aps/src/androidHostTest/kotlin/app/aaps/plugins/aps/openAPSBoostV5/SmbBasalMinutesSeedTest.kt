package app.aaps.plugins.aps.openAPSBoostV5

import app.aaps.core.keys.IntKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Boost starts the "max minutes of basal to limit SMB" settings at 15 (standard) and 20 (UAM), and
 * only where nothing is stored, so a value set by hand or brought in by an import is kept.
 */
class SmbBasalMinutesSeedTest {

    private val smb = IntKey.ApsMaxMinutesOfBasalToLimitSmb
    private val uam = IntKey.ApsUamMaxMinutesOfBasalToLimitSmb

    private fun run(start: Map<IntKey, Int>): Pair<Map<IntKey, Int>, List<IntKey>> {
        val store = start.toMutableMap()
        val written = BoostV5AutoConfigApply.seedSmbBasalMinutes({ store[it] }, { k, v -> store[k] = v })
        return store to written
    }

    @Test fun `a first start with nothing stored gets 15 and 20`() {
        val (store, written) = run(emptyMap())
        assertThat(store).containsExactly(smb, 15, uam, 20)
        assertThat(written).containsExactly(smb, uam)
    }

    @Test fun `stored values are kept, including ones above Boost's starting values`() {
        val (store, written) = run(mapOf(smb to 120, uam to 60))
        assertThat(store).containsExactly(smb, 120, uam, 60)
        assertThat(written).isEmpty()
    }

    @Test fun `a second run writes nothing, so a value raised after the first start stays`() {
        val (afterFirst, _) = run(emptyMap())
        val raised = afterFirst + (smb to 45)
        val (store, written) = run(raised)
        assertThat(store[smb]).isEqualTo(45)
        assertThat(written).isEmpty()
    }

    @Test fun `only the setting with nothing stored is written`() {
        val (store, written) = run(mapOf(smb to 30))
        assertThat(store).containsExactly(smb, 30, uam, 20)
        assertThat(written).containsExactly(uam)
    }

    @Test fun `no starting value sits below the key minimum`() {
        BoostV5AutoConfigApply.smbBasalMinutesSeeds.forEach { (key, seed) -> assertThat(seed).isAtLeast(key.min) }
    }
}
