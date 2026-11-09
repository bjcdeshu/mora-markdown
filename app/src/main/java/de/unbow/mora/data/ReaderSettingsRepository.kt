package de.unbow.mora.data

import android.content.Context
import androidx.core.content.edit
import de.unbow.mora.markdown.ReaderFontFamily
import de.unbow.mora.markdown.ReaderPreferences
import de.unbow.mora.markdown.ReaderSurfaceTone

object ReaderSettingsRepository {

    private const val preferencesName = "mora_reader_settings"
    private const val fontSizeKey = "font_size"
    private const val lineHeightKey = "line_height"
    private const val horizontalPaddingKey = "horizontal_padding"
    private const val fontFamilyKey = "font_family"
    private const val surfaceToneKey = "surface_tone"

    fun load(context: Context): ReaderPreferences {
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val storedFontFamily = preferences.getString(fontFamilyKey, null)
        val storedSurfaceTone = preferences.getString(surfaceToneKey, null)
        return ReaderPreferences(
            fontSizePx = preferences.getFloat(fontSizeKey, ReaderPreferences.Default.fontSizePx)
                .coerceIn(15f, 21f),
            lineHeight = preferences.getFloat(lineHeightKey, ReaderPreferences.Default.lineHeight)
                .coerceIn(1.5f, 2.1f),
            horizontalPaddingPx = preferences.getFloat(
                horizontalPaddingKey,
                ReaderPreferences.Default.horizontalPaddingPx,
            ).coerceIn(16f, 34f),
            fontFamily = ReaderFontFamily.entries.firstOrNull { it.name == storedFontFamily }
                ?: ReaderPreferences.Default.fontFamily,
            surfaceTone = ReaderSurfaceTone.entries.firstOrNull { it.name == storedSurfaceTone }
                ?: ReaderPreferences.Default.surfaceTone,
        )
    }

    fun save(context: Context, preferences: ReaderPreferences) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit {
                putFloat(fontSizeKey, preferences.fontSizePx)
                putFloat(lineHeightKey, preferences.lineHeight)
                putFloat(horizontalPaddingKey, preferences.horizontalPaddingPx)
                putString(fontFamilyKey, preferences.fontFamily.name)
                putString(surfaceToneKey, preferences.surfaceTone.name)
            }
    }
}
