package app.aaps.plugins.aps.openAPSBoostV5

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * 2026-07-01 (Option A) — relative dose-adequacy gate on OBSERVING → CONFIRMED.
 *
 * The single per-session CONFIRMED commit-shot must not be spent while the meal's insulin need is
 * still trivial (the noisy pre-meal upswing). Otherwise the token lands as ~0.05U and the Fix-6
 * committedInSession lock starves the real meal at the COMMITTED cap for the rest of the rise
 * (10be 2026-07-01: eventualBG → 372 held at 0.4–0.5U/cycle). The caller computes
 * `confirmDoseAdequate = baseInsulinReq >= CONFIRM_MIN_INSULINREQ_FRAC_OF_MAXIOB * maxIob`.
 *
 * Pure-function tests on step(). The gate applies to the NORMAL path only; the fast-carb fast-path
 * is intentionally left ungated (it carries its own sharp-rise gates).
 */
class MealHypothesisDoseGateTest {

    // OBSERVING run that already satisfies the score + eventualBG-offset peaks and the age gate,
    // so the ONLY remaining variable is confirmDoseAdequate.
    private fun observedReady() =
        MealHypothesisState(MealHypothesis.OBSERVING, ageCycles = 2, maxScoreInObserving = 0.60, maxEventualBgOffsetInObserving = 40.0, committedInSession = false)

    // score ≥ CONFIRM_SCORE and ≥ FALL_BACK_TO_IDLE_SCORE; offset (eventualBg-targetBg) ≥ 30.
    private val score = 0.60
    private val eventualBg = 150.0
    private val targetBg = 100.0

    @Test fun `confirms when dose is adequate`() {
        val r = step(observedReady(), score, eventualBg, targetBg, delta = 6.0, deltaAccl = 2.0,
            deltaDeclining = false, confirmDoseAdequate = true)
        assertThat(r.state).isEqualTo(MealHypothesis.CONFIRMED)
    }

    @Test fun `does NOT confirm when dose is inadequate - stays OBSERVING`() {
        val r = step(observedReady(), score, eventualBg, targetBg, delta = 6.0, deltaAccl = 2.0,
            deltaDeclining = false, confirmDoseAdequate = false)
        // All other confirm predicates pass; only the dose floor blocks it. Score is above the
        // fall-back threshold, so it holds in OBSERVING rather than dropping to IDLE.
        assertThat(r.state).isEqualTo(MealHypothesis.OBSERVING)
    }

    @Test fun `default arg preserves legacy behaviour (adequate)`() {
        // Existing callers that don't pass confirmDoseAdequate must confirm exactly as before.
        val r = step(observedReady(), score, eventualBg, targetBg, delta = 6.0, deltaAccl = 2.0,
            deltaDeclining = false)
        assertThat(r.state).isEqualTo(MealHypothesis.CONFIRMED)
    }

    @Test fun `fast-carb path is NOT gated by dose adequacy`() {
        // Sharp, accelerating, corroborated rise with the toggle on still confirms in one cycle even
        // when confirmDoseAdequate=false — the fast-path is intentionally exempt.
        val r = step(MealHypothesisState(MealHypothesis.OBSERVING, 0, 0.5, 10.0, false),
            score = 0.7, eventualBg = eventualBg, targetBg = targetBg, delta = 12.0, deltaAccl = 30.0,
            deltaDeclining = false, asleep = false, exerciseActive = false,
            fastConfirmEnabled = true, confirmDoseAdequate = false)
        assertThat(r.state).isEqualTo(MealHypothesis.CONFIRMED)
    }
}
