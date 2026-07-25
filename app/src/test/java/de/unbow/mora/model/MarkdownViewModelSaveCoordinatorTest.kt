package de.unbow.mora.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownViewModelSaveCoordinatorTest {

    @Test
    fun `a second save cannot start while the first write is active`() {
        val coordinator = DocumentSaveCoordinator()
        val first = coordinator.tryBegin(
            sessionId = 7L,
            contentRevision = 3L,
            content = "# First snapshot",
        )

        assertNotNull(first)
        assertNull(
            coordinator.tryBegin(
                sessionId = 7L,
                contentRevision = 3L,
                content = "# First snapshot",
            ),
        )

        coordinator.finish(requireNotNull(first))
        assertNotNull(
            coordinator.tryBegin(
                sessionId = 7L,
                contentRevision = 4L,
                content = "# Next snapshot",
            ),
        )
    }

    @Test
    fun `a slow save from an old document does not block the current session`() {
        val coordinator = DocumentSaveCoordinator()
        val oldDocument = coordinator.tryBegin(
            sessionId = 7L,
            contentRevision = 3L,
            content = "# Old document",
        )

        val currentDocument = coordinator.tryBegin(
            sessionId = 8L,
            contentRevision = 1L,
            content = "# Current document",
        )

        assertNotNull(oldDocument)
        assertNotNull(currentDocument)
    }

    @Test
    fun `an old save result cannot update a newer document session`() {
        val oldDocument = DocumentSaveSnapshot(
            requestId = 1L,
            sessionId = 7L,
            contentRevision = 3L,
            content = "# Old document",
        )

        assertTrue(isSaveResultCurrent(currentSessionId = 7L, savedSnapshot = oldDocument))
        assertFalse(isSaveResultCurrent(currentSessionId = 8L, savedSnapshot = oldDocument))
    }

    @Test
    fun `editing during a save remains dirty after the saved snapshot completes`() {
        val snapshot = DocumentSaveSnapshot(
            requestId = 1L,
            sessionId = 7L,
            contentRevision = 3L,
            content = "# Saved snapshot",
        )

        assertTrue(
            shouldRemainDirtyAfterSave(
                currentContent = "# Edited while saving",
                currentContentRevision = 4L,
                savedSnapshot = snapshot,
            ),
        )
    }

    @Test
    fun `returning to the saved snapshot is clean even when the revision changed`() {
        val snapshot = DocumentSaveSnapshot(
            requestId = 1L,
            sessionId = 7L,
            contentRevision = 3L,
            content = "# Saved snapshot",
        )

        assertFalse(
            shouldRemainDirtyAfterSave(
                currentContent = "# Saved snapshot",
                currentContentRevision = 5L,
                savedSnapshot = snapshot,
            ),
        )
    }

    @Test
    fun `a failed save keeps the edited content and dirty state`() {
        val savingState = DocumentUiState(
            sessionId = 7L,
            hasDocument = true,
            content = "# Keep this edit",
            isDirty = true,
            isSaving = true,
            contentRevision = 3L,
        )

        val failedState = stateAfterSaveFailure(savingState)

        assertEquals(savingState.content, failedState.content)
        assertEquals(savingState.contentRevision, failedState.contentRevision)
        assertTrue(failedState.isDirty)
        assertFalse(failedState.isSaving)
    }

    @Test
    fun `a writable existing document keeps the original save route`() {
        assertFalse(shouldUseSaveAs(hasUri = true, canWrite = true))
        assertTrue(shouldUseSaveAs(hasUri = false, canWrite = true))
        assertTrue(shouldUseSaveAs(hasUri = true, canWrite = false))
    }
}
