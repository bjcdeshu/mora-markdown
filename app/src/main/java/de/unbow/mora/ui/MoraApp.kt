package de.unbow.mora.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.unbow.mora.IncomingDocumentRequest
import de.unbow.mora.R
import de.unbow.mora.data.AppSettings
import de.unbow.mora.data.DirtyDocumentRecovery
import de.unbow.mora.data.DocumentFailure
import de.unbow.mora.data.DocumentPermission
import de.unbow.mora.data.DocumentPermissionManager
import de.unbow.mora.data.ReaderSettingsRepository
import de.unbow.mora.markdown.ReaderPalette
import de.unbow.mora.markdown.ReaderPreferences
import de.unbow.mora.model.DocumentSaveResult
import de.unbow.mora.model.DocumentUiError
import de.unbow.mora.model.MarkdownViewModel
import de.unbow.mora.model.displayDocumentName
import de.unbow.mora.model.shouldUseSaveAs
import de.unbow.mora.ui.theme.LocalMoraIsDark
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

internal data class ReaderScrollSession(
    val sessionId: Long = Long.MIN_VALUE,
    val scrollY: Int = 0,
)

internal fun resolveReaderScrollSession(
    current: ReaderScrollSession,
    hasDocument: Boolean,
    sessionId: Long,
    initialScrollY: Int,
): ReaderScrollSession = when {
    !hasDocument -> ReaderScrollSession()
    current.sessionId == sessionId -> current
    else -> ReaderScrollSession(
        sessionId = sessionId,
        scrollY = initialScrollY.coerceAtLeast(0),
    )
}

internal fun isSaveAsResultCurrent(
    requestSessionId: Long?,
    currentSessionId: Long,
): Boolean = requestSessionId != null && requestSessionId == currentSessionId

internal fun canCompletePostSaveAction(
    requestedSessionId: Long,
    requestedContentRevision: Long,
    currentSessionId: Long,
    currentContentRevision: Long,
    currentIsDirty: Boolean,
    savedOriginal: Boolean,
): Boolean = requestedSessionId == currentSessionId &&
    requestedContentRevision == currentContentRevision &&
    (!savedOriginal || !currentIsDirty)

internal fun shouldConsumeAttemptedIncomingRequest(
    hasUri: Boolean,
    accepted: Boolean,
): Boolean = accepted || !hasUri

private enum class SaveDestinationPurpose {
    SAVE_DOCUMENT,
    SAVE_COPY,
    RECOVERY_COPY,
}

