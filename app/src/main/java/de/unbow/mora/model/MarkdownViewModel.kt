package de.unbow.mora.model

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.unbow.mora.IncomingDocumentRequest
import de.unbow.mora.data.DirtyDocumentRecovery
import de.unbow.mora.data.DocumentAccessException
import de.unbow.mora.data.DocumentFailure
import de.unbow.mora.data.DocumentPayloadCodec
import de.unbow.mora.data.DocumentPermission
import de.unbow.mora.data.DocumentPermissionManager
import de.unbow.mora.data.DocumentRecoveryRepository
import de.unbow.mora.data.DocumentRepository
import de.unbow.mora.data.DocumentVersion
import de.unbow.mora.data.PendingWriteRecovery
import de.unbow.mora.data.RecentDocument
import de.unbow.mora.data.RecentDocumentsRepository
import de.unbow.mora.data.isRecoverySourceUriSupported
import de.unbow.mora.data.sanitizeRecoveryDocumentName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface DocumentUiError {
    data class OpenFailed(
        val documentName: String,
        val usesLocalizedFallback: Boolean = false,
        val failure: DocumentFailure = DocumentFailure.READ_FAILED,
    ) : DocumentUiError

    data class ActionFailed(val failure: DocumentFailure) : DocumentUiError
}

sealed interface DocumentSaveResult {
    data object Saved : DocumentSaveResult
    data object Copied : DocumentSaveResult
    data object Conflict : DocumentSaveResult
    data class Failed(val failure: DocumentFailure) : DocumentSaveResult
}

data class DocumentConflict(
    val sessionId: Long,
    val contentRevision: Long,
    val observedVersion: DocumentVersion,
)

internal data class ResolvedDocumentName(
    val storedName: String,
    val usesLocalizedFallback: Boolean,
    val persistedName: String,
)

internal fun resolveDocumentName(
    sourceName: String?,
    fallbackName: String,
    fallbackIsLocalized: Boolean,
): ResolvedDocumentName {
    val originalName = sourceName
        ?.takeIf(String::isNotBlank)
        ?.let(::sanitizeRecoveryDocumentName)
    val fallback = fallbackName
        .takeIf(String::isNotBlank)
        ?.let(::sanitizeRecoveryDocumentName)
        .orEmpty()
    val usesLocalizedFallback = originalName == null && fallbackIsLocalized
    val stableName = when {
        originalName != null -> originalName
        usesLocalizedFallback -> ""
        else -> fallback
    }
    return ResolvedDocumentName(
        storedName = stableName,
        usesLocalizedFallback = usesLocalizedFallback,
        persistedName = stableName,
    )
}

internal fun displayDocumentName(
    storedName: String,
    usesLocalizedFallback: Boolean,
    localizedFallback: String,
): String = if (usesLocalizedFallback || storedName.isBlank()) {
    localizedFallback
} else {
    storedName
}

data class DocumentUiState(
    val sessionId: Long = 0L,
    val recoveryId: String = "",
    val hasDocument: Boolean = false,
    val uri: Uri? = null,
    val name: String = "",
    val nameUsesLocalizedFallback: Boolean = false,
    val content: String = "",
    val canWrite: Boolean = false,
    val managedPermissionFlags: Int = 0,
    val baseVersion: DocumentVersion? = null,
    val hasUtf8Bom: Boolean = false,
    val initialScrollY: Int = 0,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val error: DocumentUiError? = null,
    val contentVersion: Long = 0L,
    val contentRevision: Long = 0L,
)

internal data class DocumentSaveSnapshot(
    val requestId: Long,
    val sessionId: Long,
    val contentRevision: Long,
    val content: String,
    val sourceUri: String? = null,
    val recoveryId: String = "",
    val name: String = "",
    val nameUsesLocalizedFallback: Boolean = false,
    val managedPermissionFlags: Int = 0,
    val baseVersion: DocumentVersion? = null,
    val hasUtf8Bom: Boolean = false,
    val recoveryGeneration: Long = 0L,
    val wasDirty: Boolean = false,
)

private class DocumentSaveCoordinatorState {
    private var requestCounter = 0L
    private var activeSnapshot: DocumentSaveSnapshot? = null

    fun nextRequestId(): Long {
        requestCounter += 1
        return requestCounter
    }

    fun activeSnapshot(): DocumentSaveSnapshot? = activeSnapshot

    fun setActiveSnapshot(snapshot: DocumentSaveSnapshot?) {
        activeSnapshot = snapshot
    }
}

internal class DocumentSaveCoordinator private constructor(
    private val state: DocumentSaveCoordinatorState,
) {

    constructor() : this(PROCESS_STATE)

    fun tryBegin(
        sessionId: Long,
        contentRevision: Long,
        content: String,
        sourceUri: String? = null,
        recoveryId: String = "",
        name: String = "",
        nameUsesLocalizedFallback: Boolean = false,
        managedPermissionFlags: Int = 0,
        baseVersion: DocumentVersion? = null,
        hasUtf8Bom: Boolean = false,
        recoveryGeneration: Long = 0L,
        wasDirty: Boolean = false,
    ): DocumentSaveSnapshot? = synchronized(state) {
        if (state.activeSnapshot() != null) return@synchronized null
        DocumentSaveSnapshot(
            requestId = state.nextRequestId(),
            sessionId = sessionId,
            contentRevision = contentRevision,
            content = content,
            sourceUri = sourceUri,
            recoveryId = recoveryId,
            name = name,
            nameUsesLocalizedFallback = nameUsesLocalizedFallback,
            managedPermissionFlags = managedPermissionFlags,
            baseVersion = baseVersion,
            hasUtf8Bom = hasUtf8Bom,
            recoveryGeneration = recoveryGeneration,
            wasDirty = wasDirty,
        ).also { snapshot -> state.setActiveSnapshot(snapshot) }
    }

    fun finish(snapshot: DocumentSaveSnapshot) {
        synchronized(state) {
            if (state.activeSnapshot()?.requestId == snapshot.requestId) {
                state.setActiveSnapshot(null)
            }
        }
    }

    companion object {
        private val PROCESS_STATE = DocumentSaveCoordinatorState()

        internal fun isolatedForTesting(): DocumentSaveCoordinator =
            DocumentSaveCoordinator(DocumentSaveCoordinatorState())
    }
}

internal fun shouldUseSaveAs(
    hasUri: Boolean,
    canWrite: Boolean,
): Boolean = !hasUri || !canWrite

internal fun targetsCurrentSource(
    sourceUri: String?,
    targetUri: String,
): Boolean = sourceUri != null && sourceUri == targetUri

internal fun isSaveResultCurrent(
    currentSessionId: Long,
    savedSnapshot: DocumentSaveSnapshot,
): Boolean = currentSessionId == savedSnapshot.sessionId

internal fun isSaveSnapshotCurrent(
    currentSessionId: Long,
    currentContentRevision: Long,
    savedSnapshot: DocumentSaveSnapshot,
): Boolean = isSaveResultCurrent(currentSessionId, savedSnapshot) &&
    currentContentRevision == savedSnapshot.contentRevision

internal fun shouldRemainDirtyAfterSave(
    currentContent: String,
    currentContentRevision: Long,
    savedSnapshot: DocumentSaveSnapshot,
): Boolean {
    if (currentContentRevision == savedSnapshot.contentRevision) return false
    return currentContent != savedSnapshot.content
}

