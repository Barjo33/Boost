package app.aaps.plugins.aps.openAPSBoostTwin

import app.aaps.core.interfaces.aps.RT
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Repro guard: RT carries the boostTwin_* telemetry and is @Serializable — serialize() runs after
 * every loop to upload NS devicestatus, OUTSIDE the Twin's runCatching. This asserts that path
 * doesn't throw with the Twin fields populated (a startup/near-startup crash suspect).
 */
class RtTwinSerializeTest {

    @Test fun `RT serializes with the boostTwin fields populated`() {
        val rt = RT(runningDynamicIsf = false).apply {
            boostTwin_fc30 = 120.0
            boostTwin_fc60 = 132.5
            boostTwin_lo60 = 95.0
            boostTwin_hi60 = 170.0
            boostTwin_ra = 0.42
            boostTwin_gi = 118.3
            boostTwin_insU = 0.35
        }
        val json = rt.serialize()
        assertThat(json).contains("boostTwin_fc30")
        assertThat(json).contains("boostTwin_ra")
    }
}
