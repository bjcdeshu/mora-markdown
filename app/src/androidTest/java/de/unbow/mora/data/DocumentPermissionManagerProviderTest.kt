package de.unbow.mora.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import de.unbow.mora.IncomingDocumentRequest
import de.unbow.mora.model.MarkdownViewModel
import de.unbow.mora.testprovider.TestDocumentsProvider
import java.io.File
import org.junit.After
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
class DocumentPermissionManagerProviderTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var targetContext: Context

    @Before
    fun setUp() {
        targetContext = instrumentation.targetContext
        releasePersistedTestProviderPermissions()
        resetProvider()
        clearAppTestState()
    }

    @After
    fun tearDown() {
        releasePersistedTestProviderPermissions()
        resetProvider()
        clearAppTestState()
    }

    @Test
    fun currentReferenceRetainsManagedGrantUntilTheLastReferenceIsRemoved() {
        val uri = configureDocument("managed_current")
        val managed = acquireManagedRead(uri)

        assertFalse(
            DocumentPermissionManager.releaseIfUnreferenced(
                context = targetContext,
                uri = uri,
                managedFlags = managed.persistedFlags,
                referencedUris = setOf(uri),
            ),
        )
        assertPersistedRead(uri)

        assertTrue(
            DocumentPermissionManager.releaseIfUnreferenced(
                context = targetContext,
                uri = uri,
                managedFlags = managed.persistedFlags,
                referencedUris = emptySet(),
            ),
        )
        assertNoPersistedGrant(uri)
    }

    @Test
    fun recoveryRepositoryReferenceRetainsGrantUntilRecoveryIsCleared() {
        val uri = configureDocument("managed_recovery")
        val managed = acquireManagedRead(uri)
        val recoveryRepository = DocumentRecoveryRepository(targetContext)
        val recoveryId = "permission-recovery"
        assertTrue(
            recoveryRepository.saveDirty(
                DirtyDocumentRecovery(
                    recoveryId = recoveryId,
                    sourceUri = uri.toString(),
                    name = "recovery.md",
                    managedPermissionFlags = managed.persistedFlags,
                    baseVersion = null,
                    contentRevision = 1L,
                    recoveryGeneration = 1L,
                    hasUtf8Bom = false,
                    savedAt = 1L,
                    content = "dirty recovery content",
                ),
            ),
        )

        val recoveryReferences = recoveryRepository.referencedUriStrings()
            .mapTo(mutableSetOf()) { encoded -> Uri.parse(encoded) }
        assertEquals(setOf(uri), recoveryReferences)

        DocumentPermissionManager.reconcileManagedGrants(
            context = targetContext,
            referencedUris = recoveryReferences,
        )
        assertPersistedRead(uri)

        assertTrue(recoveryRepository.clearDirty(recoveryId))
        DocumentPermissionManager.reconcileManagedGrants(
            context = targetContext,
            referencedUris = recoveryRepository.referencedUriStrings()
                .mapTo(mutableSetOf()) { encoded -> Uri.parse(encoded) },
        )
        assertNoPersistedGrant(uri)
    }

    @Test
    fun unregisteredGrantCreatedAfterMigrationIsNeverReleasedByMora() {
        DocumentPermissionManager.reconcileManagedGrants(targetContext, emptySet())
        val uri = configureDocument("unregistered_after_migration")
        targetContext.contentResolver.takePersistableUriPermission(uri, READ_GRANT)
        assertPersistedRead(uri)

        assertFalse(
            DocumentPermissionManager.releaseIfUnreferenced(
                context = targetContext,
                uri = uri,
                managedFlags = READ_GRANT,
                referencedUris = emptySet(),
            ),
        )
        DocumentPermissionManager.reconcileManagedGrants(
            context = targetContext,
            referencedUris = emptySet(),
        )
        assertPersistedRead(uri)
    }

    @Test
    fun newerDocumentUseInvalidatesAStaleScheduledRelease() {
        val uri = configureDocument("stale_release")
        val managed = acquireManagedRead(uri)
        val scheduledEpoch = DocumentPermissionManager.ownershipEpoch(uri)

        DocumentPermissionManager.currentForUse(targetContext, uri)

        assertFalse(
            DocumentPermissionManager.releaseIfUnreferenced(
                context = targetContext,
                uri = uri,
                managedFlags = managed.persistedFlags,
                referencedUris = emptySet(),
                expectedOwnershipEpoch = scheduledEpoch,
            ),
        )
        assertPersistedRead(uri)

        assertTrue(
            DocumentPermissionManager.releaseIfUnreferenced(
                context = targetContext,
                uri = uri,
                managedFlags = managed.persistedFlags,
                referencedUris = emptySet(),
                expectedOwnershipEpoch = DocumentPermissionManager.ownershipEpoch(uri),
            ),
        )
        assertNoPersistedGrant(uri)
    }

    @Test
    fun deferringThenCancellingIncomingUriDoesNotAcquireItsPersistableGrant() {
        val uri = configureDocument("cancelled_incoming")
        val permissionBefore = DocumentPermissionManager.current(targetContext, uri)
        assertEquals(0, permissionBefore.persistedFlags)
        assertTrue(permissionBefore.sessionFlags and READ_GRANT != 0)
        val request = IncomingDocumentRequest(
            id = 17L,
            uri = uri,
            suggestedName = "incoming.md",
            grantedFlags = READ_PERSISTABLE_GRANTS,
        )
        val viewModel = MarkdownViewModel()

        viewModel.deferIncomingRequest(request)
        assertEquals(request, viewModel.pendingIncomingRequest)
        viewModel.clearPendingIncomingRequest(request.id)

        assertNull(viewModel.pendingIncomingRequest)
        assertNoPersistedGrant(uri)
    }

    private fun acquireManagedRead(uri: Uri): DocumentPermission =
        DocumentPermissionManager.acquire(
            context = targetContext,
            uri = uri,
            grantedFlags = READ_PERSISTABLE_GRANTS,
        ).also { permission ->
            assertTrue(permission.hasDurableRead)
        }

    private fun configureDocument(documentId: String): Uri {
        withManageDocumentsPermission {
            assertNotNull(
                targetContext.contentResolver.call(
                    TestDocumentsProvider.AUTHORITY,
                    TestDocumentsProvider.METHOD_CONFIGURE,
                    documentId,
                    Bundle().apply {
                        putByteArray(TestDocumentsProvider.KEY_BYTES, "# $documentId".toByteArray())
                        putString(TestDocumentsProvider.KEY_DISPLAY_NAME, "$documentId.md")
                    },
                ),
            )
            assertNotNull(
                targetContext.contentResolver.call(
                    TestDocumentsProvider.AUTHORITY,
                    TestDocumentsProvider.METHOD_GRANT,
                    documentId,
                    Bundle().apply {
                        putString(TestDocumentsProvider.KEY_TARGET_PACKAGE, targetContext.packageName)
                        putInt(TestDocumentsProvider.KEY_GRANT_FLAGS, READ_PERSISTABLE_GRANTS)
                    },
                ),
            )
        }
        return TestDocumentsProvider.documentUri(documentId)
    }

    private fun assertPersistedRead(uri: Uri) {
        assertTrue(
            DocumentPermissionManager.current(targetContext, uri).persistedFlags and
                READ_GRANT != 0,
        )
    }

    private fun assertNoPersistedGrant(uri: Uri) {
        assertEquals(0, DocumentPermissionManager.current(targetContext, uri).persistedFlags)
    }

    private fun releasePersistedTestProviderPermissions() {
        targetContext.contentResolver.persistedUriPermissions
            .filter { permission -> permission.uri.authority == TestDocumentsProvider.AUTHORITY }
            .forEach { permission ->
                val flags = (if (permission.isReadPermission) READ_GRANT else 0) or
                    (if (permission.isWritePermission) WRITE_GRANT else 0)
                if (flags != 0) {
                    runCatching {
                        targetContext.contentResolver.releasePersistableUriPermission(
                            permission.uri,
                            flags,
                        )
                    }
                }
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
        private const val READ_GRANT = Intent.FLAG_GRANT_READ_URI_PERMISSION
        private const val WRITE_GRANT = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        private const val READ_PERSISTABLE_GRANTS = READ_GRANT or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    }
}
