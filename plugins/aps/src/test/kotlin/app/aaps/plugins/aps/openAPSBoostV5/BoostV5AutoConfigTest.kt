package app.aaps.plugins.aps.openAPSBoostV5

import app.aaps.core.keys.DoubleKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for the V5 auto-config calculator: conservative, transparent derivation of V5 knobs from a
 * user's last-N-day V1 history. Pure-function tests on [BoostV5AutoConfig.compute].
 */
class BoostV5AutoConfigTest {

    private fun profile(
        days: Int = 14, bg: Int = 3500, tdd: Double = 40.0,
        manual: List<Double> = listOf(3.0, 4.0, 5.0, 6.0),
        smb: List<Double> = listOf(0.2, 0.3, 0.4, 0.6, 0.8),
        tbr70: Double = 3.0, sev54: Double = 0.4, meanBg: Double = 130.0,
        maxIob: Double = 8.0, maxBolus: Double = 10.0
    ) = BoostV5AutoConfig.V1Profile(days, bg, tdd, manual, smb, tbr70, sev54, meanBg, maxIob, maxBolus)

    @Test fun `insufficient days returns null`() {
        assertThat(BoostV5AutoConfig.compute(profile(days = 5))).isNull()
    }

    @Test fun `insufficient bg readings returns null`() {
        assertThat(BoostV5AutoConfig.compute(profile(bg = 800))).isNull()
    }

    @Test fun `in-target user gets neutral aggression and no extra caution`() {
        val s = BoostV5AutoConfig.compute(profile(tbr70 = 2.5, sev54 = 0.2))!!
        assertThat(s.aggression).isEqualTo(1.0)
        assertThat(s.hypoCaution).isEqualTo(1.0)
        assertThat(s.fastCarbConfirm).isTrue()
    }

    @Test fun `hypo-prone user gets gentler aggression, higher caution, fast-carb off`() {
        val s = BoostV5AutoConfig.compute(profile(tbr70 = 8.0, sev54 = 2.5))!!
        assertThat(s.aggression).isEqualTo(0.85)
        assertThat(s.hypoCaution).isGreaterThan(1.0)
        assertThat(s.fastCarbConfirm).isFalse()
    }

    @Test fun `aggression is never auto-raised above neutral`() {
        // Even a pristine, never-low user does not get aggression > 1.0 on day one.
        val s = BoostV5AutoConfig.compute(profile(tbr70 = 0.5, sev54 = 0.0))!!
        assertThat(s.aggression).isAtMost(1.0)
    }

    @Test fun `caps derive from dose distribution and clamp to ranges`() {
        val s = BoostV5AutoConfig.compute(profile(manual = listOf(2.0, 3.0, 4.0, 5.0), smb = listOf(0.3, 0.5, 0.7)))!!
        assertThat(s.confirmedCapU).isAtLeast(1.5)
        assertThat(s.confirmedCapU).isAtMost(7.5)
        assertThat(s.committedCapU).isAtLeast(0.25)
        assertThat(s.committedCapU).isAtMost(2.5)
        assertThat(s.cumulativeSmbCap60MinU).isAtLeast(1.0)
        assertThat(s.cumulativeSmbCap60MinU).isAtMost(5.0)
        // cumulative cap is never below a single confirm shot (it must allow ≥1 confirm)
        assertThat(s.cumulativeSmbCap60MinU).isAtLeast(s.confirmedCapU - 1e-9)
    }

    @Test fun `confirmed cap covers a big-meal bolus user`() {
        val big = BoostV5AutoConfig.compute(profile(manual = listOf(5.0, 7.0, 9.0, 11.0)))!!
        val small = BoostV5AutoConfig.compute(profile(manual = listOf(1.0, 1.5, 2.0)))!!
        assertThat(big.confirmedCapU).isGreaterThan(small.confirmedCapU)
    }

    @Test fun `cumulative cap is never below a single confirmed shot, even for a big-meal user`() {
        // Big eater: confirmedCap clamps to its 7.5 ceiling. The hourly cumulative budget must not
        // saturate below that (was clamped to 5.0 before the 2026-06-26 fix).
        val s = BoostV5AutoConfig.compute(profile(manual = listOf(5.0, 7.0, 9.0, 11.0)))!!
        assertThat(s.confirmedCapU).isEqualTo(7.5)
        assertThat(s.cumulativeSmbCap60MinU).isAtLeast(s.confirmedCapU - 1e-9)
    }

    @Test fun `maxIob and bolus cap are carried and clamped`() {
        val s = BoostV5AutoConfig.compute(profile(maxIob = 15.0, maxBolus = 12.0))!!
        assertThat(s.maxIobU).isEqualTo(12.0)   // clamped to key max
        assertThat(s.bolusCapU).isEqualTo(10.0) // clamped to key max
    }

    @Test fun `percentile interpolates`() {
        val v = listOf(1.0, 2.0, 3.0, 4.0)
        assertThat(BoostV5AutoConfig.percentile(v, 0.0)).isEqualTo(1.0)
        assertThat(BoostV5AutoConfig.percentile(v, 100.0)).isEqualTo(4.0)
        assertThat(BoostV5AutoConfig.percentile(v, 50.0)).isWithin(1e-9).of(2.5)
        assertThat(BoostV5AutoConfig.percentile(emptyList(), 90.0)).isEqualTo(0.0)
    }

