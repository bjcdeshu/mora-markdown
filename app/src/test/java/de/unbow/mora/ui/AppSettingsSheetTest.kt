package de.unbow.mora.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsSheetTest {

    @Test
    fun `Android 12 and below only follow the device language`() {
        assertEquals(
            AppLanguageSettingsState.FOLLOWS_DEVICE,
            appLanguageSettingsState(sdkInt = 32),
        )
    }

    @Test
    fun `Android 13 exposes system app language settings and checks launch at runtime`() {
        assertEquals(
            AppLanguageSettingsState.AVAILABLE,
            appLanguageSettingsState(sdkInt = 33),
        )
    }

    @Test
    fun `current Android keeps the system app language entry available`() {
        assertEquals(
            AppLanguageSettingsState.AVAILABLE,
            appLanguageSettingsState(sdkInt = 36),
        )
    }
}
