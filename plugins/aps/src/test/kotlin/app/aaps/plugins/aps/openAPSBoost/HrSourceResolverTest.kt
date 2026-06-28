package app.aaps.plugins.aps.openAPSBoost

import app.aaps.plugins.aps.openAPSBoost.HrSourceResolver.Reading
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * HR source visibility (2026-06-28). HrSourceResolver classifies device tags and names the live HR
 * feed for NS without changing any consumer. Pure — nothing here doses.
 */
class HrSourceResolverTest {

    private val NOW = 1_000_000_000_000L
    private fun minsAgo(m: Int) = NOW - m * 60_000L

    @Test fun `canonical classifies garmin, worn model and health connect`() {
        assertThat(HrSourceResolver.canonical("Garmin")).isEqualTo("garmin")
        assertThat(HrSourceResolver.canonical("OPPO OWWE261")).isEqualTo("worn:OPPO OWWE261")
        assertThat(HrSourceResolver.canonical("HealthConnect")).isEqualTo("hc")
        assertThat(HrSourceResolver.canonical("")).isEqualTo("hc")
    }

    @Test fun `realtime worn feed outranks health connect`() {
        assertThat(HrSourceResolver.tier("garmin")).isLessThan(HrSourceResolver.tier("hc"))
        assertThat(HrSourceResolver.tier("worn:OPPO OWWE261")).isLessThan(HrSourceResolver.tier("hc"))
    }

    @Test fun `picks the fresh realtime source over a fresh HC feed`() {
        val r = HrSourceResolver.resolve(
            listOf(
                Reading("OPPO OWWE261", minsAgo(1)),
                Reading("OPPO OWWE261", minsAgo(3)),
                Reading("HealthConnect", minsAgo(2))
            ), NOW
        )
        assertThat(r.active).isEqualTo("worn:OPPO OWWE261")
        assertThat(r.anyFresh).isTrue()
    }

    @Test fun `silent death - no fresh source yields null active`() {
        val r = HrSourceResolver.resolve(
            listOf(Reading("Garmin", minsAgo(25)), Reading("OPPO OWWE261", minsAgo(40))), NOW
        )
        assertThat(r.active).isNull()
        assertThat(r.anyFresh).isFalse()
        // but the dead sources are still surfaced for diagnosis
        assertThat(r.note).contains("garmin(-,1,25m)")
    }

    @Test fun `falls back to HC when only HC is fresh`() {
        val r = HrSourceResolver.resolve(
            listOf(Reading("Garmin", minsAgo(30)), Reading("HealthConnect", minsAgo(2))), NOW
        )
        assertThat(r.active).isEqualTo("hc")
    }

    @Test fun `empty input is none`() {
        val r = HrSourceResolver.resolve(emptyList(), NOW)
        assertThat(r.active).isNull()
        assertThat(r.note).isEqualTo("none")
    }

    @Test fun `note lists sources best-trust first with freshness flag and count`() {
        val r = HrSourceResolver.resolve(
            listOf(
                Reading("Garmin", minsAgo(1)),
                Reading("Garmin", minsAgo(2)),
                Reading("HealthConnect", minsAgo(20))
            ), NOW
        )
        assertThat(r.note).startsWith("garmin(f,2,")     // realtime first, fresh, 2 readings
        assertThat(r.note).contains("hc(-,1,20m)")       // HC stale
    }
}