internal fun stateAfterSaveFailure(state: DocumentUiState): DocumentUiState =
    state.copy(isSaving = false)

internal fun shouldRecordRecentDocument(
    uriScheme: String?,
    hasDurableReadPermission: Boolean,
): Boolean = uriScheme != ContentResolver.SCHEME_CONTENT || hasDurableReadPermission

internal fun hasExternalVersionChanged(
    expectedVersion: DocumentVersion,
    observedVersion: DocumentVersion,
): Boolean = expectedVersion != observedVersion

internal fun isNewWorkAllowed(
    recoveryInitialized: Boolean,
    recoveryTransitioning: Boolean,
    recoverySaving: Boolean,
    hasOpenDocument: Boolean,
    documentSaving: Boolean,
    hasRecoverableWork: Boolean,
): Boolean = recoveryInitialized &&
    !recoveryTransitioning &&
    !recoverySaving &&
    !hasOpenDocument &&
    !documentSaving &&
    !hasRecoverableWork

internal fun hasRequiredUriPermission(
    permission: DocumentPermission,
    requiredFlag: Int,
): Boolean = (permission.persistedFlags or permission.sessionFlags) and requiredFlag != 0

internal fun shouldFlushRecovery(
    hasDocument: Boolean,
    isDirty: Boolean,
    @Suppress("UNUSED_PARAMETER") documentSaving: Boolean,
    recoveryTransitioning: Boolean,
): Boolean = hasDocument && isDirty && !recoveryTransitioning

internal fun shouldReportRecoveryPersistenceFailure(
    currentRecoveryId: String,
    currentContentRevision: Long,
    currentIsDirty: Boolean,
    attemptedRecoveryId: String,
    attemptedContentRevision: Long,
): Boolean = currentIsDirty &&
    currentRecoveryId == attemptedRecoveryId &&
    currentContentRevision == attemptedContentRevision

private enum class SaveRoute {
    IN_PLACE,
    SAVE_AS,
    COPY,
}

private sealed interface SaveOutcome {
    data class Success(
        val version: DocumentVersion,
        val loaded: DocumentRepository.LoadedDocument,
    ) : SaveOutcome

    data class Conflict(val observedVersion: DocumentVersion) : SaveOutcome
    data class Failure(val failure: DocumentFailure) : SaveOutcome
}

class MarkdownViewModel : ViewModel() {

    var uiState by mutableStateOf(DocumentUiState())
        private set

    var recentDocuments by mutableStateOf<List<RecentDocument>>(emptyList())
        private set

    var pendingIncomingRequest by mutableStateOf<IncomingDocumentRequest?>(null)
        private set

    var pendingConflict by mutableStateOf<DocumentConflict?>(null)
        private set

    var recoverableWork by mutableStateOf<DirtyDocumentRecovery?>(null)
        private set

    var isRecoverySaving by mutableStateOf(false)
        private set

    var isRecoveryInitialized by mutableStateOf(false)
        private set

    var isRecoveryTransitioning by mutableStateOf(false)
        private set

    private var persistedContent: String = ""
    private var requiresSuccessfulSaveToClearDirty: Boolean = false
    private var versionCounter: Long = 0L
    private var contentRevisionCounter: Long = 0L
    private var recoveryGenerationCounter: Long = 0L
    private var sessionCounter: Long = 0L
    private val saveCoordinator = DocumentSaveCoordinator()
    private var pendingConflictSnapshot: DocumentSaveSnapshot? = null
    private var initialized = false
    private var applicationContext: Context? = null
    private var recoveryRepository: DocumentRecoveryRepository? = null
    private var recoveryDebounceJob: Job? = null
    private var recoveryMutationJob: Job? = null

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val safeContext = context.applicationContext
        applicationContext = safeContext
        val repository = DocumentRecoveryRepository(safeContext)
        recoveryRepository = repository
        recentDocuments = RecentDocumentsRepository.load(safeContext)

