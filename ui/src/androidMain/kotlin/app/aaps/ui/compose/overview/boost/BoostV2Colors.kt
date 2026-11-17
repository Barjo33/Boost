package app.aaps.ui.compose.overview.boost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.aaps.core.interfaces.overview.ActivityMode
import app.aaps.core.ui.compose.LocalAapsIsDark
import app.aaps.core.ui.compose.StatusLevel

/**
 * Colour palette for the Boost overview, in a dark and a light version.
 *
 * The overview has its own card design (dark: background `#0d0f14`, cyan accent), so it keeps its own
 * palette rather than the Material colours. Which version shows follows the AAPS colour scheme setting
 * through [LocalAapsIsDark], the same setting the rest of the app and the system bar icons follow.
 * The dark values are the original V2 colours. The light values keep the same hues, darkened where a
 * colour must stay readable on white (the night glucose colours `#FFFF00` and `#00FF00` are not);
 * `BoostV2PaletteTest` checks the contrast of both.
 */
data class BoostV2Palette(
    val isDark: Boolean,

    // Surfaces
    val background: Color,
    val cardBackground: Color,
    val stroke: Color,

    // Accent
    val accent: Color,
    val accentFill: Color,      // 10% accent: badge / action-button background
    val accentStroke: Color,    // 15% accent: action-button border

    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val labelGrey: Color,

    // Semantic
    val green: Color,
    val amber: Color,
    val red: Color,
    val blue: Color,
    val purple: Color,
    val orange: Color,

    // Glucose colours (urgent, high, in range, low)
    val bgUrgent: Color,
    val bgHigh: Color,
    val bgInRange: Color,
    val bgLow: Color,

    // Chart
    val chartBackground: Color,
    val chartInRangeFill: Color,
    val chartInRangeUpperBorder: Color,
    val chartInRangeLowerBorder: Color,
    val chartTargetLine: Color,
    val chartNowLine: Color,
    val chartIobFill: Color,
    val chartBolus: Color,
    val chartPredIob: Color,
    val chartPredCob: Color,
    val chartPredACob: Color,
    val chartPredUam: Color,
    val chartPredZt: Color,
    val chartInsulinActivity: Color,
    val chartBasalLine: Color,
    val chartBasalFill: Color,
    val chartActivityFill: Color,
    val chartHeartRateLine: Color,
    val chartBgReading: Color
) {

    // Pill value colours
    val pillIob get() = blue
    val pillDynIsf get() = purple
    val pillTdd get() = orange
    val pillProfile get() = accent

    /** Range-selector active/inactive backgrounds. */
    val rangeActiveFill get() = accentFill
    val rangeInactiveFill get() = Color.Transparent

    val chartIobLine get() = blue
    val chartCarbs get() = amber
    val chartActivityLine get() = chartInsulinActivity
    val chartGrid get() = stroke
    val chartAxisLabel get() = textSecondary
}

/** The original V2 colours, copied from the live `boost_overview_v2_fragment.xml` and the 6.1.2 night theme. */
val DarkBoostV2Palette = BoostV2Palette(
    isDark = true,
    background = Color(0xFF0D0F14),
    cardBackground = Color(0xFF10121A),
    stroke = Color(0xFF1A1D28),
    accent = Color(0xFF00D4FF),
    accentFill = Color(0x1A00D4FF),
    accentStroke = Color(0x2600D4FF),
    textPrimary = Color(0xFFE8EAF0),
    textSecondary = Color(0xFFAAAAAA),
    labelGrey = Color(0xFF9AA0AD),
    green = Color(0xFF4CAF50),
    amber = Color(0xFFFFB300),
    red = Color(0xFFFF5252),
    blue = Color(0xFF60A5FA),
    purple = Color(0xFFC084FC),
    orange = Color(0xFFFB923C),
    bgUrgent = Color(0xFFFF4444),
    bgHigh = Color(0xFFFFFF00),
    bgInRange = Color(0xFF00FF00),
    bgLow = Color(0xFFFF0000),
    chartBackground = Color(0xFF0A0C10),
    chartInRangeFill = Color(0x0C00D4FF),
    chartInRangeUpperBorder = Color(0x3300D4FF),
    chartInRangeLowerBorder = Color(0x4DFF5252),
    chartTargetLine = Color(0x26FFFFFF),
    chartNowLine = Color(0x1AFFFFFF),
    chartIobFill = Color(0x6660A5FA),
    chartBolus = Color(0xFF1EA3E5),
    chartPredIob = Color(0xFF1E88E5),
    chartPredCob = Color(0xFFFB8C00),
    chartPredACob = Color(0x99FB8C00),
    chartPredUam = Color(0xFFC9BD60),
    chartPredZt = Color(0xFF00D2D2),
    chartInsulinActivity = Color(0xFFD3F166),
    chartBasalLine = Color(0xFF00FFFF),
    chartBasalFill = Color(0x59FB923C),
    chartActivityFill = Color(0x4DD3F166),
    chartHeartRateLine = Color(0xFFFF6B81),
    chartBgReading = Color(0xFFE8EAF0)
)

