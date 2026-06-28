package app.aaps.plugins.aps.openAPSBoost

import app.aaps.plugins.aps.openAPSBoost.DailyStepHistoryTracker.DailyTotal
import app.aaps.plugins.aps.openAPSBoost.DailyStepHistoryTracker.History
import app.aaps.plugins.aps.openAPSBoost.DailyStepHistoryTracker.MultiSourceHistory
import app.aaps.plugins.aps.openAPSBoost.StepSourceResolver.SourceState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Activity-load source abstraction (2026-06-28). StepSourceResolver selection + DailyStepHistoryTracker
 * multi-source history, overlap calibration, and scaled bridging. Pure — nothing here doses.
 *
 * Headline guarantee: when the primary device changes, the old source's days BRIDGE the new source's
 * window (no warmup reset) AND are scaled into the new source's units, so a phone that undercounts vs
 * a watch does not read as a false drop in activity.
 */
class StepSourceBridgeTest {

    private val T = DailyStepHistoryTracker

    private fun srcHist(src: String, days: Map<Long, Int>) =
        src to History(days.mapValues { (d, s) -> DailyTotal(d, s, src) }.toMutableMap())

    private fun msh(vararg srcDays: Pair<String, History>) =
        MultiSourceHistory(srcDays.toMap().toMutableMap())

    // ── Resolver: canonicalisation + trust order ────────────────────────────────────────────────

    @Test fun `canonical maps garmin pkg and bare HC pkg`() {
        assertThat(StepSourceResolver.canonical("com.garmin.android.apps.connectmobile")).isEqualTo("garmin")
        assertThat(StepSourceResolver.canonical("com.google.android.apps.fitness")).isEqualTo("hc:fitness")
        assertThat(StepSourceResolver.canonical("wear")).isEqualTo("wear")
        assertThat(StepSourceResolver.canonical("phone")).isEqualTo("phone")
    }

    @Test fun `trust order is wear then garmin then HC then phone`() {
        assertThat(StepSourceResolver.tier("wear")).isLessThan(StepSourceResolver.tier("garmin"))
        assertThat(StepSourceResolver.tier("garmin")).isLessThan(StepSourceResolver.tier("hc:fitness"))
        assertThat(StepSourceResolver.tier("hc:fitness")).isLessThan(StepSourceResolver.tier("phone"))
    }

    @Test fun `resolve picks highest-trust fresh source`() {
        val r = StepSourceResolver.resolve(
            listOf(
                SourceState("phone", fresh = true, coverageDays = 20, stepsToday = 5000),
                SourceState("wear", fresh = true, coverageDays = 2, stepsToday = 6000),
                SourceState("garmin", fresh = false, coverageDays = 20, stepsToday = 9000)
            )
        )
        assertThat(r.active).isEqualTo("wear")          // worn watch wins even with little history
        assertThat(r.stepsToday).isEqualTo(6000)
        assertThat(r.activeFresh).isTrue()
    }

    @Test fun `resolve falls back to highest-trust source with data when none fresh`() {
        val r = StepSourceResolver.resolve(
            listOf(
                SourceState("phone", fresh = false, coverageDays = 20, stepsToday = 5000),
                SourceState("garmin", fresh = false, coverageDays = 20, stepsToday = 9000)
            )
        )
        assertThat(r.active).isEqualTo("garmin")
    }

    @Test fun `resolve with nothing yields null active`() {
        val r = StepSourceResolver.resolve(emptyList())
        assertThat(r.active).isNull()
        assertThat(r.stepsToday).isEqualTo(0)
    }

    // ── Calibration ─────────────────────────────────────────────────────────────────────────────

    @Test fun `calibration is median ratio over overlapping days`() {
        val active = History((1L..5L).associateWith { DailyTotal(it, 9000, "phone") }.toMutableMap())
        val donor = History((1L..5L).associateWith { DailyTotal(it, 14000, "wear") }.toMutableMap())
        val cal = T.calibration(active, donor)!!
        assertThat(cal).isWithin(1e-6).of(9000.0 / 14000.0)
    }

    @Test fun `calibration null when too little overlap`() {
        val active = History(mutableMapOf(1L to DailyTotal(1, 9000, "phone"), 2L to DailyTotal(2, 9000, "phone")))
        val donor = History((1L..5L).associateWith { DailyTotal(it, 14000, "wear") }.toMutableMap())
        assertThat(T.calibration(active, donor)).isNull()   // only 2 overlap days < MIN_OVERLAP_DAYS
    }

    @Test fun `calibration ignores zero-step days`() {
        val active = History((1L..5L).associateWith { DailyTotal(it, 9000, "phone") }.toMutableMap())
        // donor has a zero day that must not pull the ratio to infinity
        val donor = History((1L..5L).associate { it to DailyTotal(it, if (it == 1L) 0 else 14000, "wear") }.toMutableMap())
        val cal = T.calibration(active, donor)!!
        assertThat(cal).isWithin(1e-6).of(9000.0 / 14000.0)
    }

    // ── Bridging: the headline device-switch guarantee ─────────────────────────────────────────

