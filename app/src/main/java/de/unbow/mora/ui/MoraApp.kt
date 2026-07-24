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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewmodel.compose.viewModel
import de.unbow.mora.IncomingDocumentRequest
import de.unbow.mora.data.AppSettings
import de.unbow.mora.data.DocumentRepository
import de.unbow.mora.data.ReaderSettingsRepository
import de.unbow.mora.markdown.ReaderPalette
import de.unbow.mora.markdown.ReaderPreferences
import de.unbow.mora.model.MarkdownViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MoraApp(
    appSettings: AppSettings,
    onAppSettingsChanged: (AppSettings) -> Unit,
    incomingRequest: IncomingDocumentRequest?,
    onIncomingRequestConsumed: (Long) -> Unit,
    markdownViewModel: MarkdownViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state = markdownViewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val storedReaderPreferences = remember(context) { ReaderSettingsRepository.load(context) }

    var mode by rememberSaveable { mutableStateOf(DocumentMode.READING) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
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
        readerScrollY = 0
        mode = DocumentMode.READING
        markdownViewModel.openDocument(context, uri)
    }

    fun acceptIncoming(request: IncomingDocumentRequest) {
        val uri = request.uri
        if (uri != null) {
            DocumentRepository.persistPermission(context, uri, request.grantedFlags)
            openDocument(uri)
        } else {
            readerScrollY = 0
            mode = DocumentMode.READING
            markdownViewModel.openSharedText(
                content = request.sharedText.orEmpty(),
                name = normalizedMarkdownName(request.suggestedName),
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
        showAppearance = false
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

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        markdownViewModel.consumeError()
    }

    val notifySave: (Result<Unit>) -> Unit = { result ->
        scope.launch {
            snackbarHostState.showSnackbar(
                result.fold(
                    onSuccess = { "已保存" },
                    onFailure = { it.message ?: "保存失败" },
                ),
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
                    readerScrollY = 0
                    mode = DocumentMode.EDITING
                    markdownViewModel.newDraft()
                },
                onOpenRecent = { document ->
                    openDocument(document.uri)
                },
                onRemoveRecent = { document ->
                    markdownViewModel.removeRecent(context, document)
                },
                onSettings = { showAppearance = true },
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
                name = state.name,
                dirty = state.isDirty,
                loading = state.isLoading,
                markdown = state.content,
                editorValue = editorValue,
                mode = mode,
                palette = palette,
                preferences = currentPreferences(),
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
                            snackbarHostState.showSnackbar("原文件为只读，修改后将另存为")
                        }
                    }
                },
                onBack = closeDocument,
                onAppearance = { showAppearance = true },
                onSave = {
                    if (state.uri == null || !state.canWrite) {
                        createDocument.launch(normalizedMarkdownName(state.name))
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

    if (showAppearance) {
        AppearanceSheet(
            title = if (state.hasDocument) "阅读排版" else "阅读设置",
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
            onDismiss = { showAppearance = false },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("返回后，当前文档中的修改不会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        readerScrollY = 0
                        markdownViewModel.closeDocument()
                    },
                ) { Text("放弃修改") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("取消") }
            },
        )
    }

    pendingIncomingRequest?.let { request ->
        AlertDialog(
            onDismissRequest = {
                pendingIncomingRequest = null
                onIncomingRequestConsumed(request.id)
            },
            title = { Text("打开新文档？") },
            text = { Text("当前文档还有未保存的修改。继续后，这些修改不会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingIncomingRequest = null
                        acceptIncoming(request)
                    },
                ) { Text("放弃并打开") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingIncomingRequest = null
                        onIncomingRequestConsumed(request.id)
                    },
                ) { Text("取消") }
            },
        )
    }
}

private fun normalizedMarkdownName(name: String): String {
    val trimmed = name.trim().ifEmpty { "未命名" }
    return if (trimmed.endsWith(".md", ignoreCase = true)) trimmed else "$trimmed.md"
}

private fun androidx.compose.ui.graphics.Color.toCssHex(): String =
    String.format(Locale.US, "#%06X", 0xFFFFFF and toArgb())
