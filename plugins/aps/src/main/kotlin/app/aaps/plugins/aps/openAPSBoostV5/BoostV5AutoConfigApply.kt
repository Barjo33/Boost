package app.aaps.plugins.aps.openAPSBoostV5

import app.aaps.core.keys.DoubleKey
import kotlin.math.abs

/**
 * Pure helpers for applying a [BoostV5AutoConfig.V5Suggestion] to preferences while respecting any
 * value the user — or a preset (e.g. a pre-seeded keystore/import) — has ALREADY set.
 *
 * Separated from [OpenAPSBoostV5Plugin]'s preference I/O so the invariants Tim cares about are
 * unit-testable: presetting ONE V6 knob must NOT block the others — the preset value is kept and
 * every other unset knob is still configured.
 *
 * ── Per-key resolution (2026-07 fix, field evidence: Roman) ──────────────────────────────────
 * The original design used one global "did run" flag plus a raw `sp.contains(key)` presence test.
 * Two field failure modes:
 *  1. Presence false-positives: anything that persists a knob AT its factory default (settings
 *     import, opening the pref dialog and tapping OK) made auto-config skip it forever — the user
 *     never objected, yet kept the stock value (Roman: committedCap stuck at factory 0.5 while his
 *     derived value was 1.24).
 *  2. Global one-shot: once the flag was consumed (older build, or carried in via a settings
 *     import), settings ADDED to auto-config later were never derived for existing installs.
 * The fix: each managed knob is tracked individually as RESOLVED once it has either been applied
 * once or been skipped because the user tuned it (stored value differs from the factory default).
 * Insufficient data resolves nothing, so unresolved knobs genuinely retry on later cycles.
 * "User tuned it" now means *value differs from the key's factory default* — mere presence in
 * storage no longer blocks a suggestion (value == default means nobody objected).
 */
internal object BoostV5AutoConfigApply {

    /**
     * Tolerance for "still at factory default": preference values can round-trip through Float
     * (AdaptiveDoublePreference persists floats), so exact Double equality would be fragile.
     */
    private const val DEFAULT_EPS = 1e-4

    /** The double-valued V5 knobs auto-config manages (stable order). */
    val managedDoubleKeys: List<DoubleKey> = listOf(
        DoubleKey.ApsBoostV5Aggression,
        DoubleKey.ApsBoostV5HypoCaution,
        DoubleKey.ApsBoostV5ConfirmedCapU,
        DoubleKey.ApsBoostV5CommittedCapU,
        DoubleKey.ApsBoostCumulativeSmbCap60Min,
        DoubleKey.ApsBoostMaxIob,
        DoubleKey.ApsBoostBolus
    )

    /** [managedDoubleKeys] paired with their suggested values (same stable order). */
    fun managedDoubleKnobs(s: BoostV5AutoConfig.V5Suggestion): List<Pair<DoubleKey, Double>> = listOf(
        DoubleKey.ApsBoostV5Aggression to s.aggression,
        DoubleKey.ApsBoostV5HypoCaution to s.hypoCaution,
        DoubleKey.ApsBoostV5ConfirmedCapU to s.confirmedCapU,
        DoubleKey.ApsBoostV5CommittedCapU to s.committedCapU,
        DoubleKey.ApsBoostCumulativeSmbCap60Min to s.cumulativeSmbCap60MinU,
        DoubleKey.ApsBoostMaxIob to s.maxIobU,
        DoubleKey.ApsBoostBolus to s.bolusCapU
    )

    /**
     * "User (or preset) has tuned this knob": a value exists in storage AND it differs from the
     * key's factory default. A missing value, or a value persisted AT the default (settings-screen
     * visit, import of stock settings), does NOT count as tuned — nobody objected to the default,
     * so the suggestion may still be applied.
     */
    fun isUserTuned(key: DoubleKey, storedValue: Double?): Boolean =
        storedValue != null && abs(storedValue - key.defaultValue) > DEFAULT_EPS

    /**
     * Apply the suggestion with per-knob resolution. For each knob, in order:
     *  - already RESOLVED (applied or skipped in an earlier run) → untouched;
     *  - user-tuned ([isUserTuned]) → kept, marked resolved (never revisited);
     *  - otherwise → suggested value written, marked resolved.
     * Per-knob and independent — presetting one never blocks the others. Returns the knobs actually
     * written (for the "configured N setting(s)" notification). Pure: the lambdas inject the
     * preference I/O so all skip/resolve behaviour is testable without the plugin/DI.
     *
     * NOT called when there is insufficient history (the caller gets no suggestion), so unresolved
     * knobs remain eligible and genuinely retry on a later cycle.
     */
    fun applyAutoConfig(
        knobs: List<Pair<DoubleKey, Double>>,
        isResolved: (DoubleKey) -> Boolean,
        storedValue: (DoubleKey) -> Double?,
        put: (DoubleKey, Double) -> Unit,
        markResolved: (DoubleKey) -> Unit
    ): List<Pair<DoubleKey, Double>> {
        val applied = mutableListOf<Pair<DoubleKey, Double>>()
        for ((key, value) in knobs) {
            if (isResolved(key)) continue
            if (isUserTuned(key, storedValue(key))) {
                markResolved(key)                      // user value kept; never revisit
                continue
            }
            put(key, value)
            markResolved(key)
            applied += key to value
        }
        return applied
    }

    /**
     * One-time migration from the legacy global "auto-config done" flag to per-key resolution.
     * Called when the legacy flag is found set: marks as resolved ONLY the keys whose stored value
     * differs from the factory default (they were plausibly applied by the old run, or user-set —
     * either way they must not be rewritten). Keys still AT their factory default stay UNRESOLVED
     * and become eligible for derivation again — this is what rescues installs where the old
     * presence-test (or a consumed flag) wrongly skipped them; suggestion-only still holds because
     * value == default means nobody objected. Returns the keys marked resolved.
     */
    fun migrateLegacyDoneFlag(
        keys: List<DoubleKey>,
        storedValue: (DoubleKey) -> Double?,
        markResolved: (DoubleKey) -> Unit
    ): List<DoubleKey> =
        keys.filter { isUserTuned(it, storedValue(it)) }.onEach(markResolved)
}
