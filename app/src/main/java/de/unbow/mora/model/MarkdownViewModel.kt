package de.unbow.mora.model

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.unbow.mora.IncomingDocumentRequest
import de.unbow.mora.data.DocumentAccessException
import de.unbow.mora.data.DocumentFailure
import de.unbow.mora.data.DocumentRepository
import de.unbow.mora.data.RecentDocument
import de.unbow.mora.data.RecentDocumentsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

sealed interface DocumentUiError {
    data class OpenFailed(
        val documentName: String,
        val usesLocalizedFallback: Boolean = false,
    ) : DocumentUiError
}

sealed interface DocumentSaveResult {
    data object Saved : DocumentSaveResult
    data class Failed(val failure: DocumentFailure) : DocumentSaveResult
}

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
    val originalName = sourceName?.takeIf(String::isNotBlank)
    val fallback = fallbackName.takeIf(String::isNotBlank).orEmpty()
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
    val hasDocument: Boolean = false,
    val uri: Uri? = null,
    val name: String = "",
    val nameUsesLocalizedFallback: Boolean = false,
    val content: String = "",
    val canWrite: Boolean = false,
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
)

internal class DocumentSaveCoordinator {
    private var requestCounter = 0L
    private var activeSnapshot: DocumentSaveSnapshot? = null

    @Synchronized
    fun tryBegin(
        sessionId: Long,
        contentRevision: Long,
        content: String,
    ): DocumentSaveSnapshot? {
        if (activeSnapshot != null) return null
        requestCounter += 1
        return DocumentSaveSnapshot(
            requestId = requestCounter,
            sessionId = sessionId,
            contentRevision = contentRevision,
            content = content,
        ).also { activeSnapshot = it }
    }

    @Synchronized
    fun finish(snapshot: DocumentSaveSnapshot) {
        if (activeSnapshot?.requestId == snapshot.requestId) {
            activeSnapshot = null
        }
    }
}

internal fun shouldUseSaveAs(
    hasUri: Boolean,
    canWrite: Boolean,
): Boolean = !hasUri || !canWrite

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

class MarkdownViewModel : ViewModel() {

    var uiState by mutableStateOf(DocumentUiState())
        private set

    var recentDocuments by mutableStateOf<List<RecentDocument>>(emptyList())
        private set

    var pendingIncomingRequest by mutableStateOf<IncomingDocumentRequest?>(null)
        private set