    @Test fun `watch dies, phone takes over - scaled bridge avoids false inactivity`() {
        // wear logged 14k/day for days 1..20 then died; phone (always carried) logged 9k/day and now
        // owns today (day 21). The window is wear-heavy but must read as NORMAL activity, not a drop.
        val multi = msh(
            srcHist("wear", (1L..20L).associateWith { 14000 }),
            srcHist("phone", (16L..20L).associateWith { 9000 })   // phone only has recent days
        )
        val bridged = T.bridgedWindow(multi, activeSource = "phone", todayIndex = 21)
        // coverage restored across the whole window from the scaled wear donor
        assertThat(bridged.history.days.keys).containsAtLeast(1L, 10L, 20L)
        assertThat(bridged.calibrated).isTrue()                  // ≥3 overlap days (16..20)
        // bridged wear days scaled phone-ward: 14000 * (9000/14000) ≈ 9000
        assertThat(bridged.history.days[1L]!!.steps).isWithin(50).of(9000)
        // baseline is in phone units, and yesterday (phone 9000) ≈ baseline → ratio ~1, NOT inactivity
        val f = T.shadowFactors(bridged.history, todayIndex = 21)
        assertThat(T.baseline(bridged.history, 21)).isWithin(200).of(9000)
        assertThat(f.note).isNotEqualTo("inactivity")
        assertThat(f.wouldDeltaIsfPct).isWithin(1.0).of(0.0)
    }

    @Test fun `bridging guarantees coverage so no warmup gap on device switch`() {
        // active source (wear, just adopted) has almost no history; donor (phone) carries the window.
        val multi = msh(
            srcHist("wear", mapOf(20L to 13000)),
            srcHist("phone", (1L..20L).associateWith { 9000 })
        )
        // single-source view of wear alone would be insufficient-history:
        assertThat(T.baseline(multi.sources["wear"]!!, 21)).isNull()
        // bridged view has full coverage → baseline forms (no warmup)
        val bridged = T.bridgedWindow(multi, activeSource = "wear", todayIndex = 21)
        assertThat(T.baseline(bridged.history, 21)).isNotNull()
    }

    @Test fun `bridge without enough overlap is flagged uncalibrated and spliced raw`() {
        val multi = msh(
            srcHist("wear", mapOf(20L to 13000)),                 // 1 day only → no overlap to calibrate
            srcHist("phone", (1L..20L).associateWith { 9000 })
        )
        val bridged = T.bridgedWindow(multi, activeSource = "wear", todayIndex = 21)
        assertThat(bridged.calibrated).isFalse()
        assertThat(bridged.history.days[1L]!!.steps).isEqualTo(9000)   // raw phone value, no scaling
    }

    @Test fun `highest-trust donor wins a bridged day`() {
        val multi = msh(
            srcHist("garmin", (1L..20L).associateWith { 12000 }),
            srcHist("phone", (1L..20L).associateWith { 9000 })
        )
        // active = wear (no days) → every day bridged; garmin (tier 1) should win over phone (tier 3)
        val bridged = T.bridgedWindow(multi, activeSource = "wear", todayIndex = 21)
        assertThat(bridged.history.days[5L]!!.source).isEqualTo("garmin")
    }

    @Test fun `active source days are never overwritten by donors`() {
        val multi = msh(
            srcHist("wear", mapOf(10L to 15000)),
            srcHist("phone", (1L..20L).associateWith { 9000 })
        )
        val bridged = T.bridgedWindow(multi, activeSource = "wear", todayIndex = 21)
        assertThat(bridged.history.days[10L]!!.steps).isEqualTo(15000)   // wear's own day kept
        assertThat(bridged.history.days[10L]!!.source).isEqualTo("wear")
    }

    // ── MultiSourceHistory: persistence + merge ────────────────────────────────────────────────

    @Test fun `multi-source serialize round-trips`() {
        val multi = msh(
            srcHist("wear", mapOf(1L to 14000, 2L to 15000)),
            srcHist("phone", mapOf(1L to 9000))
        )
        val back = MultiSourceHistory.deserialize(multi.serialize())
        assertThat(back.sources.keys).containsExactly("wear", "phone")
        assertThat(back.sources["wear"]!!.days[2L]!!.steps).isEqualTo(15000)
    }

    @Test fun `deserialize migrates old single-history format grouped by source`() {
        // old format: a flat History with per-day src tags
        val old = History(mutableMapOf(
            1L to DailyTotal(1, 14000, "wear"),
            2L to DailyTotal(2, 9000, "com.garmin.android.apps.connectmobile")
        ))
        val migrated = MultiSourceHistory.deserialize(old.serialize())
        assertThat(migrated.sources.keys).containsExactly("wear", "garmin")
        assertThat(migrated.sources["garmin"]!!.days[2L]!!.steps).isEqualTo(9000)
    }

    @Test fun `corrupt blob deserializes to empty multi-history`() {
        assertThat(MultiSourceHistory.deserialize("{{bad").sources).isEmpty()
        assertThat(MultiSourceHistory.deserialize("").sources).isEmpty()
    }

    @Test fun `mergeSource adds completed days to the right source and prunes empties`() {
        var multi = MultiSourceHistory()
        multi = T.mergeSource(multi, "wear", listOf(DailyTotal(100, 14000, "wear"), DailyTotal(101, 15000, "wear")), todayIndex = 102)
        assertThat(multi.sources["wear"]!!.days.keys).containsExactly(100L, 101L)
        // a source whose only days fall outside the window is pruned
        multi = T.mergeSource(multi, "phone", listOf(DailyTotal(1, 9000, "phone")), todayIndex = 102)
        assertThat(multi.sources).doesNotContainKey("phone")
    }
}
