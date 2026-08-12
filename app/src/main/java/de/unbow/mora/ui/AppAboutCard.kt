package de.unbow.mora.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.unbow.mora.R
import de.unbow.mora.data.AppSettings
import de.unbow.mora.platform.AppDiagnostics
import de.unbow.mora.platform.MoraExternalPage
import de.unbow.mora.platform.copyMoraDiagnostics
import de.unbow.mora.platform.loadBundledThirdPartyNotices
import de.unbow.mora.platform.loadInstalledAppMetadata
import de.unbow.mora.platform.openMoraExternalPage
import java.util.Locale

@Composable
internal fun AppAboutCard(
    appSettings: AppSettings,
) {
    val context = LocalContext.current
    val localeTags = LocalConfiguration.current.locales.toLanguageTags()
    val installedApp = remember(context) { loadInstalledAppMetadata(context) }
    val bundledNotices = remember(context) { loadBundledThirdPartyNotices(context) }
    val fallbackAppName = stringResource(R.string.app_name)
    val installedVersion = if (
        installedApp.versionName != null && installedApp.versionCode != null
    ) {
        stringResource(
            R.string.app_version_format,
            installedApp.versionName,
            installedApp.versionCode,
        )
    } else {
        stringResource(R.string.app_version_unavailable)
    }
    val currentLanguage = stringResource(R.string.current_ui_language_name)
    val diagnostics = AppDiagnostics(
        versionName = installedApp.versionName,
        versionCode = installedApp.versionCode,
        sdkInt = installedApp.sdkInt,
        deviceManufacturer = installedApp.deviceManufacturer,
        deviceModel = installedApp.deviceModel,
        localeTags = localeTags,
        appTheme = appSettings.themeMode.name.lowercase(Locale.ROOT),
        appLanguage = currentLanguage,
    )
    val linkOpenFailedMessage = stringResource(R.string.about_link_open_failed)
    val diagnosticsCopiedMessage = stringResource(R.string.diagnostics_copied)
    val diagnosticsCopyFailedMessage = stringResource(R.string.diagnostics_copy_failed)
    val diagnosticsClipboardLabel = stringResource(R.string.diagnostics_clipboard_label)
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showBundledNotices by remember { mutableStateOf(false) }

    fun openPage(page: MoraExternalPage) {
        actionMessage = if (openMoraExternalPage(context, page)) {
            null
        } else {
            linkOpenFailedMessage
        }
    }

    fun copyDiagnostics() {
        actionMessage = if (
            copyMoraDiagnostics(
                context = context,
                label = diagnosticsClipboardLabel,
                diagnostics = diagnostics,
            )
        ) {
            diagnosticsCopiedMessage
        } else {
            diagnosticsCopyFailedMessage
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            Text(
                text = installedApp.appName.ifBlank { fallbackAppName },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.product_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = installedVersion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            AboutActionRow(
                firstLabel = stringResource(R.string.about_repository),
                onFirstClick = { openPage(MoraExternalPage.REPOSITORY) },
                secondLabel = stringResource(R.string.about_latest_release),
                onSecondClick = { openPage(MoraExternalPage.LATEST_RELEASE) },
            )
            AboutActionRow(
                firstLabel = stringResource(R.string.about_feedback),
                onFirstClick = { openPage(MoraExternalPage.FEEDBACK) },
                secondLabel = stringResource(R.string.about_mora_license),
                onSecondClick = { openPage(MoraExternalPage.LICENSE) },
            )
            AboutActionRow(
                firstLabel = stringResource(R.string.about_third_party_notices),
                onFirstClick = {
                    if (bundledNotices != null) {
                        showBundledNotices = true
                    } else {
                        openPage(MoraExternalPage.THIRD_PARTY_NOTICES)
                    }
                },
                secondLabel = stringResource(R.string.copy_diagnostics),
                onSecondClick = ::copyDiagnostics,
            )
            Text(
                text = stringResource(R.string.diagnostics_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
            actionMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }

    if (showBundledNotices && bundledNotices != null) {
        AlertDialog(
            onDismissRequest = { showBundledNotices = false },
            title = { Text(stringResource(R.string.about_third_party_notices)) },
            text = {
                Text(
                    text = bundledNotices,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showBundledNotices = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
private fun AboutActionRow(
    firstLabel: String,
    onFirstClick: () -> Unit,
    secondLabel: String,
    onSecondClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(
            onClick = onFirstClick,
            modifier = Modifier.weight(1f),
        ) {
            Text(firstLabel)
        }
        TextButton(
            onClick = onSecondClick,
            modifier = Modifier.weight(1f),
        ) {
            Text(secondLabel)
        }
    }
}
