package app.aaps.plugins.aps

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey

/**
 * Boost dose-path preference reads that bypass Simple-Mode value masking.
 *
 * ## Why this exists
 * `PreferencesImpl.get(key)` returns the FACTORY DEFAULT for any `defaultedBySM = true` key while
 * Simple Mode is ON (`PreferencesImpl.kt:125-126` for Double, and the mirror branches for Boolean/
 * Int/UnitDouble/String). Every Boost dosing/safety key is declared `defaultedBySM = true` so that
 * Simple Mode HIDES it in the settings UI — but that same flag also silently masks the stored value
 * at read time. In Simple Mode the dosing engine therefore read `boost_maxIOB = 1.0` (not the user's
 * 8), `confirmedCap = 2.5`, `committedCap = 0.5`, etc. — regardless of what the user or auto-config
 * had configured. The base-oref tier guards (`iob < boostMaxIOB`) all skipped and V6's
 * `min(boost_maxIOB, max_iob)` clamped every dose to ~0, zeroing BOTH engines. One field user had V6
 * read-suppressed on ~94% of cycles (see `backtesting/reports/2026-07_maxiob_consistency_REPORT.md`).
 *
 * ## What this does
 * Returns the raw stored value (`getIfExists`) or, when the key was never persisted, the factory
 * default — exactly the pattern `BoostV5AutoConfig` already uses (`OpenAPSBoostV5Plugin.kt:175/194`),
 * which is why auto-config was UNAFFECTED by the mask. This DECOUPLES the two concerns:
 *  - the Simple-Mode UI HIDING stays (the keys keep `defaultedBySM = true`, so they remain hidden);
 *  - the doser no longer has its values masked — it honours the user's real / auto-configured settings.
 *
 * Bit-identity: for every Boost key (none of which set `calculatedBySM`, `calculatedDefaultValue`,
 * or `engineeringModeOnly`) this returns the SAME value `get(key)` returns when Simple Mode is OFF —
 * the stored value if present, else `defaultValue`. So with Simple Mode OFF, behaviour is unchanged.
 *
 * Use ONLY for Boost dose-path reads. Leave UI/preference-screen reads and non-Boost keys on `get()`.
 */
fun Preferences.getBoostDosing(key: DoublePreferenceKey): Double = getIfExists(key) ?: key.defaultValue
fun Preferences.getBoostDosing(key: BooleanPreferenceKey): Boolean = getIfExists(key) ?: key.defaultValue
fun Preferences.getBoostDosing(key: IntPreferenceKey): Int = getIfExists(key) ?: key.defaultValue
fun Preferences.getBoostDosing(key: UnitDoublePreferenceKey): Double = getIfExists(key) ?: key.defaultValue
fun Preferences.getBoostDosing(key: StringPreferenceKey): String = getIfExists(key) ?: key.defaultValue