    @Test fun `rationale explains every setting`() {
        val s = BoostV5AutoConfig.compute(profile())!!
        assertThat(s.rationale).isNotEmpty()
        assertThat(s.rationale.any { it.contains("HypoCaution") }).isTrue()
        assertThat(s.rationale.any { it.contains("Aggression") }).isTrue()
    }

    // ── Application of the suggestion (BoostV5AutoConfigApply): per-key resolution ──
    // These exercise the SAME helper OpenAPSBoostV5Plugin.maybeAutoConfigure uses, so they lock:
    // tuning one V6 knob must not block the others; each knob resolves (applied once, or
    // skipped-because-user-tuned) exactly once; insufficient data leaves knobs unresolved.

    /** Minimal in-memory stand-in for the plugin's preference + resolution-mark I/O. */
    private class FakeStore(vararg preset: Pair<DoubleKey, Double>) {
        val store = linkedMapOf(*preset)
        val resolved = mutableSetOf<DoubleKey>()
        fun apply(knobs: List<Pair<DoubleKey, Double>>) = BoostV5AutoConfigApply.applyAutoConfig(
            knobs,
            isResolved = { it in resolved },
            storedValue = { store[it] },
            put = { k, v -> store[k] = v },
            markResolved = { resolved += it }
        )
    }

    @Test fun `managed knobs cover exactly the auto-configured doubles`() {
        val keys = BoostV5AutoConfigApply.managedDoubleKnobs(BoostV5AutoConfig.compute(profile())!!).map { it.first }
        assertThat(keys).containsExactlyElementsIn(BoostV5AutoConfigApply.managedDoubleKeys)
        assertThat(keys).containsExactly(
            DoubleKey.ApsBoostV5Aggression, DoubleKey.ApsBoostV5HypoCaution,
            DoubleKey.ApsBoostV5ConfirmedCapU, DoubleKey.ApsBoostV5CommittedCapU,
            DoubleKey.ApsBoostCumulativeSmbCap60Min, DoubleKey.ApsBoostMaxIob, DoubleKey.ApsBoostBolus
        )
    }

    @Test fun `with nothing preset, every knob is configured and resolved — including the cumulative cap`() {
        val s = BoostV5AutoConfig.compute(profile())!!
        val knobs = BoostV5AutoConfigApply.managedDoubleKnobs(s)
        val f = FakeStore()
        val applied = f.apply(knobs)
        assertThat(applied.map { it.first }).containsExactlyElementsIn(knobs.map { it.first })
        assertThat(f.store.keys).containsExactlyElementsIn(knobs.map { it.first })
        assertThat(f.resolved).containsExactlyElementsIn(knobs.map { it.first })
        // The cumulative 60-min SMB cap is derived, WRITTEN, and part of the applied (=notified) list.
        assertThat(f.store[DoubleKey.ApsBoostCumulativeSmbCap60Min]).isEqualTo(s.cumulativeSmbCap60MinU)
        assertThat(applied).contains(DoubleKey.ApsBoostCumulativeSmbCap60Min to s.cumulativeSmbCap60MinU)
    }

    @Test fun `tuning one knob keeps it (resolved-skipped) and still configures all the others`() {
        val knobs = BoostV5AutoConfigApply.managedDoubleKnobs(BoostV5AutoConfig.compute(profile())!!)
        val preset = DoubleKey.ApsBoostCumulativeSmbCap60Min   // user tuned the SMB cap (≠ default 10.0)
        val presetValue = 2.5
        val f = FakeStore(preset to presetValue)
        val applied = f.apply(knobs)
        val others = knobs.map { it.first }.filter { it != preset }
        // tuned knob NOT applied; every other knob IS; tuned knob still RESOLVED (never revisited)
        assertThat(applied.map { it.first }).containsExactlyElementsIn(others)
        assertThat(applied.map { it.first }).doesNotContain(preset)
        assertThat(f.resolved).contains(preset)
        // tuned value untouched; all others now written with the suggested value
        assertThat(f.store[preset]).isEqualTo(presetValue)
        knobs.filter { it.first != preset }.forEach { (k, v) -> assertThat(f.store[k]).isEqualTo(v) }
    }

    @Test fun `a knob persisted AT its factory default does not block the suggestion`() {
        // Roman's failure mode with the old presence test: committedCap existed in storage at the
        // stock 0.5 (settings import / pref-dialog OK) and was skipped forever. Value == default
        // means nobody objected — the suggestion must still be applied.
        val s = BoostV5AutoConfig.compute(profile())!!
        val knobs = BoostV5AutoConfigApply.managedDoubleKnobs(s)
        val key = DoubleKey.ApsBoostV5CommittedCapU
        val f = FakeStore(key to key.defaultValue)             // present, but stock
        val applied = f.apply(knobs)
        assertThat(applied.map { it.first }).contains(key)
        assertThat(f.store[key]).isEqualTo(s.committedCapU)
    }

