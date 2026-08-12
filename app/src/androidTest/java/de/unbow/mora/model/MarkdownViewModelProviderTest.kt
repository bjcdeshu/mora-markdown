package de.unbow.mora.model

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import de.unbow.mora.data.DocumentFailure
import de.unbow.mora.data.DocumentRecoveryRepository
import de.unbow.mora.data.DocumentRepository
import de.unbow.mora.testprovider.TestDocumentsProvider
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
class MarkdownViewModelProviderTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val viewModelStores = mutableListOf<ViewModelStore>()
    private lateinit var targetContext: Context

    @Before
    fun setUp() {
        targetContext = instrumentation.targetContext
        resetProvider()
        clearAppTestState()
    }

    @After
    fun tearDown() {
        onMain {
            viewModelStores.forEach(ViewModelStore::clear)
            viewModelStores.clear()
        }
        instrumentation.waitForIdleSync()
        resetProvider()
        clearAppTestState()
    }

    @Test
    fun readOnlySourceSaveAsWritesNewUriWithoutChangingOriginalBytes() {
        val originalBytes = "# Original\n\nsource bytes\n".toByteArray()
        val source = configureDocument(
            documentId = "viewmodel_read_only_source",
            bytes = originalBytes,
            documentFlags = 0,
            grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val target = configureDocument(
            documentId = "viewmodel_save_as_target",
            bytes = byteArrayOf(),
            grantFlags = READ_WRITE_GRANTS,
        )
        val viewModel = initializedViewModel()

        assertTrue(onMain { viewModel.openDocument(targetContext, source, "fallback.md") })
        waitForState("read-only source to open") {
            viewModel.uiState.hasDocument &&
                !viewModel.uiState.isLoading &&
                viewModel.uiState.content == originalBytes.toString(Charsets.UTF_8)
        }
        assertFalse(onMain { viewModel.uiState.canWrite })

        val edited = "# Edited\n\nkept away from the source\n"
        assertNull(onMain { viewModel.updateContent(edited) })
        val result = awaitSaveResult { callback ->
            viewModel.saveAs(targetContext, target, callback)
        }

        assertEquals(DocumentSaveResult.Saved, result)
        assertArrayEquals(originalBytes, readRawBytes(source))
        assertEquals(edited, readDocument(target).content)
        assertEquals(target, onMain { viewModel.uiState.uri })
        assertFalse(onMain { viewModel.uiState.isDirty })
    }

    @Test
    fun saveCopyCannotUseTheCurrentSourceUriAsItsTarget() {
        val originalBytes = "# Original\n\nsource bytes\n".toByteArray()
        val source = configureDocument(
            documentId = "viewmodel_copy_same_source",
            bytes = originalBytes,
            grantFlags = READ_WRITE_GRANTS,
        )
        val viewModel = initializedViewModel()

        assertTrue(onMain { viewModel.openDocument(targetContext, source, "fallback.md") })
        waitForState("copy source to open") {
            viewModel.uiState.hasDocument && !viewModel.uiState.isLoading
        }
        val edited = "# Edited\n\nmust remain unsaved\n"
        assertNull(onMain { viewModel.updateContent(edited) })

        val result = awaitSaveResult { callback ->
            viewModel.saveCopy(targetContext, source, callback)
        }

        assertEquals(
            DocumentSaveResult.Failed(DocumentFailure.SAVE_AS_REQUIRED),
            result,
        )
        assertArrayEquals(originalBytes, readRawBytes(source))
        assertEquals(edited, onMain { viewModel.uiState.content })
        assertTrue(onMain { viewModel.uiState.isDirty })
    }

    @Test
    fun revokedWritableSourceReturnsPermissionLostAndKeepsDirtyContent() {
        val source = configureDocument(
            documentId = "viewmodel_revoked_source",
            bytes = "before revoke".toByteArray(),
            grantFlags = READ_WRITE_GRANTS,
        )
        val viewModel = initializedViewModel()

        assertTrue(onMain { viewModel.openDocument(targetContext, source, "fallback.md") })
        waitForState("writable source to open") {
            viewModel.uiState.hasDocument &&
                !viewModel.uiState.isLoading &&
                viewModel.uiState.canWrite
        }
        val edited = "unsaved after permission loss"
        assertNull(onMain { viewModel.updateContent(edited) })
        revokeDocument(source)

        val result = awaitSaveResult { callback ->
            viewModel.save(targetContext, callback)
        }

        assertEquals(
            DocumentSaveResult.Failed(DocumentFailure.PERMISSION_LOST),
            result,
        )
        assertTrue(onMain { viewModel.uiState.isDirty })
        assertEquals(edited, onMain { viewModel.uiState.content })
        assertFalse(onMain { viewModel.uiState.isSaving })
    }

    @Test
    fun sourceThatStopsAdvertisingWriteSupportRequiresSaveAsBeforeWriting() {
        val originalBytes = "before provider became read-only".toByteArray()
        val source = configureDocument(
            documentId = "viewmodel_dynamic_read_only_source",
            bytes = originalBytes,
            grantFlags = READ_WRITE_GRANTS,
        )
        val viewModel = initializedViewModel()

        assertTrue(onMain { viewModel.openDocument(targetContext, source, "fallback.md") })
        waitForState("dynamic source to open writable") {
            viewModel.uiState.hasDocument &&
                !viewModel.uiState.isLoading &&
                viewModel.uiState.canWrite
        }
        val edited = "must not overwrite a newly read-only provider document"
        assertNull(onMain { viewModel.updateContent(edited) })
        configureDocument(
            documentId = "viewmodel_dynamic_read_only_source",
            bytes = originalBytes,
            documentFlags = 0,
            grantFlags = null,
        )

        val result = awaitSaveResult { callback ->
            viewModel.save(targetContext, callback)
        }

        assertEquals(
            DocumentSaveResult.Failed(DocumentFailure.SAVE_AS_REQUIRED),
            result,
        )
        assertArrayEquals(originalBytes, readRawBytes(source))
        assertEquals(edited, onMain { viewModel.uiState.content })
        assertTrue(onMain { viewModel.uiState.isDirty })
        assertFalse(onMain { viewModel.uiState.isSaving })
    }

    @Test
    fun conflictRechecksConfirmedOverwriteThenCopiesAndReloadsLatestExternalVersion() {
        val source = configureDocument(
            documentId = "viewmodel_conflict_source",
            bytes = "base version".toByteArray(),
            grantFlags = READ_WRITE_GRANTS,
        )
        val copyTarget = configureDocument(
            documentId = "viewmodel_conflict_copy",
            bytes = byteArrayOf(),
            grantFlags = READ_WRITE_GRANTS,
        )
        val viewModel = initializedViewModel()

        assertTrue(onMain { viewModel.openDocument(targetContext, source, "fallback.md") })
        waitForState("conflict source to open") {
            viewModel.uiState.hasDocument && !viewModel.uiState.isLoading
        }
        val localEdit = "local unsaved edit"
        assertNull(onMain { viewModel.updateContent(localEdit) })

        val externalEdit = "external provider edit"
        configureDocument(
            documentId = "viewmodel_conflict_source",
            bytes = externalEdit.toByteArray(),
            grantFlags = null,
        )
        val conflictResult = awaitSaveResult { callback ->
            viewModel.save(targetContext, callback)
        }

        assertEquals(DocumentSaveResult.Conflict, conflictResult)
        assertNotNull(onMain { viewModel.pendingConflict })
        assertEquals(localEdit, onMain { viewModel.uiState.content })
        assertTrue(onMain { viewModel.uiState.isDirty })

        val externalEditAfterConfirmation = "external edit after overwrite confirmation"
        configureDocument(
            documentId = "viewmodel_conflict_source",
            bytes = externalEditAfterConfirmation.toByteArray(),
            grantFlags = null,
        )
        val secondConflictResult = awaitSaveResult { callback ->
            viewModel.overwriteConflict(targetContext, callback)
        }

        assertEquals(DocumentSaveResult.Conflict, secondConflictResult)
        assertNotNull(onMain { viewModel.pendingConflict })
        assertEquals(externalEditAfterConfirmation, readDocument(source).content)

        val copyResult = awaitSaveResult { callback ->
            viewModel.saveCopy(targetContext, copyTarget, callback)
        }

        assertEquals(DocumentSaveResult.Copied, copyResult)
        assertEquals(externalEditAfterConfirmation, readDocument(source).content)
        assertEquals(localEdit, readDocument(copyTarget).content)

        onMain { viewModel.reloadConflict(targetContext) }
        waitForState("external version to reload") {
            !viewModel.uiState.isLoading &&
                !viewModel.isRecoveryTransitioning &&
                viewModel.pendingConflict == null &&
                viewModel.uiState.content == externalEditAfterConfirmation &&
                !viewModel.uiState.isDirty
        }

        assertEquals(externalEditAfterConfirmation, readDocument(source).content)
        assertEquals(source, onMain { viewModel.uiState.uri })
    }

    @Test
    fun newViewModelInitializationFindsExplicitlyFlushedDirtyRecovery() {
        val originalViewModel = initializedViewModel()
        val dirtyContent = "# Recovered\n\nprocess-like restart payload\n"

        assertNull(onMain { originalViewModel.newDraft("recovery.md", dirtyContent) })
        onMain { originalViewModel.flushRecovery() }

        waitForCondition("dirty recovery to reach private storage") {
            DocumentRecoveryRepository(targetContext).loadDirty()?.content == dirtyContent
        }

        val restartedViewModel = newViewModel()
        onMain { restartedViewModel.initialize(targetContext) }
        waitForState("new ViewModel to discover recovery") {
            restartedViewModel.isRecoveryInitialized &&
                restartedViewModel.recoverableWork?.content == dirtyContent
        }

        val recovered = onMain { restartedViewModel.recoverableWork }
        assertNotNull(recovered)
        assertEquals("recovery.md", recovered?.name)
        assertEquals(dirtyContent, recovered?.content)
        assertFalse(recovered?.isOriginalBackup ?: true)
    }

    private fun initializedViewModel(): MarkdownViewModel {
        val viewModel = newViewModel()
        onMain { viewModel.initialize(targetContext) }
        waitForState("ViewModel recovery initialization") {
            viewModel.isRecoveryInitialized
        }
        assertNull(onMain { viewModel.recoverableWork })
        return viewModel
    }

    private fun newViewModel(): MarkdownViewModel {
        val store = ViewModelStore()
        viewModelStores += store
        return ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MarkdownViewModel() as T
            },
        )[MarkdownViewModel::class.java]
    }

    private fun awaitSaveResult(
        action: (((DocumentSaveResult) -> Unit) -> Unit),
    ): DocumentSaveResult {
        val result = AtomicReference<DocumentSaveResult>()
        val completed = CountDownLatch(1)
        onMain {
            action { saveResult ->
                result.set(saveResult)
                completed.countDown()
            }
        }
        assertTrue(
            "Timed out waiting for save result",
            completed.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        instrumentation.waitForIdleSync()
        return requireNotNull(result.get())
    }

    private fun waitForState(description: String, condition: () -> Boolean) {
        waitForCondition(description) { onMain(condition) }
    }

    private fun waitForCondition(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + OPERATION_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            instrumentation.waitForIdleSync()
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("Timed out waiting for $description")
    }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync {
            result.set(runCatching(block))
        }
        return requireNotNull(result.get()).getOrThrow()
    }

    private fun readDocument(uri: Uri): DocumentRepository.LoadedDocument = runBlocking {
        DocumentRepository.read(targetContext, uri)
    }

    private fun readRawBytes(uri: Uri): ByteArray =
        requireNotNull(targetContext.contentResolver.openInputStream(uri)).use { input ->
            input.readBytes()
        }

    private fun configureDocument(
        documentId: String,
        bytes: ByteArray,
        documentFlags: Int = DocumentsContract.Document.FLAG_SUPPORTS_WRITE,
        grantFlags: Int?,
    ): Uri {
        withManageDocumentsPermission {
            assertNotNull(
                targetContext.contentResolver.call(
                    TestDocumentsProvider.AUTHORITY,
                    TestDocumentsProvider.METHOD_CONFIGURE,
                    documentId,
                    Bundle().apply {
                        putByteArray(TestDocumentsProvider.KEY_BYTES, bytes)
                        putString(TestDocumentsProvider.KEY_DISPLAY_NAME, "$documentId.md")
                        putInt(TestDocumentsProvider.KEY_DOCUMENT_FLAGS, documentFlags)
                    },
                ),
            )
            if (grantFlags != null) {
                assertNotNull(
                    targetContext.contentResolver.call(
                        TestDocumentsProvider.AUTHORITY,
                        TestDocumentsProvider.METHOD_GRANT,
                        documentId,
                        Bundle().apply {
                            putString(
                                TestDocumentsProvider.KEY_TARGET_PACKAGE,
                                targetContext.packageName,
                            )
                            putInt(TestDocumentsProvider.KEY_GRANT_FLAGS, grantFlags)
                        },
                    ),
                )
            }
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

    private fun clearAppTestState() {
        File(targetContext.noBackupFilesDir, "document-recovery").deleteRecursively()
        targetContext.getSharedPreferences("mora_recent_documents", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        targetContext.getSharedPreferences(
            "mora_managed_uri_permissions",
            Context.MODE_PRIVATE,
        ).edit()
            .clear()
            .commit()
    }

    private inline fun <T> withManageDocumentsPermission(block: () -> T): T {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(
            Manifest.permission.MANAGE_DOCUMENTS,
        )
        return try {
            block()
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    companion object {
        private const val OPERATION_TIMEOUT_SECONDS = 15L
        private const val OPERATION_TIMEOUT_MILLIS = OPERATION_TIMEOUT_SECONDS * 1_000L
        private const val POLL_INTERVAL_MILLIS = 25L
        private const val READ_WRITE_GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
