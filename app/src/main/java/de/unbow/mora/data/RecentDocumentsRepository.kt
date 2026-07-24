package de.unbow.mora.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class RecentDocument(
    val uri: Uri,
    val name: String,
    val lastOpenedAt: Long,
    val scrollY: Int,
)

object RecentDocumentsRepository {

    private const val preferencesName = "mora_recent_documents"
    private const val documentsKey = "documents"
    private const val maximumDocuments = 12

    fun load(context: Context): List<RecentDocument> {
        val raw = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getString(documentsKey, null)
            ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val uri = item.optString("uri").takeIf(String::isNotBlank) ?: continue
                    val name = item.optString("name").takeIf(String::isNotBlank) ?: "文档.md"
                    add(
                        RecentDocument(
                            uri = Uri.parse(uri),
                            name = name,
                            lastOpenedAt = item.optLong("lastOpenedAt"),
                            scrollY = item.optInt("scrollY").coerceAtLeast(0),
                        ),
                    )
                }
            }.sortedByDescending(RecentDocument::lastOpenedAt)
                .take(maximumDocuments)
        }.getOrDefault(emptyList())
    }

    fun recordOpened(
        context: Context,
        uri: Uri,
        name: String,
        openedAt: Long = System.currentTimeMillis(),
    ): List<RecentDocument> {
        val existing = load(context)
        val previous = existing.firstOrNull { it.uri == uri }
        val updated = buildList {
            add(
                RecentDocument(
                    uri = uri,
                    name = name,
                    lastOpenedAt = openedAt,
                    scrollY = previous?.scrollY ?: 0,
                ),
            )
            addAll(existing.filterNot { it.uri == uri })
        }.take(maximumDocuments)

        save(context, updated)
        return updated
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
                    .put("scrollY", document.scrollY),
            )
        }

        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(documentsKey, array.toString())
            .apply()
    }
}