        viewModelScope.launch {
            try {
                val recovery = reconcileRecoveryAfterRestart(safeContext, repository)
                recoverableWork = recovery
                recovery?.let {
                    contentRevisionCounter = maxOf(contentRevisionCounter, it.contentRevision)
                    recoveryGenerationCounter = maxOf(
                        recoveryGenerationCounter,
                        it.recoveryGeneration,
                    )
                }
                reconcileManagedPermissions(safeContext, repository)
            } finally {
                isRecoveryInitialized = true
            }
        }
    }

    fun canStartNewWork(): Boolean = isNewWorkAllowed(
        recoveryInitialized = isRecoveryInitialized,
        recoveryTransitioning = isRecoveryTransitioning,
        recoverySaving = isRecoverySaving,
        hasOpenDocument = uiState.hasDocument,
        documentSaving = uiState.isSaving,
        hasRecoverableWork = recoverableWork != null,
    )

    fun deferIncomingRequest(request: IncomingDocumentRequest) {
        pendingIncomingRequest = request
    }

    fun clearPendingIncomingRequest(requestId: Long) {
        if (pendingIncomingRequest?.id == requestId) {
            pendingIncomingRequest = null
        }
    }

    fun clearPendingIncomingRequest() {
        pendingIncomingRequest = null
    }

    fun openDocument(
        context: Context,
        uri: Uri,
        fallbackName: String,
        acquiredPermission: DocumentPermission? = null,
    ): Boolean {
        if (!canStartNewWork()) return false
        recoveryDebounceJob?.cancel()
        val safeContext = context.applicationContext
        val permission = acquiredPermission
            ?: DocumentPermissionManager.currentForUse(safeContext, uri)
        val requestSessionId = nextSessionId()
        val requestVersion = nextVersion()
        val knownPosition = recentDocuments.firstOrNull { it.uri == uri }?.scrollY
            ?: RecentDocumentsRepository.load(safeContext).firstOrNull { it.uri == uri }?.scrollY
            ?: 0
        val uriName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
        val initialName = resolveDocumentName(
            sourceName = uriName,
            fallbackName = fallbackName,
            fallbackIsLocalized = true,
        )
        persistedContent = ""
        requiresSuccessfulSaveToClearDirty = false
        pendingConflict = null
        pendingConflictSnapshot = null
        uiState = DocumentUiState(
            sessionId = requestSessionId,
            recoveryId = UUID.randomUUID().toString(),
            hasDocument = true,
            uri = uri,
            name = initialName.storedName,
            nameUsesLocalizedFallback = initialName.usesLocalizedFallback,
            managedPermissionFlags = permission.persistedFlags,
            isLoading = true,
            contentVersion = requestVersion,
            contentRevision = nextContentRevision(),
            initialScrollY = knownPosition,
        )

        viewModelScope.launch {
            val loaded = try {
                DocumentRepository.read(safeContext, uri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: DocumentAccessException) {
                handleOpenFailure(
                    context = safeContext,
                    requestSessionId = requestSessionId,
                    initialName = initialName,
                    failure = error.failure,
                    uri = uri,
                    managedPermissionFlags = permission.persistedFlags,
                )
                return@launch
            } catch (_: Exception) {
                handleOpenFailure(
                    context = safeContext,
                    requestSessionId = requestSessionId,
                    initialName = initialName,
                    failure = DocumentFailure.READ_FAILED,
                    uri = uri,
                    managedPermissionFlags = permission.persistedFlags,
                )
                return@launch
            }

            if (uiState.sessionId != requestSessionId) {
                releasePermissionIfUnused(
                    safeContext,
                    uri,
                    permission.persistedFlags,
                )
                return@launch
            }
            persistedContent = loaded.content
            val resolvedName = resolveDocumentName(
                sourceName = loaded.name,
                fallbackName = fallbackName,
                fallbackIsLocalized = true,
            )
            if (shouldRecordRecentDocument(uri.scheme, permission.hasDurableRead)) {
                val mutation = RecentDocumentsRepository.recordOpenedWithEvictions(
                    context = safeContext,
                    uri = uri,
                    name = resolvedName.persistedName,
                    managedPermissionFlags = permission.persistedFlags,
                )
                recentDocuments = mutation.documents
                releaseRemovedPermissions(safeContext, mutation.removed)
            }
            uiState = uiState.copy(
                name = resolvedName.storedName,
                nameUsesLocalizedFallback = resolvedName.usesLocalizedFallback,
                content = loaded.content,
                canWrite = loaded.canWrite && isRecoverySourceUriSupported(uri.toString()),
                baseVersion = loaded.version,
                hasUtf8Bom = loaded.hasUtf8Bom,
                isDirty = false,
                isLoading = false,
                initialScrollY = knownPosition,
                contentVersion = nextVersion(),
                contentRevision = nextContentRevision(),
            )
        }
        return true
    }

    fun newDraft(name: String, initialContent: String): DocumentFailure? =
        openUnsavedContent(name, initialContent)

    fun openSharedText(content: String, name: String): DocumentFailure? =
        openUnsavedContent(name, content)

    private fun openUnsavedContent(name: String, content: String): DocumentFailure? {
        if (!canStartNewWork()) return DocumentFailure.RECOVERY_FAILED
        val failure = validateEditableContent(content, includeUtf8Bom = false)
        if (failure != null) {
            uiState = uiState.copy(error = DocumentUiError.ActionFailed(failure))
            return failure
        }
        recoveryDebounceJob?.cancel()
        persistedContent = ""
        requiresSuccessfulSaveToClearDirty = false
        pendingConflict = null
        pendingConflictSnapshot = null
        uiState = DocumentUiState(
            sessionId = nextSessionId(),
            recoveryId = UUID.randomUUID().toString(),
            hasDocument = true,
            name = sanitizeRecoveryDocumentName(name),
            content = content,
            canWrite = false,
            isDirty = true,
            contentVersion = nextVersion(),
            contentRevision = nextContentRevision(),
        )
        scheduleDirtyRecovery(uiState)
        return null
    }

    fun updateContent(content: String): DocumentFailure? {
        if (!uiState.hasDocument || isRecoveryTransitioning) {
            return DocumentFailure.RECOVERY_FAILED
        }
        if (content == uiState.content) return null
        val failure = validateEditableContent(content, uiState.hasUtf8Bom)
        if (failure != null) {
            uiState = uiState.copy(error = DocumentUiError.ActionFailed(failure))
            return failure
        }
        uiState = uiState.copy(
            content = content,
            isDirty = uiState.hasDocument && (
                uiState.isSaving ||
                    uiState.uri == null ||
                    requiresSuccessfulSaveToClearDirty ||
                    content != persistedContent
                ),
            contentRevision = nextContentRevision(),
        )
        if (uiState.isDirty) {
            scheduleDirtyRecovery(uiState)
        } else {
            recoveryDebounceJob?.cancel()
            clearDirtyRecovery(uiState.recoveryId)
        }
        return null
    }

    fun flushRecovery() {
        val state = uiState
        if (
            !shouldFlushRecovery(
                hasDocument = state.hasDocument,
                isDirty = state.isDirty,
                documentSaving = state.isSaving,
                recoveryTransitioning = isRecoveryTransitioning,
            )
        ) {
            return
        }
        recoveryDebounceJob?.cancel()
        persistDirtyRecovery(state)
    }

    fun recover(work: DirtyDocumentRecovery? = recoverableWork) {
        val recovery = work ?: return
        if (
            !isRecoveryInitialized ||
            isRecoveryTransitioning ||
            isRecoverySaving ||
            uiState.hasDocument ||
            uiState.isSaving ||
            recoverableWork?.recoveryId != recovery.recoveryId
        ) {
            return
        }
        val uri = recovery.sourceUri?.let(Uri::parse)
        uri?.let { DocumentPermissionManager.currentForUse(requireContext(), it) }
        val recoveredSessionId = nextSessionId()
        recoveryDebounceJob?.cancel()
        persistedContent = ""
        requiresSuccessfulSaveToClearDirty = true
        recoverableWork = null
        pendingConflict = null
        pendingConflictSnapshot = null
        contentRevisionCounter = maxOf(contentRevisionCounter, recovery.contentRevision)
        recoveryGenerationCounter = maxOf(
            recoveryGenerationCounter,
            recovery.recoveryGeneration,
        )
        uiState = DocumentUiState(
            sessionId = recoveredSessionId,
            recoveryId = recovery.recoveryId,
            hasDocument = true,
            uri = uri,
            name = recovery.name,
            content = recovery.content,
            canWrite = false,
            managedPermissionFlags = recovery.managedPermissionFlags,
            baseVersion = recovery.baseVersion,
            hasUtf8Bom = recovery.hasUtf8Bom,
            isDirty = true,
            contentVersion = nextVersion(),
            contentRevision = recovery.contentRevision,
        )
        scheduleDirtyRecovery(uiState)
        if (uri != null) {
            viewModelScope.launch {
                val canWrite = isRecoverySourceUriSupported(uri.toString()) &&
                    DocumentRepository.supportsInPlaceWrite(requireContext(), uri)
                if (uiState.sessionId == recoveredSessionId) {
                    uiState = uiState.copy(canWrite = canWrite)
                }
            }
        }
    }

    fun discardRecovery(context: Context, work: DirtyDocumentRecovery? = recoverableWork) {
        val recovery = work ?: return
        if (
            !isRecoveryInitialized ||
            isRecoveryTransitioning ||
            isRecoverySaving ||
            uiState.hasDocument ||
            recoverableWork?.recoveryId != recovery.recoveryId
        ) {
            return
        }
        val clearThroughGeneration = nextRecoveryGeneration()
        val pendingRecoveryMutation = recoveryMutationJob
        isRecoveryTransitioning = true
        viewModelScope.launch {
            try {
                pendingRecoveryMutation?.join()
                val replacement = withContext(Dispatchers.IO) {
                    val repository = recoveryRepository
                        ?: return@withContext recovery
                    repository.clearDirtyAtOrBefore(
                        recovery.recoveryId,
                        clearThroughGeneration,
                    )
                    if (recovery.isOriginalBackup) {
                        repository.deleteOriginalBackup(recovery.recoveryId)
                    } else {
                        repository.markPendingIntentDiscarded(recovery.recoveryId)
                    }
                    repository.loadDirty() ?: repository.recoverPendingAvailable()
                }
                recoverableWork = replacement
                recovery.sourceUri?.let(Uri::parse)?.let { uri ->
                    releasePermissionIfUnused(
                        context.applicationContext,
                        uri,
                        recovery.managedPermissionFlags,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                uiState = uiState.copy(
                    error = DocumentUiError.ActionFailed(DocumentFailure.RECOVERY_FAILED),
                )
            } finally {
                isRecoveryTransitioning = false
            }
        }
    }

    fun saveRecoveryCopy(
        context: Context,
        uri: Uri,
        work: DirtyDocumentRecovery? = recoverableWork,
        onResult: (DocumentSaveResult) -> Unit,
    ) {
        val recovery = work ?: return
        if (
            !isRecoveryInitialized ||
            isRecoveryTransitioning ||
            isRecoverySaving ||
            uiState.hasDocument ||
            recoverableWork?.recoveryId != recovery.recoveryId
        ) {
            return
        }
        if (targetsCurrentSource(recovery.sourceUri, uri.toString())) {
            onResult(DocumentSaveResult.Failed(DocumentFailure.SAVE_AS_REQUIRED))
            return
        }
        val clearThroughGeneration = nextRecoveryGeneration()
        val pendingRecoveryMutation = recoveryMutationJob
        isRecoverySaving = true
        viewModelScope.launch {
            try {
                pendingRecoveryMutation?.join()
                val outcome = try {
                    val version = DocumentRepository.write(
                        context.applicationContext,
                        uri,
                        recovery.content,
                        includeUtf8Bom = recovery.hasUtf8Bom,
                    )
                    val verified = DocumentRepository.read(context.applicationContext, uri)
                    if (verified.version != version) {
                        SaveOutcome.Failure(DocumentFailure.VERIFICATION_FAILED)
                    } else {
                        SaveOutcome.Success(version, verified)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: DocumentAccessException) {
                    SaveOutcome.Failure(error.failure)
                } catch (_: Exception) {
                    SaveOutcome.Failure(DocumentFailure.WRITE_FAILED)
                }

                when (outcome) {
                    is SaveOutcome.Success -> {
                        val replacement = try {
                            withContext(Dispatchers.IO) {
                                val repository = recoveryRepository
                                    ?: throw IllegalStateException(
                                        "Recovery is not initialized.",
                                    )
                                repository.clearDirtyAtOrBefore(
                                    recovery.recoveryId,
                                    clearThroughGeneration,
                                )
                                if (recovery.isOriginalBackup) {
                                    repository.deleteOriginalBackup(recovery.recoveryId)
                                } else {
                                    repository.markPendingIntentDiscarded(recovery.recoveryId)
                                }
                                repository.loadDirty() ?: repository.recoverPendingAvailable()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            onResult(
                                DocumentSaveResult.Failed(DocumentFailure.RECOVERY_FAILED),
                            )
                            return@launch
                        }
                        if (recoverableWork?.recoveryId == recovery.recoveryId) {
                            recoverableWork = replacement
                        }
                        onResult(DocumentSaveResult.Copied)
                    }

                    is SaveOutcome.Failure -> onResult(DocumentSaveResult.Failed(outcome.failure))
                    is SaveOutcome.Conflict -> Unit
                }
            } finally {
                isRecoverySaving = false
            }
        }
    }

    fun save(context: Context, onResult: (DocumentSaveResult) -> Unit) {
        val stateToSave = uiState
        val uri = stateToSave.uri
        if (shouldUseSaveAs(hasUri = uri != null, canWrite = stateToSave.canWrite)) {
            onResult(DocumentSaveResult.Failed(DocumentFailure.SAVE_AS_REQUIRED))
            return
        }
        if (stateToSave.baseVersion == null) {
            onResult(DocumentSaveResult.Failed(DocumentFailure.SAVE_AS_REQUIRED))
            return
        }
        launchSave(
            context = context.applicationContext,
            uri = requireNotNull(uri),
            stateToSave = stateToSave,
            route = SaveRoute.IN_PLACE,
            approvedExternalVersion = null,
            onResult = onResult,
        )
    }

    fun saveAs(context: Context, uri: Uri, onResult: (DocumentSaveResult) -> Unit) {
        launchSave(
            context = context.applicationContext,
            uri = uri,
            stateToSave = uiState,
            route = SaveRoute.SAVE_AS,
            approvedExternalVersion = null,
            onResult = onResult,
        )
    }

    fun saveCopy(context: Context, uri: Uri, onResult: (DocumentSaveResult) -> Unit) {
        launchSave(
            context = context.applicationContext,
            uri = uri,
            stateToSave = uiState,
            route = SaveRoute.COPY,
            approvedExternalVersion = null,
            onResult = onResult,
        )
    }

    fun overwriteConflict(context: Context, onResult: (DocumentSaveResult) -> Unit) {
        val conflict = pendingConflict ?: return
        val snapshot = pendingConflictSnapshot ?: return
        val currentState = uiState
        if (
            !isSaveSnapshotCurrent(
                currentSessionId = currentState.sessionId,
                currentContentRevision = currentState.contentRevision,
                savedSnapshot = snapshot,
            )
        ) {
            clearConflict()
            return
        }
        val uri = currentState.uri ?: return
        clearConflict()
        launchSave(
            context = context.applicationContext,
            uri = uri,
            stateToSave = currentState,
            route = SaveRoute.IN_PLACE,
            approvedExternalVersion = conflict.observedVersion,
            onResult = onResult,
        )
    }

    fun reloadConflict(context: Context) {
        val snapshot = pendingConflictSnapshot ?: return
        val uri = uiState.uri ?: return
        if (uiState.isSaving || isRecoverySaving || isRecoveryTransitioning) return
        if (!isSaveSnapshotCurrent(uiState.sessionId, uiState.contentRevision, snapshot)) {
            clearConflict()
            return
        }
        val sessionId = snapshot.sessionId
        val clearThroughGeneration = nextRecoveryGeneration()
        clearConflict()
        isRecoveryTransitioning = true
        uiState = uiState.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val loaded = try {
                    DocumentRepository.read(context.applicationContext, uri)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: DocumentAccessException) {
                    if (uiState.sessionId == sessionId) {
                        uiState = uiState.copy(
                            isLoading = false,
                            error = DocumentUiError.ActionFailed(
                                permissionAwareFailure(
                                    context.applicationContext,
                                    uri,
                                    error.failure,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                ),
                            ),
                        )
                    }
                    return@launch
                }
                if (uiState.sessionId != sessionId) return@launch
                persistedContent = loaded.content
                requiresSuccessfulSaveToClearDirty = false
                recoveryDebounceJob?.cancel()
                recoveryMutationJob?.join()
                val recoveryId = uiState.recoveryId
                val replacement = withContext(Dispatchers.IO) {
                    val repository = recoveryRepository
                        ?: return@withContext null
                    repository.clearDirtyAtOrBefore(
                        recoveryId,
                        clearThroughGeneration,
                    )
                    repository.markPendingIntentDiscarded(recoveryId)
                    repository.loadDirty() ?: repository.recoverPendingAvailable()
                }
                recoverableWork = replacement
                uiState = uiState.copy(
                    content = loaded.content,
                    canWrite = loaded.canWrite && isRecoverySourceUriSupported(uri.toString()),
                    baseVersion = loaded.version,
                    hasUtf8Bom = loaded.hasUtf8Bom,
                    isDirty = false,
                    isLoading = false,
                    contentVersion = nextVersion(),
                    contentRevision = nextContentRevision(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (uiState.sessionId == sessionId) {
                    uiState = uiState.copy(
                        isLoading = false,
                        error = DocumentUiError.ActionFailed(DocumentFailure.RECOVERY_FAILED),
                    )
                }
            } finally {
                isRecoveryTransitioning = false
            }
        }
    }

    fun clearConflict() {
        pendingConflict = null
        pendingConflictSnapshot = null
    }

    fun updateReadingPosition(context: Context, uri: Uri, scrollY: Int) {
        recentDocuments = RecentDocumentsRepository.updatePosition(context, uri, scrollY)
    }

    fun removeRecent(context: Context, document: RecentDocument) {
        recentDocuments = RecentDocumentsRepository.remove(context, document.uri)
        releasePermissionIfUnused(
            context.applicationContext,
            document.uri,
            document.managedPermissionFlags,
        )
    }

    private fun launchSave(
        context: Context,
        uri: Uri,
        stateToSave: DocumentUiState,
        route: SaveRoute,
        approvedExternalVersion: DocumentVersion?,
        onResult: (DocumentSaveResult) -> Unit,
    ) {
        if (
            !isRecoveryInitialized ||
            isRecoveryTransitioning ||
            isRecoverySaving ||
            !stateToSave.hasDocument ||
            uiState.sessionId != stateToSave.sessionId
        ) {
            onResult(DocumentSaveResult.Failed(DocumentFailure.RECOVERY_FAILED))
            return
        }
        val snapshot = saveCoordinator.tryBegin(
            sessionId = stateToSave.sessionId,
            contentRevision = stateToSave.contentRevision,
            content = stateToSave.content,
            sourceUri = stateToSave.uri?.toString(),
            recoveryId = stateToSave.recoveryId,
            name = stateToSave.name,
            nameUsesLocalizedFallback = stateToSave.nameUsesLocalizedFallback,
            managedPermissionFlags = stateToSave.managedPermissionFlags,
            baseVersion = stateToSave.baseVersion,
            hasUtf8Bom = stateToSave.hasUtf8Bom,
            recoveryGeneration = nextRecoveryGeneration(),
            wasDirty = stateToSave.isDirty,
        ) ?: run {
            onResult(DocumentSaveResult.Failed(DocumentFailure.WRITE_FAILED))
            return
        }
        if (uiState.sessionId != stateToSave.sessionId) {
            saveCoordinator.finish(snapshot)
            return
        }
        uiState = uiState.copy(isSaving = true)

        val saveJob = viewModelScope.launch {
            try {
                val outcome = when (route) {
                SaveRoute.IN_PLACE -> performInPlaceSave(
                    context = context,
                    uri = uri,
                    snapshot = snapshot,
                    approvedExternalVersion = approvedExternalVersion,
                )

                SaveRoute.SAVE_AS,
                SaveRoute.COPY,
                -> performNewTargetSave(context, uri, snapshot)
            }

                if (!isSaveResultCurrent(uiState.sessionId, snapshot)) return@launch

                when (outcome) {
                is SaveOutcome.Failure -> {
                    uiState = stateAfterSaveFailure(uiState)
                    onResult(DocumentSaveResult.Failed(outcome.failure))
                }

                is SaveOutcome.Conflict -> {
                    uiState = stateAfterSaveFailure(uiState)
                    if (isSaveSnapshotCurrent(uiState.sessionId, uiState.contentRevision, snapshot)) {
                        pendingConflictSnapshot = snapshot
                        pendingConflict = DocumentConflict(
                            sessionId = snapshot.sessionId,
                            contentRevision = snapshot.contentRevision,
                            observedVersion = outcome.observedVersion,
                        )
                        onResult(DocumentSaveResult.Conflict)
                    } else {
                        onResult(DocumentSaveResult.Failed(DocumentFailure.WRITE_FAILED))
                    }
                }

                is SaveOutcome.Success -> {
                    if (route == SaveRoute.COPY) {
                        uiState = uiState.copy(isSaving = false)
                        onResult(DocumentSaveResult.Copied)
                        return@launch
                    }
                    if (route == SaveRoute.SAVE_AS) {
                        val unresolvedSourceBackup = withContext(Dispatchers.IO) {
                            recoveryRepository?.loadPendingWrite()?.let { pending ->
                                pending.recoveryId == snapshot.recoveryId &&
                                    pending.sourceUri != uri.toString()
                            } == true
                        }
                        if (unresolvedSourceBackup) {
                            uiState = uiState.copy(isSaving = false)
                            onResult(DocumentSaveResult.Copied)
                            return@launch
                        }
                    }

                    val previousUri = uiState.uri
                    val previousManagedPermissionFlags = uiState.managedPermissionFlags
                    val resolvedName = if (route == SaveRoute.SAVE_AS) {
                        resolveDocumentName(
                            sourceName = outcome.loaded.name,
                            fallbackName = snapshot.name,
                            fallbackIsLocalized = snapshot.nameUsesLocalizedFallback,
                        )
                    } else {
                        null
                    }
                    val permission = if (route == SaveRoute.SAVE_AS) {
                        DocumentPermissionManager.acquire(context, uri)
                    } else {
                        DocumentPermission(
                            persistedFlags = uiState.managedPermissionFlags,
                            sessionFlags = 0,
                        )
                    }
                    if (!isSaveResultCurrent(uiState.sessionId, snapshot)) return@launch

                    persistedContent = snapshot.content
                    requiresSuccessfulSaveToClearDirty = false
                    if (
                        route == SaveRoute.SAVE_AS &&
                        shouldRecordRecentDocument(uri.scheme, permission.hasDurableRead)
                    ) {
                        val mutation = RecentDocumentsRepository.recordOpenedWithEvictions(
                            context = context,
                            uri = uri,
                            name = resolvedName?.persistedName.orEmpty(),
                            managedPermissionFlags = permission.persistedFlags,
                        )
                        recentDocuments = mutation.documents
                        releaseRemovedPermissions(context, mutation.removed)
                    }
                    val remainsDirty = shouldRemainDirtyAfterSave(
                        currentContent = uiState.content,
                        currentContentRevision = uiState.contentRevision,
                        savedSnapshot = snapshot,
                    )
                    uiState = uiState.copy(
                        uri = uri,
                        name = resolvedName?.storedName ?: uiState.name,
                        nameUsesLocalizedFallback = resolvedName?.usesLocalizedFallback
                            ?: uiState.nameUsesLocalizedFallback,
                        canWrite = outcome.loaded.canWrite &&
                            isRecoverySourceUriSupported(uri.toString()),
                        managedPermissionFlags = if (route == SaveRoute.SAVE_AS) {
                            permission.persistedFlags
                        } else {
                            uiState.managedPermissionFlags
                        },
                        baseVersion = outcome.version,
                        hasUtf8Bom = outcome.loaded.hasUtf8Bom,
                        initialScrollY = if (route == SaveRoute.SAVE_AS) {
                            0
                        } else {
                            uiState.initialScrollY
                        },
                        isDirty = remainsDirty,
                        isSaving = route == SaveRoute.SAVE_AS && remainsDirty,
                        error = null,
                    )
                    if (remainsDirty) {
                        if (route == SaveRoute.SAVE_AS) {
                            val recoveryReady = persistDirtyRecoveryNow(uiState)
                            uiState = uiState.copy(
                                isSaving = false,
                                error = if (recoveryReady) {
                                    null
                                } else {
                                    DocumentUiError.ActionFailed(
                                        DocumentFailure.RECOVERY_FAILED,
                                    )
                                },
                            )
                            if (!recoveryReady) {
                                onResult(
                                    DocumentSaveResult.Failed(
                                        DocumentFailure.RECOVERY_FAILED,
                                    ),
                                )
                                return@launch
                            }
                        } else {
                            scheduleDirtyRecovery(uiState)
                        }
                    } else {
                        val clearThroughGeneration = nextRecoveryGeneration()
                        withContext(Dispatchers.IO) {
                            recoveryRepository?.clearDirtyAtOrBefore(
                                snapshot.recoveryId,
                                clearThroughGeneration,
                            )
                        }
                    }
                    if (
                        route == SaveRoute.IN_PLACE &&
                        recoverableWork?.recoveryId == snapshot.recoveryId
                    ) {
                        recoverableWork = null
                    }
                    if (
                        route == SaveRoute.SAVE_AS &&
                        !remainsDirty &&
                        previousUri != null &&
                        previousUri != uri
                    ) {
                        releasePermissionIfUnused(
                            context = context,
                            uri = previousUri,
                            managedFlags = previousManagedPermissionFlags,
                        )
                    }
                    onResult(DocumentSaveResult.Saved)
                }
                }
            } finally {
                saveCoordinator.finish(snapshot)
                if (
                    isSaveResultCurrent(uiState.sessionId, snapshot) &&
                    uiState.isSaving
                ) {
                    uiState = uiState.copy(isSaving = false)
                }
            }
        }
        saveJob.invokeOnCompletion {
            saveCoordinator.finish(snapshot)
        }
    }

    private suspend fun performInPlaceSave(
        context: Context,
        uri: Uri,
        snapshot: DocumentSaveSnapshot,
        approvedExternalVersion: DocumentVersion?,
    ): SaveOutcome {
        if (!isRecoverySourceUriSupported(uri.toString())) {
            return SaveOutcome.Failure(DocumentFailure.SAVE_AS_REQUIRED)
        }
        if (!DocumentRepository.supportsInPlaceWrite(context, uri)) {
            return SaveOutcome.Failure(
                permissionAwareFailure(
                    context,
                    uri,
                    DocumentFailure.SAVE_AS_REQUIRED,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                ),
            )
        }
        val sourceBefore = try {
            DocumentRepository.read(context, uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: DocumentAccessException) {
            return SaveOutcome.Failure(
                permissionAwareFailure(
                    context,
                    uri,
                    error.failure,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                ),
            )
        } catch (_: Exception) {
            return SaveOutcome.Failure(
                permissionAwareFailure(
                    context,
                    uri,
                    DocumentFailure.READ_FAILED,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                ),
            )
        }

        val expectedVersion = approvedExternalVersion ?: snapshot.baseVersion
            ?: return SaveOutcome.Failure(DocumentFailure.SAVE_AS_REQUIRED)
        if (hasExternalVersionChanged(expectedVersion, sourceBefore.version)) {
            return SaveOutcome.Conflict(sourceBefore.version)
        }

        val previousBytes: ByteArray
        val intendedBytes: ByteArray
        try {
            previousBytes = DocumentPayloadCodec.encodeUtf8(
                sourceBefore.content,
                sourceBefore.hasUtf8Bom,
            )
            intendedBytes = DocumentPayloadCodec.encodeUtf8(
                snapshot.content,
                snapshot.hasUtf8Bom,
            )
        } catch (error: DocumentAccessException) {
            return SaveOutcome.Failure(error.failure)
        }
        val intendedVersion = DocumentPayloadCodec.versionOf(intendedBytes)

        val recoveryReady = try {
            withContext(Dispatchers.IO) {
                val repository = recoveryRepository
                    ?: throw IllegalStateException("Recovery is not initialized.")
                val dirtySaved = repository.saveDirty(
                    snapshot.toRecovery(sourceBefore.version),
                )
                val existingDirty = repository.loadDirty()
                check(
                    dirtySaved || (
                        existingDirty?.recoveryId == snapshot.recoveryId &&
                            existingDirty.recoveryGeneration >= snapshot.recoveryGeneration
                        ),
                )
                repository.savePendingWrite(
                    PendingWriteRecovery(
                        recoveryId = snapshot.recoveryId,
                        sourceUri = uri.toString(),
                        name = sanitizeRecoveryDocumentName(snapshot.name),
                        managedPermissionFlags = snapshot.managedPermissionFlags,
                        baseVersion = sourceBefore.version,
                        intendedVersion = intendedVersion,
                        contentRevision = snapshot.contentRevision,
                        recoveryGeneration = snapshot.recoveryGeneration,
                        savedAt = System.currentTimeMillis(),
                        previousBytes = previousBytes,
                        intendedBytes = intendedBytes,
                    ),
                )
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (!recoveryReady) return SaveOutcome.Failure(DocumentFailure.RECOVERY_FAILED)

        requiresSuccessfulSaveToClearDirty = true
        if (isSaveResultCurrent(uiState.sessionId, snapshot) && !uiState.isDirty) {
            uiState = uiState.copy(isDirty = true)
        }

        val writtenVersion = try {
            DocumentRepository.write(
                context = context,
                uri = uri,
                content = snapshot.content,
                includeUtf8Bom = snapshot.hasUtf8Bom,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: DocumentAccessException) {
            return SaveOutcome.Failure(
                permissionAwareFailure(
                    context,
                    uri,
                    error.failure,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                ),
            )
        } catch (_: Exception) {
            return SaveOutcome.Failure(DocumentFailure.WRITE_FAILED)
        }

        val verified = try {
            DocumentRepository.read(context, uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return SaveOutcome.Failure(
                permissionAwareFailure(
                    context,
                    uri,
                    DocumentFailure.VERIFICATION_FAILED,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                ),
            )
        }
        if (writtenVersion != intendedVersion || verified.version != intendedVersion) {
            return SaveOutcome.Failure(DocumentFailure.VERIFICATION_FAILED)
        }

        withContext(Dispatchers.IO) {
            recoveryRepository?.clearPendingWrite(uri.toString(), intendedVersion)
        }
        return SaveOutcome.Success(intendedVersion, verified)
    }

    private suspend fun performNewTargetSave(
        context: Context,
        uri: Uri,
        snapshot: DocumentSaveSnapshot,
    ): SaveOutcome {
        if (targetsCurrentSource(snapshot.sourceUri, uri.toString())) {
            return SaveOutcome.Failure(DocumentFailure.SAVE_AS_REQUIRED)
        }
        if (snapshot.wasDirty) {
            val recoveryReady = try {
                withContext(Dispatchers.IO) {
                    val repository = recoveryRepository
                        ?: throw IllegalStateException("Recovery is not initialized.")
                    val saved = repository.saveDirty(
                        snapshot.toRecovery(snapshot.baseVersion),
                    )
                    val existing = repository.loadDirty()
                    check(
                        saved || (
                            existing?.recoveryId == snapshot.recoveryId &&
                                existing.recoveryGeneration >= snapshot.recoveryGeneration
                            ),
                    )
                }
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (!recoveryReady) {
                return SaveOutcome.Failure(DocumentFailure.RECOVERY_FAILED)
            }
        }
        val writtenVersion = try {
            DocumentRepository.write(
                context = context,
                uri = uri,
                content = snapshot.content,
                includeUtf8Bom = snapshot.hasUtf8Bom,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: DocumentAccessException) {
            return SaveOutcome.Failure(
                permissionAwareFailure(
                    context,
                    uri,
                    error.failure,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                ),
            )
        } catch (_: Exception) {
            return SaveOutcome.Failure(DocumentFailure.WRITE_FAILED)
        }
        val verified = try {
            DocumentRepository.read(context, uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return SaveOutcome.Failure(
                permissionAwareFailure(
                    context,
                    uri,
                    DocumentFailure.VERIFICATION_FAILED,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                ),
            )
        }
        return if (verified.version == writtenVersion) {
            SaveOutcome.Success(writtenVersion, verified)
        } else {
            SaveOutcome.Failure(DocumentFailure.VERIFICATION_FAILED)
        }
    }

    fun closeDocument(
        context: Context? = applicationContext,
        discardChanges: Boolean = false,
    ): Boolean {
        if (
            uiState.isSaving ||
            isRecoverySaving ||
            isRecoveryTransitioning
        ) {
            return false
        }
        val closingState = uiState
        if (!closingState.hasDocument) return false
        val safeContext = context?.applicationContext
        val pendingRecoveryJob = recoveryDebounceJob
        pendingRecoveryJob?.cancel()
        recoveryDebounceJob = null
        val pendingRecoveryMutation = recoveryMutationJob
        val existingRecoverableWork = recoverableWork
        val fallbackRecovery = if (discardChanges && closingState.recoveryId.isNotBlank()) {
            closingState.toRecovery(
                version = closingState.baseVersion,
                recoveryGeneration = nextRecoveryGeneration(),
            )
        } else {
            null
        }
        if (discardChanges && closingState.recoveryId.isNotBlank()) {
            isRecoveryTransitioning = true
            viewModelScope.launch {
                try {
                    pendingRecoveryJob?.join()
                    pendingRecoveryMutation?.join()
                    val replacement = withContext(Dispatchers.IO) {
                        val repository = recoveryRepository
                            ?: return@withContext fallbackRecovery
                        repository.clearDirtyAtOrBefore(
                            closingState.recoveryId,
                            requireNotNull(fallbackRecovery).recoveryGeneration,
                        )
                        repository.markPendingIntentDiscarded(closingState.recoveryId)
                        repository.loadDirty() ?: repository.recoverPendingAvailable()
                    }
                    recoverableWork = replacement
                    if (safeContext != null && closingState.uri != null) {
                        releasePermissionIfUnused(
                            safeContext,
                            closingState.uri,
                            closingState.managedPermissionFlags,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    recoverableWork = fallbackRecovery
                    uiState = uiState.copy(
                        error = DocumentUiError.ActionFailed(DocumentFailure.RECOVERY_FAILED),
                    )
                } finally {
                    isRecoveryTransitioning = false
                }
            }
        } else {
            isRecoveryTransitioning = true
            viewModelScope.launch {
                try {
                    pendingRecoveryJob?.join()
                    pendingRecoveryMutation?.join()
                    recoverableWork = withContext(Dispatchers.IO) {
                        val repository = recoveryRepository
                            ?: return@withContext existingRecoverableWork
                        repository.loadDirty() ?: repository.recoverPendingAvailable()
                    }
                    if (safeContext != null && closingState.uri != null) {
                        releasePermissionIfUnused(
                            safeContext,
                            closingState.uri,
                            closingState.managedPermissionFlags,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    recoverableWork = existingRecoverableWork
                    uiState = uiState.copy(
                        error = DocumentUiError.ActionFailed(DocumentFailure.RECOVERY_FAILED),
                    )
                } finally {
                    isRecoveryTransitioning = false
                }
            }
        }
        persistedContent = ""
        requiresSuccessfulSaveToClearDirty = false
        clearConflict()
        uiState = DocumentUiState(
            sessionId = nextSessionId(),
            contentVersion = nextVersion(),
        )
        return true
    }

    fun consumeError() {
        uiState = uiState.copy(error = null)
    }

    private fun handleOpenFailure(
        context: Context,
        requestSessionId: Long,
        initialName: ResolvedDocumentName,
        failure: DocumentFailure,
        uri: Uri,
        managedPermissionFlags: Int,
    ) {
        if (uiState.sessionId != requestSessionId) {
            releasePermissionIfUnused(context, uri, managedPermissionFlags)
            return
        }
        val resolvedFailure = permissionAwareFailure(
            context = context,
            uri = uri,
            fallback = failure,
            requiredFlag = Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        persistedContent = ""
        requiresSuccessfulSaveToClearDirty = false
        uiState = DocumentUiState(
            sessionId = requestSessionId,
            error = DocumentUiError.OpenFailed(
                documentName = initialName.storedName,
                usesLocalizedFallback = initialName.usesLocalizedFallback,
                failure = resolvedFailure,
            ),
            contentVersion = nextVersion(),
            contentRevision = nextContentRevision(),
        )
        releasePermissionIfUnused(context, uri, managedPermissionFlags)
    }

    private fun validateEditableContent(
        content: String,
        includeUtf8Bom: Boolean,
    ): DocumentFailure? = try {
        DocumentPayloadCodec.encodedSize(content, includeUtf8Bom)
        null
    } catch (error: DocumentAccessException) {
        error.failure
    } catch (_: Exception) {
        DocumentFailure.WRITE_FAILED
    }

    private fun scheduleDirtyRecovery(state: DocumentUiState) {
        val repository = recoveryRepository ?: return
        if (!state.hasDocument || !state.isDirty || state.recoveryId.isBlank()) return
        val recovery = state.toRecovery(
            version = state.baseVersion,
            recoveryGeneration = nextRecoveryGeneration(),
        )
        recoveryDebounceJob?.cancel()
        recoveryDebounceJob = viewModelScope.launch {
            delay(RECOVERY_DEBOUNCE_MILLIS)
            val mutation = enqueueRecoveryMutation {
                val succeeded = withContext(Dispatchers.IO) {
                    runCatching { repository.saveDirty(recovery) }.getOrDefault(false) ||
                        repository.loadDirty()?.let { current ->
                            current.recoveryId == recovery.recoveryId &&
                                current.recoveryGeneration >= recovery.recoveryGeneration
                        } == true
                }
                if (
                    !succeeded &&
                    shouldReportRecoveryPersistenceFailure(
                        currentRecoveryId = uiState.recoveryId,
                        currentContentRevision = uiState.contentRevision,
                        currentIsDirty = uiState.isDirty,
                        attemptedRecoveryId = recovery.recoveryId,
                        attemptedContentRevision = recovery.contentRevision,
                    )
                ) {
                    uiState = uiState.copy(
                        error = DocumentUiError.ActionFailed(DocumentFailure.RECOVERY_FAILED),
                    )
                }
            }
            mutation.join()
        }
    }

    private fun persistDirtyRecovery(state: DocumentUiState) {
        val repository = recoveryRepository ?: return
        val recovery = state.toRecovery(
            version = state.baseVersion,
            recoveryGeneration = nextRecoveryGeneration(),
        )
        enqueueRecoveryMutation {
            val succeeded = withContext(Dispatchers.IO) {
                runCatching { repository.saveDirty(recovery) }.getOrDefault(false) ||
                    repository.loadDirty()?.let { current ->
                        current.recoveryId == recovery.recoveryId &&
                            current.recoveryGeneration >= recovery.recoveryGeneration
                    } == true
            }
            if (
                !succeeded &&
                shouldReportRecoveryPersistenceFailure(
                    currentRecoveryId = uiState.recoveryId,
                    currentContentRevision = uiState.contentRevision,
                    currentIsDirty = uiState.isDirty,
                    attemptedRecoveryId = recovery.recoveryId,
                    attemptedContentRevision = recovery.contentRevision,
                )
            ) {
                uiState = uiState.copy(
                    error = DocumentUiError.ActionFailed(DocumentFailure.RECOVERY_FAILED),
                )
            }
        }
    }

    private suspend fun persistDirtyRecoveryNow(state: DocumentUiState): Boolean {
        val repository = recoveryRepository ?: return false
        val recovery = state.toRecovery(
            version = state.baseVersion,
            recoveryGeneration = nextRecoveryGeneration(),
        )
        return withContext(Dispatchers.IO) {
            runCatching { repository.saveDirty(recovery) }.getOrDefault(false) ||
                repository.loadDirty()?.let { current ->
                    current.recoveryId == recovery.recoveryId &&
                        current.recoveryGeneration >= recovery.recoveryGeneration
                } == true
        }
    }

    private fun clearDirtyRecovery(recoveryId: String) {
        if (recoveryId.isBlank()) return
        val clearThroughGeneration = nextRecoveryGeneration()
        enqueueRecoveryMutation {
            withContext(Dispatchers.IO) {
                recoveryRepository?.clearDirtyAtOrBefore(
                    recoveryId,
                    clearThroughGeneration,
                )
            }
        }
    }

    private fun enqueueRecoveryMutation(operation: suspend () -> Unit): Job {
        val previous = recoveryMutationJob
        return viewModelScope.launch {
            previous?.join()
            operation()
        }.also { recoveryMutationJob = it }
    }

    private suspend fun reconcileRecoveryAfterRestart(
        context: Context,
        repository: DocumentRecoveryRepository,
    ): DirtyDocumentRecovery? {
        val pending = withContext(Dispatchers.IO) { repository.loadPendingWrite() }
        if (pending != null) {
            val targetContainsIntendedBytes = try {
                DocumentRepository.read(context, Uri.parse(pending.sourceUri)).version ==
                    pending.intendedVersion
            } catch (_: Exception) {
                false
            }
            if (targetContainsIntendedBytes && !pending.intendedDiscarded) {
                withContext(Dispatchers.IO) {
                    repository.clearPendingWrite(pending.sourceUri, pending.intendedVersion)
                    repository.clearDirtyAtOrBefore(
                        pending.recoveryId,
                        pending.recoveryGeneration,
                    )
                }
            }
        }
        return withContext(Dispatchers.IO) {
            repository.loadDirty() ?: repository.recoverPendingAvailable()
        }
    }

    private suspend fun reconcileManagedPermissions(
        context: Context,
        repository: DocumentRecoveryRepository,
    ) {
        val recoveryUris = withContext(Dispatchers.IO) {
            repository.referencedUriStrings().mapNotNullTo(mutableSetOf()) { encoded ->
                runCatching { Uri.parse(encoded) }.getOrNull()
            }
        }
        val referenced = recentDocuments.mapTo(recoveryUris) { it.uri }
        uiState.uri?.let(referenced::add)
        withContext(Dispatchers.IO) {
            DocumentPermissionManager.reconcileManagedGrants(context, referenced)
        }
    }

    private fun releaseRemovedPermissions(context: Context, removed: List<RecentDocument>) {
        removed.forEach { document ->
            releasePermissionIfUnused(
                context = context,
                uri = document.uri,
                managedFlags = document.managedPermissionFlags,
            )
        }
    }

    private fun releasePermissionIfUnused(
        context: Context,
        uri: Uri,
        managedFlags: Int,
    ) {
        if (managedFlags == 0) return
        val expectedOwnershipEpoch = DocumentPermissionManager.ownershipEpoch(uri)
        val recentUris = recentDocuments.mapTo(mutableSetOf()) { it.uri }
        uiState.uri?.let(recentUris::add)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val referencedUris =
                    recoveryRepository?.referencedUriStrings()
                        .orEmpty()
                        .mapNotNullTo(recentUris) { encoded ->
                            runCatching { Uri.parse(encoded) }.getOrNull()
                        }
                DocumentPermissionManager.releaseIfUnreferenced(
                    context = context,
                    uri = uri,
                    managedFlags = managedFlags,
                    referencedUris = referencedUris,
                    expectedOwnershipEpoch = expectedOwnershipEpoch,
                )
            }
        }
    }

    private fun permissionAwareFailure(
        context: Context,
        uri: Uri,
        fallback: DocumentFailure,
        requiredFlag: Int,
    ): DocumentFailure {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return fallback
        val permission = DocumentPermissionManager.current(context, uri)
        return if (!hasRequiredUriPermission(permission, requiredFlag)) {
            DocumentFailure.PERMISSION_LOST
        } else {
            fallback
        }
    }

    private fun DocumentSaveSnapshot.toRecovery(
        version: DocumentVersion?,
    ) = DirtyDocumentRecovery(
        recoveryId = recoveryId,
        sourceUri = sourceUri?.takeIf(::isRecoverySourceUriSupported),
        name = sanitizeRecoveryDocumentName(name),
        managedPermissionFlags = managedPermissionFlags,
        baseVersion = version,
        contentRevision = contentRevision,
        recoveryGeneration = recoveryGeneration,
        hasUtf8Bom = hasUtf8Bom,
        savedAt = System.currentTimeMillis(),
        content = content,
    )

    private fun DocumentUiState.toRecovery(
        version: DocumentVersion?,
        recoveryGeneration: Long,
    ) = DirtyDocumentRecovery(
        recoveryId = recoveryId,
        sourceUri = uri?.toString()?.takeIf(::isRecoverySourceUriSupported),
        name = sanitizeRecoveryDocumentName(name),
        managedPermissionFlags = managedPermissionFlags,
        baseVersion = version,
        contentRevision = contentRevision,
        recoveryGeneration = recoveryGeneration,
        hasUtf8Bom = hasUtf8Bom,
        savedAt = System.currentTimeMillis(),
        content = content,
    )

    private fun requireContext(): Context = requireNotNull(applicationContext) {
        "MarkdownViewModel must be initialized before recovery is opened."
    }

    private fun nextVersion(): Long {
        versionCounter += 1
        return versionCounter
    }

    private fun nextContentRevision(): Long {
        contentRevisionCounter += 1
        return contentRevisionCounter
    }

    private fun nextRecoveryGeneration(): Long {
        recoveryGenerationCounter += 1
        return recoveryGenerationCounter
    }

    private fun nextSessionId(): Long {
        sessionCounter += 1
        return sessionCounter
    }

    companion object {
        private const val RECOVERY_DEBOUNCE_MILLIS = 750L
    }
}
