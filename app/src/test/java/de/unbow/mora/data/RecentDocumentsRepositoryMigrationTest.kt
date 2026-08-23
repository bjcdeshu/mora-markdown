package de.unbow.mora.data

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentDocumentsRepositoryMigrationTest {

    @Test
    fun `legacy recent without flags inherits its app-held managed grant`() {
        val held = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        assertEquals(
            held,
            resolvedManagedPermissionFlags(
                storedFlags = null,
                appHeldManagedFlags = held,
            ),
        )
    }

    @Test
    fun `explicit zero is not reclassified as a managed grant`() {
        assertEquals(
            0,
            resolvedManagedPermissionFlags(
                storedFlags = 0,
                appHeldManagedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
            ),
        )
    }

    @Test
    fun `legacy inference strips unsupported permission bits`() {
        assertEquals(
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
            resolvedManagedPermissionFlags(
                storedFlags = null,
                appHeldManagedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or (1 shl 20),
            ),
        )
    }
}