    private var persistedContent: String = ""
    private var versionCounter: Long = 0L
    private var contentRevisionCounter: Long = 0L
    private var sessionCounter: Long = 0L
    private val saveCoordinator = DocumentSaveCoordinator()
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        recentDocuments = RecentDocumentsRepository.load(context)
    }

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
    ) {
        val requestSessionId = nextSessionId()
        val requestVersion = nextVersion()
        val knownPosition = recentDocuments.firstOrNull { it.uri == uri }?.scrollY
            ?: RecentDocumentsRepository.load(context).firstOrNull { it.uri == uri }?.scrollY
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
        uiState = DocumentUiState(
            sessionId = requestSessionId,
            hasDocument = true,
            uri = uri,
            name = initialName.storedName,
            nameUsesLocalizedFallback = initialName.usesLocalizedFallback,
            isLoading = true,
            contentVersion = requestVersion,
            contentRevision = nextContentRevision(),
            initialScrollY = knownPosition,
        )

        viewModelScope.launch {
            val loaded = try {
                DocumentRepository.read(context, uri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (uiState.sessionId != requestSessionId) return@launch
                recentDocuments = RecentDocumentsRepository.remove(context, uri)
                persistedContent = ""
                uiState = DocumentUiState(
                    sessionId = requestSessionId,
                    error = DocumentUiError.OpenFailed(
                        documentName = initialName.storedName,
                        usesLocalizedFallback = initialName.usesLocalizedFallback,
                    ),
                    contentVersion = nextVersion(),
                    contentRevision = nextContentRevision(),
                )
                return@launch
            }

            if (uiState.sessionId != requestSessionId) return@launch
            persistedContent = loaded.content
            val resolvedName = resolveDocumentName(
                sourceName = loaded.name,
                fallbackName = fallbackName,
                fallbackIsLocalized = true,
            )
            recentDocuments = RecentDocumentsRepository.recordOpened(
                context = context,
                uri = uri,
                name = resolvedName.persistedName,
            )
            uiState = uiState.copy(
                name = resolvedName.storedName,
                nameUsesLocalizedFallback = resolvedName.usesLocalizedFallback,
                content = loaded.content,
                canWrite = loaded.canWrite,
                isDirty = false,
                isLoading = false,
                initialScrollY = knownPosition,
                contentVersion = nextVersion(),
                contentRevision = nextContentRevision(),
            )
        }
    }

    fun newDraft(name: String, initialContent: String) {
        persistedContent = ""
        uiState = DocumentUiState(
            sessionId = nextSessionId(),
            hasDocument = true,
            name = name,
            content = initialContent,
            canWrite = false,
            isDirty = true,
            contentVersion = nextVersion(),
            contentRevision = nextContentRevision(),
        )
    }

    fun openSharedText(content: String, name: String) {
        persistedContent = ""
        uiState = DocumentUiState(
            sessionId = nextSessionId(),
            hasDocument = true,
            name = name,
            content = content,
            canWrite = false,
            isDirty = true,
            contentVersion = nextVersion(),
            contentRevision = nextContentRevision(),
        )
    }

    fun updateContent(content: String) {
        if (content == uiState.content) return
        uiState = uiState.copy(
            content = content,
            isDirty = uiState.hasDocument && (
                uiState.isSaving ||
                    uiState.uri == null ||
                    content != persistedContent
                ),
            contentRevision = nextContentRevision(),
        )
    }

    fun save(context: Context, onResult: (DocumentSaveResult) -> Unit) {
        val stateToSave = uiState
        val uri = stateToSave.uri
        if (shouldUseSaveAs(hasUri = uri != null, canWrite = stateToSave.canWrite)) {
            onResult(DocumentSaveResult.Failed(DocumentFailure.SAVE_AS_REQUIRED))
            return
        }
        val writableUri = uri ?: return
        saveTo(
            context = context,
            uri = writableUri,
            sessionId = stateToSave.sessionId,
            contentToSave = stateToSave.content,
            contentRevision = stateToSave.contentRevision,
            updateDocumentIdentity = false,
            fallbackName = stateToSave.name,
            fallbackIsLocalized = stateToSave.nameUsesLocalizedFallback,
            onResult = onResult,
        )
    }

    fun saveAs(context: Context, uri: Uri, onResult: (DocumentSaveResult) -> Unit) {
        val stateToSave = uiState
        saveTo(
            context = context,
            uri = uri,
            sessionId = stateToSave.sessionId,
            contentToSave = stateToSave.content,
            contentRevision = stateToSave.contentRevision,
            updateDocumentIdentity = true,
            fallbackName = stateToSave.name,
            fallbackIsLocalized = stateToSave.nameUsesLocalizedFallback,
            onResult = onResult,
        )
    }

    fun updateReadingPosition(context: Context, uri: Uri, scrollY: Int) {
        recentDocuments = RecentDocumentsRepository.updatePosition(context, uri, scrollY)
    }

    fun removeRecent(context: Context, document: RecentDocument) {
        recentDocuments = RecentDocumentsRepository.remove(context, document.uri)
    }

    private fun saveTo(
        context: Context,
        uri: Uri,
        sessionId: Long,
        contentToSave: String,
        contentRevision: Long,
        updateDocumentIdentity: Boolean,
        fallbackName: String,
        fallbackIsLocalized: Boolean,
        onResult: (DocumentSaveResult) -> Unit,
    ) {
        val snapshot = saveCoordinator.tryBegin(
            sessionId = sessionId,
            contentRevision = contentRevision,
            content = contentToSave,
        ) ?: return
        if (uiState.sessionId != sessionId) {
            saveCoordinator.finish(snapshot)
            return
        }
        uiState = uiState.copy(isSaving = true)

        viewModelScope.launch {
            val failure = try {
                DocumentRepository.write(context, uri, snapshot.content)
                null
            } catch (cancelled: CancellationException) {
                saveCoordinator.finish(snapshot)
                if (uiState.sessionId == sessionId) {
                    uiState = uiState.copy(isSaving = false)
                }
                throw cancelled
            } catch (error: DocumentAccessException) {
                error.failure
            } catch (_: Exception) {
                DocumentFailure.WRITE_FAILED
            }

            saveCoordinator.finish(snapshot)
            if (uiState.sessionId != sessionId) return@launch

            if (failure != null) {
                uiState = stateAfterSaveFailure(uiState)
                onResult(DocumentSaveResult.Failed(failure))
                return@launch
            }

            persistedContent = snapshot.content
            val resolvedName = if (updateDocumentIdentity) {
                resolveDocumentName(
                    sourceName = DocumentRepository.displayName(context, uri),
                    fallbackName = fallbackName,
                    fallbackIsLocalized = fallbackIsLocalized,
                )
            } else {
                null
            }
            if (updateDocumentIdentity) {
                recentDocuments = RecentDocumentsRepository.recordOpened(
                    context = context,
                    uri = uri,
                    name = resolvedName?.persistedName.orEmpty(),
                )
            }
            uiState = uiState.copy(
                uri = uri,
                name = resolvedName?.storedName ?: uiState.name,
                nameUsesLocalizedFallback = resolvedName?.usesLocalizedFallback
                    ?: uiState.nameUsesLocalizedFallback,
                canWrite = true,
                initialScrollY = if (updateDocumentIdentity) 0 else uiState.initialScrollY,
                isDirty = shouldRemainDirtyAfterSave(
                    currentContent = uiState.content,
                    currentContentRevision = uiState.contentRevision,
                    savedSnapshot = snapshot,
                ),
                isSaving = false,
                error = null,
            )

            onResult(DocumentSaveResult.Saved)
        }
    }

    fun closeDocument() {
        persistedContent = ""
        uiState = DocumentUiState(
            sessionId = nextSessionId(),
            contentVersion = nextVersion(),
        )
    }

    fun consumeError() {
        uiState = uiState.copy(error = null)
    }

    private fun nextVersion(): Long {
        versionCounter += 1
        return versionCounter
    }

    private fun nextContentRevision(): Long {
        contentRevisionCounter += 1
        return contentRevisionCounter
    }

    private fun nextSessionId(): Long {
        sessionCounter += 1
        return sessionCounter
    }
}
