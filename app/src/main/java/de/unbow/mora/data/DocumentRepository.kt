package de.unbow.mora.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DocumentRepository {

    data class LoadedDocument(
        val name: String,
        val content: String,
        val canWrite: Boolean,
    )

    suspend fun read(
        context: Context,
        uri: Uri,
    ): LoadedDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val content = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText()
        }?.removePrefix("\uFEFF") ?: error("文件没有提供可读取的内容")

        LoadedDocument(
            name = queryDisplayName(context, uri) ?: "文档.md",
            content = content,
            canWrite = canWrite(context, uri),
        )
    }

    suspend fun write(context: Context, uri: Uri, content: String) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val stream = runCatching {
            resolver.openOutputStream(uri, "rwt")
        }.getOrNull()
            ?: resolver.openOutputStream(uri, "wt")
            ?: error("无法写入这个文件")

        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(content)
            writer.flush()
        }
    }

    suspend fun displayName(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        queryDisplayName(context, uri)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    fun persistPermission(context: Context, uri: Uri, grantedFlags: Int? = null) {
        val resolver = context.contentResolver
        val supportedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val requestedFlags = grantedFlags?.and(supportedFlags)
            ?.takeIf { it != 0 }
            ?: supportedFlags

        try {
            resolver.takePersistableUriPermission(uri, requestedFlags)
        } catch (_: Exception) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
                // 有些文档提供者不支持持久授权；当前会话仍可继续使用。
            }
        }
    }

    private fun canWrite(context: Context, uri: Uri): Boolean {
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> context.checkUriPermission(
                uri,
                Process.myPid(),
                Process.myUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ) == PackageManager.PERMISSION_GRANTED

            ContentResolver.SCHEME_FILE -> uri.path
                ?.let(::File)
                ?.canWrite()
                ?: false

            else -> false
        }
    }
}
