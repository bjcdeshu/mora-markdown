package de.unbow.mora.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import de.unbow.mora.MainActivity
import de.unbow.mora.R
import de.unbow.mora.data.DocumentFailure
import de.unbow.mora.data.DocumentRecoveryRepository
import de.unbow.mora.data.DocumentRepository
import de.unbow.mora.testprovider.TestDocumentsControlProvider
import de.unbow.mora.testprovider.TestDocumentsProvider
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
class MarkdownViewModelSaveFlowTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val viewModelStores = mutableListOf<ViewModelStore>()
    private lateinit var targetContext: Context
    private lateinit var provider: ProviderHarness

    @Before
    fun setUp() {
        targetContext = instrumentation.targetContext
        provider = ProviderHarness(targetContext)
        provider.reset()
        clearPersistentTestState(targetContext)
    }

    @After
    fun tearDown() {
        onMain {
            viewModelStores.forEach(ViewModelStore::clear)
            viewModelStores.clear()
        }
        instrumentation.waitForIdleSync()
        provider.reset()
        clearPersistentTestState(targetContext)
    }

    @Test
    fun failedSaveKeepsDraftStableThenAllowsSaveCopyAndRetry() {
        val failingTarget = provider.configure(
            documentId = "save_flow_failed_target",
            bytes = byteArrayOf(),
            failWrites = true,
            grantFlags = READ_WRITE_GRANTS,
        )
        val copyTarget = provider.configure(
            documentId = "save_flow_copy_target",
            bytes = byteArrayOf(),
            grantFlags = READ_WRITE_GRANTS,
        )
        val viewModel = initializedViewModel()
        val draft = "# Unsaved\n\nKeep this exact revision after failure.\n"

        assertNull(onMain { viewModel.newDraft("failure.md", draft) })
        val failed = awaitSaveResult { callback ->
            viewModel.saveAs(targetContext, failingTarget, callback)
        }

        assertEquals(
            DocumentSaveResult.Failed(DocumentFailure.WRITE_FAILED),
            failed,
        )
        val failedState = onMain { viewModel.uiState }
        assertTrue(failedState.hasDocument)
        assertTrue(failedState.isDirty)
        assertFalse(failedState.isSaving)
        assertNull(failedState.uri)
        assertEquals(draft, failedState.content)

        // Dismissing the failure dialog is deliberately a UI-only no-op. Until another
        // action is requested, the ViewModel must retain the exact failed revision.
        instrumentation.waitForIdleSync()
        assertEquals(failedState, onMain { viewModel.uiState })

        val copied = awaitSaveResult { callback ->
            viewModel.saveCopy(targetContext, copyTarget, callback)
        }

        assertEquals(DocumentSaveResult.Copied, copied)
        assertEquals(draft, readDocument(copyTarget).content)
        assertEquals(failedState, onMain { viewModel.uiState })

        provider.configure(
            documentId = "save_flow_failed_target",
            bytes = byteArrayOf(),
            failWrites = false,
            grantFlags = null,
        )
        val retried = awaitSaveResult { callback ->
            viewModel.saveAs(targetContext, failingTarget, callback)
        }

        assertEquals(DocumentSaveResult.Saved, retried)
        assertEquals(draft, readDocument(failingTarget).content)
        val savedState = onMain { viewModel.uiState }
        assertEquals(failingTarget, savedState.uri)
        assertFalse(savedState.isDirty)
        assertFalse(savedState.isSaving)
    }

    @Test
    fun unreadableSourceBecomesActionableOpenFailureWithoutOpeningAnEditor() {
        val unreadable = provider.configure(
            documentId = "save_flow_unreadable_source",
            bytes = "provider bytes".toByteArray(),
            failReads = true,
            grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val viewModel = initializedViewModel()

        assertTrue(onMain {
            viewModel.openDocument(targetContext, unreadable, "fallback.md")
        })
        waitForState("unreadable source failure") {
            !viewModel.uiState.isLoading && viewModel.uiState.error != null
        }

        val state = onMain { viewModel.uiState }
        val failure = state.error as DocumentUiError.OpenFailed
        assertEquals(DocumentFailure.READ_FAILED, failure.failure)
        assertFalse(state.hasDocument)
        assertFalse(state.isDirty)
        assertEquals("save_flow_unreadable_source", failure.documentName)
    }

    @Test
    fun fullWriteReportedAsFailureCannotMakeARevertedRevisionLookClean() {
        val original = "known-good original"
        val intended = "provider persisted this but reported failure"
        val source = provider.configure(
            documentId = "reported_failure_after_full_write",
            bytes = original.toByteArray(),
            grantFlags = READ_WRITE_GRANTS,
            failVerificationReadAfterWrite = true,
        )
        val viewModel = initializedViewModel()

        assertTrue(onMain {
            viewModel.openDocument(targetContext, source, "fallback.md")
        })
        waitForState("writable source") {
            viewModel.uiState.hasDocument &&
                !viewModel.uiState.isLoading &&
                viewModel.uiState.canWrite
        }
        assertNull(onMain { viewModel.updateContent(intended) })

        assertEquals(
            DocumentSaveResult.Failed(DocumentFailure.VERIFICATION_FAILED),
            awaitSaveResult { callback -> viewModel.save(targetContext, callback) },
        )
        assertEquals(intended, readDocument(source).content)

        assertNull(onMain { viewModel.updateContent(original) })
        assertTrue(onMain { viewModel.uiState.isDirty })
        onMain { viewModel.flushRecovery() }
        waitForState("reverted recovery snapshot") {
            DocumentRecoveryRepository(targetContext).loadDirty()?.content == original
        }

        val restarted = initializedViewModel(expectNoRecovery = false)
        waitForState("reverted revision after restart reconciliation") {
            restarted.recoverableWork?.content == original
        }
        assertEquals(original, onMain { restarted.recoverableWork?.content })
        assertEquals(intended, readDocument(source).content)
    }

    private fun initializedViewModel(expectNoRecovery: Boolean = true): MarkdownViewModel {
        val store = ViewModelStore()
        viewModelStores += store
        val viewModel = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MarkdownViewModel() as T
            },
        )[MarkdownViewModel::class.java]
        onMain { viewModel.initialize(targetContext) }
        waitForState("ViewModel recovery initialization") {
            viewModel.isRecoveryInitialized
        }
        if (expectNoRecovery) assertNull(onMain { viewModel.recoverableWork })
        return viewModel
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
        val deadline = SystemClock.elapsedRealtime() + OPERATION_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (onMain(condition)) return
            instrumentation.waitForIdleSync()
            Thread.yield()
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
}

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
class MoraAppSaveFlowRecreationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var targetContext: Context
    private lateinit var provider: ProviderHarness
    private lateinit var viewModel: MarkdownViewModel

    @Before
    fun setUp() {
        targetContext = instrumentation.targetContext
        provider = ProviderHarness(targetContext)
        provider.reset()
        clearPersistentTestState(targetContext)
        composeRule.runOnIdle {
            viewModel = ViewModelProvider(composeRule.activity)[MarkdownViewModel::class.java]
            viewModel.initialize(targetContext)
        }
        composeRule.waitUntil(OPERATION_TIMEOUT_MILLIS) {
            viewModel.isRecoveryInitialized
        }
        onMain {
            viewModel.recoverableWork?.let { recovery ->
                viewModel.discardRecovery(targetContext, recovery)
            }
        }
        composeRule.waitUntil(OPERATION_TIMEOUT_MILLIS) {
            viewModel.recoverableWork == null && !viewModel.isRecoveryTransitioning
        }
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            onMain {
                viewModel.clearPendingIncomingRequest()
                if (
                    viewModel.uiState.hasDocument &&
                    !viewModel.uiState.isSaving &&
                    !viewModel.isRecoverySaving &&
                    !viewModel.isRecoveryTransitioning
                ) {
                    viewModel.closeDocument(targetContext, discardChanges = true)
                }
            }
            composeRule.waitUntil(OPERATION_TIMEOUT_MILLIS) {
                !viewModel.uiState.isSaving &&
                    !viewModel.isRecoverySaving &&
                    !viewModel.isRecoveryTransitioning
            }
        }
        provider.reset()
        clearPersistentTestState(targetContext)
    }

    @Test
    fun dirtyCloseCancelKeepsRevisionAndDiscardClearsItsRecovery() {
        val content = "# Close flow\n\nUnsaved and recoverable.\n"
        onMain {
            assertNull(viewModel.newDraft("close-flow.md", content))
            viewModel.flushRecovery()
        }
        composeRule.waitUntil(OPERATION_TIMEOUT_MILLIS) {
            DocumentRecoveryRepository(targetContext).loadDirty()?.content == content
        }
        val sessionId = onMain { viewModel.uiState.sessionId }
        val revision = onMain { viewModel.uiState.contentRevision }

        clickBackAndWaitForCloseDialog()
        composeRule.onNodeWithText(label(R.string.cancel)).performClick()
        waitForTextToDisappear(label(R.string.unsaved_close_title))

        val cancelledState = onMain { viewModel.uiState }
        assertTrue(cancelledState.hasDocument)
        assertTrue(cancelledState.isDirty)
        assertEquals(sessionId, cancelledState.sessionId)
        assertEquals(revision, cancelledState.contentRevision)
        assertEquals(content, cancelledState.content)

        clickBackAndWaitForCloseDialog()
        composeRule.onNodeWithText(label(R.string.discard_changes)).performClick()
        composeRule.waitUntil(OPERATION_TIMEOUT_MILLIS) {
            !viewModel.uiState.hasDocument && !viewModel.isRecoveryTransitioning
        }

        assertNull(DocumentRecoveryRepository(targetContext).loadDirty())
        assertNull(onMain { viewModel.recoverableWork })
    }

    @Test
    fun incomingCancelKeepsDirtyEditorAndStaleCancellationCannotDropReplacement() {
        val localContent = "# Local draft\n\nMust not be replaced.\n"
        onMain { assertNull(viewModel.newDraft("local.md", localContent)) }
        sendSharedText("first incoming")
        composeRule.waitUntil(OPERATION_TIMEOUT_MILLIS) {
            viewModel.pendingIncomingRequest?.sharedText == "first incoming"
        }
        val firstId = requireNotNull(onMain { viewModel.pendingIncomingRequest }).id

        sendSharedText("replacement incoming")
        composeRule.waitUntil(OPERATION_TIMEOUT_MILLIS) {
            viewModel.pendingIncomingRequest?.sharedText == "replacement incoming"
        }
        val replacement = requireNotNull(onMain { viewModel.pendingIncomingRequest })
        assertNotEquals(firstId, replacement.id)

        onMain { viewModel.clearPendingIncomingRequest(firstId) }
        assertEquals(replacement, onMain { viewModel.pendingIncomingRequest })
        assertEquals(
            DocumentFailure.RECOVERY_FAILED,
            onMain { viewModel.openSharedText("bypass", "bypass.md") },
        )
        waitForText(label(R.string.open_new_document_title))
        composeRule.onNodeWithText(label(R.string.cancel)).performClick()
        composeRule.waitUntil(OPERATION_TIMEOUT_MILLIS) {
            viewModel.pendingIncomingRequest == null
        }

        val state = onMain { viewModel.uiState }
        assertTrue(state.hasDocument)
        assertTrue(state.isDirty)
        assertEquals(localContent, state.content)
    }

    @Test
    fun pendingFailureAndPostSaveCloseSurviveActivityRecreation() {
        val source = openEditedWritableSource(
            documentId = "recreated_retry_source",
            original = "before retry",
            edited = "after recreated retry",
            failWrites = true,
        )

        beginSaveAndCloseUntilFailure()
        composeRule.activityRule.scenario.recreate()
        waitForText(label(R.string.save_failed_title))
        composeRule.onNodeWithText(label(R.string.retry)).assertIsDisplayed()

        provider.configure(
            documentId = "recreated_retry_source",
            bytes = "before retry".toByteArray(),
            failWrites = false,
            grantFlags = null,
        )
        composeRule.onNodeWithText(label(R.string.retry)).performClick()
        composeRule.waitUntil(OPERATION_TIMEOUT_MILLIS) {
            !viewModel.uiState.hasDocument &&
                !viewModel.uiState.isSaving &&
                !viewModel.isRecoveryTransitioning
        }

        assertEquals("after recreated retry", readDocument(source).content)
    }

    @Test
    fun cancellingRecreatedFailureClearsPostSaveCloseContinuation() {
        val source = openEditedWritableSource(
            documentId = "recreated_cancel_source",
            original = "before cancel",
            edited = "saved after cancel",
            failWrites = true,
        )

        beginSaveAndCloseUntilFailure()
        composeRule.activityRule.scenario.recreate()
        waitForText(label(R.string.save_failed_title))
        composeRule.onNodeWithText(label(R.string.cancel)).performClick()
        waitForTextToDisappear(label(R.string.save_failed_title))

        val cancelledState = onMain { viewModel.uiState }
        assertTrue(cancelledState.hasDocument)
        assertTrue(cancelledState.isDirty)
        provider.configure(
            documentId = "recreated_cancel_source",
            bytes = "before cancel".toByteArray(),
            failWrites = false,
            grantFlags = null,
        )
        composeRule.onNodeWithContentDescription(label(R.string.save_document)).performClick()
        composeRule.waitUntil(OPERATION_TIMEOUT_MILLIS) {
            !viewModel.uiState.isSaving && !viewModel.uiState.isDirty
        }

        assertTrue(onMain { viewModel.uiState.hasDocument })
        assertEquals(source, onMain { viewModel.uiState.uri })
        assertEquals("saved after cancel", readDocument(source).content)
    }

    private fun openEditedWritableSource(
        documentId: String,
        original: String,
        edited: String,
        failWrites: Boolean,
    ): Uri {
        val source = provider.configure(
            documentId = documentId,
            bytes = original.toByteArray(),
            failWrites = failWrites,
            grantFlags = READ_WRITE_GRANTS,
        )
        onMain {
            assertTrue(viewModel.openDocument(targetContext, source, "fallback.md"))
        }
        composeRule.waitUntil(OPERATION_TIMEOUT_MILLIS) {
            viewModel.uiState.hasDocument &&
                !viewModel.uiState.isLoading &&
                viewModel.uiState.canWrite
        }
        onMain { assertNull(viewModel.updateContent(edited)) }
        composeRule.onNodeWithContentDescription(label(R.string.edit_document)).performClick()
        composeRule.onNodeWithContentDescription(label(R.string.save_document))
            .assertIsDisplayed()
        return source
    }

    private fun beginSaveAndCloseUntilFailure() {
        clickBackAndWaitForCloseDialog()
        composeRule.onNodeWithText(label(R.string.save_document)).performClick()
        waitForText(label(R.string.save_failed_title))
        assertTrue(onMain { viewModel.uiState.hasDocument })
        assertTrue(onMain { viewModel.uiState.isDirty })
    }

    private fun clickBackAndWaitForCloseDialog() {
        composeRule.onNodeWithContentDescription(label(R.string.navigate_back)).performClick()
        waitForText(label(R.string.unsaved_close_title))
    }

    private fun sendSharedText(content: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, content)
            .putExtra(Intent.EXTRA_TITLE, "incoming.md")
        onMain {
            val activity = composeRule.activity
            val scenarioLaunchIntent = activity.intent
            try {
                instrumentation.callActivityOnNewIntent(activity, intent)
            } finally {
                // MainActivity intentionally keeps the latest external Intent. ActivityScenario,
                // however, ignores lifecycle events when Activity.getIntent() no longer matches
                // the Intent it launched, so restore only the harness identity after delivery.
                activity.intent = scenarioLaunchIntent
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(OPERATION_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun waitForTextToDisappear(text: String) {
        composeRule.waitUntil(OPERATION_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun label(resourceId: Int): String = composeRule.activity.getString(resourceId)

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        composeRule.runOnIdle {
            result.set(runCatching(block))
        }
        return requireNotNull(result.get()).getOrThrow()
    }

    private fun readDocument(uri: Uri): DocumentRepository.LoadedDocument = runBlocking {
        DocumentRepository.read(targetContext, uri)
    }
}

private class ProviderHarness(
    private val targetContext: Context,
) {

    fun configure(
        documentId: String,
        bytes: ByteArray,
        documentFlags: Int = DocumentsContract.Document.FLAG_SUPPORTS_WRITE,
        failReads: Boolean = false,
        failWrites: Boolean = false,
        failVerificationReadAfterWrite: Boolean = false,
        partialWriteFailureAfterBytes: Int? = null,
        grantFlags: Int?,
    ): Uri {
        assertNotNull(
            targetContext.contentResolver.call(
                TestDocumentsControlProvider.AUTHORITY,
                TestDocumentsProvider.METHOD_CONFIGURE,
                documentId,
                Bundle().apply {
                    putByteArray(TestDocumentsProvider.KEY_BYTES, bytes)
                    putString(TestDocumentsProvider.KEY_DISPLAY_NAME, "$documentId.md")
                    putInt(TestDocumentsProvider.KEY_DOCUMENT_FLAGS, documentFlags)
                    putBoolean(TestDocumentsProvider.KEY_FAIL_READS, failReads)
                    putBoolean(TestDocumentsProvider.KEY_FAIL_WRITES, failWrites)
                    putBoolean(
                        TestDocumentsProvider.KEY_FAIL_VERIFICATION_READ_AFTER_WRITE,
                        failVerificationReadAfterWrite,
                    )
                    partialWriteFailureAfterBytes?.let { byteCount ->
                        putInt(
                            TestDocumentsProvider.KEY_PARTIAL_WRITE_FAILURE_AFTER_BYTES,
                            byteCount,
                        )
                    }
                },
            ),
        )
        if (grantFlags != null) {
            assertNotNull(
                targetContext.contentResolver.call(
                    TestDocumentsControlProvider.AUTHORITY,
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
        return TestDocumentsProvider.documentUri(documentId)
    }

    fun reset() {
        assertNotNull(
            targetContext.contentResolver.call(
                TestDocumentsControlProvider.AUTHORITY,
                TestDocumentsProvider.METHOD_RESET,
                null,
                null,
            ),
        )
    }
}

private fun clearPersistentTestState(context: Context) {
    File(context.noBackupFilesDir, "document-recovery").deleteRecursively()
    context.getSharedPreferences("mora_recent_documents", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit()
    context.getSharedPreferences("mora_managed_uri_permissions", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit()
}

private const val OPERATION_TIMEOUT_SECONDS = 15L
private const val OPERATION_TIMEOUT_MILLIS = OPERATION_TIMEOUT_SECONDS * 1_000L
private const val READ_WRITE_GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
