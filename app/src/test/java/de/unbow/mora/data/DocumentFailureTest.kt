package de.unbow.mora.data

import de.unbow.mora.model.DocumentUiError
import de.unbow.mora.model.DocumentUiState
import de.unbow.mora.model.displayDocumentName
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
    fun `localized fallback is derived again after a language change`() {
        val resolved = resolveDocumentName(
            sourceName = null,
            fallbackName = "Document.md",
            fallbackIsLocalized = true,
        )

        assertEquals("", resolved.storedName)
        assertEquals(true, resolved.usesLocalizedFallback)
        assertEquals(
            "Document.md",
            displayDocumentName(
                storedName = resolved.storedName,
                usesLocalizedFallback = resolved.usesLocalizedFallback,
                localizedFallback = "Document.md",
            ),
        )
        assertEquals(
            "文档.md",
            displayDocumentName(
                storedName = resolved.storedName,
                usesLocalizedFallback = resolved.usesLocalizedFallback,
                localizedFallback = "文档.md",
            ),
        )
        assertEquals("", resolved.persistedName)
    }

    @Test
    fun `real provider names remain unchanged across language changes`() {
        val resolved = resolveDocumentName(
            sourceName = "notes.md",
            fallbackName = "Document.md",
            fallbackIsLocalized = true,
        )

        assertEquals(
            "notes.md",
            displayDocumentName(
                storedName = resolved.storedName,
                usesLocalizedFallback = resolved.usesLocalizedFallback,
                localizedFallback = "文档.md",
            ),
        )
        assertEquals("notes.md", resolved.persistedName)
    }

    @Test
    fun `creation time fallback names remain stable and persistable`() {
        val resolved = resolveDocumentName(
            sourceName = null,
            fallbackName = "Untitled.md",
            fallbackIsLocalized = false,
        )

        assertEquals(
            "Untitled.md",
            displayDocumentName(
                storedName = resolved.storedName,
                usesLocalizedFallback = resolved.usesLocalizedFallback,
                localizedFallback = "文档.md",
            ),
        )
        assertEquals(false, resolved.usesLocalizedFallback)
        assertEquals("Untitled.md", resolved.persistedName)
    }
}
