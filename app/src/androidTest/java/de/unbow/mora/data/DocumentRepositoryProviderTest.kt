package de.unbow.mora.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import de.unbow.mora.testprovider.TestDocumentsProvider
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
class DocumentRepositoryProviderTest {

    private lateinit var targetContext: Context

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        resetProvider()
    }

    @After
    fun tearDown() {
        resetProvider()
    }

    @Test
    fun validProviderDocumentReadsContentMetadataAndRawByteVersion() {
        val content = "# Mora\n\n安静地阅读 🌙\n"
        val bytes = content.toByteArray(Charsets.UTF_8)
        val uri = configureDocument(
            documentId = "valid",
            bytes = bytes,
            displayName = "notes.md",
        )

        val loaded = read(uri)

        assertEquals("notes.md", loaded.name)
        assertEquals(content, loaded.content)
        assertFalse(loaded.hasUtf8Bom)
        assertFalse(loaded.canWrite)
        assertEquals(bytes.size.toLong(), loaded.version.byteCount)
        assertEquals(sha256(bytes), loaded.version.sha256)
    }

    @Test
    fun missingAndFalseSizeMetadataCannotBypassTheStreamingLimit() {
        val exactLimitUri = configureDocument(
            documentId = "exact_limit",
            repeatedByteCount = PAYLOAD_LIMIT_BYTES,
            reportSize = false,
        )

        val exactLimit = read(exactLimitUri)

        assertEquals(PAYLOAD_LIMIT_BYTES, exactLimit.content.length)
        assertEquals(PAYLOAD_LIMIT_BYTES.toLong(), exactLimit.version.byteCount)

        val underreportedUri = configureDocument(
            documentId = "underreported",
            repeatedByteCount = PAYLOAD_LIMIT_BYTES + 1,
            reportedSize = 1L,
        )

        assertEquals(
            DocumentFailure.FILE_TOO_LARGE,
            readFailure(underreportedUri).failure,
        )
    }

    @Test
    fun malformedProviderBytesAreRejectedInsteadOfBeingReplaced() {
        val uri = configureDocument(
            documentId = "malformed",
            bytes = byteArrayOf(0xC3.toByte(), 0x28),
        )

        assertEquals(DocumentFailure.MALFORMED_UTF8, readFailure(uri).failure)
    }

    @Test
    fun providerReadFailureMapsToTheRepositoryReadFailure() {
        val uri = configureDocument(
            documentId = "read_failure",
            failReads = true,
        )

        assertEquals(DocumentFailure.READ_FAILED, readFailure(uri).failure)
    }

    @Test
    fun slowReadWithMissingSizeMetadataStillStopsAtTheStreamingLimit() {
        val uri = configureDocument(
            documentId = "slow_oversized",
            repeatedByteCount = PAYLOAD_LIMIT_BYTES + 1,
            reportSize = false,
            slowReadDelayMillis = 1L,
            slowReadChunkBytes = 8 * 1024,
        )

        val failure = runBlocking {
            withTimeout(SLOW_READ_TEST_TIMEOUT_MILLIS) {
                try {
                    DocumentRepository.read(targetContext, uri)
                    throw AssertionError("Expected bounded slow read failure")
                } catch (error: DocumentAccessException) {
                    error
                }
            }
        }

        assertEquals(DocumentFailure.FILE_TOO_LARGE, failure.failure)
    }

    @Test
    fun inPlaceWriteNeedsBothProviderSupportAndASessionWriteGrant() {
        val providerReadOnly = configureDocument(
            documentId = "provider_read_only",
            documentFlags = 0,
            grantFlags = READ_WRITE_GRANTS,
        )
        assertTrue(DocumentsContract.isDocumentUri(targetContext, providerReadOnly))
        assertFalse(read(providerReadOnly).canWrite)

        val grantReadOnly = configureDocument(
            documentId = "grant_read_only",
            documentFlags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE,
            grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        assertFalse(read(grantReadOnly).canWrite)

        val writable = configureDocument(
            documentId = "writable",
            documentFlags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE,
            grantFlags = READ_WRITE_GRANTS,
        )
        assertTrue(read(writable).canWrite)
    }

    @Test
    fun writeThenReopenReturnsTheExactPersistedVersion() {
        val uri = configureDocument(
            documentId = "round_trip",
            bytes = "before".toByteArray(),
            grantFlags = READ_WRITE_GRANTS,
        )
        val replacement = "# After\n\n内容 🌲"

        val writtenVersion = runBlocking {
            DocumentRepository.write(
                context = targetContext,
                uri = uri,
                content = replacement,
                includeUtf8Bom = true,
            )
        }
        val reopened = read(uri)

        assertEquals(replacement, reopened.content)
        assertTrue(reopened.hasUtf8Bom)
        assertEquals(writtenVersion, reopened.version)
    }

    @Test
    fun externalProviderReplacementChangesTheObservedVersion() {
        val uri = configureDocument(
            documentId = "external_change",
            bytes = "first".toByteArray(),
        )
        val first = read(uri)

        configureDocument(
            documentId = "external_change",
            bytes = "second version".toByteArray(),
        )
        val second = read(uri)

        assertEquals("second version", second.content)
        assertNotEquals(first.version, second.version)
    }

    @Test
    fun providerWriteFailureMapsToTheRepositoryWriteFailure() {
        val uri = configureDocument(
            documentId = "write_failure",
            bytes = "unchanged".toByteArray(),
            grantFlags = READ_WRITE_GRANTS,
            failWrites = true,
        )

        val failure = try {
            runBlocking {
                DocumentRepository.write(targetContext, uri, "replacement")
            }
            throw AssertionError("Expected provider write failure")
        } catch (error: DocumentAccessException) {
            error
        }

        assertEquals(DocumentFailure.WRITE_FAILED, failure.failure)
        assertEquals("unchanged", read(uri).content)
    }

    @Test
    fun partialWriteFailureReportsFailureAndExposesThePersistedPrefix() {
        val original = "original content"
        val replacement = "replacement-" + "x".repeat(1024 * 1024)
        val persistedPrefixBytes = 37
        val uri = configureDocument(
            documentId = "partial_write_failure",
            bytes = original.toByteArray(),
            grantFlags = READ_WRITE_GRANTS,
            partialWriteFailureAfterBytes = persistedPrefixBytes,
        )

        val failure = writeFailure(uri, replacement)
        val damaged = read(uri)

        assertEquals(DocumentFailure.WRITE_FAILED, failure.failure)
        assertEquals(replacement.take(persistedPrefixBytes), damaged.content)
        assertNotEquals(original, damaged.content)
        assertNotEquals(replacement, damaged.content)
    }

    @Test
    fun truncatingWriteFailureReportsFailureAndExposesAnEmptyTarget() {
        val uri = configureDocument(
            documentId = "truncate_write_failure",
            bytes = "must survive only in recovery".toByteArray(),
            grantFlags = READ_WRITE_GRANTS,
            partialWriteFailureAfterBytes = 0,
        )

        val failure = writeFailure(uri, "replacement-" + "y".repeat(1024 * 1024))
        val damaged = read(uri)

        assertEquals(DocumentFailure.WRITE_FAILED, failure.failure)
        assertEquals("", damaged.content)
        assertEquals(0L, damaged.version.byteCount)
    }

    @Test
    fun temporaryGrantCannotBePersistedAndRevocationRemovesAccess() {
        val uri = configureDocument(
            documentId = "temporary_grant",
            bytes = "temporary".toByteArray(),
            grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )

        val acquired = DocumentPermissionManager.acquire(
            context = targetContext,
            uri = uri,
            grantedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )

        assertFalse(acquired.hasDurableRead)
        assertTrue(
            acquired.sessionFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )

        revokeDocument(uri)

        val revoked = DocumentPermissionManager.current(targetContext, uri)
        assertEquals(0, revoked.persistedFlags)
        assertEquals(0, revoked.sessionFlags)
        assertEquals(DocumentFailure.READ_FAILED, readFailure(uri).failure)
    }

    @Test
    fun persistableGrantIsHeldUntilTheProviderRevokesIt() {
        val uri = configureDocument(
            documentId = "persistable_grant",
            bytes = "persistable".toByteArray(),
            grantFlags = READ_WRITE_PERSISTABLE_GRANTS,
        )

        val acquired = DocumentPermissionManager.acquire(
            context = targetContext,
            uri = uri,
            grantedFlags = READ_WRITE_PERSISTABLE_GRANTS,
        )

        assertTrue(acquired.hasDurableRead)
        assertTrue(
            acquired.persistedFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0,
        )
        assertEquals("persistable", read(uri).content)

        revokeDocument(uri)

        val revoked = DocumentPermissionManager.current(targetContext, uri)
        assertEquals(0, revoked.persistedFlags)
        assertEquals(0, revoked.sessionFlags)
        assertEquals(DocumentFailure.READ_FAILED, readFailure(uri).failure)
    }

    private fun read(uri: Uri): DocumentRepository.LoadedDocument = runBlocking {
        DocumentRepository.read(targetContext, uri)
    }

    private fun readFailure(uri: Uri): DocumentAccessException {
        return try {
            read(uri)
            throw AssertionError("Expected document read failure")
        } catch (error: DocumentAccessException) {
            error
        }
    }

    private fun writeFailure(uri: Uri, content: String): DocumentAccessException {
        return try {
            runBlocking { DocumentRepository.write(targetContext, uri, content) }
            throw AssertionError("Expected document write failure")
        } catch (error: DocumentAccessException) {
            error
        }
    }

    private fun configureDocument(
        documentId: String,
        bytes: ByteArray = byteArrayOf(),
        repeatedByteCount: Int? = null,
        displayName: String = "$documentId.md",
        documentFlags: Int = DocumentsContract.Document.FLAG_SUPPORTS_WRITE,
        grantFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION,
        reportSize: Boolean = true,
        reportedSize: Long? = null,
        failReads: Boolean = false,
        failWrites: Boolean = false,
        slowReadDelayMillis: Long = 0L,
        slowReadChunkBytes: Int = 8 * 1024,
        partialWriteFailureAfterBytes: Int? = null,
    ): Uri {
        val configuration = Bundle().apply {
            if (repeatedByteCount == null) {
                putByteArray(TestDocumentsProvider.KEY_BYTES, bytes)
            } else {
                putInt(TestDocumentsProvider.KEY_REPEATED_BYTE_COUNT, repeatedByteCount)
            }
            putString(TestDocumentsProvider.KEY_DISPLAY_NAME, displayName)
            putInt(TestDocumentsProvider.KEY_DOCUMENT_FLAGS, documentFlags)
            putBoolean(TestDocumentsProvider.KEY_REPORT_SIZE, reportSize)
            reportedSize?.let { putLong(TestDocumentsProvider.KEY_REPORTED_SIZE, it) }
            putBoolean(TestDocumentsProvider.KEY_FAIL_READS, failReads)
            putBoolean(TestDocumentsProvider.KEY_FAIL_WRITES, failWrites)
            putLong(TestDocumentsProvider.KEY_SLOW_READ_DELAY_MILLIS, slowReadDelayMillis)
            putInt(TestDocumentsProvider.KEY_SLOW_READ_CHUNK_BYTES, slowReadChunkBytes)
            partialWriteFailureAfterBytes?.let { byteCount ->
                putInt(
                    TestDocumentsProvider.KEY_PARTIAL_WRITE_FAILURE_AFTER_BYTES,
                    byteCount,
                )
            }
        }
        withManageDocumentsPermission {
            assertNotNull(
                targetContext.contentResolver.call(
                    TestDocumentsProvider.AUTHORITY,
                    TestDocumentsProvider.METHOD_CONFIGURE,
                    documentId,
                    configuration,
                ),
            )
            assertNotNull(
                targetContext.contentResolver.call(
                    TestDocumentsProvider.AUTHORITY,
                    TestDocumentsProvider.METHOD_GRANT,
                    documentId,
                    Bundle().apply {
                        putString(TestDocumentsProvider.KEY_TARGET_PACKAGE, targetContext.packageName)
                        putInt(TestDocumentsProvider.KEY_GRANT_FLAGS, grantFlags)
                    },
                ),
            )
        }
        return TestDocumentsProvider.documentUri(documentId)
    }

    private fun revokeDocument(uri: Uri) {
        withManageDocumentsPermission {
            assertNotNull(
                targetContext.contentResolver.call(
                    TestDocumentsProvider.AUTHORITY,
                    TestDocumentsProvider.METHOD_REVOKE,
                    DocumentsContract.getDocumentId(uri),
                    null,
                ),
            )
        }
    }

    private fun resetProvider() {
        withManageDocumentsPermission {
            assertNotNull(
                targetContext.contentResolver.call(
                    TestDocumentsProvider.AUTHORITY,
                    TestDocumentsProvider.METHOD_RESET,
                    null,
                    null,
                ),
            )
        }
    }

    private inline fun <T> withManageDocumentsPermission(block: () -> T): T {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity(Manifest.permission.MANAGE_DOCUMENTS)
        return try {
            block()
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { value -> "%02x".format(value.toInt() and 0xFF) }

    companion object {
        private const val PAYLOAD_LIMIT_BYTES = 5 * 1024 * 1024
        private const val SLOW_READ_TEST_TIMEOUT_MILLIS = 15_000L
        private const val READ_WRITE_GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        private const val READ_WRITE_PERSISTABLE_GRANTS = READ_WRITE_GRANTS or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    }
}
