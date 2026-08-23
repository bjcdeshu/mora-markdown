package de.unbow.mora.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

enum class DocumentFailure {
    READ_FAILED,
    WRITE_FAILED,
    SAVE_AS_REQUIRED,
    FILE_TOO_LARGE,
    MALFORMED_UTF8,
    PERMISSION_LOST,
    RECOVERY_FAILED,
    VERIFICATION_FAILED,
}

class DocumentAccessException(
    val failure: DocumentFailure,
    cause: Throwable? = null,
) : Exception(null, cause)

object DocumentRepository {

    data class LoadedDocument(
        val name: String?,
        val content: String,
        val canWrite: Boolean,
        val hasUtf8Bom: Boolean,
        val version: DocumentVersion,
    )

    suspend fun read(
        context: Context,
        uri: Uri,
    ): LoadedDocument = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val payload = DocumentPayloadCodec.decodeUtf8(
                knownSizeBytes = queryDocumentSize(context, uri),
                openInputStream = {
                    resolver.openInputStream(uri)
                        ?: throw DocumentAccessException(DocumentFailure.READ_FAILED)
                },
            )

            LoadedDocument(
                name = queryDisplayName(context, uri),
                content = payload.content,
                canWrite = canWrite(context, uri),
                hasUtf8Bom = payload.hasUtf8Bom,
                version = payload.version,
            )
        } catch (failure: DocumentAccessException) {
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw DocumentAccessException(DocumentFailure.READ_FAILED, error)
        }
    }

    suspend fun write(
        context: Context,
        uri: Uri,
        content: String,
        includeUtf8Bom: Boolean = false,
    ): DocumentVersion = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            DocumentPayloadCodec.writeUtf8(
                content = content,
                includeUtf8Bom = includeUtf8Bom,
                openOutputStream = {
                    openWritableStream(resolver, uri)
                },
            )
        } catch (failure: DocumentAccessException) {
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw DocumentAccessException(DocumentFailure.WRITE_FAILED, error)
        }
    }

    private fun openWritableStream(resolver: ContentResolver, uri: Uri): OutputStream {
        val truncateExisting = try {
            resolver.openOutputStream(uri, "rwt")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        return truncateExisting
            ?: resolver.openOutputStream(uri, "wt")
            ?: throw DocumentAccessException(DocumentFailure.WRITE_FAILED)
    }

    private fun queryDocumentSize(context: Context, uri: Uri): Long? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path
                ?.let(::File)
                ?.takeIf(File::isFile)
                ?.length()
        }
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null

        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index < 0 || cursor.isNull(index)) {
                    null
                } else {
                    cursor.getLong(index).takeIf { size -> size >= 0L }
                }
            }
        }.getOrNull()
    }

    suspend fun displayName(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        queryDisplayName(context, uri)
    }

    suspend fun supportsInPlaceWrite(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            canWrite(context, uri)
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

    private fun canWrite(context: Context, uri: Uri): Boolean {
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                if (!DocumentPermissionManager.current(context, uri).hasSessionWrite) {
                    return false
                }
                val isDocumentUri = runCatching {
                    DocumentsContract.isDocumentUri(context, uri)
                }.getOrDefault(false)
                if (!isDocumentUri) return true

                runCatching {
                    context.contentResolver.query(
                        uri,
                        arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (!cursor.moveToFirst()) return@use false
                        val index = cursor.getColumnIndex(
                            DocumentsContract.Document.COLUMN_FLAGS,
                        )
                        index >= 0 && !cursor.isNull(index) &&
                            cursor.getInt(index) and
                            DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0
                    } ?: false
                }.getOrDefault(false)
            }

            ContentResolver.SCHEME_FILE -> uri.path
                ?.let(::File)
                ?.canWrite()
                ?: false

            else -> false
        }
    }
}
