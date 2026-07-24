package de.unbow.mora.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.unbow.mora.R
import de.unbow.mora.data.AppSettings
import de.unbow.mora.data.DarkSurfaceStyle
import de.unbow.mora.data.LauncherIcon
import de.unbow.mora.data.ThemeMode
import kotlinx.coroutines.launch

internal enum class AppLanguageSettingsState {
    FOLLOWS_DEVICE,
    AVAILABLE,
}

internal fun appLanguageSettingsState(
    sdkInt: Int,
): AppLanguageSettingsState = when {
    sdkInt < Build.VERSION_CODES.TIRAMISU -> AppLanguageSettingsState.FOLLOWS_DEVICE
    else -> AppLanguageSettingsState.AVAILABLE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSettingsSheet(
    appSettings: AppSettings,
    onAppSettingsChanged: (AppSettings) -> Boolean,
    onLauncherIconChangeFailed: () -> Unit,
    onLanguageSettingsUnavailable: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val languageIntent = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appLanguageSettingsIntent(context)
        } else {
            null
        }
    }
    val languageSettingsState = appLanguageSettingsState(
        sdkInt = Build.VERSION.SDK_INT,
    )
    val currentLanguage = stringResource(R.string.current_ui_language_name)

    fun openLanguageSettings() {
        val intent = languageIntent ?: return
        scope.launch {
            sheetState.hide()
            onDismiss()
            if (!startAppLanguageSettings(context, intent)) {
                onLanguageSettingsUnavailable()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.app_settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(24.dp))

            SettingsSectionTitle(stringResource(R.string.settings_theme))
            Spacer(Modifier.height(8.dp))
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    SettingsChoiceRow(
                        label = when (mode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.theme_follow_system)
                            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                            ThemeMode.DARK -> stringResource(R.string.theme_dark)
                        },
                        selected = appSettings.themeMode == mode,
                        onClick = {
                            onAppSettingsChanged(appSettings.copy(themeMode = mode))
                        },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.dark_background),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.dark_background_dark_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Column(Modifier.selectableGroup()) {
                DarkSurfaceStyle.entries.forEach { style ->
                    SettingsChoiceRow(
                        label = when (style) {
                            DarkSurfaceStyle.DEFAULT ->
                                stringResource(R.string.dark_background_default)

                            DarkSurfaceStyle.PURE_BLACK ->
                                stringResource(R.string.dark_background_pure_black)
                        },
                        selected = appSettings.darkSurfaceStyle == style,
                        onClick = {
                            onAppSettingsChanged(appSettings.copy(darkSurfaceStyle = style))
                        },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 20.dp))

            SettingsSectionTitle(stringResource(R.string.settings_launcher_icon))
            Spacer(Modifier.height(8.dp))
            Column(Modifier.selectableGroup()) {
                LauncherIcon.entries.forEach { launcherIcon ->
                    LauncherIconChoiceRow(
                        launcherIcon = launcherIcon,
                        label = when (launcherIcon) {
                            LauncherIcon.INDIGO ->
                                stringResource(R.string.launcher_icon_indigo)

                            LauncherIcon.PINE ->
                                stringResource(R.string.launcher_icon_pine)

                            LauncherIcon.NIGHT ->
                                stringResource(R.string.launcher_icon_night)
                        },
                        selected = appSettings.launcherIcon == launcherIcon,
                        onClick = {
                            val changed = onAppSettingsChanged(
                                appSettings.copy(launcherIcon = launcherIcon),
                            )
                            if (!changed) onLauncherIconChangeFailed()
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.launcher_icon_themed_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 20.dp))

            SettingsSectionTitle(stringResource(R.string.settings_language))
            Spacer(Modifier.height(8.dp))
            LanguageSettingsRow(
                currentLanguage = currentLanguage,
                state = languageSettingsState,
                onClick = ::openLanguageSettings,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LauncherIconChoiceRow(
    launcherIcon: LauncherIcon,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val previewBackground = colorResource(
        when (launcherIcon) {
            LauncherIcon.INDIGO -> R.color.mora_icon_background
            LauncherIcon.PINE -> R.color.mora_icon_pine_background
            LauncherIcon.NIGHT -> R.color.mora_icon_night_background
        },
    )
    val foregroundDrawable = when (launcherIcon) {
        LauncherIcon.INDIGO -> R.drawable.ic_launcher_foreground
        LauncherIcon.PINE -> R.drawable.ic_launcher_foreground_pine
        LauncherIcon.NIGHT -> R.drawable.ic_launcher_foreground_night
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.medium,
                color = previewBackground,
            ) {
                Image(
                    painter = painterResource(foregroundDrawable),
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (selected) {
                    Text(
                        text = stringResource(R.string.launcher_icon_current),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RadioButton(
                selected = selected,
                onClick = null,
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
            )
            Text(
                text = label,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun LanguageSettingsRow(
    currentLanguage: String,
    state: AppLanguageSettingsState,
    onClick: () -> Unit,
) {
    val enabled = state == AppLanguageSettingsState.AVAILABLE
    val supportingText = when (state) {
        AppLanguageSettingsState.FOLLOWS_DEVICE ->
            stringResource(R.string.language_follows_device)

        AppLanguageSettingsState.AVAILABLE ->
            stringResource(R.string.language_change_in_android)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = currentLanguage,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun appLanguageSettingsIntent(context: Context): Intent = Intent(
    Settings.ACTION_APP_LOCALE_SETTINGS,
    "package:${context.packageName}".toUri(),
)

private fun startAppLanguageSettings(
    context: Context,
    intent: Intent,
): Boolean = try {
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
