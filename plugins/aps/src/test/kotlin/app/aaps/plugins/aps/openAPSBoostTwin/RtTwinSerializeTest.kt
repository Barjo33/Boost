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
            boostTwin = "120.0,132.5,95.0,170.0,0.42,118.3,0.35"
        }
        val json = rt.serialize()
        assertThat(json).contains("boostTwin")
        assertThat(json).contains("132.5")
    }
}
