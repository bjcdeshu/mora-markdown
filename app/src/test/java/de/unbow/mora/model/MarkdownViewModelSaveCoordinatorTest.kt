package de.unbow.mora.model

import android.content.Intent
import de.unbow.mora.data.DocumentPermission
import de.unbow.mora.data.DocumentVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownViewModelSaveCoordinatorTest {

    @Test
    fun `new target routes reject the exact current source uri`() {
        val source = "content://de.unbow.mora.test/documents/source"

        assertTrue(targetsCurrentSource(source, source))
        assertFalse(targetsCurrentSource(source, "$source-copy"))
        assertFalse(targetsCurrentSource(null, source))
    }

    @Test
    fun `cleared stale recovery failure is silent after a successful clean save`() {
        assertFalse(
            shouldReportRecoveryPersistenceFailure(
                currentRecoveryId = "recovery-1",
                currentContentRevision = 7L,
                currentIsDirty = false,
                attemptedRecoveryId = "recovery-1",
                attemptedContentRevision = 7L,
            ),
        )
        assertTrue(
            shouldReportRecoveryPersistenceFailure(
                currentRecoveryId = "recovery-1",
                currentContentRevision = 8L,
                currentIsDirty = true,
                attemptedRecoveryId = "recovery-1",
                attemptedContentRevision = 8L,
            ),
        )
    }

    @Test
    fun `a second save cannot start while the first write is active`() {
        val coordinator = DocumentSaveCoordinator.isolatedForTesting()
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
        val second = coordinator.tryBegin(
            sessionId = 7L,
            contentRevision = 4L,
            content = "# Next snapshot",
        )
        assertNotNull(second)
        coordinator.finish(requireNotNull(second))
    }

    @Test
    fun `only one destructive save can run across document sessions`() {
        val coordinator = DocumentSaveCoordinator.isolatedForTesting()
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
        assertNull(currentDocument)

        coordinator.finish(requireNotNull(oldDocument))
        val next = coordinator.tryBegin(
            sessionId = 8L,
            contentRevision = 1L,
            content = "# Current document",
        )
        assertNotNull(next)
        coordinator.finish(requireNotNull(next))
    }

    @Test
    fun `save coordination is shared across view model instances`() {
        val firstCoordinator = DocumentSaveCoordinator()
        val secondCoordinator = DocumentSaveCoordinator()
        val first = requireNotNull(
            firstCoordinator.tryBegin(
                sessionId = 1L,
                contentRevision = 1L,
                content = "first instance",
            ),
        )
        try {
            assertNull(
                secondCoordinator.tryBegin(
                    sessionId = 1L,
                    contentRevision = 1L,
                    content = "second instance",
                ),
            )
        } finally {
            firstCoordinator.finish(first)
        }
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

    @Test
    fun `external version comparison uses both digest and byte count`() {
        val baseline = DocumentVersion(sha256 = "a".repeat(64), byteCount = 12)

        assertFalse(hasExternalVersionChanged(baseline, baseline.copy()))
        assertTrue(
            hasExternalVersionChanged(
                baseline,
                baseline.copy(sha256 = "b".repeat(64)),
            ),
        )
        assertTrue(
            hasExternalVersionChanged(
                baseline,
                baseline.copy(byteCount = 13),
            ),
        )
    }

    @Test
    fun `conflict action is stale after the editor advances`() {
        val snapshot = DocumentSaveSnapshot(
            requestId = 1L,
            sessionId = 7L,
            contentRevision = 3L,
            content = "snapshot",
        )

        assertTrue(isSaveSnapshotCurrent(7L, 3L, snapshot))
        assertFalse(isSaveSnapshotCurrent(7L, 4L, snapshot))
        assertFalse(isSaveSnapshotCurrent(8L, 3L, snapshot))
    }

    @Test
    fun `session-only content uri is not advertised as a recent document`() {
        assertFalse(
            shouldRecordRecentDocument(
                uriScheme = "content",
                hasDurableReadPermission = false,
            ),
        )
        assertTrue(
            shouldRecordRecentDocument(
                uriScheme = "content",
                hasDurableReadPermission = true,
            ),
        )
        assertTrue(
            shouldRecordRecentDocument(
                uriScheme = "file",
                hasDurableReadPermission = false,
            ),
        )
    }

    @Test
    fun `new work waits for recovery initialization and resolution`() {
        assertFalse(
            isNewWorkAllowed(
                recoveryInitialized = false,
                recoveryTransitioning = false,
                recoverySaving = false,
                hasOpenDocument = false,
                documentSaving = false,
                hasRecoverableWork = false,
            ),
        )
        assertFalse(
            isNewWorkAllowed(
                recoveryInitialized = true,
                recoveryTransitioning = false,
                recoverySaving = false,
                hasOpenDocument = false,
                documentSaving = false,
                hasRecoverableWork = true,
            ),
        )
        assertTrue(
            isNewWorkAllowed(
                recoveryInitialized = true,
                recoveryTransitioning = false,
                recoverySaving = false,
                hasOpenDocument = false,
                documentSaving = false,
                hasRecoverableWork = false,
            ),
        )
    }

    @Test
    fun `read permission does not mask a lost write grant`() {
        val readOnly = DocumentPermission(
            persistedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )

        assertTrue(
            hasRequiredUriPermission(
                readOnly,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            ),
        )
        assertFalse(
            hasRequiredUriPermission(
                readOnly,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ),
        )
    }

    @Test
    fun `lifecycle flush still protects edits while a save is active`() {
        assertTrue(
            shouldFlushRecovery(
                hasDocument = true,
                isDirty = true,
                documentSaving = true,
                recoveryTransitioning = false,
            ),
        )
    }
}
