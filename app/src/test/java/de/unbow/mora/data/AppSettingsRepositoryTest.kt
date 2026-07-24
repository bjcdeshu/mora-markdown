package de.unbow.mora.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsRepositoryTest {

    @Test
    fun missingValuesDecodeToDefaults() {
        assertEquals(AppSettings(), decodeAppSettings(null, null, null))
    }

    @Test
    fun legalValuesDecodeWithoutChangingThem() {
        assertEquals(
            AppSettings(
                themeMode = ThemeMode.DARK,
                darkSurfaceStyle = DarkSurfaceStyle.PURE_BLACK,
                launcherIcon = LauncherIcon.NIGHT,
            ),
            decodeAppSettings(
                themeMode = "DARK",
                darkSurfaceStyle = "PURE_BLACK",
                launcherIcon = "NIGHT",
            ),
        )
    }

    @Test
    fun savedValuesLoadBackUnchanged() {
        val repository = AppSettingsRepository(InMemorySharedPreferences())
        val expected = AppSettings(
            themeMode = ThemeMode.LIGHT,
            darkSurfaceStyle = DarkSurfaceStyle.PURE_BLACK,
            launcherIcon = LauncherIcon.PINE,
        )

        repository.save(expected)

        assertEquals(expected, repository.load())
    }

    @Test
    fun anUnknownFieldFallsBackWithoutDiscardingOtherLegalFields() {
        assertEquals(
            AppSettings(
                themeMode = ThemeMode.SYSTEM,
                darkSurfaceStyle = DarkSurfaceStyle.PURE_BLACK,
                launcherIcon = LauncherIcon.NIGHT,
            ),
            decodeAppSettings(
                themeMode = "UNKNOWN",
                darkSurfaceStyle = "PURE_BLACK",
                launcherIcon = "NIGHT",
            ),
        )
        assertEquals(
            AppSettings(
                themeMode = ThemeMode.LIGHT,
                darkSurfaceStyle = DarkSurfaceStyle.DEFAULT,
                launcherIcon = LauncherIcon.PINE,
            ),
            decodeAppSettings(
                themeMode = "LIGHT",
                darkSurfaceStyle = "UNKNOWN",
                launcherIcon = "PINE",
            ),
        )
        assertEquals(
            AppSettings(
                themeMode = ThemeMode.DARK,
                darkSurfaceStyle = DarkSurfaceStyle.PURE_BLACK,
                launcherIcon = LauncherIcon.INDIGO,
            ),
            decodeAppSettings(
                themeMode = "DARK",
                darkSurfaceStyle = "PURE_BLACK",
                launcherIcon = "UNKNOWN",
            ),
        )
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(key: String?, defaultValue: String?): String? =
        values[key] as? String ?: defaultValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defaultValues: Set<String>?): Set<String>? =
        values[key] as? Set<String> ?: defaultValues

    override fun getInt(key: String?, defaultValue: Int): Int =
        values[key] as? Int ?: defaultValue

    override fun getLong(key: String?, defaultValue: Long): Long =
        values[key] as? Long ?: defaultValue

    override fun getFloat(key: String?, defaultValue: Float): Float =
        values[key] as? Float ?: defaultValue

    override fun getBoolean(key: String?, defaultValue: Boolean): Boolean =
        values[key] as? Boolean ?: defaultValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(values)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private class Editor(
        private val values: MutableMap<String, Any?>,
    ) : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor =
            update(key, value)

        override fun putStringSet(
            key: String?,
            values: Set<String>?,
        ): SharedPreferences.Editor = update(key, values?.toSet())

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor =
            update(key, value)

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor =
            update(key, value)

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor =
            update(key, value)

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            update(key, value)

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            key?.let {
                removals += it
                updates -= it
            }
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested = true
            updates.clear()
            removals.clear()
        }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun update(key: String?, value: Any?): SharedPreferences.Editor = apply {
            key?.let {
                updates[it] = value
                removals -= it
            }
        }

        private fun applyChanges() {
            if (clearRequested) values.clear()
            removals.forEach(values::remove)
            updates.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }
    }
}
