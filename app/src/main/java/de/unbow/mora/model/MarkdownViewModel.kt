package de.unbow.mora.model

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.unbow.mora.data.DocumentRepository
import de.unbow.mora.data.RecentDocument
import de.unbow.mora.data.RecentDocumentsRepository
import kotlinx.coroutines.launch

data class DocumentUiState(
    val sessionId: Long = 0L,
    val hasDocument: Boolean = false,
    val uri: Uri? = null,
    val name: String = "",
    val content: String = "",
    val canWrite: Boolean = false,
    val initialScrollY: Int = 0,
    val isDirty: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
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
    ) {
        val requestSessionId = nextSessionId()
        val requestVersion = nextVersion()
        val knownPosition = recentDocuments.firstOrNull { it.uri == uri }?.scrollY
            ?: RecentDocumentsRepository.load(context).firstOrNull { it.uri == uri }?.scrollY
            ?: 0
        val displayName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
            ?: "正在打开…"

        persistedContent = ""
        uiState = DocumentUiState(
            sessionId = requestSessionId,
            hasDocument = true,
            uri = uri,
            name = displayName,
            isLoading = true,
            contentVersion = requestVersion,
            initialScrollY = knownPosition,
        )

        viewModelScope.launch {
            runCatching { DocumentRepository.read(context, uri) }
                .onSuccess { loaded ->
                    if (uiState.sessionId != requestSessionId) return@onSuccess
                    persistedContent = loaded.content
                    recentDocuments = RecentDocumentsRepository.recordOpened(
                        context = context,
                        uri = uri,
                        name = loaded.name,
                    )
                    uiState = uiState.copy(
                        name = loaded.name,
                        content = loaded.content,
                        canWrite = loaded.canWrite,
                        isDirty = false,
                        isLoading = false,
                        initialScrollY = knownPosition,
                        contentVersion = nextVersion(),
                    )
                }
                .onFailure { error ->
                    if (uiState.sessionId != requestSessionId) return@onFailure
                    recentDocuments = RecentDocumentsRepository.remove(context, uri)
                    persistedContent = ""
                    uiState = DocumentUiState(
                        sessionId = requestSessionId,
                        errorMessage = "无法打开「$displayName」：${error.message ?: "读取失败"}",
                        contentVersion = nextVersion(),
                    )
                }
        }
    }

    fun newDraft() {
        val initial = "# 未命名文档\n\n从这里开始写作。"
        persistedContent = ""
        uiState = DocumentUiState(
            sessionId = nextSessionId(),
            hasDocument = true,
            name = "未命名.md",
            content = initial,
            canWrite = false,
            isDirty = true,
            contentVersion = nextVersion(),
        )
    }

    fun openSharedText(content: String, name: String = "共享内容.md") {
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

    fun save(context: Context, onResult: (Result<Unit>) -> Unit) {
        val stateToSave = uiState
        val uri = stateToSave.uri
        if (uri == null || !stateToSave.canWrite) {
            onResult(Result.failure(IllegalStateException("这个文件需要另存为")))
            return
        }
        saveTo(
            context = context,
            uri = uri,
            sessionId = stateToSave.sessionId,
            contentToSave = stateToSave.content,
            updateDocumentIdentity = false,
            fallbackName = stateToSave.name,
            onResult = onResult,
        )
    }

    fun saveAs(context: Context, uri: Uri, onResult: (Result<Unit>) -> Unit) {
        val stateToSave = uiState
        saveTo(
            context = context,
            uri = uri,
            sessionId = stateToSave.sessionId,
            contentToSave = stateToSave.content,
            updateDocumentIdentity = true,
            fallbackName = stateToSave.name,
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
        onResult: (Result<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = runCatching {
                DocumentRepository.write(context, uri, contentToSave)
            }

            if (uiState.sessionId != sessionId) return@launch

            result.onSuccess {
                persistedContent = contentToSave
                val name = if (updateDocumentIdentity) {
                    DocumentRepository.displayName(context, uri) ?: fallbackName
                } else {
                    uiState.name
                }
                if (updateDocumentIdentity) {
                    recentDocuments = RecentDocumentsRepository.recordOpened(
                        context = context,
                        uri = uri,
                        name = name,
                    )
                }
                uiState = uiState.copy(
                    uri = uri,
                    name = name,
                    canWrite = true,
                    initialScrollY = if (updateDocumentIdentity) 0 else uiState.initialScrollY,
                    isDirty = uiState.content != contentToSave,
                    errorMessage = null,
                )
            }

            onResult(result)
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
        uiState = uiState.copy(errorMessage = null)
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
