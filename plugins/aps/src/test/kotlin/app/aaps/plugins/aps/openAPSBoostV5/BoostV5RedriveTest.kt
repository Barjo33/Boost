package app.aaps.plugins.aps.openAPSBoostV5

import app.aaps.core.keys.DoubleKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * 2026-08-03 periodic re-derivation — pure-function tests of [BoostV5AutoConfigApply.redrive].
 *
 * Design evidence: only committedCap and the cumulative 60-min cap have five-month drift that
 * beats their own measurement noise (2.72 [1.15, 4.59] and 2.33 [1.06, 3.63]); hypoCaution and
 * aggression do not, so they are deliberately out of scope. The deadbands are the measured
 * bootstrap half-widths over a 28-day window. See REDRIVE_REPORT.md and CADENCE_GRID.md.
 */
class BoostV5RedriveTest {

    /** A profile whose derived committedCap is driven by TDD/40, which is what actually binds. */
    private fun suggestionForTdd(tdd: Double, tbr70: Double = 1.0, sev54: Double = 0.1) =
        BoostV5AutoConfig.compute(
            BoostV5AutoConfig.V1Profile(
                daysWithData = 28, bgReadingCount = 6000, tddMedianU = tdd,
                manualBolusesU = List(20) { 4.0 }, smbAmountsU = List(200) { 0.3 },
                tbrBelow70Pct = tbr70, timeBelow54Pct = sev54, meanGlucoseMgdl = 130.0,
                currentMaxIobU = 6.0, currentMaxBolusU = 2.5
            )
        )!!

    private class Store(
        val stored: MutableMap<DoubleKey, Double>,
        val applied: MutableMap<DoubleKey, Double>
    ) {
        val writes = mutableListOf<Pair<DoubleKey, Double>>()
        val retired = mutableListOf<DoubleKey>()
        val pending = mutableMapOf<DoubleKey, Double>()
        fun run(s: BoostV5AutoConfig.V5Suggestion, tbr70: Double = 1.0, sev54: Double = 0.1) =
            BoostV5AutoConfigApply.redrive(
                s, tbr70, sev54,
                storedValue = { stored[it] },
                appliedValue = { applied[it] },
                pendingValue = { pending[it] },
                put = { k, v -> writes += k to v; stored[k] = v },
                setApplied = { k, v -> applied[k] = v },
                setPending = { k, v -> if (v == null) pending.remove(k) else pending[k] = v },
                retire = { retired += it; applied.remove(it) })
    }

    private val CCAP = DoubleKey.ApsBoostV5CommittedCapU
    private val CUM = DoubleKey.ApsBoostCumulativeSmbCap60Min
    private val FCAP = DoubleKey.ApsBoostV5ConfirmedCapU

    @Test fun `a move bigger than the noise band is written`() {
        // TDD 60 -> committedCap 1.50, from a stored 1.00: a 0.50 move, well past the 0.07 band
        val st = Store(mutableMapOf(CCAP to 1.00, CUM to 4.0, FCAP to 3.0),
                       mutableMapOf(CCAP to 1.00, CUM to 4.0))
        val res = st.run(suggestionForTdd(60.0))
        val ccap = res.first { it.key == CCAP }
        assertThat(ccap.outcome).isEqualTo(BoostV5AutoConfigApply.Outcome.REDRIVEN)
        assertThat(ccap.operativeValue).isEqualTo(1.5)
        assertThat(st.stored[CCAP]).isEqualTo(1.5)
        assertThat(st.applied[CCAP]).isEqualTo(1.5)     // ledger follows, so we still own it
    }

    @Test fun `a move inside the noise band is not written`() {
        // TDD 40.4 -> 1.01 against a stored 1.00: 0.01, inside the 0.07 band
        val st = Store(mutableMapOf(CCAP to 1.00, CUM to 4.0, FCAP to 3.0),
                       mutableMapOf(CCAP to 1.00, CUM to 4.0))
        val res = st.run(suggestionForTdd(40.4))
        assertThat(res.first { it.key == CCAP }.outcome)
            .isEqualTo(BoostV5AutoConfigApply.Outcome.INSIDE_DEADBAND)
        assertThat(st.writes.none { it.first == CCAP }).isTrue()
        assertThat(st.stored[CCAP]).isEqualTo(1.00)
    }

    @Test fun `a knob the user has edited is retired, not overwritten`() {
        // we last wrote 1.00; the user has since set 1.40
        val st = Store(mutableMapOf(CCAP to 1.40, CUM to 4.0, FCAP to 3.0),
                       mutableMapOf(CCAP to 1.00, CUM to 4.0))
        val res = st.run(suggestionForTdd(60.0))
        assertThat(res.first { it.key == CCAP }.outcome)
            .isEqualTo(BoostV5AutoConfigApply.Outcome.RETIRED_USER_EDITED)
        assertThat(st.stored[CCAP]).isEqualTo(1.40)      // untouched
        assertThat(st.retired).contains(CCAP)
        assertThat(st.applied).doesNotContainKey(CCAP)   // never revisited
    }

