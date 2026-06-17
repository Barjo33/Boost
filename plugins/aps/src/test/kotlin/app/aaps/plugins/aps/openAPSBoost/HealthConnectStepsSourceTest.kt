package app.aaps.plugins.aps.openAPSBoost

import app.aaps.plugins.aps.openAPSBoost.HealthConnectStepsIngest.Companion.chooseSource
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * 2026-06-17 step-source preference: Garmin first, then fall back to the phone (dominant source).
 */
class HealthConnectStepsSourceTest {

    private val GARMIN = "com.garmin.android.apps.connectmobile"
    private val FIT = "com.google.android.apps.fitness"
    private val SHEALTH = "com.sec.android.app.shealth"

    @Test fun `prefers Garmin even when the phone has more steps`() {
        assertThat(chooseSource(mapOf(GARMIN to 3000L, FIT to 12000L))).isEqualTo(GARMIN)
    }

    @Test fun `falls back to the dominant source when Garmin absent`() {
        assertThat(chooseSource(mapOf(FIT to 9000L, SHEALTH to 4000L))).isEqualTo(FIT)
    }

    @Test fun `ignores a Garmin source that wrote zero steps`() {
        assertThat(chooseSource(mapOf(GARMIN to 0L, FIT to 8000L))).isEqualTo(FIT)
    }

    @Test fun `returns null when nothing has data`() {
        assertThat(chooseSource(mapOf(GARMIN to 0L, FIT to 0L))).isNull()
        assertThat(chooseSource(emptyMap())).isNull()
    }
}
