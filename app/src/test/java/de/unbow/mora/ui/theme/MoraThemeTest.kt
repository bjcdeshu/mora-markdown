package de.unbow.mora.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import de.unbow.mora.data.DarkSurfaceStyle
import de.unbow.mora.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MoraThemeTest {

    @Test
    fun defaultDarkSurfaceStyleLeavesTheSchemeUnchanged() {
        val original = darkColorScheme()

        val result = applyDarkSurfaceStyle(
            colorScheme = original,
            effectiveDark = true,
            style = DarkSurfaceStyle.DEFAULT,
        )

        assertSame(original, result)
    }

    @Test
    fun pureBlackOverridesEveryDarkNeutralSurfaceLevel() {
        val accent = Color(0xFFABCDEF)
        val original = darkColorScheme(primary = accent, surfaceTint = accent)

        val result = applyDarkSurfaceStyle(
            colorScheme = original,
            effectiveDark = true,
            style = DarkSurfaceStyle.PURE_BLACK,
        )

        assertEquals(Color.Black, result.background)
        assertEquals(Color.Black, result.surface)
        assertEquals(Color.Black, result.surfaceDim)
        assertEquals(Color.Black, result.surfaceContainerLowest)
        assertEquals(Color(0xFF0A0A0A), result.surfaceContainerLow)
        assertEquals(Color(0xFF101010), result.surfaceContainer)
        assertEquals(Color(0xFF171717), result.surfaceContainerHigh)
        assertEquals(Color(0xFF1F1F1F), result.surfaceContainerHighest)
        assertEquals(Color(0xFF242424), result.surfaceBright)
        assertEquals(Color.Transparent, result.surfaceTint)
        assertEquals(accent, result.primary)
        assertEquals(original.onSurface, result.onSurface)
    }

    @Test
    fun pureBlackDoesNotApplyToALightScheme() {
        val original = lightColorScheme()

        val result = applyDarkSurfaceStyle(
            colorScheme = original,
            effectiveDark = false,
            style = DarkSurfaceStyle.PURE_BLACK,
        )

        assertSame(original, result)
    }

    @Test
    fun themeModeResolvesAgainstTheSystemOnlyWhenRequested() {
        assertFalse(resolveEffectiveDark(ThemeMode.SYSTEM, systemDark = false))
        assertTrue(resolveEffectiveDark(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(resolveEffectiveDark(ThemeMode.LIGHT, systemDark = true))
        assertTrue(resolveEffectiveDark(ThemeMode.DARK, systemDark = false))
    }
}
