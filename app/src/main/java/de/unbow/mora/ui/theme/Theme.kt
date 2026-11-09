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
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import de.unbow.mora.data.DarkSurfaceStyle
import de.unbow.mora.data.ThemeMode

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF2B4162),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4F2),
    onPrimaryContainer = Color(0xFF101D30),
    secondaryContainer = Color(0xFFE6ECF5),
    onSecondaryContainer = Color(0xFF1B2A40),
    surface = Color(0xFFFAF8F5),
    onSurface = Color(0xFF1B1F24),
    onSurfaceVariant = Color(0xFF525A65),
    surfaceContainerLow = Color(0xFFF3F0EA),
    surfaceContainer = Color(0xFFEDE9E1),
    surfaceContainerHigh = Color(0xFFE5DFD5),
    outlineVariant = Color(0xFFD5CFC4),
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFFAAC4E8),
    onPrimary = Color(0xFF122742),
    primaryContainer = Color(0xFF263D5C),
    onPrimaryContainer = Color(0xFFDCE4F2),
    secondaryContainer = Color(0xFF283548),
    onSecondaryContainer = Color(0xFFD7E2F0),
    surface = Color(0xFF12151A),
    onSurface = Color(0xFFE5E8EE),
    onSurfaceVariant = Color(0xFFA0A8B4),
    surfaceContainerLow = Color(0xFF181C22),
    surfaceContainer = Color(0xFF1E232B),
    surfaceContainerHigh = Color(0xFF262C36),
    outlineVariant = Color(0xFF333B48),
)

private val LocalMoraIsDarkProvider = staticCompositionLocalOf { false }

val LocalMoraIsDark: CompositionLocal<Boolean>
    get() = LocalMoraIsDarkProvider

internal fun shouldUseDynamicColor(
    sdkInt: Int,
    useDynamicColor: Boolean,
): Boolean = sdkInt >= Build.VERSION_CODES.S && useDynamicColor

@Composable
fun MoraTheme(
    themeMode: ThemeMode,
    darkSurfaceStyle: DarkSurfaceStyle,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val effectiveDark = resolveEffectiveDark(
        themeMode = themeMode,
        systemDark = isSystemInDarkTheme(),
    )
    val context = LocalContext.current
    val view = LocalView.current
    val dynamicEnabled = shouldUseDynamicColor(
        sdkInt = Build.VERSION.SDK_INT,
        useDynamicColor = useDynamicColor,
    )

    val baseColors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dynamicEnabled) {
        if (effectiveDark) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    } else if (effectiveDark) {
        FallbackDark
    } else {
        FallbackLight
    }
    val colors = applyDarkSurfaceStyle(
        colorScheme = baseColors,
        effectiveDark = effectiveDark,
        style = darkSurfaceStyle,
    )

    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        syncLegacyNavigationBarColor(
            activity = activity,
            sdkInt = Build.VERSION.SDK_INT,
            surfaceColor = colors.surface,
        )
        WindowInsetsControllerCompat(activity.window, view).apply {
            isAppearanceLightStatusBars = !effectiveDark
            isAppearanceLightNavigationBars = !effectiveDark
        }
    }

    CompositionLocalProvider(LocalMoraIsDarkProvider provides effectiveDark) {
        MaterialTheme(
            colorScheme = colors,
            content = content,
        )
    }
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

internal fun legacyNavigationBarColor(
    sdkInt: Int,
    surfaceColor: Color,
): Color? = surfaceColor.takeIf {
    sdkInt in Build.VERSION_CODES.O..Build.VERSION_CODES.P
}

@Suppress("DEPRECATION")
private fun syncLegacyNavigationBarColor(
    activity: Activity,
    sdkInt: Int,
    surfaceColor: Color,
) {
    legacyNavigationBarColor(sdkInt, surfaceColor)?.let { color ->
        activity.window.navigationBarColor = color.toArgb()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
