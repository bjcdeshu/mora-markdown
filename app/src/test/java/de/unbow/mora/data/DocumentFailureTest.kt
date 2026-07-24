package de.unbow.mora.data

import de.unbow.mora.model.DocumentUiError
import de.unbow.mora.model.DocumentUiState
import de.unbow.mora.model.resolveDocumentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentFailureTest {

    @Test
    fun `typed repository failure never exposes provider message`() {
        val exception = DocumentAccessException(
            failure = DocumentFailure.READ_FAILED,
            cause = IllegalStateException("private provider detail"),
        )

        assertEquals(DocumentFailure.READ_FAILED, exception.failure)
        assertNull(exception.message)
        assertEquals("private provider detail", exception.cause?.message)
    }

    @Test
    fun `document UI state keeps a typed error and its formatting argument`() {
        val error = DocumentUiError.OpenFailed(documentName = "notes.md")
        val state = DocumentUiState(error = error)

        assertEquals(error, state.error)
        assertEquals("notes.md", (state.error as DocumentUiError.OpenFailed).documentName)
    }

    @Test
    fun `localized fallback is display only and never persisted as source metadata`() {
        val english = resolveDocumentName(sourceName = null, fallbackName = "Document.md")
        val chinese = resolveDocumentName(sourceName = null, fallbackName = "文档.md")

        assertEquals("Document.md", english.displayName)
        assertEquals("文档.md", chinese.displayName)
        assertEquals("", english.persistedName)
        assertEquals("", chinese.persistedName)
    }
}
