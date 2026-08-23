package de.unbow.mora.platform

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri

internal enum class MoraExternalPage(
    val url: String,
) {
    REPOSITORY("https://github.com/bjcdeshu/mora-markdown"),
    LATEST_RELEASE("https://github.com/bjcdeshu/mora-markdown/releases/latest"),
    FEEDBACK("https://github.com/bjcdeshu/mora-markdown/issues/15"),
    LICENSE("https://github.com/bjcdeshu/mora-markdown/blob/main/LICENSE"),
    THIRD_PARTY_NOTICES(
        "https://github.com/bjcdeshu/mora-markdown/blob/main/THIRD_PARTY_NOTICES.md",
    ),
}

internal data class InstalledAppMetadata(
    val appName: String,
    val versionName: String?,
    val versionCode: Long?,
    val sdkInt: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
)

internal data class AppDiagnostics(
    val versionName: String?,
    val versionCode: Long?,
    val sdkInt: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val localeTags: String,
    val appTheme: String,
    val appLanguage: String,
)

internal fun loadInstalledAppMetadata(context: Context): InstalledAppMetadata {
    val packageInfo = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    @Suppress("DEPRECATION")
    val versionCode = packageInfo?.let { info ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    }

    return InstalledAppMetadata(
        appName = try {
            context.applicationInfo.loadLabel(context.packageManager).toString()
        } catch (_: RuntimeException) {
            ""
        },
        versionName = packageInfo?.versionName?.takeIf(String::isNotBlank),
        versionCode = versionCode,
        sdkInt = Build.VERSION.SDK_INT,
        deviceManufacturer = Build.MANUFACTURER.orEmpty(),
        deviceModel = Build.MODEL.orEmpty(),
    )
}

internal fun buildDiagnosticsText(diagnostics: AppDiagnostics): String {
    val versionName = diagnosticsValue(diagnostics.versionName)
    val versionCode = diagnostics.versionCode?.toString() ?: DIAGNOSTICS_UNKNOWN_VALUE
    val manufacturer = diagnosticsValue(diagnostics.deviceManufacturer)
    val model = diagnosticsValue(diagnostics.deviceModel)
    val localeTags = diagnosticsValue(diagnostics.localeTags)
    val appTheme = diagnosticsValue(diagnostics.appTheme)
    val appLanguage = diagnosticsValue(diagnostics.appLanguage)

    return buildString {
        appendLine("Mora diagnostics")
        appendLine("Version: $versionName ($versionCode)")
        appendLine("Android SDK: ${diagnostics.sdkInt}")
        appendLine("Device: $manufacturer $model")
        appendLine("Locale: $localeTags")
        appendLine("App theme: $appTheme")
        append("App language: $appLanguage")
    }
}

internal fun openMoraExternalPage(
    context: Context,
    page: MoraExternalPage,
): Boolean = try {
    val intent = Intent(Intent.ACTION_VIEW, page.url.toUri())
        .addCategory(Intent.CATEGORY_BROWSABLE)
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
} catch (_: RuntimeException) {
    false
}

internal fun copyMoraDiagnostics(
    context: Context,
    label: String,
    diagnostics: AppDiagnostics,
): Boolean = try {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(
        ClipData.newPlainText(label, buildDiagnosticsText(diagnostics)),
    )
    true
} catch (_: SecurityException) {
    false
} catch (_: RuntimeException) {
    false
}

internal fun loadBundledThirdPartyNotices(context: Context): String? = runCatching {
    context.assets.open(BUNDLED_THIRD_PARTY_NOTICES).bufferedReader().use { reader ->
        reader.readText()
    }
}.getOrNull()?.takeIf(String::isNotBlank)

private fun diagnosticsValue(value: String?): String {
    val singleLine = value
        .orEmpty()
        .replace(DIAGNOSTICS_LINE_BREAKS, " ")
        .trim()
    return singleLine.ifEmpty { DIAGNOSTICS_UNKNOWN_VALUE }
}

private const val DIAGNOSTICS_UNKNOWN_VALUE = "unknown"
private const val BUNDLED_THIRD_PARTY_NOTICES = "THIRD_PARTY_NOTICES.md"
private val DIAGNOSTICS_LINE_BREAKS = Regex("[\\p{Cc}\\p{Zl}\\p{Zp}]+")
