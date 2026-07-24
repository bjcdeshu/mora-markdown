package de.unbow.mora.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsSheetTest {

    @Test
    fun `Android 12 and below only follow the device language`() {
        assertEquals(
            AppLanguageSettingsState.FOLLOWS_DEVICE,
            appLanguageSettingsState(sdkInt = 32, hasSettingsHandler = true),
        )
        assertEquals(
            AppLanguageSettingsState.FOLLOWS_DEVICE,
            appLanguageSettingsState(sdkInt = 32, hasSettingsHandler = false),
        )
    }

    @Test
    fun `Android 13 opens system app language settings when handled`() {
        assertEquals(
            AppLanguageSettingsState.AVAILABLE,
            appLanguageSettingsState(sdkInt = 33, hasSettingsHandler = true),
        )
    }

    @Test
    fun `Android 13 safely disables an unavailable system language entry`() {
        assertEquals(
            AppLanguageSettingsState.UNAVAILABLE,
            appLanguageSettingsState(sdkInt = 33, hasSettingsHandler = false),
        )
    }
}