/** Same hues as the dark palette, darkened so lines and text stay readable on a light background. */
val LightBoostV2Palette = BoostV2Palette(
    isDark = false,
    background = Color(0xFFF4F6FA),
    cardBackground = Color(0xFFFFFFFF),
    stroke = Color(0xFFDDE1EA),
    accent = Color(0xFF00749E),
    accentFill = Color(0x1A00749E),
    accentStroke = Color(0x2600749E),
    textPrimary = Color(0xFF1A1D28),
    textSecondary = Color(0xFF5C6270),
    labelGrey = Color(0xFF636A78),
    green = Color(0xFF2E7D32),
    amber = Color(0xFFA05A00),
    red = Color(0xFFC62828),
    blue = Color(0xFF1D5FD1),
    purple = Color(0xFF7E22CE),
    orange = Color(0xFFC2410C),
    bgUrgent = Color(0xFFB71C1C),
    bgHigh = Color(0xFFD35400),
    bgInRange = Color(0xFF2E8B34),
    bgLow = Color(0xFFE53935),
    chartBackground = Color(0xFFFAFBFD),
    chartInRangeFill = Color(0x1400749E),
    chartInRangeUpperBorder = Color(0x5500749E),
    chartInRangeLowerBorder = Color(0x66C62828),
    chartTargetLine = Color(0x33000000),
    chartNowLine = Color(0x26000000),
    chartIobFill = Color(0x551D5FD1),
    chartBolus = Color(0xFF0B7BC0),
    chartPredIob = Color(0xFF1565C0),
    chartPredCob = Color(0xFFC86400),
    chartPredACob = Color(0x99C86400),
    chartPredUam = Color(0xFF8C8020),
    chartPredZt = Color(0xFF00897B),
    chartInsulinActivity = Color(0xFF6B8E00),
    chartBasalLine = Color(0xFF00838F),
    chartBasalFill = Color(0x40FB923C),
    chartActivityFill = Color(0x406B8E00),
    chartHeartRateLine = Color(0xFFD81B60),
    chartBgReading = Color(0xFF3A3F4B)
)

/** The palette for the current AAPS colour scheme. */
val BoostV2Colors: BoostV2Palette
    @Composable @ReadOnlyComposable get() = if (LocalAapsIsDark.current) DarkBoostV2Palette else LightBoostV2Palette

/** 5-zone BG ring colour by mg/dL. */
internal fun BoostV2Palette.bgZoneColor(bgMgdl: Double): Color = when {
    bgMgdl > 250 -> bgUrgent   // very high — urgent red
    bgMgdl > 180 -> bgHigh      // high — orange
    bgMgdl >= 70 -> bgInRange   // in range — green
    bgMgdl >= 54 -> bgLow       // low — deep orange
    bgMgdl > 0   -> bgUrgent    // very low — urgent red
    else         -> labelGrey   // no reading — grey, never the in-range green
}

/** Pump reservoir / battery / age warning level → V2 colour. */
internal fun BoostV2Palette.statusLevelColorV2(level: StatusLevel): Color = when (level) {
    StatusLevel.NORMAL      -> textPrimary
    StatusLevel.WARNING     -> amber
    StatusLevel.CRITICAL    -> red
    StatusLevel.UNSPECIFIED -> textSecondary
}

/** AID (loop) status → indicator dot / text colour. */
internal fun BoostV2Palette.aidStatusColorV2(status: AidStatus): Color = when (status) {
    AidStatus.ACTIVE    -> green
    AidStatus.ATTENTION -> amber
    AidStatus.ALERT     -> red
    AidStatus.DISABLED  -> labelGrey
}

/** Exercise / activity mode → label colour (matches `updateHeroRight`). */
internal fun BoostV2Palette.activityModeColorV2(mode: ActivityMode): Color = when (mode) {
    ActivityMode.ACTIVE    -> amber
    ActivityMode.INACTIVE  -> blue
    ActivityMode.SLEEP_IN  -> purple
    ActivityMode.BOOST_OFF -> labelGrey
    ActivityMode.NORMAL    -> textSecondary
}
