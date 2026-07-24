package de.unbow.mora.model

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isLoading: Boolean = false,
    val error: DocumentUiError? = null,
    val contentVersion: Long = 0L,
)

class MarkdownViewModel : ViewModel() {

    var uiState by mutableStateOf(DocumentUiState())
        private set

    var recentDocuments by mutableStateOf<List<RecentDocument>>(emptyList())
        private set

    private var persistedContent: String = ""
    private var versionCounter: Long = 0L
    private var sessionCounter: Long = 0L
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        recentDocuments = RecentDocumentsRepository.load(context)
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
        )
    }

    fun updateContent(content: String) {
        uiState = uiState.copy(
            content = content,
            isDirty = uiState.hasDocument &&
                (uiState.uri == null || content != persistedContent),
        )
    }

    fun save(context: Context, onResult: (DocumentSaveResult) -> Unit) {
        val stateToSave = uiState
        val uri = stateToSave.uri
        if (uri == null || !stateToSave.canWrite) {
            onResult(DocumentSaveResult.Failed(DocumentFailure.SAVE_AS_REQUIRED))
            return
        }
        saveTo(
            context = context,
            uri = uri,
            sessionId = stateToSave.sessionId,
            contentToSave = stateToSave.content,
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
        updateDocumentIdentity: Boolean,
        fallbackName: String,
        fallbackIsLocalized: Boolean,
        onResult: (DocumentSaveResult) -> Unit,
    ) {
        viewModelScope.launch {
            val failure = try {
                DocumentRepository.write(context, uri, contentToSave)
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: DocumentAccessException) {
                error.failure
            } catch (_: Exception) {
                DocumentFailure.WRITE_FAILED
            }

            if (uiState.sessionId != sessionId) return@launch

            if (failure != null) {
                onResult(DocumentSaveResult.Failed(failure))
                return@launch
            }

            persistedContent = contentToSave
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
                isDirty = uiState.content != contentToSave,
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

    private fun nextSessionId(): Long {
        sessionCounter += 1
        return sessionCounter
    }
}
