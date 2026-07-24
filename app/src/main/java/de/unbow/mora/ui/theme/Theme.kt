package de.unbow.mora.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import de.unbow.mora.data.DarkSurfaceStyle
import de.unbow.mora.data.ThemeMode

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF5D5B92),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E0FF),
    onPrimaryContainer = Color(0xFF19174A),
    secondaryContainer = Color(0xFFE2E0F9),
    surface = Color(0xFFFBF8FF),
    surfaceContainer = Color(0xFFF0EDF6),
    surfaceContainerHigh = Color(0xFFEAE7F0),
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFFC6C2FF),
    onPrimary = Color(0xFF2E2C60),
    primaryContainer = Color(0xFF454477),
    onPrimaryContainer = Color(0xFFE4E0FF),
    secondaryContainer = Color(0xFF454559),
    surface = Color(0xFF131318),
    surfaceContainer = Color(0xFF201F25),
    surfaceContainerHigh = Color(0xFF2B292F),
)

@Composable
fun MoraTheme(
    themeMode: ThemeMode,
    darkSurfaceStyle: DarkSurfaceStyle,
    content: @Composable () -> Unit,
) {
    val effectiveDark = resolveEffectiveDark(
        themeMode = themeMode,
        systemDark = isSystemInDarkTheme(),
    )
    val context = LocalContext.current
    val view = LocalView.current

    val baseColors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && effectiveDark ->
            dynamicDarkColorScheme(context)

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        effectiveDark -> FallbackDark
        else -> FallbackLight
    }
    val colors = applyDarkSurfaceStyle(
        colorScheme = baseColors,
        effectiveDark = effectiveDark,
        style = darkSurfaceStyle,
    )

    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        WindowInsetsControllerCompat(activity.window, view).apply {
            isAppearanceLightStatusBars = !effectiveDark
            isAppearanceLightNavigationBars = !effectiveDark
        }
    }

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}

internal fun resolveEffectiveDark(
    themeMode: ThemeMode,
    systemDark: Boolean,
): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

internal fun applyDarkSurfaceStyle(
    colorScheme: ColorScheme,
    effectiveDark: Boolean,
    style: DarkSurfaceStyle,
): ColorScheme {
    if (!effectiveDark || style != DarkSurfaceStyle.PURE_BLACK) return colorScheme

    return colorScheme.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceBright = Color(0xFF242424),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF101010),
        surfaceContainerHigh = Color(0xFF171717),
        surfaceContainerHighest = Color(0xFF1F1F1F),
        surfaceTint = Color.Transparent,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
