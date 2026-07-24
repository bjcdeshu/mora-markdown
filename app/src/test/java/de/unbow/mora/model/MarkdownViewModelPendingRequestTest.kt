package de.unbow.mora.model

import de.unbow.mora.IncomingDocumentRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownViewModelPendingRequestTest {

    @Test
    fun pendingIncomingRequestSurvivesInViewModelUntilMatchingRequestIsCleared() {
        val viewModel = MarkdownViewModel()
        val original = IncomingDocumentRequest(
            id = 7L,
            sharedText = "# Kept in memory",
            suggestedName = "shared.md",
            grantedFlags = 0,
        )
        val replacement = original.copy(id = 8L)

        viewModel.deferIncomingRequest(original)
        assertEquals(original, viewModel.pendingIncomingRequest)

        viewModel.deferIncomingRequest(replacement)
        viewModel.clearPendingIncomingRequest(original.id)
        assertEquals(replacement, viewModel.pendingIncomingRequest)

        viewModel.clearPendingIncomingRequest(replacement.id)
        assertNull(viewModel.pendingIncomingRequest)

        viewModel.deferIncomingRequest(original)
        viewModel.clearPendingIncomingRequest()
        assertNull(viewModel.pendingIncomingRequest)
    }
}