    @Test fun `once applied, a knob is resolved and never re-applied`() {
        val knobs = BoostV5AutoConfigApply.managedDoubleKnobs(BoostV5AutoConfig.compute(profile())!!)
        val f = FakeStore()
        f.apply(knobs)
        // User later sets a knob BACK to something (even the suggestion's own value re-derivation
        // would overwrite differently) — a second run must not touch anything.
        f.store[DoubleKey.ApsBoostV5CommittedCapU] = 0.33
        val secondKnobs = knobs.map { (k, v) -> k to v * 2 }   // a different (re-derived) suggestion
        val appliedAgain = f.apply(secondKnobs)
        assertThat(appliedAgain).isEmpty()
        assertThat(f.store[DoubleKey.ApsBoostV5CommittedCapU]).isEqualTo(0.33)
    }

    @Test fun `insufficient data resolves nothing so knobs genuinely retry`() {
        // The caller gets no suggestion → applyAutoConfig is never invoked → no knob resolves.
        assertThat(BoostV5AutoConfig.compute(profile(days = 5))).isNull()
        val f = FakeStore()
        assertThat(f.resolved).isEmpty()                        // still all eligible
        // Once data accrues, the SAME store applies everything.
        val knobs = BoostV5AutoConfigApply.managedDoubleKnobs(BoostV5AutoConfig.compute(profile())!!)
        assertThat(f.apply(knobs).map { it.first }).containsExactlyElementsIn(knobs.map { it.first })
    }

    // ── Migration from the legacy global done-flag ──

    @Test fun `legacy-flag migration resolves only knobs off their factory default`() {
        val tuned = DoubleKey.ApsBoostV5ConfirmedCapU           // user/old-run value ≠ default 2.5
        val stock = DoubleKey.ApsBoostV5CommittedCapU           // persisted AT default 0.5 (Roman)
        val store = mapOf(tuned to 4.0, stock to stock.defaultValue)  // others absent
        val resolved = mutableSetOf<DoubleKey>()
        val migrated = BoostV5AutoConfigApply.migrateLegacyDoneFlag(
            BoostV5AutoConfigApply.managedDoubleKeys,
            storedValue = { store[it] },
            markResolved = { resolved += it }
        )
        assertThat(migrated).containsExactly(tuned)             // off-default → left alone forever
        assertThat(resolved).containsExactly(tuned)
        // stock + absent keys stay UNRESOLVED → eligible for derivation again on the next cycle
        assertThat(resolved).doesNotContain(stock)
    }

    @Test fun `roman regression — flag consumed, keys at defaults, rich history — caps get applied`() {
        // Roman: V6-active 06-30, months of history, TDD ~50U; committedCap stuck at factory 0.5
        // although his derived value is 1.24. After migration (nothing resolved because everything
        // is at stock), the next cycle must apply BOTH the committed cap and the cumulative cap.
        val roman = profile(
            tdd = 49.6,                                          // 49.6/40 = 1.24 committed
            smb = listOf(0.5, 0.5, 0.5, 0.5, 0.5),               // p75 clipped at the old 0.5 cap
            manual = listOf(4.0, 5.0, 6.0, 6.0, 6.0)             // p90 = 6.0 → confirmedCap 6.0
        )
        val s = BoostV5AutoConfig.compute(roman)!!
        assertThat(s.confirmedCapU).isEqualTo(6.0)
        assertThat(s.committedCapU).isEqualTo(1.24)
        // cumulative = clamp(6.0 + 2×1.24, 1.0, max(5.0, 6.0)) = clamp(8.48 → 6.0) = 6.0
        assertThat(s.cumulativeSmbCap60MinU).isEqualTo(6.0)

        // Storage as found in the field: managed knobs present at stock (or absent) after the old run.
        val f = FakeStore(
            DoubleKey.ApsBoostV5CommittedCapU to DoubleKey.ApsBoostV5CommittedCapU.defaultValue,
            DoubleKey.ApsBoostCumulativeSmbCap60Min to DoubleKey.ApsBoostCumulativeSmbCap60Min.defaultValue
        )
        val migrated = BoostV5AutoConfigApply.migrateLegacyDoneFlag(
            BoostV5AutoConfigApply.managedDoubleKeys, storedValue = { f.store[it] }, markResolved = { f.resolved += it }
        )
        assertThat(migrated).isEmpty()                           // nothing off-default → all eligible
        val applied = f.apply(BoostV5AutoConfigApply.managedDoubleKnobs(s))
        assertThat(applied.map { it.first }).contains(DoubleKey.ApsBoostV5CommittedCapU)
        assertThat(applied.map { it.first }).contains(DoubleKey.ApsBoostCumulativeSmbCap60Min)
        assertThat(f.store[DoubleKey.ApsBoostV5CommittedCapU]).isEqualTo(1.24)
        assertThat(f.store[DoubleKey.ApsBoostCumulativeSmbCap60Min]).isEqualTo(6.0)
        // Invariant: the suggestion never auto-raises Aggression above neutral.
        assertThat(s.aggression).isAtMost(1.0)
    }
}
