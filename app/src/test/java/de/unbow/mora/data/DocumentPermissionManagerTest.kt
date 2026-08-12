package de.unbow.mora.data

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPermissionManagerTest {

    @Test
    fun `referenced grants are never released`() {
        assertEquals(
            0,
            managedFlagsToRelease(
                managedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                heldFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                isStillReferenced = true,
            ),
        )
    }

    @Test
    fun `release is limited to flags both managed and still held`() {
        assertEquals(
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
            managedFlagsToRelease(
                managedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
                heldFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                isStillReferenced = false,
            ),
        )
        assertEquals(
            0,
            managedFlagsToRelease(
                managedFlags = 0,
                heldFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
                isStillReferenced = false,
            ),
        )
    }

    @Test
    fun `first migration adopts the finite app-held snapshot`() {
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val migrated = managedEntriesAfterLegacyMigration(
            existingManagedEntries = mapOf("content://mora/current" to read),
            appHeldEntries = mapOf(
                "content://mora/current" to write,
                "content://mora/orphan" to read,
            ),
            migrationAlreadyCompleted = false,
        )

        assertEquals(read or write, migrated["content://mora/current"])
        assertEquals(read, migrated["content://mora/orphan"])
    }

    @Test
    fun `completed migration never adopts a later unregistered grant`() {
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val migrated = managedEntriesAfterLegacyMigration(
            existingManagedEntries = mapOf("content://mora/managed" to read),
            appHeldEntries = mapOf(
                "content://mora/managed" to read,
                "content://another-component/unregistered" to read,
            ),
            migrationAlreadyCompleted = true,
        )

        assertEquals(setOf("content://mora/managed"), migrated.keys)
        assertFalse("content://another-component/unregistered" in migrated)
    }

    @Test
    fun `reconcile plan retains referenced migration grant and releases orphan`() {
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val managed = managedEntriesAfterLegacyMigration(
            existingManagedEntries = emptyMap(),
            appHeldEntries = mapOf(
                "content://mora/referenced" to read,
                "content://mora/orphan" to read,
            ),
            migrationAlreadyCompleted = false,
        )

        val referencedRelease = registeredManagedFlagsToRelease(
            requestedManagedFlags = managed.getValue("content://mora/referenced"),
            registeredManagedFlags = managed.getValue("content://mora/referenced"),
            heldFlags = read,
            isStillReferenced = true,
        )
        val orphanRelease = registeredManagedFlagsToRelease(
            requestedManagedFlags = managed.getValue("content://mora/orphan"),
            registeredManagedFlags = managed.getValue("content://mora/orphan"),
            heldFlags = read,
            isStillReferenced = false,
        )

        assertEquals(0, referencedRelease)
        assertEquals(read, orphanRelease)
        assertTrue(orphanRelease != 0)
    }

    @Test
    fun `unregistered held flags can never be released`() {
        assertEquals(
            0,
            registeredManagedFlagsToRelease(
                requestedManagedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
                registeredManagedFlags = 0,
                heldFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
                isStillReferenced = false,
            ),
        )
    }

    @Test
    fun `a newer acquire invalidates an older scheduled release`() {
        assertTrue(ownershipEpochMatches(expectedEpoch = 4L, currentEpoch = 4L))
        assertFalse(ownershipEpochMatches(expectedEpoch = 4L, currentEpoch = 5L))
    }
}
