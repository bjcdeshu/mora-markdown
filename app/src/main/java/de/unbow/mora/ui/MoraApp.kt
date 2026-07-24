package de.unbow.mora.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewmodel.compose.viewModel
import de.unbow.mora.IncomingDocumentRequest
import de.unbow.mora.R
import de.unbow.mora.data.AppSettings
import de.unbow.mora.data.DocumentFailure
import de.unbow.mora.data.DocumentRepository
import de.unbow.mora.data.ReaderSettingsRepository
import de.unbow.mora.markdown.ReaderPalette
import de.unbow.mora.markdown.ReaderPreferences
import de.unbow.mora.model.DocumentSaveResult
import de.unbow.mora.model.DocumentUiError
import de.unbow.mora.model.MarkdownViewModel
import de.unbow.mora.model.displayDocumentName
import de.unbow.mora.ui.theme.LocalMoraIsDark
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MoraApp(
    appSettings: AppSettings,
    onAppSettingsChanged: (AppSettings) -> Boolean,
    incomingRequest: IncomingDocumentRequest?,
    onIncomingRequestConsumed: (Long) -> Unit,
    markdownViewModel: MarkdownViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state = markdownViewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val storedReaderPreferences = remember(context) { ReaderSettingsRepository.load(context) }
    val effectiveDark = LocalMoraIsDark.current
    val defaultDocumentFilename = stringResource(R.string.default_document_filename)
    val untitledFilenameBase = stringResource(R.string.untitled_filename_base)
    val untitledDocumentFilename = stringResource(R.string.untitled_document_filename)
    val newDraftTemplate = stringResource(R.string.new_draft_template)
    val untitledHeading = stringResource(R.string.untitled_heading)
    val savedMessage = stringResource(R.string.document_saved)
    val saveFailedMessage = stringResource(R.string.document_save_failed)
    val saveAsRequiredMessage = stringResource(R.string.document_requires_save_as)
    val readOnlyNotice = stringResource(R.string.read_only_document_notice)
    val languageSettingsUnavailable = stringResource(R.string.language_settings_unavailable)
    val launcherIconChangeFailed = stringResource(R.string.launcher_icon_change_failed)
    val displayedDocumentName = displayDocumentName(
        storedName = state.name,
        usesLocalizedFallback = state.nameUsesLocalizedFallback,
        localizedFallback = defaultDocumentFilename,
    )

    var mode by rememberSaveable { mutableStateOf(DocumentMode.READING) }
    var showReaderAppearance by rememberSaveable { mutableStateOf(false) }
    var showAppSettings by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var pendingIncomingRequest by remember { mutableStateOf<IncomingDocumentRequest?>(null) }
    var fontSize by rememberSaveable {
        mutableFloatStateOf(storedReaderPreferences.fontSizePx)
    }
    var lineHeight by rememberSaveable {
        mutableFloatStateOf(storedReaderPreferences.lineHeight)
    }
    var horizontalPadding by rememberSaveable {
        mutableFloatStateOf(storedReaderPreferences.horizontalPaddingPx)
    }
    var readerScrollY by rememberSaveable { mutableIntStateOf(0) }

    fun currentPreferences() = ReaderPreferences(
        fontSizePx = fontSize,
        lineHeight = lineHeight,
        horizontalPaddingPx = horizontalPadding,
    )

    fun persistReaderPreferences() {
        ReaderSettingsRepository.save(context, currentPreferences())
    }

    fun openDocument(uri: Uri) {
        showReaderAppearance = false
        showAppSettings = false
        readerScrollY = 0
        mode = DocumentMode.READING
        markdownViewModel.openDocument(
            context = context,
            uri = uri,
            fallbackName = defaultDocumentFilename,
        )
    }

    fun acceptIncoming(request: IncomingDocumentRequest) {
        val uri = request.uri
        if (uri != null) {
            DocumentRepository.persistPermission(context, uri, request.grantedFlags)
            openDocument(uri)
        } else {
            showReaderAppearance = false
            showAppSettings = false
            readerScrollY = 0
            mode = DocumentMode.READING
            markdownViewModel.openSharedText(
                content = request.sharedText.orEmpty(),
                name = request.suggestedName,
            )
        }
        onIncomingRequestConsumed(request.id)
    }

    LaunchedEffect(Unit) {
        markdownViewModel.initialize(context)
    }

    LaunchedEffect(incomingRequest?.id) {
        val request = incomingRequest ?: return@LaunchedEffect
        markdownViewModel.initialize(context)
        showReaderAppearance = false
        showAppSettings = false
        showDiscardDialog = false
        request.uri?.let { uri ->
            DocumentRepository.persistPermission(context, uri, request.grantedFlags)
        }
        if (state.hasDocument && state.isDirty) {
            pendingIncomingRequest = request
        } else {
            acceptIncoming(request)
        }
    }

    LaunchedEffect(state.contentVersion, state.isLoading) {
        if (state.hasDocument && !state.isLoading) {
            readerScrollY = state.initialScrollY
        }
    }

    LaunchedEffect(state.hasDocument) {
        if (state.hasDocument) {
            showAppSettings = false
        } else {
            showReaderAppearance = false
        }
    }

    val localizedError = when (val error = state.error) {
        is DocumentUiError.OpenFailed -> stringResource(
            R.string.open_document_failed,
            displayDocumentName(
                storedName = error.documentName,
                usesLocalizedFallback = error.usesLocalizedFallback,
                localizedFallback = defaultDocumentFilename,
            ),
        )

        null -> null
    }
    LaunchedEffect(state.error, localizedError) {
        val message = localizedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        markdownViewModel.consumeError()
    }

    val notifySave: (DocumentSaveResult) -> Unit = { result ->
        scope.launch {
            snackbarHostState.showSnackbar(
                when (result) {
                    DocumentSaveResult.Saved -> savedMessage
                    is DocumentSaveResult.Failed -> when (result.failure) {
                        DocumentFailure.SAVE_AS_REQUIRED -> saveAsRequiredMessage
                        DocumentFailure.READ_FAILED,
                        DocumentFailure.WRITE_FAILED,
                        -> saveFailedMessage
                    }
                },
            )
        }
    }

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        DocumentRepository.persistPermission(context, uri)
        markdownViewModel.saveAs(context, uri, notifySave)
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        DocumentRepository.persistPermission(context, uri)
        openDocument(uri)
    }

    val closeDocument: () -> Unit = {
        showReaderAppearance = false
        showAppSettings = false
        if (state.isDirty) {
            showDiscardDialog = true
        } else {
            readerScrollY = 0
            markdownViewModel.closeDocument()
        }
    }

    AnimatedContent(
        targetState = state.hasDocument,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "home-document",
    ) { hasDocument ->
        if (!hasDocument) {
            HomeScreen(
                recentDocuments = markdownViewModel.recentDocuments,
                snackbarHostState = snackbarHostState,
                onOpenFile = {
                    openDocumentLauncher.launch(
                        arrayOf(
                            "text/markdown",
                            "text/x-markdown",
                            "application/x-markdown",
                            "text/plain",
                            "application/octet-stream",
                        ),
                    )
                },
                onNewDraft = {
                    showAppSettings = false
                    readerScrollY = 0
                    mode = DocumentMode.EDITING
                    markdownViewModel.newDraft(
                        name = untitledDocumentFilename,
                        initialContent = newDraftTemplate,
                    )
                },
                onOpenRecent = { document ->
                    openDocument(document.uri)
                },
                onRemoveRecent = { document ->
                    markdownViewModel.removeRecent(context, document)
                },
                onSettings = {
                    showReaderAppearance = false
                    showAppSettings = true
                },
            )
        } else {
            var editorValue by remember(state.contentVersion) {
                mutableStateOf(TextFieldValue(state.content))
            }

            val colors = MaterialTheme.colorScheme
            val palette = remember(colors) {
                ReaderPalette(
                    background = colors.surface.toCssHex(),
                    text = colors.onSurface.toCssHex(),
                    muted = colors.onSurfaceVariant.toCssHex(),
                    accent = colors.primary.toCssHex(),
                    softSurface = colors.surfaceContainer.toCssHex(),
                    outline = colors.outlineVariant.toCssHex(),
                )
            }

            DocumentScreen(
                documentKey = state.sessionId,
                documentUri = state.uri,
                name = displayedDocumentName,
                dirty = state.isDirty,
                loading = state.isLoading,
                markdown = state.content,
                editorValue = editorValue,
                mode = mode,
                palette = palette,
                preferences = currentPreferences(),
                effectiveDark = effectiveDark,
                untitledHeading = untitledHeading,
                readerScrollY = readerScrollY,
                snackbarHostState = snackbarHostState,
                onReaderPositionChanged = { uri, position ->
                    if (uri == markdownViewModel.uiState.uri) {
                        readerScrollY = position
                    }
                    markdownViewModel.updateReadingPosition(context, uri, position)
                },
                onModeChanged = { newMode ->
                    mode = newMode
                    if (
                        newMode == DocumentMode.EDITING &&
                        state.uri != null &&
                        !state.canWrite
                    ) {
                        scope.launch {
                            snackbarHostState.showSnackbar(readOnlyNotice)
                        }
                    }
                },
                onBack = closeDocument,
                onAppearance = {
                    showAppSettings = false
                    showReaderAppearance = true
                },
                onSave = {
                    if (state.uri == null || !state.canWrite) {
                        createDocument.launch(
                            normalizedMarkdownName(
                                displayedDocumentName,
                                untitledFilenameBase,
                            ),
                        )
                    } else {
                        markdownViewModel.save(context, notifySave)
                    }
                },
                onEditorChanged = { value ->
                    editorValue = value
                    markdownViewModel.updateContent(value.text)
                },
            )
        }
    }

    if (showAppSettings && !state.hasDocument) {
        AppSettingsSheet(
            appSettings = appSettings,
            onAppSettingsChanged = onAppSettingsChanged,
            onLauncherIconChangeFailed = {
                scope.launch {
                    snackbarHostState.showSnackbar(launcherIconChangeFailed)
                }
            },
            onLanguageSettingsUnavailable = {
                scope.launch {
                    snackbarHostState.showSnackbar(languageSettingsUnavailable)
                }
            },
            onDismiss = { showAppSettings = false },
        )
    }

    if (showReaderAppearance && state.hasDocument) {
        AppearanceSheet(
            fontSize = fontSize,
            lineHeight = lineHeight,
            horizontalPadding = horizontalPadding,
            onFontSizeChanged = {
                fontSize = it
                persistReaderPreferences()
            },
            onLineHeightChanged = {
                lineHeight = it
                persistReaderPreferences()
            },
            onHorizontalPaddingChanged = {
                horizontalPadding = it
                persistReaderPreferences()
            },
            onReset = {
                fontSize = ReaderPreferences.Default.fontSizePx
                lineHeight = ReaderPreferences.Default.lineHeight
                horizontalPadding = ReaderPreferences.Default.horizontalPaddingPx
                persistReaderPreferences()
            },
            onDismiss = { showReaderAppearance = false },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        showReaderAppearance = false
                        showAppSettings = false
                        readerScrollY = 0
                        markdownViewModel.closeDocument()
                    },
                ) { Text(stringResource(R.string.discard_changes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingIncomingRequest?.let { request ->
        AlertDialog(
            onDismissRequest = {
                pendingIncomingRequest = null
                onIncomingRequestConsumed(request.id)
            },
            title = { Text(stringResource(R.string.open_new_document_title)) },
            text = { Text(stringResource(R.string.open_new_document_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingIncomingRequest = null
                        acceptIncoming(request)
                    },
                ) { Text(stringResource(R.string.discard_and_open)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingIncomingRequest = null
                        onIncomingRequestConsumed(request.id)
                    },
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun normalizedMarkdownName(name: String, fallbackName: String): String {
    val trimmed = name.trim().ifEmpty { fallbackName }
    return if (trimmed.endsWith(".md", ignoreCase = true)) trimmed else "$trimmed.md"
}

private fun androidx.compose.ui.graphics.Color.toCssHex(): String =
    String.format(Locale.US, "#%06X", 0xFFFFFF and toArgb())
