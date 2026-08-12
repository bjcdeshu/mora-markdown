package de.unbow.mora.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject

data class RecentDocument(
    val uri: Uri,
    val name: String,
    val lastOpenedAt: Long,
    val scrollY: Int,
    val managedPermissionFlags: Int = 0,
)

data class RecentDocumentsMutation(
    val documents: List<RecentDocument>,
    val removed: List<RecentDocument> = emptyList(),
)

internal fun updateRecentDocuments(
    existing: List<RecentDocument>,
    uri: Uri,
    name: String,
    openedAt: Long,
    managedPermissionFlags: Int,
    maximumDocuments: Int,
): RecentDocumentsMutation {
    val previous = existing.firstOrNull { it.uri == uri }
    val persistedName = name.takeIf(String::isNotBlank) ?: previous?.name.orEmpty()
    val candidates = buildList {
        add(
            RecentDocument(
                uri = uri,
                name = persistedName,
                lastOpenedAt = openedAt,
                scrollY = previous?.scrollY ?: 0,
                managedPermissionFlags = managedPermissionFlags.takeIf { it != 0 }
                    ?: previous?.managedPermissionFlags
                    ?: 0,
            ),
        )
        addAll(existing.filterNot { it.uri == uri })
    }
    val updated = candidates.take(maximumDocuments.coerceAtLeast(0))
    val retainedUris = updated.mapTo(mutableSetOf(), RecentDocument::uri)
    return RecentDocumentsMutation(
        documents = updated,
        removed = candidates.drop(updated.size)
            .filterNot { it.uri in retainedUris },
    )
}

internal fun resolvedManagedPermissionFlags(
    storedFlags: Int?,
    appHeldManagedFlags: Int,
): Int = (storedFlags ?: appHeldManagedFlags) and DocumentPermissionManager.SUPPORTED_FLAGS

private data class StoredRecentDocument(
    val uri: Uri,
    val name: String,
    val lastOpenedAt: Long,
    val scrollY: Int,
    val managedPermissionFlags: Int?,
)

object RecentDocumentsRepository {

    private const val preferencesName = "mora_recent_documents"
    private const val documentsKey = "documents"
    private const val maximumDocuments = 12

    fun load(context: Context): List<RecentDocument> {
        val raw = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getString(documentsKey, null)
            ?: run {
                DocumentPermissionManager.migrateLegacyManagedGrants(context)
                return emptyList()
            }

        val storedDocuments = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val uri = item.optString("uri").takeIf(String::isNotBlank) ?: continue
                    val name = item.optString("name")
                    add(
                        StoredRecentDocument(
                            uri = uri.toUri(),
                            name = name,
                            lastOpenedAt = item.optLong("lastOpenedAt"),
                            scrollY = item.optInt("scrollY").coerceAtLeast(0),
                            managedPermissionFlags = if (
                                item.has("managedPermissionFlags") &&
                                !item.isNull("managedPermissionFlags")
                            ) {
                                item.optInt("managedPermissionFlags")
                            } else {
                                null
                            },
                        ),
                    )
                }
            }
        }.getOrNull() ?: return emptyList()

        val managedHeldFlags = DocumentPermissionManager.migrateLegacyManagedGrants(context)
        val documents = storedDocuments.map { stored ->
            RecentDocument(
                uri = stored.uri,
                name = stored.name,
                lastOpenedAt = stored.lastOpenedAt,
                scrollY = stored.scrollY,
                managedPermissionFlags = resolvedManagedPermissionFlags(
                    storedFlags = stored.managedPermissionFlags,
                    appHeldManagedFlags = managedHeldFlags?.get(stored.uri) ?: 0,
                ),
            )
        }.sortedByDescending(RecentDocument::lastOpenedAt)
            .take(maximumDocuments)

        if (
            managedHeldFlags != null &&
            storedDocuments.any { document -> document.managedPermissionFlags == null }
        ) {
            save(context, documents)
        }
        return documents
    }

    fun recordOpened(
        context: Context,
        uri: Uri,
        name: String,
        openedAt: Long = System.currentTimeMillis(),
        managedPermissionFlags: Int = 0,
    ): List<RecentDocument> = recordOpenedWithEvictions(
        context = context,
        uri = uri,
        name = name,
        openedAt = openedAt,
        managedPermissionFlags = managedPermissionFlags,
    ).documents

    fun recordOpenedWithEvictions(
        context: Context,
        uri: Uri,
        name: String,
        openedAt: Long = System.currentTimeMillis(),
        managedPermissionFlags: Int = 0,
    ): RecentDocumentsMutation {
        val existing = load(context)
        val mutation = updateRecentDocuments(
            existing = existing,
            uri = uri,
            name = name,
            openedAt = openedAt,
            managedPermissionFlags = managedPermissionFlags,
            maximumDocuments = maximumDocuments,
        )
        save(context, mutation.documents)
        return mutation
    }

    fun updatePosition(
        context: Context,
        uri: Uri,
        scrollY: Int,
    ): List<RecentDocument> {
        val existing = load(context)
        if (existing.none { it.uri == uri }) return existing

        val updated = existing.map { document ->
            if (document.uri == uri) {
                document.copy(scrollY = scrollY.coerceAtLeast(0))
            } else {
                document
            }
        }
        save(context, updated)
        return updated
    }

    fun remove(context: Context, uri: Uri): List<RecentDocument> {
        val updated = load(context).filterNot { it.uri == uri }
        save(context, updated)
        return updated
    }

    private fun save(context: Context, documents: List<RecentDocument>) {
        val array = JSONArray()
        documents.take(maximumDocuments).forEach { document ->
            array.put(
                JSONObject()
                    .put("uri", document.uri.toString())
                    .put("name", document.name)
                    .put("lastOpenedAt", document.lastOpenedAt)
                    .put("scrollY", document.scrollY)
                    .put("managedPermissionFlags", document.managedPermissionFlags),
            )
        }

        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit {
                putString(documentsKey, array.toString())
            }
    }
}