private enum class PostSaveAction {
    NONE,
    CLOSE,
    OPEN_INCOMING,
}

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
    val pendingIncomingRequest = markdownViewModel.pendingIncomingRequest
    val pendingConflict = markdownViewModel.pendingConflict
    val recoverableWork = markdownViewModel.recoverableWork
    val isRecoveryInitialized = markdownViewModel.isRecoveryInitialized
    val isRecoveryTransitioning = markdownViewModel.isRecoveryTransitioning
    val isRecoverySaving = markdownViewModel.isRecoverySaving
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
    val copiedMessage = stringResource(R.string.document_copy_saved)
    val saveFailedMessage = stringResource(R.string.document_save_failed)
    val saveAsRequiredMessage = stringResource(R.string.document_requires_save_as)
    val documentTooLargeMessage = stringResource(R.string.document_too_large)
    val malformedUtf8Message = stringResource(R.string.document_invalid_utf8)
    val permissionLostMessage = stringResource(R.string.document_permission_lost)
    val recoveryFailedMessage = stringResource(R.string.document_recovery_failed)
    val verificationFailedMessage = stringResource(R.string.document_verification_failed)
    val waitForSaveMessage = stringResource(R.string.wait_for_save)
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
    var showCloseDialog by rememberSaveable { mutableStateOf(false) }
    var saveAsRequestSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var saveAsRequestRevision by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
    var saveDestinationPurpose by rememberSaveable {
        mutableStateOf<SaveDestinationPurpose?>(null)
    }
    var recoveryCopyId by rememberSaveable { mutableStateOf<String?>(null) }
    var failedRecoveryCopyId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSaveFailure by rememberSaveable { mutableStateOf<DocumentFailure?>(null) }
    var pendingFailureSessionId by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
    var pendingFailureRevision by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
    var postSaveAction by rememberSaveable { mutableStateOf(PostSaveAction.NONE) }
    var postSaveSessionId by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
    var postSaveRevision by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
    var overlayStateSessionId by rememberSaveable { mutableLongStateOf(state.sessionId) }
    val saveAsPending = saveDestinationPurpose != null &&
        saveAsRequestSessionId == state.sessionId
    var fontSize by rememberSaveable {
        mutableFloatStateOf(storedReaderPreferences.fontSizePx)
    }
    var lineHeight by rememberSaveable {
        mutableFloatStateOf(storedReaderPreferences.lineHeight)
    }
    var horizontalPadding by rememberSaveable {
        mutableFloatStateOf(storedReaderPreferences.horizontalPaddingPx)
    }
    var readerScrollSessionId by rememberSaveable {
        mutableLongStateOf(Long.MIN_VALUE)
    }
    var readerScrollY by rememberSaveable { mutableIntStateOf(0) }
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    var predictiveBackSwipeEdge by remember {
        mutableStateOf(DocumentBackSwipeEdge.LEFT)
    }
    var predictiveBackVisualActive by remember { mutableStateOf(false) }
    var predictiveBackResetJob by remember { mutableStateOf<Job?>(null) }

    fun resetPredictiveBackImmediately() {
        predictiveBackResetJob?.cancel()
        predictiveBackResetJob = null
        predictiveBackProgress = 0f
        predictiveBackVisualActive = false
    }

    fun animatePredictiveBackCancellation() {
        predictiveBackResetJob?.cancel()
        val startingProgress = predictiveBackProgress
        if (!predictiveBackVisualActive || startingProgress <= 0f) {
            resetPredictiveBackImmediately()
            return
        }
        predictiveBackResetJob = scope.launch {
            animate(
                initialValue = startingProgress,
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = calculatePredictiveBackCancellationDurationMillis(
                        startingProgress,
                    ),
                    easing = LinearOutSlowInEasing,
                ),
            ) { value, _ ->
                predictiveBackProgress = value
            }
            predictiveBackProgress = 0f
            predictiveBackVisualActive = false
            predictiveBackResetJob = null
        }
    }

    fun currentPreferences() = ReaderPreferences(
        fontSizePx = fontSize,
        lineHeight = lineHeight,
        horizontalPaddingPx = horizontalPadding,
    )

    fun persistReaderPreferences() {
        ReaderSettingsRepository.save(context, currentPreferences())
    }

    fun openDocument(
        uri: Uri,
        permission: DocumentPermission? = null,
    ): Boolean {
        if (!markdownViewModel.canStartNewWork()) return false
        val accepted = markdownViewModel.openDocument(
            context = context,
            uri = uri,
            fallbackName = defaultDocumentFilename,
            acquiredPermission = permission,
        )
        if (!accepted) return false
        showReaderAppearance = false
        showAppSettings = false
        readerScrollY = 0
        mode = DocumentMode.READING
        return true
    }

    fun acceptIncoming(request: IncomingDocumentRequest): Boolean {
        if (!markdownViewModel.canStartNewWork()) return false
        val uri = request.uri
        val accepted = if (uri != null) {
            val permission = DocumentPermissionManager.acquire(
                context = context,
                uri = uri,
                grantedFlags = request.grantedFlags,
            )
            openDocument(uri, permission)
        } else {
            val failure = markdownViewModel.openSharedText(
                content = request.sharedText.orEmpty(),
                name = request.suggestedName,
            )
            if (failure == null) {
                showReaderAppearance = false
                showAppSettings = false
                readerScrollY = 0
                mode = DocumentMode.READING
                true
            } else {
                false
            }
        }
        if (shouldConsumeAttemptedIncomingRequest(hasUri = uri != null, accepted = accepted)) {
            markdownViewModel.clearPendingIncomingRequest(request.id)
            onIncomingRequestConsumed(request.id)
        }
        return accepted
    }

    fun clearPostSaveAction() {
        postSaveAction = PostSaveAction.NONE
        postSaveSessionId = Long.MIN_VALUE
        postSaveRevision = Long.MIN_VALUE
    }

    fun completePostSaveIfCurrent(result: DocumentSaveResult) {
        val current = markdownViewModel.uiState
        if (postSaveAction == PostSaveAction.NONE || !canCompletePostSaveAction(
                requestedSessionId = postSaveSessionId,
                requestedContentRevision = postSaveRevision,
                currentSessionId = current.sessionId,
                currentContentRevision = current.contentRevision,
                currentIsDirty = current.isDirty,
                savedOriginal = result == DocumentSaveResult.Saved,
            )
        ) {
            if (postSaveAction != PostSaveAction.NONE) clearPostSaveAction()
            return
        }
        val action = postSaveAction
        clearPostSaveAction()
        when (action) {
            PostSaveAction.NONE -> Unit
            PostSaveAction.CLOSE -> {
                readerScrollY = 0
                markdownViewModel.closeDocument(
                    context = context,
                    discardChanges = result == DocumentSaveResult.Copied,
                )
            }

            PostSaveAction.OPEN_INCOMING -> {
                if (markdownViewModel.pendingIncomingRequest == null) return
                markdownViewModel.closeDocument(
                    context = context,
                    discardChanges = result == DocumentSaveResult.Copied,
                )
            }
        }
    }

    fun failureMessage(failure: DocumentFailure): String = when (failure) {
        DocumentFailure.SAVE_AS_REQUIRED -> saveAsRequiredMessage
        DocumentFailure.FILE_TOO_LARGE -> documentTooLargeMessage
        DocumentFailure.MALFORMED_UTF8 -> malformedUtf8Message
        DocumentFailure.PERMISSION_LOST -> permissionLostMessage
        DocumentFailure.RECOVERY_FAILED -> recoveryFailedMessage
        DocumentFailure.VERIFICATION_FAILED -> verificationFailedMessage
        DocumentFailure.READ_FAILED,
        DocumentFailure.WRITE_FAILED,
        -> saveFailedMessage
    }

    fun handleSaveResult(result: DocumentSaveResult) {
        when (result) {
            DocumentSaveResult.Saved -> {
                pendingSaveFailure = null
                scope.launch { snackbarHostState.showSnackbar(savedMessage) }
                completePostSaveIfCurrent(result)
            }

            DocumentSaveResult.Copied -> {
                pendingSaveFailure = null
                scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                completePostSaveIfCurrent(result)
            }

            DocumentSaveResult.Conflict -> pendingSaveFailure = null
            is DocumentSaveResult.Failed -> {
                val current = markdownViewModel.uiState
                pendingSaveFailure = result.failure
                pendingFailureSessionId = current.sessionId
                pendingFailureRevision = current.contentRevision
            }
        }
    }

    LaunchedEffect(Unit) {
        markdownViewModel.initialize(context)
    }

    LaunchedEffect(incomingRequest?.id) {
        val request = incomingRequest ?: return@LaunchedEffect
        markdownViewModel.initialize(context)
        showReaderAppearance = false
        showAppSettings = false
        showCloseDialog = false
        markdownViewModel.deferIncomingRequest(request)
    }

    LaunchedEffect(
        pendingIncomingRequest?.id,
        isRecoveryInitialized,
        isRecoveryTransitioning,
        isRecoverySaving,
        recoverableWork?.recoveryId,
        state.hasDocument,
        state.isDirty,
        state.isSaving,
        state.sessionId,
        saveAsPending,
    ) {
        val request = pendingIncomingRequest ?: return@LaunchedEffect
        if (
            !isRecoveryInitialized ||
            isRecoveryTransitioning ||
            isRecoverySaving ||
            recoverableWork != null ||
            state.isSaving ||
            saveAsPending
        ) {
            return@LaunchedEffect
        }
        if (!state.hasDocument) {
            acceptIncoming(request)
        } else if (!state.isDirty) {
            markdownViewModel.closeDocument(context)
        }
    }

    LaunchedEffect(state.hasDocument, state.sessionId, state.initialScrollY) {
        val resolved = resolveReaderScrollSession(
            current = ReaderScrollSession(
                sessionId = readerScrollSessionId,
                scrollY = readerScrollY,
            ),
            hasDocument = state.hasDocument,
            sessionId = state.sessionId,
            initialScrollY = state.initialScrollY,
        )
        readerScrollSessionId = resolved.sessionId
        readerScrollY = resolved.scrollY
    }

    LaunchedEffect(state.hasDocument) {
        if (state.hasDocument) {
            showAppSettings = false
        } else {
            showReaderAppearance = false
            resetPredictiveBackImmediately()
        }
    }

    LaunchedEffect(state.sessionId) {
        if (overlayStateSessionId == state.sessionId) return@LaunchedEffect
        overlayStateSessionId = state.sessionId
        resetPredictiveBackImmediately()
        pendingSaveFailure = null
        saveDestinationPurpose = null
        saveAsRequestSessionId = null
        recoveryCopyId = null
        clearPostSaveAction()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, markdownViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) markdownViewModel.flushRecovery()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val localizedError = when (val error = state.error) {
        is DocumentUiError.OpenFailed -> when (error.failure) {
            DocumentFailure.FILE_TOO_LARGE -> documentTooLargeMessage
            DocumentFailure.MALFORMED_UTF8 -> malformedUtf8Message
            DocumentFailure.PERMISSION_LOST -> permissionLostMessage
            else -> stringResource(
                R.string.open_document_failed,
                displayDocumentName(
                    storedName = error.documentName,
                    usesLocalizedFallback = error.usesLocalizedFallback,
                    localizedFallback = defaultDocumentFilename,
                ),
            )
        }

        is DocumentUiError.ActionFailed -> failureMessage(error.failure)

        null -> null
    }
    LaunchedEffect(state.error, localizedError) {
        val message = localizedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        markdownViewModel.consumeError()
    }

    val notifySave: (DocumentSaveResult) -> Unit = ::handleSaveResult

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        val requestSessionId = saveAsRequestSessionId
        val requestRevision = saveAsRequestRevision
        val purpose = saveDestinationPurpose
        val requestedRecoveryId = recoveryCopyId
        saveAsRequestSessionId = null
        saveAsRequestRevision = Long.MIN_VALUE
        saveDestinationPurpose = null
        recoveryCopyId = null
        if (uri == null || purpose == null) {
            clearPostSaveAction()
            return@rememberLauncherForActivityResult
        }
        if (purpose == SaveDestinationPurpose.RECOVERY_COPY) {
            val recovery = markdownViewModel.recoverableWork
                ?.takeIf { it.recoveryId == requestedRecoveryId }
                ?: return@rememberLauncherForActivityResult
            markdownViewModel.saveRecoveryCopy(context, uri, recovery) { result ->
                failedRecoveryCopyId = if (result is DocumentSaveResult.Failed) {
                    recovery.recoveryId
                } else {
                    null
                }
                notifySave(result)
            }
            return@rememberLauncherForActivityResult
        }
        val current = markdownViewModel.uiState
        if (
            !isSaveAsResultCurrent(requestSessionId, current.sessionId) ||
            requestRevision != current.contentRevision
        ) {
            clearPostSaveAction()
            return@rememberLauncherForActivityResult
        }
        when (purpose) {
            SaveDestinationPurpose.SAVE_DOCUMENT ->
                markdownViewModel.saveAs(context, uri, notifySave)

            SaveDestinationPurpose.SAVE_COPY ->
                markdownViewModel.saveCopy(context, uri, notifySave)

            SaveDestinationPurpose.RECOVERY_COPY -> Unit
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        if (!markdownViewModel.canStartNewWork()) return@rememberLauncherForActivityResult
        val permission = DocumentPermissionManager.acquire(context, uri)
        openDocument(uri, permission)
    }

    fun launchSaveDestination(
        purpose: SaveDestinationPurpose,
        recovery: DirtyDocumentRecovery? = null,
    ) {
        val current = markdownViewModel.uiState
        saveDestinationPurpose = purpose
        saveAsRequestSessionId = current.sessionId
        saveAsRequestRevision = current.contentRevision
        recoveryCopyId = recovery?.recoveryId
        createDocument.launch(
            normalizedMarkdownName(
                recovery?.name ?: displayedDocumentName,
                untitledFilenameBase,
            ),
        )
    }

    fun beginPostSave(action: PostSaveAction) {
        val current = markdownViewModel.uiState
        postSaveAction = action
        postSaveSessionId = current.sessionId
        postSaveRevision = current.contentRevision
    }

    fun saveCurrentDocument() {
        val current = markdownViewModel.uiState
        if (current.isSaving || saveDestinationPurpose != null) return
        if (
            shouldUseSaveAs(current.uri != null, current.canWrite) ||
            current.baseVersion == null
        ) {
            launchSaveDestination(SaveDestinationPurpose.SAVE_DOCUMENT)
        } else {
            markdownViewModel.save(context, notifySave)
        }
    }

    val closeDocument: () -> Unit = {
        showReaderAppearance = false
        showAppSettings = false
        val current = markdownViewModel.uiState
        if (
            current.isSaving ||
            saveAsPending ||
            markdownViewModel.isRecoveryTransitioning ||
            markdownViewModel.isRecoverySaving
        ) {
            scope.launch { snackbarHostState.showSnackbar(waitForSaveMessage) }
        } else if (current.isDirty) {
            showCloseDialog = true
        } else {
            readerScrollY = 0
            markdownViewModel.closeDocument(context)
        }
    }

    val density = LocalDensity.current
    val windowWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    val maximumBackTranslation = calculateMaximumPredictiveBackTranslation(
        windowWidth = windowWidth,
    )
    val maximumBackCornerRadius = with(density) { 28.dp.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HomeScreen(
            modifier = Modifier
                .then(
                    if (state.hasDocument) {
                        Modifier.clearAndSetSemantics {}
                    } else {
                        Modifier
                    },
                ),
            recentDocuments = markdownViewModel.recentDocuments,
            recoverableWork = recoverableWork,
            snackbarHostState = snackbarHostState,
            showSnackbarHost = !state.hasDocument,
            interactive = !state.hasDocument &&
                isRecoveryInitialized &&
                !isRecoveryTransitioning &&
                !isRecoverySaving &&
                !saveAsPending,
            onOpenFile = {
                openDocumentLauncher.launch(
                    arrayOf(
                        "text/markdown",
                        "text/x-markdown",
                        "application/markdown",
                        "application/x-markdown",
                        "text/plain",
                        "application/octet-stream",
                    ),
                )
            },
            onNewDraft = {
                val failure = markdownViewModel.newDraft(
                    name = untitledDocumentFilename,
                    initialContent = newDraftTemplate,
                )
                if (failure == null) {
                    showAppSettings = false
                    readerScrollY = 0
                    mode = DocumentMode.EDITING
                }
            },
            onRecover = { recovery ->
                showAppSettings = false
                readerScrollY = 0
                mode = DocumentMode.EDITING
                markdownViewModel.recover(recovery)
            },
            onSaveRecoveryCopy = { recovery ->
                launchSaveDestination(
                    purpose = SaveDestinationPurpose.RECOVERY_COPY,
                    recovery = recovery,
                )
            },
            onDiscardRecovery = { recovery ->
                markdownViewModel.discardRecovery(context, recovery)
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

        if (state.hasDocument) {
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

            // Keep this container free of full-screen pointer consumers. The
            // AndroidView reader must receive unconsumed MotionEvents for native
            // WebView scrolling; Home is already non-interactive while a document
            // is open and remains behind this full-size surface.
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val frame = if (predictiveBackVisualActive) {
                            calculatePredictiveDocumentBackFrame(
                                progress = predictiveBackProgress,
                                swipeEdge = predictiveBackSwipeEdge,
                                maximumTranslation = maximumBackTranslation,
                                maximumCornerRadius = maximumBackCornerRadius,
                            )
                        } else {
                            PredictiveDocumentBackFrame.Idle
                        }
                        scaleX = frame.document.scale
                        scaleY = frame.document.scale
                        translationX = frame.document.translation
                        alpha = frame.document.alpha
                        val cornerRadius = frame.document.cornerRadius
                        shape = RoundedCornerShape(cornerRadius.toDp())
                        clip = cornerRadius > 0f
                    },
                color = colors.surface,
            ) {
                DocumentScreen(
                    documentKey = state.sessionId,
                    documentUri = state.uri,
                    name = displayedDocumentName,
                    dirty = state.isDirty,
                    saving = state.isSaving || saveAsPending || isRecoveryTransitioning,
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
                            readerScrollSessionId = markdownViewModel.uiState.sessionId
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
                    predictiveBackBlocked = showReaderAppearance ||
                        showCloseDialog ||
                        state.isSaving ||
                        saveAsPending ||
                        isRecoveryTransitioning ||
                        pendingIncomingRequest != null ||
                        pendingConflict != null ||
                        pendingSaveFailure != null,
                    predictiveBackVisualActive = predictiveBackVisualActive,
                    onPredictiveBackProgress = { gestureKey, progress, swipeEdge ->
                        if (markdownViewModel.uiState.sessionId == gestureKey) {
                            predictiveBackResetJob?.cancel()
                            predictiveBackResetJob = null
                            predictiveBackSwipeEdge = swipeEdge
                            predictiveBackProgress = progress
                            predictiveBackVisualActive = true
                        }
                    },
                    onPredictiveBackCancelled = { gestureKey ->
                        if (markdownViewModel.uiState.sessionId == gestureKey) {
                            animatePredictiveBackCancellation()
                        }
                    },
                    onPredictiveBackCompleted = { gestureKey ->
                        val currentState = markdownViewModel.uiState
                        if (
                            currentState.hasDocument &&
                            currentState.sessionId == gestureKey &&
                            !currentState.isDirty &&
                            !currentState.isSaving &&
                            !markdownViewModel.isRecoveryTransitioning
                        ) {
                            resetPredictiveBackImmediately()
                            showReaderAppearance = false
                            showAppSettings = false
                            readerScrollY = 0
                            markdownViewModel.closeDocument(context)
                        } else {
                            animatePredictiveBackCancellation()
                        }
                    },
                    onAppearance = {
                        showAppSettings = false
                        showReaderAppearance = true
                    },
                    onSave = ::saveCurrentDocument,
                    onEditorChanged = { value ->
                        if (markdownViewModel.updateContent(value.text) == null) {
                            editorValue = value
                        }
                    },
                )
            }
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

    if (showCloseDialog) {
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = { Text(stringResource(R.string.unsaved_close_title)) },
            text = { Text(stringResource(R.string.unsaved_close_body)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            showCloseDialog = false
                            beginPostSave(PostSaveAction.CLOSE)
                            saveCurrentDocument()
                        },
                    ) { Text(stringResource(R.string.save_document)) }
                    TextButton(
                        onClick = {
                            showCloseDialog = false
                            showReaderAppearance = false
                            showAppSettings = false
                            readerScrollY = 0
                            markdownViewModel.closeDocument(
                                context = context,
                                discardChanges = true,
                            )
                        },
                    ) { Text(stringResource(R.string.discard_changes)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingIncomingRequest
        ?.takeIf {
            postSaveAction != PostSaveAction.OPEN_INCOMING &&
                isRecoveryInitialized &&
                !isRecoveryTransitioning &&
                !isRecoverySaving &&
                recoverableWork == null &&
                state.hasDocument &&
                state.isDirty &&
                !state.isSaving &&
                !saveAsPending &&
                pendingConflict == null &&
                pendingSaveFailure == null
        }
        ?.let { request ->
        AlertDialog(
            onDismissRequest = {
                markdownViewModel.clearPendingIncomingRequest(request.id)
                onIncomingRequestConsumed(request.id)
                clearPostSaveAction()
            },
            title = { Text(stringResource(R.string.open_new_document_title)) },
            text = { Text(stringResource(R.string.open_new_document_body)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            beginPostSave(PostSaveAction.OPEN_INCOMING)
                            saveCurrentDocument()
                        },
                    ) { Text(stringResource(R.string.save_document)) }
                    TextButton(
                        onClick = {
                            markdownViewModel.closeDocument(
                                context = context,
                                discardChanges = true,
                            )
                        },
                    ) { Text(stringResource(R.string.discard_and_open)) }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        markdownViewModel.clearPendingIncomingRequest(request.id)
                        onIncomingRequestConsumed(request.id)
                        clearPostSaveAction()
                    },
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingConflict?.let {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.external_change_title)) },
            text = { Text(stringResource(R.string.external_change_body)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            markdownViewModel.overwriteConflict(context, notifySave)
                        },
                    ) { Text(stringResource(R.string.overwrite_document)) }
                    TextButton(
                        onClick = {
                            markdownViewModel.clearConflict()
                            launchSaveDestination(SaveDestinationPurpose.SAVE_COPY)
                        },
                    ) { Text(stringResource(R.string.save_copy)) }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        clearPostSaveAction()
                        markdownViewModel.reloadConflict(context)
                    },
                ) { Text(stringResource(R.string.reload_document)) }
            },
        )
    }

    pendingSaveFailure?.let { failure ->
        AlertDialog(
            onDismissRequest = {
                pendingSaveFailure = null
                failedRecoveryCopyId = null
                clearPostSaveAction()
            },
            title = { Text(stringResource(R.string.save_failed_title)) },
            text = { Text(failureMessage(failure)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            pendingSaveFailure = null
                            val recovery = recoverableWork?.takeIf {
                                it.recoveryId == failedRecoveryCopyId
                            }
                            if (recovery != null) {
                                launchSaveDestination(
                                    SaveDestinationPurpose.RECOVERY_COPY,
                                    recovery,
                                )
                            } else if (
                                markdownViewModel.uiState.sessionId ==
                                pendingFailureSessionId &&
                                markdownViewModel.uiState.contentRevision ==
                                pendingFailureRevision
                            ) {
                                saveCurrentDocument()
                            } else {
                                clearPostSaveAction()
                            }
                        },
                    ) { Text(stringResource(R.string.retry)) }
                    TextButton(
                        onClick = {
                            pendingSaveFailure = null
                            val recovery = recoverableWork?.takeIf {
                                it.recoveryId == failedRecoveryCopyId
                            }
                            if (recovery != null) {
                                launchSaveDestination(
                                    SaveDestinationPurpose.RECOVERY_COPY,
                                    recovery,
                                )
                            } else {
                                launchSaveDestination(SaveDestinationPurpose.SAVE_COPY)
                            }
                        },
                    ) { Text(stringResource(R.string.save_copy)) }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingSaveFailure = null
                        failedRecoveryCopyId = null
                        clearPostSaveAction()
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
