package app.aaps.plugins.aps.openAPSBoostV5

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * 2026-07-06 — composed Phase-3 floor (F = 0.25), SHADOW ONLY.
 *
 * Forensic + 40,180-cycle cohort backtest: on meal-session high cycles the composed post-budget
 * multiplier (stateMult × velocityFactor × iobHeadroomBrake × decelerationBrake) has MEDIAN
 * 0.037 — the V4-era multiplicative brake stack reassembled — and doses floor-round to zero for
 * 30+ min mid-meal (Episode B: BG 268–277, six zero cycles, ended 297 + manual bolus).
 *
 * These tests drive the FULL decide() pipeline with an Episode-B-like fixture and assert:
 * (a) the shadow arithmetic, (b) every gating condition flips floorWouldAdd to null,
 * (c) budget = 0 → null (Episode-A guard BY CONSTRUCTION), and (d) the DELIVERED dose is
 * unchanged in all cases — the floor is telemetry only.
 */
class ComposedFloorShadowTest {

    private val determineBasal = DetermineBasalBoostV5()

    /**
     * Episode-B-like fixture: persisted COMMITTED holding state on a high, slow, decelerating
     * meal tail. Composed soft multiplier = velocityFactor 0.40 (rise 12 ≤ 25) × iobHeadroomBrake
     * 0.40 (iob 8.5/10 ≥ 0.85) × decelerationBrake 0.30 (accl −15, delta 2 ≤ 8) = 0.048 — the
     * backtest's ~0.04 median. Pipeline: budget 0.5 × COMMITTED 1.0 → velocity 0.2 → state cap
     * 0.2 → brakes → 0.024 → rounds (0.05 step) to ZERO. deltaHistory is flat, so deltaDeclining
     * is false and COMMITTED does NOT back off to RECOVERING despite accl −15.
     */
    private fun episodeBInputs() = V5Inputs(
        delta = 2.0,
        shortAvgDelta = 2.0,
        deltaAccl = -15.0,
        bg = 270.0,
        eventualBg = 280.0,
        targetBg = 100.0,
        maxDelta = 2.0,
        minGuardBg = 150.0,
        minGuardThreshold = 80.0,
        deltaHistory = listOf(2.0, 2.0, 2.0),
        iob = 8.5,
        maxIob = 10.0,
        baseInsulinReq = 0.5,        // budget = 0.5 (no ML damping, no post-ex, sensitivity 1.0)
        roundSmbTo = 0.05,
        enableSmbPreChecks = true,
        mlHypoRisk = null,
        mlMealLikely = 0.5,
        recentLowBg = 120.0,
        cumulativeRise30min = 12.0,  // ≤ 25 → velocityFactor 0.40
        hour = 12,
        exerciseActive = false,
        inPostExerciseWindow = false,
        asleep = false,
        committedCapU = 0.5,
        confirmedCapU = 2.5,
        postRescueWindow = false,
        v1WouldDoseU = null,
    )

    private fun committedState() = V5PersistedState(
        mealHypothesis = MealHypothesisState(MealHypothesis.COMMITTED, ageCycles = 1, committedInSession = true)
    )

    private fun recoveringState() = V5PersistedState(
        mealHypothesis = MealHypothesisState(MealHypothesis.RECOVERING, ageCycles = 1, committedInSession = true)
    )

    // ── Arithmetic on the Episode-B fixture ─────────────────────────────────────────────────────

    @Test fun `Episode-B-like COMMITTED zero-dose cycle - floor would add 0_125U, delivery untouched`() {
        val d = determineBasal.decide(episodeBInputs(), committedState())
        assertThat(d.mealHypothesis).isEqualTo(MealHypothesis.COMMITTED)
        // The defect: composed mult 0.048 drives 0.2U pre-brake dose to 0.024U → rounds to ZERO.
        assertThat(d.finalDose).isWithin(1e-9).of(0.0)
        // Shadow: flooredDose = min(budget 0.5 × 0.25, committedCap 0.5) = 0.125; wouldAdd =
        // max(0, 0.125 − 0.0) = 0.125. Exactly the spec fixture (budget 0.5, composed ~0.04).
        assertThat(d.floorWouldAdd).isNotNull()
        assertThat(d.floorWouldAdd!!).isWithin(1e-9).of(0.125)
    }

    @Test fun `committedCap bounds the floored dose (one routine hold is the ceiling)`() {
        val d = determineBasal.decide(episodeBInputs().copy(committedCapU = 0.1), committedState())
        // min(0.5 × 0.25, 0.1) = 0.1 → wouldAdd 0.1.
        assertThat(d.floorWouldAdd!!).isWithin(1e-9).of(0.1)
    }

    // ── Condition gating: each condition flips the shadow to null ──────────────────────────────

    @Test fun `bg at or below 160 - null`() {
        val d = determineBasal.decide(episodeBInputs().copy(bg = 150.0), committedState())
        assertThat(d.floorWouldAdd).isNull()
    }

