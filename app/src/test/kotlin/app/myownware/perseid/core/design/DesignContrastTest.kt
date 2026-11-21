package app.myownware.perseid.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignContrastTest {
    @Test
    fun textMeetsAaOnBothSurfaces() {
        for (palette in listOf(LightPalette, DarkPalette)) {
            for (surface in listOf(palette.scheme.background, palette.scheme.surface)) {
                for (text in listOf(palette.scheme.onSurface, palette.scheme.onSurfaceVariant, palette.scheme.error)) {
                    assertContrast(4.5, text, surface)
                }
                assertContrast(3.0, palette.scheme.outline, surface)
            }
            assertContrast(4.5, palette.scheme.onPrimary, palette.scheme.primary)
        }
    }

    @Test
    fun amberProgressIsDistinguishableFromItsTrack() {
        for (palette in listOf(LightPalette, DarkPalette)) {
            assertContrast(3.0, palette.scheme.primary, palette.progressTrack)
        }
    }

    private fun assertContrast(
        minimum: Double,
        first: Color,
        second: Color,
    ) {
        val (dark, light) = listOf(first.luminance(), second.luminance()).sorted()
        val ratio = (light + 0.05) / (dark + 0.05)
        assertTrue("Expected at least $minimum:1, measured $ratio:1", ratio >= minimum)
    }
}
