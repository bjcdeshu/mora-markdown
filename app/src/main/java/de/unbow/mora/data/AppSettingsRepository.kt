package de.unbow.mora.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class DarkSurfaceStyle {
    DEFAULT,
    PURE_BLACK,
}

enum class LauncherIcon {
    INDIGO,
    PINE,
    NIGHT,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val darkSurfaceStyle: DarkSurfaceStyle = DarkSurfaceStyle.DEFAULT,
    val launcherIcon: LauncherIcon = LauncherIcon.INDIGO,
)

fun decodeAppSettings(
    themeMode: String?,
    darkSurfaceStyle: String?,
    launcherIcon: String?,
): AppSettings = AppSettings(
    themeMode = decodeEnum(themeMode, ThemeMode.SYSTEM),
    darkSurfaceStyle = decodeEnum(darkSurfaceStyle, DarkSurfaceStyle.DEFAULT),
    launcherIcon = decodeEnum(launcherIcon, LauncherIcon.INDIGO),
)

class AppSettingsRepository internal constructor(
    private val preferences: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE),
    )

    fun load(): AppSettings {
        val storedValues = preferences.all
        return decodeAppSettings(
            themeMode = storedValues[themeModeKey] as? String,
            darkSurfaceStyle = storedValues[darkSurfaceStyleKey] as? String,
            launcherIcon = storedValues[launcherIconKey] as? String,
        )
    }

    fun save(settings: AppSettings) {
        preferences.edit {
            putString(themeModeKey, settings.themeMode.name)
            putString(darkSurfaceStyleKey, settings.darkSurfaceStyle.name)
            putString(launcherIconKey, settings.launcherIcon.name)
        }
    }

    private companion object {
        const val preferencesName = "mora_app_settings"
        const val themeModeKey = "theme_mode"
        const val darkSurfaceStyleKey = "dark_surface_style"
        const val launcherIconKey = "launcher_icon"
    }
}

private inline fun <reified T : Enum<T>> decodeEnum(
    storedValue: String?,
    defaultValue: T,
): T = enumValues<T>().firstOrNull { value -> value.name == storedValue } ?: defaultValue
