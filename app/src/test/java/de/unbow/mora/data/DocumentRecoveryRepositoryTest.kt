package de.unbow.mora.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DocumentRecoveryRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `dirty snapshot round trips BOM version and multilingual content`() {
        val repository = repository()
        val record = dirty(
            content = "# Mora\n\n中文🙂",
            hasUtf8Bom = true,
            revision = 8L,
        )

        repository.saveDirty(record)

        assertEquals(record, repository.loadDirty())
        assertEquals(setOf("content://mora/document/1"), repository.referencedUriStrings())
    }

    @Test
    fun `untrusted recovery metadata is bounded without blocking a dirty snapshot`() {
        val repository = repository()
        val longName = "🙂".repeat(2_000) + '\uD800'
        val unsupportedUri = "content://mora/" + "路".repeat(MAX_RECOVERY_URI_BYTES)
        val record = dirty(content = "safe", revision = 3L).copy(
            sourceUri = unsupportedUri,
            name = longName,
        )

        assertTrue(repository.saveDirty(record))

        val loaded = requireNotNull(repository.loadDirty())
        assertNull(loaded.sourceUri)
        assertEquals(sanitizeRecoveryDocumentName(longName), loaded.name)
        assertTrue(DocumentPayloadCodec.encodeUtf8(loaded.name).size <= MAX_RECOVERY_NAME_BYTES)
    }

    @Test
    fun `recovery URI support rejects malformed or oversized metadata`() {
        assertTrue(isRecoverySourceUriSupported("content://mora/document/1"))
        assertFalse(isRecoverySourceUriSupported("content://mora/\uD800"))
        assertFalse(
            isRecoverySourceUriSupported(
                "content://mora/" + "x".repeat(MAX_RECOVERY_URI_BYTES),
            ),
        )
    }

    @Test
    fun `newer dirty snapshot atomically replaces the older revision`() {
        val repository = repository()
        repository.saveDirty(dirty(content = "old", revision = 1L))
        repository.saveDirty(dirty(content = "new", revision = 2L))

        assertEquals("new", repository.loadDirty()?.content)
        assertFalse(repository.clearDirty("recovery-1", contentRevision = 1L))
        assertTrue(repository.clearDirty("recovery-1", contentRevision = 2L))
        assertNull(repository.loadDirty())
    }

    @Test
    fun `pending write retains both exact source and intended bytes`() {
        val repository = repository()
        val previousBytes = DocumentPayloadCodec.encodeUtf8("before", includeUtf8Bom = true)
        val intendedBytes = DocumentPayloadCodec.encodeUtf8("after", includeUtf8Bom = true)
        val pending = PendingWriteRecovery(
            recoveryId = "recovery-1",
            sourceUri = "content://mora/document/1",
            name = "notes.md",
            managedPermissionFlags = 3,
            baseVersion = DocumentPayloadCodec.versionOf(previousBytes),
            intendedVersion = DocumentPayloadCodec.versionOf(intendedBytes),
            contentRevision = 9L,
            recoveryGeneration = 4L,
            savedAt = 12L,
            previousBytes = previousBytes,
            intendedBytes = intendedBytes,
        )

        repository.savePendingWrite(pending)

        assertEquals(pending, repository.loadPendingWrite())
        val recovered = repository.recoverPendingIntent()
        assertEquals("after", recovered?.content)
        assertEquals(true, recovered?.hasUtf8Bom)
        assertFalse(
            repository.clearPendingWrite(
                pending.sourceUri,
                DocumentPayloadCodec.versionOf(previousBytes),
            ),
        )
        assertTrue(repository.clearPendingWrite(pending.sourceUri, pending.intendedVersion))
    }

    @Test
    fun `retry keeps earliest known good bytes and updates intended bytes`() {
        val repository = repository()
        val originalBytes = DocumentPayloadCodec.encodeUtf8("original")
        val partialBytes = DocumentPayloadCodec.encodeUtf8("part")
        val firstIntent = DocumentPayloadCodec.encodeUtf8("first edit")
        val retryIntent = DocumentPayloadCodec.encodeUtf8("retry edit")
        repository.savePendingWrite(
            pending(
                previousBytes = originalBytes,
                intendedBytes = firstIntent,
                revision = 4L,
                generation = 7L,
            ),
        )

        repository.savePendingWrite(
            pending(
                previousBytes = partialBytes,
                intendedBytes = retryIntent,
                revision = 5L,
                generation = 8L,
            ),
        )

        val stored = requireNotNull(repository.loadPendingWrite())
        assertTrue(stored.previousBytes.contentEquals(originalBytes))
        assertTrue(stored.intendedBytes.contentEquals(retryIntent))
        assertEquals(DocumentPayloadCodec.versionOf(originalBytes), stored.baseVersion)
        assertEquals("retry edit", repository.recoverPendingIntent()?.content)
    }

    @Test
    fun `discarding intent exposes original backup without deleting it`() {
        val repository = repository()
        repository.savePendingWrite(
            pending(
                previousBytes = DocumentPayloadCodec.encodeUtf8("original"),
                intendedBytes = DocumentPayloadCodec.encodeUtf8("discard me"),
            ),
        )

        val backup = repository.markPendingIntentDiscarded("recovery-1")

        assertEquals("original", backup?.content)
        assertEquals(true, backup?.isOriginalBackup)
        assertEquals("original", repository.recoverPendingAvailable()?.content)
        assertEquals(true, repository.loadPendingWrite()?.intendedDiscarded)
        assertTrue(repository.deleteOriginalBackup("recovery-1"))
        assertNull(repository.loadPendingWrite())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a different recovery cannot replace an existing pending backup`() {
        val repository = repository()
        repository.savePendingWrite(pending())

        repository.savePendingWrite(pending(recoveryId = "recovery-2"))
    }

    @Test
    fun `older and equal recovery generations cannot replace a dirty snapshot`() {
        val repository = repository()
        assertTrue(repository.saveDirty(dirty(content = "new", revision = 2L, generation = 5L)))

        assertFalse(repository.saveDirty(dirty(content = "old", revision = 1L, generation = 4L)))
        assertFalse(repository.saveDirty(dirty(content = "equal", revision = 3L, generation = 5L)))
        assertFalse(
            repository.saveDirty(
                dirty(
                    content = "other",
                    revision = 4L,
                    generation = 6L,
                    recoveryId = "recovery-2",
                ),
            ),
        )
        assertEquals("new", repository.loadDirty()?.content)
    }

    @Test
    fun `clear generation tombstone rejects a late stale recovery write`() {
        val repository = repository()
        val releaseLateWrite = CountDownLatch(1)
        val lateWriteFinished = CountDownLatch(1)
        val accepted = AtomicReference<Boolean>()
        val lateWriter = Thread {
            releaseLateWrite.await()
            accepted.set(
                repository.saveDirty(
                    dirty(content = "stale", revision = 1L, generation = 4L),
                ),
            )
            lateWriteFinished.countDown()
        }
        lateWriter.start()

        repository.clearDirtyAtOrBefore("recovery-1", maximumGeneration = 5L)
        releaseLateWrite.countDown()

        assertTrue(lateWriteFinished.await(5, TimeUnit.SECONDS))
        assertFalse(requireNotNull(accepted.get()))
        assertNull(repository.loadDirty())
    }

    @Test
    fun `two repository instances allow only one recovery to claim the directory`() {
        val directory = temporaryFolder.newFolder()
        val firstRepository = DocumentRecoveryRepository(directory, forTesting = true)
        val secondRepository = DocumentRecoveryRepository(directory, forTesting = true)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val finished = CountDownLatch(2)
        val firstAccepted = AtomicReference<Boolean>()
        val secondAccepted = AtomicReference<Boolean>()

        fun writer(
            repository: DocumentRecoveryRepository,
            record: DirtyDocumentRecovery,
            result: AtomicReference<Boolean>,
        ) = Thread {
            ready.countDown()
            start.await()
            result.set(repository.saveDirty(record))
            finished.countDown()
        }

        val firstWriter = writer(
            firstRepository,
            dirty(content = "first", revision = 1L, recoveryId = "recovery-1"),
            firstAccepted,
        )
        val secondWriter = writer(
            secondRepository,
            dirty(content = "second", revision = 1L, recoveryId = "recovery-2"),
            secondAccepted,
        )
        firstWriter.start()
        secondWriter.start()

        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))

        assertEquals(1, listOf(firstAccepted.get(), secondAccepted.get()).count { it == true })
        val stored = requireNotNull(firstRepository.loadDirty())
        assertTrue(stored.recoveryId == "recovery-1" || stored.recoveryId == "recovery-2")
        assertEquals(stored, secondRepository.loadDirty())
    }

    @Test
    fun `clear tombstone from one instance rejects a late write through another instance`() {
        val directory = temporaryFolder.newFolder()
        val clearingRepository = DocumentRecoveryRepository(directory, forTesting = true)
        val writingRepository = DocumentRecoveryRepository(directory, forTesting = true)
        val releaseLateWrite = CountDownLatch(1)
        val lateWriteFinished = CountDownLatch(1)
        val accepted = AtomicReference<Boolean>()
        val lateWriter = Thread {
            releaseLateWrite.await()
            accepted.set(
                writingRepository.saveDirty(
                    dirty(content = "stale", revision = 1L, generation = 4L),
                ),
            )
            lateWriteFinished.countDown()
        }
        lateWriter.start()

        clearingRepository.clearDirtyAtOrBefore("recovery-1", maximumGeneration = 5L)
        releaseLateWrite.countDown()

        assertTrue(lateWriteFinished.await(5, TimeUnit.SECONDS))
        assertFalse(requireNotNull(accepted.get()))
        assertNull(clearingRepository.loadDirty())
        assertNull(writingRepository.loadDirty())
    }

    @Test
    fun `successful unchanged save clears a newer lifecycle flush of the same content`() {
        val repository = repository()
        assertTrue(repository.saveDirty(dirty(content = "saved", revision = 5L, generation = 7L)))
        assertTrue(repository.saveDirty(dirty(content = "saved", revision = 5L, generation = 8L)))

        assertTrue(repository.clearDirtyAtOrBefore("recovery-1", maximumGeneration = 9L))

        assertNull(repository.loadDirty())
        assertFalse(
            repository.saveDirty(dirty(content = "saved", revision = 5L, generation = 8L)),
        )
    }

    @Test
    fun `corrupted recovery data is ignored without exposing partial content`() {
        val directory = temporaryFolder.newFolder("recovery")
        directory.resolve("dirty-v2.bin").writeBytes(byteArrayOf(1, 2, 3, 4))
        val repository = DocumentRecoveryRepository(directory, forTesting = true)

        assertNull(repository.loadDirty())
        assertTrue(repository.referencedUriStrings().isEmpty())
    }

    private fun repository() = DocumentRecoveryRepository(
        directory = temporaryFolder.newFolder(),
        forTesting = true,
    )

    private fun dirty(
        content: String,
        hasUtf8Bom: Boolean = false,
        revision: Long,
        generation: Long = revision,
        recoveryId: String = "recovery-1",
    ) = DirtyDocumentRecovery(
        recoveryId = recoveryId,
        sourceUri = "content://mora/document/1",
        name = "notes.md",
        managedPermissionFlags = 3,
        baseVersion = DocumentPayloadCodec.versionOf(
            DocumentPayloadCodec.encodeUtf8("baseline", includeUtf8Bom = hasUtf8Bom),
        ),
        contentRevision = revision,
        recoveryGeneration = generation,
        hasUtf8Bom = hasUtf8Bom,
        savedAt = 10L,
        content = content,
    )

    private fun pending(
        recoveryId: String = "recovery-1",
        previousBytes: ByteArray = DocumentPayloadCodec.encodeUtf8("before"),
        intendedBytes: ByteArray = DocumentPayloadCodec.encodeUtf8("after"),
        revision: Long = 9L,
        generation: Long = 10L,
    ) = PendingWriteRecovery(
        recoveryId = recoveryId,
        sourceUri = "content://mora/document/1",
        name = "notes.md",
        managedPermissionFlags = 3,
        baseVersion = DocumentPayloadCodec.versionOf(previousBytes),
        intendedVersion = DocumentPayloadCodec.versionOf(intendedBytes),
        contentRevision = revision,
        recoveryGeneration = generation,
        savedAt = 12L,
        previousBytes = previousBytes,
        intendedBytes = intendedBytes,
    )
}