    @Test fun `a knob auto-config never wrote is left alone entirely`() {
        val st = Store(mutableMapOf(CCAP to 1.00, CUM to 4.0, FCAP to 3.0), mutableMapOf())
        val res = st.run(suggestionForTdd(60.0))
        assertThat(res).isEmpty()
        assertThat(st.writes).isEmpty()
    }

    @Test fun `a raise is held when the TBR guard trips, a lowering still applies`() {
        val held = Store(mutableMapOf(CCAP to 1.00, CUM to 4.0, FCAP to 3.0),
                         mutableMapOf(CCAP to 1.00, CUM to 4.0))
        val r1 = held.run(suggestionForTdd(60.0), tbr70 = 6.0, sev54 = 0.2)   // raise, guard tripped
        assertThat(r1.first { it.key == CCAP }.outcome)
            .isEqualTo(BoostV5AutoConfigApply.Outcome.SUGGESTED_NOT_APPLIED_TBR)
        assertThat(held.stored[CCAP]).isEqualTo(1.00)

        val lower = Store(mutableMapOf(CCAP to 1.50, CUM to 4.0, FCAP to 3.0),
                          mutableMapOf(CCAP to 1.50, CUM to 4.0))
        val r2 = lower.run(suggestionForTdd(40.0), tbr70 = 6.0, sev54 = 0.2)  // lowering
        assertThat(r2.first { it.key == CCAP }.outcome)
            .isEqualTo(BoostV5AutoConfigApply.Outcome.REDRIVEN)
        assertThat(lower.stored[CCAP]).isEqualTo(1.0)
    }

    @Test fun `the severe-hypo co-guard also holds a raise`() {
        val st = Store(mutableMapOf(CCAP to 1.00, CUM to 4.0, FCAP to 3.0),
                       mutableMapOf(CCAP to 1.00, CUM to 4.0))
        val res = st.run(suggestionForTdd(60.0), tbr70 = 1.0, sev54 = 1.0)   // <70 fine, <54 over
        assertThat(res.first { it.key == CCAP }.outcome)
            .isEqualTo(BoostV5AutoConfigApply.Outcome.SUGGESTED_NOT_APPLIED_TBR)
    }

    // ── quantised knobs: hysteresis, not a deadband ───────────────────────────────────────────
    @Test fun `a quantised knob is not written until the same value repeats`() {
        val AGG = DoubleKey.ApsBoostV5Aggression
        val st = Store(mutableMapOf(AGG to 1.0, CCAP to 1.0, CUM to 4.0, FCAP to 3.0),
                       mutableMapOf(AGG to 1.0, CCAP to 1.0, CUM to 4.0, FCAP to 3.0))
        // TBR 5% -> aggression 0.92, a real threshold crossing
        val first = st.run(suggestionForTdd(40.0, tbr70 = 5.0), tbr70 = 5.0)
        assertThat(first.first { it.key == AGG }.outcome)
            .isEqualTo(BoostV5AutoConfigApply.Outcome.AWAITING_CONFIRMATION)
        assertThat(st.stored[AGG]).isEqualTo(1.0)                 // not written on first sight
        assertThat(st.pending[AGG]).isEqualTo(0.92)

        val second = st.run(suggestionForTdd(40.0, tbr70 = 5.0), tbr70 = 5.0)
        assertThat(second.first { it.key == AGG }.outcome)
            .isEqualTo(BoostV5AutoConfigApply.Outcome.REDRIVEN)
        assertThat(st.stored[AGG]).isEqualTo(0.92)
        assertThat(st.pending).doesNotContainKey(AGG)
    }

    @Test fun `a flapping quantised knob is never written`() {
        val AGG = DoubleKey.ApsBoostV5Aggression
        val st = Store(mutableMapOf(AGG to 1.0, CCAP to 1.0, CUM to 4.0, FCAP to 3.0),
                       mutableMapOf(AGG to 1.0, CCAP to 1.0, CUM to 4.0, FCAP to 3.0))
        // alternate either side of the 4% TBR line, as cohort user C did
        repeat(6) { i ->
            val tbr = if (i % 2 == 0) 5.0 else 3.0
            st.run(suggestionForTdd(40.0, tbr70 = tbr), tbr70 = tbr)
        }
        assertThat(st.stored[AGG]).isEqualTo(1.0)                 // never committed to the flap
        assertThat(st.writes.none { it.first == AGG }).isTrue()
    }

