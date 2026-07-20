package app.aaps.plugins.aps.openAPSBoostTwin

/**
 * KAIROS — anticipatory BACK-OUT controller, SHADOW (2026-07-20).
 *
 * Runs the retractable-anticipation state machine and logs what it WOULD do; delivers NOTHING. It is the
 * foundation the anticipation layer depends on: an anticipatory add-insulin action must be retractable and
 * unwound the moment the anticipated event fails to confirm — so anticipation needn't be accurate, only
 * retractable. See backtesting/scripts/2026-07-anticipation-backout/BACKOUT_CONTROLLER_SPEC.md.
 *
 * Until the event-anticipator model exists, the ARM trigger is a PLACEHOLDER: mlMealLikely crossing a
 * threshold (an existing meal-likelihood signal). Confirmation = the Twin's inferred meal appearance Ra
 * rising OR BG rising within a deadline (validated crux, kairos-lab E08: confirm AUC 0.83–0.87). Back-out
 * = deadline-without-confirm OR an early low-trip (BG or the Twin lo30 heading low). All READ-ONLY:
 * emits an `antBackout=...` reason tag the extractor parses; the delivered dose is never touched.
 *
 * State is held in memory across cycles (like TwinShadow's ensemble); re-arms cleanly after a restart.
 * Pure + deterministic given inputs (unit-testable). NOTE Ra is in mg/dL/min (raMargin matches E08).
 */
class AnticipationBackoutShadow(
    private val windowMin: Double = 40.0,        // deadline to confirm before back-out
    private val raMargin: Double = 1.0,          // Ra rise (mg/dL/min) that counts as a meal appearing (E08)
    private val bgConfirmRise: Double = 15.0,    // BG rise (mg/dL) that counts as an excursion started
    private val lowTrip: Double = 85.0,          // early back-out floor
    private val trigMealLikely: Double = 0.6,    // placeholder ARM trigger until the anticipator model exists
) {
    private enum class St { IDLE, ARMED }
    private var st = St.IDLE
    private var ra0 = 0.0
    private var bg0 = 0.0
    private var armedAtMs = 0L

    /** One cycle. Returns the reason-tag payload (or null if inputs unusable). Delivers nothing. */
    fun runCycle(nowMs: Long, bg: Double?, ra: Double?, lo30: Double?, mealLikely: Double?): String? {
        if (bg == null || ra == null) return null
        val ml = mealLikely ?: 0.0
        var confirmed = 0
        var backedOut = 0
        var trip = 0
        when (st) {
            St.IDLE ->
                if (ml >= trigMealLikely) {              // anticipation fires → ARM (would pre-position)
                    st = St.ARMED; ra0 = ra; bg0 = bg; armedAtMs = nowMs
                }
            St.ARMED -> {
                val ageMin = (nowMs - armedAtMs) / 60000.0
                val raConfirm = (ra - ra0) >= raMargin
                val bgConfirm = (bg - bg0) >= bgConfirmRise
                val lowNow = bg < lowTrip || (lo30 != null && lo30 < 70.0)
                when {
                    raConfirm || bgConfirm -> { confirmed = 1; st = St.IDLE }   // meal real → hand off
                    lowNow                 -> { trip = 1; backedOut = 1; st = St.IDLE }  // early back-out
                    ageMin >= windowMin    -> { backedOut = 1; st = St.IDLE }   // deadline back-out
                }
            }
        }
        // state after transition, plus the deltas — enough to reconstruct ARM→confirm/back-out economics
        return "$st,${round3(ra0)},${round3(ra)},${bg0.toInt()},${bg.toInt()}," +
            "$confirmed,$backedOut,$trip,${round2(ml)}"
    }

    private fun round2(x: Double) = Math.round(x * 100.0) / 100.0
    private fun round3(x: Double) = Math.round(x * 1000.0) / 1000.0
}
