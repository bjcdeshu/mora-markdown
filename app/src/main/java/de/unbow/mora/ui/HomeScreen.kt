package de.unbow.mora.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.unbow.mora.R
import de.unbow.mora.data.RecentDocument
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
internal fun HomeScreen(
    modifier: Modifier = Modifier,
    recentDocuments: List<RecentDocument>,
    snackbarHostState: SnackbarHostState,
    showSnackbarHost: Boolean = true,
    interactive: Boolean = true,
    onOpenFile: () -> Unit,
    onNewDraft: () -> Unit,
    onOpenRecent: (RecentDocument) -> Unit,
    onRemoveRecent: (RecentDocument) -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = {
            if (showSnackbarHost) {
                SnackbarHost(snackbarHostState)
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.product_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { if (interactive) onSettings() },
                        modifier = Modifier.focusProperties { canFocus = interactive },
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.app_settings),
                        )
                    }
                }
            }

            recentDocuments.firstOrNull()?.let { document ->
                item {
                    Column {
                        SectionTitle(stringResource(R.string.continue_reading))
                        Spacer(Modifier.height(10.dp))
                        ElevatedCard(
                            onClick = {
                                if (interactive) onOpenRecent(document)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusProperties { canFocus = interactive },
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                                    modifier = Modifier.size(46.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Outlined.Description,
                                            contentDescription = null,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = document.name.ifBlank {
                                            stringResource(R.string.default_document_filename)
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = recentTimeLabel(document.lastOpenedAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (interactive) onRemoveRecent(document)
                                    },
                                    modifier = Modifier.focusProperties {
                                        canFocus = interactive
                                    },
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = stringResource(
                                            R.string.remove_recent_document,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = { if (interactive) onOpenFile() },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 54.dp)
                            .focusProperties { canFocus = interactive },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.open_document))
                    }
                    FilledTonalButton(
                        onClick = { if (interactive) onNewDraft() },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 54.dp)
                            .focusProperties { canFocus = interactive },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.new_document))
                    }
                }
            }

            if (recentDocuments.size > 1) {
                item { SectionTitle(stringResource(R.string.recent_documents)) }
                items(
                    items = recentDocuments.drop(1),
                    key = { it.uri.toString() },
                ) { document ->
                    RecentDocumentRow(
                        document = document,
                        interactive = interactive,
                        onOpen = { if (interactive) onOpenRecent(document) },
                        onRemove = { if (interactive) onRemoveRecent(document) },
                    )
                }
            } else if (recentDocuments.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Outlined.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.empty_recent_documents),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun RecentDocumentRow(
    document: RecentDocument,
    interactive: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    ElevatedCard(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = interactive },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 13.dp, bottom = 13.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = document.name.ifBlank {
                        stringResource(R.string.default_document_filename)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = recentTimeLabel(document.lastOpenedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.focusProperties { canFocus = interactive },
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.remove_recent_document),
                )
            }
        }
    }
}

@Composable
private fun recentTimeLabel(timestamp: Long): String =
    recentTimeLabel(
        resources = LocalResources.current,
        timestamp = timestamp,
    )

internal fun recentTimeLabel(
    resources: Resources,
    timestamp: Long,
    now: Long = System.currentTimeMillis(),
): String {
    if (timestamp <= 0L) return resources.getString(R.string.recently_opened)
    val elapsed = (now - timestamp).coerceAtLeast(0)
    return when {
        elapsed < TimeUnit.MINUTES.toMillis(2) ->
            resources.getString(R.string.just_opened)

        elapsed < TimeUnit.HOURS.toMillis(1) -> {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed).toInt()
            resources.getQuantityString(R.plurals.minutes_ago, minutes, minutes)
        }

        elapsed < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(elapsed).toInt()
            resources.getQuantityString(R.plurals.hours_ago, hours, hours)
        }

        elapsed < TimeUnit.DAYS.toMillis(7) -> {
            val days = TimeUnit.MILLISECONDS.toDays(elapsed).toInt()
            resources.getQuantityString(R.plurals.days_ago, days, days)
        }

        else -> DateFormat.getDateInstance(
            DateFormat.MEDIUM,
            resources.configuration.locales[0],
        ).format(Date(timestamp))
    }
}
