package app.aaps.core.interfaces.profile

/** Formats a glucose value for oref's console log. Not part of the dose calculation. */
interface ProfileUtil { fun fromMgdlToStringInUnits(value: Double): String }
class PlainProfileUtil : ProfileUtil {
    override fun fromMgdlToStringInUnits(value: Double): String = value.toString()
}