    @Test fun `eventualBg not more than target+20 - null`() {
        val d = determineBasal.decide(episodeBInputs().copy(eventualBg = 115.0), committedState())
        assertThat(d.floorWouldAdd).isNull()
    }

    @Test fun `asleep - null`() {
        val d = determineBasal.decide(episodeBInputs().copy(asleep = true), committedState())
        assertThat(d.floorWouldAdd).isNull()
    }

    @Test fun `post-rescue window - null`() {
        val d = determineBasal.decide(episodeBInputs().copy(postRescueWindow = true), committedState())
        assertThat(d.floorWouldAdd).isNull()
    }

    @Test fun `non-meal-session state - null`() {
        // Persisted IDLE; whatever the step produces (IDLE or OBSERVING) is outside the
        // CONFIRMED/COMMITTED/RECOVERING meal-session set.
        val d = determineBasal.decide(episodeBInputs(), V5PersistedState())
        assertThat(d.mealHypothesis).isAnyOf(MealHypothesis.IDLE, MealHypothesis.OBSERVING)
        assertThat(d.floorWouldAdd).isNull()
    }

    @Test fun `budget zero - null (Episode-A guard BY CONSTRUCTION)`() {
        // baseInsulinReq = 0 → budget = 0 (the AggressionBudget floor is a FRACTION of
        // baseInsulinReq, so it is 0 too). A zero-budget cycle — the Episode-A shape — can never
        // produce a floored dose: the budget > 0 condition nulls the shadow by construction.
        val d = determineBasal.decide(episodeBInputs().copy(baseInsulinReq = 0.0), committedState())
        assertThat(d.aggressionBudget.budget).isWithin(1e-12).of(0.0)
        assertThat(d.floorWouldAdd).isNull()
        assertThat(d.finalDose).isWithin(1e-9).of(0.0)
    }

    @Test fun `phase-3 HARD gate fired - wouldAdd is zero, not the floored dose`() {
        // minGuardBg below threshold → hard gate zeroes the dose regardless of any multiplier
        // floor, so the floored pipeline would deliver 0 too. Conditions ARE met → 0.0, not null.
        val d = determineBasal.decide(episodeBInputs().copy(minGuardBg = 70.0), committedState())
        assertThat(d.finalDose).isWithin(1e-9).of(0.0)
        assertThat(d.floorWouldAdd).isNotNull()
        assertThat(d.floorWouldAdd!!).isWithin(1e-9).of(0.0)
    }

    // ── RECOVERING: v1-bound where applicable (non-meal-state cap at the override seam) ─────────

    @Test fun `RECOVERING floored dose is bounded at V1's would-dose`() {
        // delta 2 ≥ 0, score ≈0.38 ≥ 0.18, accl −15 ≤ re-engage threshold → stays RECOVERING.
        val d = determineBasal.decide(episodeBInputs().copy(v1WouldDoseU = 0.05), recoveringState())
        assertThat(d.mealHypothesis).isEqualTo(MealHypothesis.RECOVERING)
        // flooredDose 0.125 → bounded to v1Would 0.05 (RECOVERING is v1-capped at the seam).
        assertThat(d.floorWouldAdd!!).isWithin(1e-9).of(0.05)
    }

    @Test fun `RECOVERING without a v1 bound uses the unbounded floored dose`() {
        val d = determineBasal.decide(episodeBInputs().copy(v1WouldDoseU = null), recoveringState())
        assertThat(d.mealHypothesis).isEqualTo(MealHypothesis.RECOVERING)
        assertThat(d.floorWouldAdd!!).isWithin(1e-9).of(0.125)
    }

    @Test fun `COMMITTED is NOT v1-bound (meal state keeps the full floored dose)`() {
        val d = determineBasal.decide(episodeBInputs().copy(v1WouldDoseU = 0.05), committedState())
        assertThat(d.mealHypothesis).isEqualTo(MealHypothesis.COMMITTED)
        assertThat(d.floorWouldAdd!!).isWithin(1e-9).of(0.125)
    }

    // ── SHADOW-ONLY invariant: delivered dose unchanged in all cases ───────────────────────────

    @Test fun `delivered dose is identical whether or not the shadow computes`() {
        val active = determineBasal.decide(episodeBInputs(), committedState())
        val nulled = determineBasal.decide(episodeBInputs().copy(postRescueWindow = true), committedState())
        // postRescueWindow feeds ONLY the shadow — every dosing output must be bit-identical.
        assertThat(active.finalDose).isEqualTo(nulled.finalDose)
        assertThat(active.insulinToDeliver).isEqualTo(nulled.insulinToDeliver)
        assertThat(active.phase3.reductions).isEqualTo(nulled.phase3.reductions)
        assertThat(active.floorWouldAdd).isNotNull()
        assertThat(nulled.floorWouldAdd).isNull()
    }
}
