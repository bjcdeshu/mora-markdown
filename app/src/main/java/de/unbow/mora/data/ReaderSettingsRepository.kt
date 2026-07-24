package de.unbow.mora.data

import android.content.Context
import de.unbow.mora.markdown.ReaderPreferences

object ReaderSettingsRepository {

    private const val preferencesName = "mora_reader_settings"
    private const val fontSizeKey = "font_size"
    private const val lineHeightKey = "line_height"
    private const val horizontalPaddingKey = "horizontal_padding"

    fun load(context: Context): ReaderPreferences {
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        return ReaderPreferences(
            fontSizePx = preferences.getFloat(fontSizeKey, ReaderPreferences.Default.fontSizePx)
                .coerceIn(15f, 21f),
            lineHeight = preferences.getFloat(lineHeightKey, ReaderPreferences.Default.lineHeight)
                .coerceIn(1.5f, 2.1f),
            horizontalPaddingPx = preferences.getFloat(
                horizontalPaddingKey,
                ReaderPreferences.Default.horizontalPaddingPx,
            ).coerceIn(16f, 34f),
        )
    }

    fun save(context: Context, preferences: ReaderPreferences) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putFloat(fontSizeKey, preferences.fontSizePx)
            .putFloat(lineHeightKey, preferences.lineHeight)
            .putFloat(horizontalPaddingKey, preferences.horizontalPaddingPx)
            .apply()
    }
}
