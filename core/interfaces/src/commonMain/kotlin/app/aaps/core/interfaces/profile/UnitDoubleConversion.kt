package app.aaps.core.interfaces.profile

import app.aaps.core.data.configuration.Constants
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey

/**
 * A stored unit-double as the user's units. Glucose values keep the magnitude detection that decodes
 * numbers saved by 3.3 in either unit; an offset is always mg/dL, see [UnitDoublePreferenceKey.isOffset].
 */
fun ProfileUtil.unitDoubleToUnits(key: UnitDoublePreferenceKey, stored: Double): Double =
    if (key.isOffset) fromMgdlToUnits(offsetMgdl(key, stored), units) else valueInCurrentUnitsDetect(stored)

/** A value the user entered in their units, as the mg/dL number to store. */
fun ProfileUtil.unitDoubleToMgdl(key: UnitDoublePreferenceKey, display: Double): Double =
    if (key.isOffset) convertToMgdl(display, units) else convertToMgdlDetect(display)

// Before offsets were marked, the settings screen detected units on entry, so a mg/dL user typing 27
// stored 27 mmol/L as 486. The screen bounds every entry to maxMgdl, so a larger number can only be that.
// It repairs entries of 5 to 35 mg/dL; 1 to 4 were stored as 18 to 72, which a mmol/L user could also
// have saved, so those cannot be told apart and read wider (more cautious).
private fun offsetMgdl(key: UnitDoublePreferenceKey, stored: Double): Double =
    if (stored > key.maxMgdl) stored * Constants.MGDL_TO_MMOLL else stored
