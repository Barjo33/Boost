package app.aaps.core.keys.interfaces

interface UnitDoublePreferenceKey : PreferenceKey {

    /**
     * Default value if not changed from preferences
     */
    val defaultValue: Double

    /**
     *  Minimal allowed value
     */
    val minMgdl: Int

    /**
     *  Maximal allowed value
     */
    val maxMgdl: Int

    /**
     * A difference between two glucose values rather than a glucose value. Its stored number is always
     * mg/dL and must never be unit-detected by magnitude: 27 mg/dL is below the 36 that detection takes
     * for mmol/L, so it read back as 27 mmol/L.
     */
    val isOffset: Boolean get() = false

}