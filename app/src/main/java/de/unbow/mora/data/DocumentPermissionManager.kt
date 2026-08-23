package de.unbow.mora.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import androidx.core.net.toUri

data class DocumentPermission(
    val persistedFlags: Int = 0,
    val sessionFlags: Int = 0,
) {
    val hasDurableRead: Boolean
        get() = persistedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0

    val hasSessionWrite: Boolean
        get() = sessionFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0
}

internal fun managedFlagsToRelease(
    managedFlags: Int,
    heldFlags: Int,
    isStillReferenced: Boolean,
): Int = if (isStillReferenced) {
    0
} else {
    managedFlags and heldFlags and DocumentPermissionManager.SUPPORTED_FLAGS
}

internal fun registeredManagedFlagsToRelease(
    requestedManagedFlags: Int,
    registeredManagedFlags: Int,
    heldFlags: Int,
    isStillReferenced: Boolean,
): Int = managedFlagsToRelease(
    managedFlags = requestedManagedFlags and registeredManagedFlags,
    heldFlags = heldFlags,
    isStillReferenced = isStillReferenced,
)

internal fun managedEntriesAfterLegacyMigration(
    existingManagedEntries: Map<String, Int>,
    appHeldEntries: Map<String, Int>,
    migrationAlreadyCompleted: Boolean,
): Map<String, Int> {
    val existing = existingManagedEntries.mapValues { (_, flags) ->
        flags and DocumentPermissionManager.SUPPORTED_FLAGS
    }.filterValues { flags -> flags != 0 }
    if (migrationAlreadyCompleted) return existing

    return existing.toMutableMap().apply {
        appHeldEntries.forEach { (uri, heldFlags) ->
            val normalized = heldFlags and DocumentPermissionManager.SUPPORTED_FLAGS
            if (normalized != 0) {
                this[uri] = (this[uri] ?: 0) or normalized
            }
        }
    }
}

internal fun ownershipEpochMatches(expectedEpoch: Long, currentEpoch: Long): Boolean =
    expectedEpoch == currentEpoch

object DocumentPermissionManager {

    private val ownershipEpochs = mutableMapOf<String, Long>()

    internal const val SUPPORTED_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    @Synchronized
    fun acquire(
        context: Context,
        uri: Uri,
        grantedFlags: Int? = null,
    ): DocumentPermission {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return current(context, uri)
        }

        val requestedFlags = grantedFlags
            ?.and(SUPPORTED_FLAGS)
            ?.takeIf { it != 0 }
            ?: SUPPORTED_FLAGS
        val resolver = context.contentResolver

