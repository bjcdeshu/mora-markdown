package de.unbow.mora.testprovider

import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.FileInputStream
import java.io.FileOutputStream

/** A deterministic, test-APK-only provider for exercising Mora's SAF boundary. */
class TestDocumentsProvider : DocumentsProvider() {

    private data class TestDocument(
        val file: File,
        val displayName: String,
        val flags: Int,
        val reportSize: Boolean,
        val reportedSizeOverride: Long?,
        val failReads: Boolean,
        val failWrites: Boolean,
        val failVerificationReadAfterWrite: Boolean,
        var pendingVerificationReadFailure: Boolean,
        val slowReadDelayMillis: Long,
        val slowReadChunkBytes: Int,
        val partialWriteFailureAfterBytes: Int?,
    )

    private val lock = Any()
    private val documents = mutableMapOf<String, TestDocument>()

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val resolvedProjection = projection ?: DEFAULT_ROOT_PROJECTION
        return MatrixCursor(resolvedProjection).apply {
            val row = newRow()
            resolvedProjection.forEach { column ->
                row.add(
                    when (column) {
                        DocumentsContract.Root.COLUMN_ROOT_ID -> ROOT_ID
                        DocumentsContract.Root.COLUMN_DOCUMENT_ID -> ROOT_DOCUMENT_ID
                        DocumentsContract.Root.COLUMN_TITLE -> "Mora test documents"
                        DocumentsContract.Root.COLUMN_FLAGS ->
                            DocumentsContract.Root.FLAG_LOCAL_ONLY
                        DocumentsContract.Root.COLUMN_ICON -> android.R.drawable.ic_menu_save
                        DocumentsContract.Root.COLUMN_MIME_TYPES -> MIME_TYPE_MARKDOWN
                        DocumentsContract.Root.COLUMN_AVAILABLE_BYTES -> Long.MAX_VALUE
                        else -> null
                    },
                )
            }
        }
    }

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ): Cursor {
        val resolvedProjection = projection ?: DEFAULT_DOCUMENT_PROJECTION
        return MatrixCursor(resolvedProjection).apply {
            if (documentId == ROOT_DOCUMENT_ID) {
                addDocumentRow(
                    projection = resolvedProjection,
                    documentId = ROOT_DOCUMENT_ID,
                    document = null,
                )
            } else {
                val document = synchronized(lock) { documents[documentId] }
                    ?: throw FileNotFoundException(documentId)
                addDocumentRow(resolvedProjection, documentId, document)
            }
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (parentDocumentId != ROOT_DOCUMENT_ID) {
            throw FileNotFoundException(parentDocumentId)
        }
        val resolvedProjection = projection ?: DEFAULT_DOCUMENT_PROJECTION
        val snapshot = synchronized(lock) { documents.toSortedMap() }
        return MatrixCursor(resolvedProjection).apply {
            snapshot.forEach { (documentId, document) ->
                addDocumentRow(resolvedProjection, documentId, document)
            }
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        signal?.throwIfCanceled()
        val document = synchronized(lock) { documents[documentId] }
            ?: throw FileNotFoundException(documentId)
        val wantsWrite = mode.any { character ->
            character == 'w' || character == 'a' || character == 't'
        }
        if (!wantsWrite) {
            val failVerificationRead = synchronized(lock) {
                if (document.pendingVerificationReadFailure) {
                    document.pendingVerificationReadFailure = false
                    true
                } else {
                    false
                }
            }
            if (document.failReads || failVerificationRead) {
                throw FileNotFoundException("Reads are disabled for $documentId")
            }
        }
        if (
            wantsWrite &&
            (document.failWrites ||
                document.flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE == 0)
        ) {
            throw FileNotFoundException("Writes are disabled for $documentId")
        }
        document.partialWriteFailureAfterBytes
            ?.takeIf { wantsWrite }
            ?.let { failureAfterBytes ->
                return openFailingWritePipe(
                    document = document,
                    failureAfterBytes = failureAfterBytes,
                    signal = signal,
                )
            }
        if (!wantsWrite && document.slowReadDelayMillis > 0L) {
            return openSlowReadPipe(document, signal)
        }
        if (wantsWrite && document.failVerificationReadAfterWrite) {
            synchronized(lock) {
                document.pendingVerificationReadFailure = true
            }
        }
        return ParcelFileDescriptor.open(document.file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        super.call(method, arg, extras)?.let { frameworkResult ->
            return frameworkResult
        }
        return when (method) {
            METHOD_CONFIGURE -> {
                configureDocument(requireDocumentId(arg), extras ?: Bundle())
                Bundle()
            }

            METHOD_GRANT -> {
                val documentId = requireDocumentId(arg)
                val grantExtras = requireNotNull(extras)
                val targetPackage = grantExtras.getString(KEY_TARGET_PACKAGE)
                    ?: error("Missing target package")
                val flags = grantExtras.getInt(KEY_GRANT_FLAGS) and URI_GRANT_FLAGS
                check(flags and ACCESS_GRANT_FLAGS != 0) {
                    "At least one read or write URI permission is required"
                }
                check(synchronized(lock) { documents.containsKey(documentId) }) {
                    "Unknown test document: $documentId"
                }
                withProviderIdentity {
                    val providerContext = checkNotNull(context)
                    val uri = documentUri(documentId)
                    providerContext.revokeUriPermission(uri, ACCESS_GRANT_FLAGS)
                    providerContext.grantUriPermission(
                        targetPackage,
                        uri,
                        flags,
                    )
                }
                Bundle()
            }

            METHOD_REVOKE -> {
                val documentId = requireDocumentId(arg)
                check(synchronized(lock) { documents.containsKey(documentId) }) {
                    "Unknown test document: $documentId"
                }
                withProviderIdentity {
                    checkNotNull(context).revokeUriPermission(
                        documentUri(documentId),
                        ACCESS_GRANT_FLAGS,
                    )
                }
                Bundle()
            }

            METHOD_RESET -> {
                resetDocuments()
                Bundle()
            }

            else -> null
        }
    }

    private fun configureDocument(documentId: String, extras: Bundle) {
        val providerContext = checkNotNull(context)
        val directory = File(providerContext.filesDir, DOCUMENT_DIRECTORY).apply {
            check(mkdirs() || isDirectory)
        }
        val file = File(directory, documentId)
        require(file.parentFile == directory && DOCUMENT_ID_PATTERN.matches(documentId)) {
            "Unsafe document id"
        }

        val repeatedByteCount = extras.getInt(KEY_REPEATED_BYTE_COUNT, -1)
        if (repeatedByteCount >= 0) {
            writeRepeatedBytes(
                file = file,
                byteCount = repeatedByteCount,
                value = extras.getInt(KEY_REPEATED_BYTE, 'a'.code).toByte(),
            )
        } else {
            file.writeBytes(extras.getByteArray(KEY_BYTES) ?: byteArrayOf())
        }

        val document = TestDocument(
            file = file,
            displayName = extras.getString(KEY_DISPLAY_NAME) ?: "$documentId.md",
            flags = extras.getInt(
                KEY_DOCUMENT_FLAGS,
                DocumentsContract.Document.FLAG_SUPPORTS_WRITE,
            ),
            reportSize = extras.getBoolean(KEY_REPORT_SIZE, true),
            reportedSizeOverride = if (extras.containsKey(KEY_REPORTED_SIZE)) {
                extras.getLong(KEY_REPORTED_SIZE)
            } else {
                null
            },
            failReads = extras.getBoolean(KEY_FAIL_READS, false),
            failWrites = extras.getBoolean(KEY_FAIL_WRITES, false),
            failVerificationReadAfterWrite = extras.getBoolean(
                KEY_FAIL_VERIFICATION_READ_AFTER_WRITE,
                false,
            ),
            pendingVerificationReadFailure = false,
            slowReadDelayMillis = extras.getLong(KEY_SLOW_READ_DELAY_MILLIS, 0L)
                .also { delay -> require(delay >= 0L) },
            slowReadChunkBytes = extras.getInt(KEY_SLOW_READ_CHUNK_BYTES, BUFFER_SIZE)
                .also { chunkBytes -> require(chunkBytes > 0) },
            partialWriteFailureAfterBytes = if (
                extras.containsKey(KEY_PARTIAL_WRITE_FAILURE_AFTER_BYTES)
            ) {
                extras.getInt(KEY_PARTIAL_WRITE_FAILURE_AFTER_BYTES)
                    .also { byteCount -> require(byteCount >= 0) }
            } else {
                null
            },
        )
        synchronized(lock) {
            documents[documentId] = document
        }
    }

    private fun resetDocuments() {
        val providerContext = checkNotNull(context)
        val documentIds = synchronized(lock) {
            documents.keys.toList().also { documents.clear() }
        }
        documentIds.forEach { documentId ->
            withProviderIdentity {
                providerContext.revokeUriPermission(
                    documentUri(documentId),
                    ACCESS_GRANT_FLAGS,
                )
            }
        }
        File(providerContext.filesDir, DOCUMENT_DIRECTORY).deleteRecursively()
    }

    private fun MatrixCursor.addDocumentRow(
        projection: Array<out String>,
        documentId: String,
        document: TestDocument?,
    ) {
        val row = newRow()
        projection.forEach { column ->
            row.add(
                when (column) {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID -> documentId
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME ->
                        document?.displayName ?: "Test documents"
                    DocumentsContract.Document.COLUMN_MIME_TYPE ->
                        document?.let { MIME_TYPE_MARKDOWN }
                            ?: DocumentsContract.Document.MIME_TYPE_DIR
                    DocumentsContract.Document.COLUMN_FLAGS -> document?.flags ?: 0
                    DocumentsContract.Document.COLUMN_SIZE -> when {
                        document == null || !document.reportSize -> null
                        document.reportedSizeOverride != null -> document.reportedSizeOverride
                        else -> document.file.length()
                    }
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED ->
                        document?.file?.lastModified()
                    else -> null
                },
            )
        }
    }

    private fun writeRepeatedBytes(file: File, byteCount: Int, value: Byte) {
        FileOutputStream(file, false).use { output ->
            val buffer = ByteArray(minOf(BUFFER_SIZE, byteCount.coerceAtLeast(1))) { value }
            var remaining = byteCount
            while (remaining > 0) {
                val count = minOf(buffer.size, remaining)
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
    }

    /**
     * Streams through a reliable pipe so every fixture controls provider latency
     * without sleeping the Binder thread that opens the document.
     */
    private fun openSlowReadPipe(
        document: TestDocument,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val (readEnd, writeEnd) = ParcelFileDescriptor.createReliablePipe()
        signal?.setOnCancelListener {
            runCatching { writeEnd.closeWithError("Slow read cancelled") }
        }
        startIoThread("slow-read-${document.file.name}") {
            try {
                FileInputStream(document.file).use { input ->
                    ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { output ->
                        val buffer = ByteArray(document.slowReadChunkBytes)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            SystemClock.sleep(document.slowReadDelayMillis)
                            output.write(buffer, 0, count)
                            output.flush()
                        }
                    }
                }
            } catch (_: Exception) {
                runCatching { writeEnd.closeWithError("Slow read consumer closed") }
            }
        }
        return readEnd
    }

    /**
     * Models providers that truncate first, persist only a prefix, and then fail.
     * A threshold of zero is the distinct truncate-without-bytes fixture.
     */
    private fun openFailingWritePipe(
        document: TestDocument,
        failureAfterBytes: Int,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val (readEnd, writeEnd) = ParcelFileDescriptor.createReliablePipe()
        signal?.setOnCancelListener {
            runCatching { readEnd.closeWithError("Partial write cancelled") }
        }
        startIoThread("partial-write-${document.file.name}") {
            try {
                FileOutputStream(document.file, false).use { target ->
                    ParcelFileDescriptor.AutoCloseInputStream(readEnd).use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var remaining = failureAfterBytes
                        while (remaining > 0) {
                            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                            if (count < 0) break
                            target.write(buffer, 0, count)
                            target.flush()
                            remaining -= count
                        }
                        readEnd.closeWithError("Deterministic partial write failure")
                    }
                }
            } catch (_: Exception) {
                runCatching { readEnd.closeWithError("Deterministic partial write failure") }
            }
        }
        return writeEnd
    }

    private fun startIoThread(name: String, block: () -> Unit) {
        Thread(block, "MoraTestProvider-$name").apply {
            isDaemon = true
            start()
        }
    }

    private fun requireDocumentId(documentId: String?): String =
        requireNotNull(documentId).also { id ->
            require(DOCUMENT_ID_PATTERN.matches(id)) { "Unsafe document id" }
        }

    private inline fun <T> withProviderIdentity(block: () -> T): T {
        val callingIdentity = Binder.clearCallingIdentity()
        return try {
            block()
        } finally {
            Binder.restoreCallingIdentity(callingIdentity)
        }
    }

    companion object {
        const val AUTHORITY = "de.unbow.mora.test.documents"
        const val METHOD_CONFIGURE = "configure"
        const val METHOD_GRANT = "grant"
        const val METHOD_REVOKE = "revoke"
        const val METHOD_RESET = "reset"

        const val KEY_BYTES = "bytes"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_DOCUMENT_FLAGS = "document_flags"
        const val KEY_FAIL_READS = "fail_reads"
        const val KEY_FAIL_WRITES = "fail_writes"
        const val KEY_FAIL_VERIFICATION_READ_AFTER_WRITE =
            "fail_verification_read_after_write"
        const val KEY_GRANT_FLAGS = "grant_flags"
        const val KEY_PARTIAL_WRITE_FAILURE_AFTER_BYTES =
            "partial_write_failure_after_bytes"
        const val KEY_REPEATED_BYTE = "repeated_byte"
        const val KEY_REPEATED_BYTE_COUNT = "repeated_byte_count"
        const val KEY_REPORTED_SIZE = "reported_size"
        const val KEY_REPORT_SIZE = "report_size"
        const val KEY_SLOW_READ_CHUNK_BYTES = "slow_read_chunk_bytes"
        const val KEY_SLOW_READ_DELAY_MILLIS = "slow_read_delay_millis"
        const val KEY_TARGET_PACKAGE = "target_package"

        private const val ROOT_ID = "test-root"
        private const val ROOT_DOCUMENT_ID = "root"
        private const val DOCUMENT_DIRECTORY = "saf-provider-documents"
        private const val MIME_TYPE_MARKDOWN = "text/markdown"
        private const val BUFFER_SIZE = 8 * 1024
        private val DOCUMENT_ID_PATTERN = Regex("[A-Za-z0-9_-]+")
        private const val ACCESS_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        private const val URI_GRANT_FLAGS = ACCESS_GRANT_FLAGS or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        fun documentUri(documentId: String): Uri =
            DocumentsContract.buildDocumentUri(AUTHORITY, documentId)
    }
}
