package app.aaps.ui.compose.overview.boost

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Both Boost overview palettes must stay readable. Text needs 4.5:1 against the background and the cards
 * (WCAG AA). Status, glucose and chart line colours need 3:1 (WCAG for graphics). The night glucose
 * colours on white (`#FFFF00` is about 1.1:1) are the case this was written for.
 */
class BoostV2PaletteTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun BoostV2Palette.text() = mapOf(
        "textPrimary" to textPrimary, "textSecondary" to textSecondary, "labelGrey" to labelGrey, "accent" to accent
    )

    private fun BoostV2Palette.graphics() = mapOf(
        "green" to green, "amber" to amber, "red" to red, "blue" to blue, "purple" to purple, "orange" to orange,
        "bgUrgent" to bgUrgent, "bgHigh" to bgHigh, "bgInRange" to bgInRange, "bgLow" to bgLow,
        "chartBolus" to chartBolus, "chartPredIob" to chartPredIob, "chartPredCob" to chartPredCob,
        "chartPredUam" to chartPredUam, "chartPredZt" to chartPredZt, "chartInsulinActivity" to chartInsulinActivity,
        "chartBasalLine" to chartBasalLine, "chartHeartRateLine" to chartHeartRateLine, "chartBgReading" to chartBgReading
    )

    private fun check(palette: BoostV2Palette) {
        val name = if (palette.isDark) "dark" else "light"
        for ((surfaceName, surface) in mapOf("background" to palette.background, "cardBackground" to palette.cardBackground, "chartBackground" to palette.chartBackground)) {
            palette.text().forEach { (n, c) ->
                assertWithMessage("$name $n on $surfaceName").that(contrast(c, surface)).isAtLeast(4.5)
            }
            palette.graphics().forEach { (n, c) ->
                assertWithMessage("$name $n on $surfaceName").that(contrast(c, surface)).isAtLeast(3.0)
            }
        }
    }

    @Test
    fun `dark palette is readable`() = check(DarkBoostV2Palette)

    @Test
    fun `light palette is readable`() = check(LightBoostV2Palette)

    @Test
    fun `night glucose colours would fail on the light background`() {
        // Guards the check itself: it must reject the colours the light palette replaced.
        val light = LightBoostV2Palette
        assertWithMessage("night high on light").that(contrast(DarkBoostV2Palette.bgHigh, light.background)).isLessThan(3.0)
        assertWithMessage("night in range on light").that(contrast(DarkBoostV2Palette.bgInRange, light.background)).isLessThan(3.0)
    }

    @Test
    fun `palettes differ and say which they are`() {
        assertWithMessage("dark flag").that(DarkBoostV2Palette.isDark).isTrue()
        assertWithMessage("light flag").that(LightBoostV2Palette.isDark).isFalse()
        assertWithMessage("backgrounds").that(DarkBoostV2Palette.background).isNotEqualTo(LightBoostV2Palette.background)
    }
}