        runCatching {
            resolver.takePersistableUriPermission(uri, requestedFlags)
        }.recoverCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        return current(context, uri).also { permission ->
            if (permission.persistedFlags != 0) {
                registerManaged(context, uri, permission.persistedFlags)
            }
            advanceOwnershipEpoch(uri)
        }
    }

    @Synchronized
    fun currentForUse(context: Context, uri: Uri): DocumentPermission {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) advanceOwnershipEpoch(uri)
        return current(context, uri)
    }

    @Synchronized
    fun ownershipEpoch(uri: Uri): Long = ownershipEpochs[uri.toString()] ?: 0L

    fun current(context: Context, uri: Uri): DocumentPermission {
        val persistedFlags = if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                context.contentResolver.persistedUriPermissions
                    .firstOrNull { permission -> permission.uri == uri }
                    ?.let { permission ->
                        (if (permission.isReadPermission) {
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        } else {
                            0
                        }) or (if (permission.isWritePermission) {
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        } else {
                            0
                        })
                    }
                    ?: 0
            }.getOrDefault(0)
        } else {
            0
        }

        val sessionFlags = when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                val read = context.checkUriPermission(
                    uri,
                    Process.myPid(),
                    Process.myUid(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                ) == PackageManager.PERMISSION_GRANTED
                val write = context.checkUriPermission(
                    uri,
                    Process.myPid(),
                    Process.myUid(),
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                ) == PackageManager.PERMISSION_GRANTED
                (if (read) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                    (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            }

            else -> 0
        }

        return DocumentPermission(
            persistedFlags = persistedFlags,
            sessionFlags = sessionFlags,
        )
    }

    fun releaseIfUnreferenced(
        context: Context,
        uri: Uri,
        managedFlags: Int,
        referencedUris: Set<Uri>,
        expectedOwnershipEpoch: Long? = null,
    ): Boolean {
        return synchronized(this) {
            if (uri.scheme != ContentResolver.SCHEME_CONTENT || managedFlags == 0) {
                return@synchronized false
            }
            if (
                expectedOwnershipEpoch != null &&
                !ownershipEpochMatches(expectedOwnershipEpoch, ownershipEpoch(uri))
            ) {
                return@synchronized false
            }
            val registeredFlags = managedEntries(context)
                .firstOrNull { (managedUri, _) -> managedUri == uri }
                ?.second
                ?: return@synchronized false
            val heldByApp = persistedGrantFlags(context) ?: return@synchronized false
            val heldFlags = heldByApp[uri] ?: 0
            val flagsToRelease = registeredManagedFlagsToRelease(
                requestedManagedFlags = managedFlags,
                registeredManagedFlags = registeredFlags,
                heldFlags = heldFlags,
                isStillReferenced = uri in referencedUris,
            )
            if (flagsToRelease == 0) {
                if (uri !in referencedUris && heldFlags == 0) {
                    updateManagedRegistration(context, uri, 0)
                }
                return@synchronized false
            }

            runCatching {
                context.contentResolver.releasePersistableUriPermission(uri, flagsToRelease)
                updateManagedRegistration(
                    context = context,
                    uri = uri,
                    flags = registeredFlags and heldFlags and flagsToRelease.inv(),
                )
                advanceOwnershipEpoch(uri)
                true
            }.getOrDefault(false)
        }
    }

    fun reconcileManagedGrants(
        context: Context,
        referencedUris: Set<Uri>,
    ) {
        migrateLegacyManagedGrants(context)
        managedEntries(context).forEach { (uri, flags) ->
            releaseIfUnreferenced(
                context = context,
                uri = uri,
                managedFlags = flags,
                referencedUris = referencedUris,
            )
        }
    }

    /**
     * One-time upgrade bridge for grants persisted before Mora kept a registry.
     *
     * Android exposes only grants held by this application. The first migration
     * adopts that finite snapshot into Mora's registry; after the marker is set,
     * later unregistered grants are deliberately not adopted or released.
     */
    internal fun migrateLegacyManagedGrants(context: Context): Map<Uri, Int>? {
        val heldByApp = persistedGrantFlags(context) ?: return null
        val heldByUriString = heldByApp.mapKeys { (uri, _) -> uri.toString() }

        return synchronized(this) {
            val preferences = context.getSharedPreferences(
                MANAGED_PERMISSION_PREFERENCES,
                Context.MODE_PRIVATE,
            )
            val existing = readManagedEntries(preferences)
            val migrationAlreadyCompleted = preferences.getBoolean(
                LEGACY_MIGRATION_COMPLETED_KEY,
                false,
            )
            val managed = managedEntriesAfterLegacyMigration(
                existingManagedEntries = existing,
                appHeldEntries = heldByUriString,
                migrationAlreadyCompleted = migrationAlreadyCompleted,
            )
            if (!migrationAlreadyCompleted) {
                preferences.edit()
                    .putStringSet(MANAGED_PERMISSION_KEY, encodeManagedEntries(managed))
                    .putBoolean(LEGACY_MIGRATION_COMPLETED_KEY, true)
                    .apply()
            }

            buildMap {
                heldByApp.forEach { (uri, heldFlags) ->
                    val managedHeldFlags = managed[uri.toString()].orZero() and heldFlags
                    if (managedHeldFlags != 0) put(uri, managedHeldFlags)
                }
            }
        }
    }

    private fun registerManaged(context: Context, uri: Uri, flags: Int) {
        val normalizedFlags = flags and SUPPORTED_FLAGS
        if (normalizedFlags == 0) return
        synchronized(this) {
            val preferences = context.getSharedPreferences(
                MANAGED_PERMISSION_PREFERENCES,
                Context.MODE_PRIVATE,
            )
            val current = readManagedEntries(preferences).toMutableMap()
            current[uri.toString()] = normalizedFlags
            preferences.edit()
                .putStringSet(MANAGED_PERMISSION_KEY, encodeManagedEntries(current))
                .apply()
        }
    }

    private fun updateManagedRegistration(context: Context, uri: Uri, flags: Int) {
        synchronized(this) {
            val preferences = context.getSharedPreferences(
                MANAGED_PERMISSION_PREFERENCES,
                Context.MODE_PRIVATE,
            )
            val retained = readManagedEntries(preferences).toMutableMap()
            val normalizedFlags = flags and SUPPORTED_FLAGS
            if (normalizedFlags == 0) {
                retained.remove(uri.toString())
            } else {
                retained[uri.toString()] = normalizedFlags
            }
            preferences.edit()
                .putStringSet(MANAGED_PERMISSION_KEY, encodeManagedEntries(retained))
                .apply()
        }
    }

    private fun managedEntries(context: Context): List<Pair<Uri, Int>> = synchronized(this) {
        readManagedEntries(
            context.getSharedPreferences(
                MANAGED_PERMISSION_PREFERENCES,
                Context.MODE_PRIVATE,
            ),
        ).mapNotNull { (uri, flags) ->
            runCatching { uri.toUri() }.getOrNull()?.let { parsed -> parsed to flags }
        }
    }

    private fun readManagedEntries(
        preferences: android.content.SharedPreferences,
    ): Map<String, Int> = preferences.getStringSet(MANAGED_PERMISSION_KEY, emptySet())
        .orEmpty()
        .mapNotNull(::decodeManagedEntry)
        .associate { (uri, flags) -> uri.toString() to flags }

    private fun encodeManagedEntries(entries: Map<String, Int>): Set<String> =
        entries.mapNotNullTo(mutableSetOf()) { (uriString, flags) ->
            runCatching { uriString.toUri() }.getOrNull()
                ?.let { uri -> encodeManagedEntry(uri, flags) }
        }

    private fun persistedGrantFlags(context: Context): Map<Uri, Int>? = runCatching {
        buildMap {
            context.contentResolver.persistedUriPermissions.forEach { permission ->
                val flags = (if (permission.isReadPermission) {
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                } else {
                    0
                }) or (if (permission.isWritePermission) {
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                } else {
                    0
                })
                val normalizedFlags = flags and SUPPORTED_FLAGS
                if (normalizedFlags != 0) {
                    put(
                        permission.uri,
                        (get(permission.uri) ?: 0) or normalizedFlags,
                    )
                }
            }
        }
    }.getOrNull()

    private fun encodeManagedEntry(uri: Uri, flags: Int): String =
        "${flags and SUPPORTED_FLAGS}:${uri}"

    private fun decodeManagedEntry(encoded: String): Pair<Uri, Int>? {
        val delimiter = encoded.indexOf(':')
        if (delimiter <= 0 || delimiter == encoded.lastIndex) return null
        val flags = encoded.substring(0, delimiter).toIntOrNull()
            ?.and(SUPPORTED_FLAGS)
            ?.takeIf { it != 0 }
            ?: return null
        val uri = runCatching { encoded.substring(delimiter + 1).toUri() }.getOrNull()
            ?: return null
        return uri to flags
    }

    private fun advanceOwnershipEpoch(uri: Uri) {
        val key = uri.toString()
        ownershipEpochs[key] = (ownershipEpochs[key] ?: 0L) + 1L
    }

    private const val MANAGED_PERMISSION_PREFERENCES = "mora_managed_uri_permissions"
    private const val MANAGED_PERMISSION_KEY = "permissions"
    private const val LEGACY_MIGRATION_COMPLETED_KEY = "legacy_migration_completed_v1"
}

private fun Int?.orZero(): Int = this ?: 0