    @Test fun `hypoCaution is in scope and tightens without needing the raise-guard`() {
        val HC = DoubleKey.ApsBoostV5HypoCaution
        val st = Store(mutableMapOf(HC to 1.0, CCAP to 1.0, CUM to 4.0, FCAP to 3.0),
                       mutableMapOf(HC to 1.0, CCAP to 1.0, CUM to 4.0, FCAP to 3.0))
        // TBR 8% -> caution 2.0; a TIGHTENING, so the guard being tripped must not block it
        st.run(suggestionForTdd(40.0, tbr70 = 8.0, sev54 = 2.0), tbr70 = 8.0, sev54 = 2.0)
        val second = st.run(suggestionForTdd(40.0, tbr70 = 8.0, sev54 = 2.0), tbr70 = 8.0, sev54 = 2.0)
        assertThat(second.first { it.key == HC }.outcome)
            .isEqualTo(BoostV5AutoConfigApply.Outcome.REDRIVEN)
        assertThat(st.stored[HC]).isEqualTo(2.0)
    }

    @Test fun `every derived knob except the carried AAPS limits is in scope`() {
        assertThat(BoostV5AutoConfigApply.REDRIVE_KEYS).containsExactly(
            DoubleKey.ApsBoostV5Aggression, DoubleKey.ApsBoostV5HypoCaution,
            DoubleKey.ApsBoostV5ConfirmedCapU, DoubleKey.ApsBoostV5CommittedCapU,
            DoubleKey.ApsBoostCumulativeSmbCap60Min, DoubleKey.ApsBoostV5PrimerCapU)
        assertThat(BoostV5AutoConfigApply.REDRIVE_KEYS)
            .containsNoneOf(DoubleKey.ApsBoostMaxIob, DoubleKey.ApsBoostBolus)
    }

    @Test fun `no deadband is wider than its knob's own quantum`() {
        // hypoCaution (quantum 0.1) and aggression (0.07) must NOT be deadband-filtered — a band
        // wider than the quantum would freeze the knob below a double step.
        BoostV5AutoConfigApply.REDRIVE_CONFIRM_TWICE.forEach {
            assertThat(BoostV5AutoConfigApply.REDRIVE_DEADBAND).doesNotContainKey(it)
        }
    }

    @Test fun `only the scheduled knobs are ever touched`() {
        // every scheduled knob must be OWNED, or it is correctly skipped without a resolution
        val all = mapOf(CCAP to 1.00, CUM to 4.0, FCAP to 3.0,
                        DoubleKey.ApsBoostV5Aggression to 1.0,
                        DoubleKey.ApsBoostV5HypoCaution to 1.0,
                        DoubleKey.ApsBoostV5PrimerCapU to 0.3)
        val st = Store(all.toMutableMap(), all.toMutableMap())
        val res = st.run(suggestionForTdd(60.0, tbr70 = 8.0, sev54 = 2.0), tbr70 = 8.0, sev54 = 2.0)
        assertThat(res.map { it.key }.toSet()).isEqualTo(BoostV5AutoConfigApply.REDRIVE_KEYS.toSet())
        assertThat(st.writes.map { it.first })
            .containsNoneOf(DoubleKey.ApsBoostMaxIob, DoubleKey.ApsBoostBolus)
    }

    @Test fun `the cumulative cap is recomputed from the operative per-shot caps`() {
        // confirmedCap stays 3.0 (not re-derived); committedCap moves 1.00 -> 1.50
        // => cumulative should become 3.0 + 2*1.5 = 6.0, not whatever the derivation said
        val st = Store(mutableMapOf(CCAP to 1.00, CUM to 4.0, FCAP to 3.0),
                       mutableMapOf(CCAP to 1.00, CUM to 4.0))
        val res = st.run(suggestionForTdd(60.0))
        val cum = res.first { it.key == CUM }
        assertThat(cum.outcome).isEqualTo(BoostV5AutoConfigApply.Outcome.REDRIVEN)
        assertThat(cum.operativeValue).isEqualTo(6.0)
    }

    @Test fun `deadbands match the measured noise floors`() {
        assertThat(BoostV5AutoConfigApply.REDRIVE_DEADBAND[CCAP]).isEqualTo(0.07)
        assertThat(BoostV5AutoConfigApply.REDRIVE_DEADBAND[CUM]).isEqualTo(0.54)
        assertThat(BoostV5AutoConfigApply.REDRIVE_DEADBAND[FCAP]).isEqualTo(0.47)
    }

    @Test fun `cadence and window are the values the grid selected`() {
        assertThat(BoostV5AutoConfig.REDRIVE_INTERVAL_DAYS).isEqualTo(7L)
        assertThat(BoostV5AutoConfig.REDRIVE_LOOKBACK_DAYS).isEqualTo(28L)
    }
}
