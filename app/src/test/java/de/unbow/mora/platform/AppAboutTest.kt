package de.unbow.mora.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAboutTest {

    @Test
    fun `diagnostics contain only the approved support fields`() {
        val diagnostics = AppDiagnostics(
            versionName = "0.4.0",
            versionCode = 8,
            sdkInt = 36,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 10",
            localeTags = "zh-CN",
            appTheme = "system",
            appLanguage = "简体中文",
        )

        val text = buildDiagnosticsText(diagnostics)

        assertEquals(
            """
            Mora diagnostics
            Version: 0.4.0 (8)
            Android SDK: 36
            Device: Google Pixel 10
            Locale: zh-CN
            App theme: system
            App language: 简体中文
            """.trimIndent(),
            text,
        )
        listOf(
            "Document",
            "URI",
            "Path",
            "Account",
            "Credential",
            "Package",
        ).forEach { forbiddenLabel ->
            assertFalse(text.contains(forbiddenLabel, ignoreCase = true))
        }
    }

    @Test
    fun `diagnostic values cannot inject extra lines`() {
        val diagnostics = AppDiagnostics(
            versionName = "0.4.0\npreview",
            versionCode = null,
            sdkInt = 36,
            deviceManufacturer = "  ",
            deviceModel = "Pixel\r10",
            localeTags = "",
            appTheme = "dark",
            appLanguage = "English",
        )

        val text = buildDiagnosticsText(diagnostics)

        assertTrue(text.contains("Version: 0.4.0 preview (unknown)"))
        assertTrue(text.contains("Device: unknown Pixel 10"))
        assertTrue(text.contains("Locale: unknown"))
        assertEquals(7, text.lines().size)
    }

    @Test
    fun `about destinations are a fixed HTTPS allowlist`() {
        assertEquals(
            listOf(
                "https://github.com/bjcdeshu/mora-markdown",
                "https://github.com/bjcdeshu/mora-markdown/releases/latest",
                "https://github.com/bjcdeshu/mora-markdown/issues/15",
                "https://github.com/bjcdeshu/mora-markdown/blob/main/LICENSE",
                "https://github.com/bjcdeshu/mora-markdown/blob/main/THIRD_PARTY_NOTICES.md",
            ),
            MoraExternalPage.entries.map(MoraExternalPage::url),
        )
        assertTrue(MoraExternalPage.entries.all { page -> page.url.startsWith("https://") })
    }
}
