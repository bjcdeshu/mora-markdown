package de.unbow.mora.data

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal const val MAX_RECOVERY_URI_BYTES = 16 * 1024
internal const val MAX_RECOVERY_NAME_BYTES = 4 * 1024

internal fun sanitizeRecoveryDocumentName(value: String): String {
    val sanitized = StringBuilder(minOf(value.length, MAX_RECOVERY_NAME_BYTES))
    var byteCount = 0
    var index = 0
    while (index < value.length) {
        val current = value[index]
        val (characterCount, encodedSize) = when {
            current.code <= 0x7F -> 1 to 1
            current.code <= 0x7FF -> 1 to 2
            Character.isHighSurrogate(current) &&
                index + 1 < value.length &&
                Character.isLowSurrogate(value[index + 1]) -> 2 to 4
            Character.isHighSurrogate(current) || Character.isLowSurrogate(current) -> 0 to 3
            else -> 1 to 3
        }
        if (byteCount > MAX_RECOVERY_NAME_BYTES - encodedSize) break
        if (characterCount == 0) {
            sanitized.append('\uFFFD')
            index += 1
        } else {
            sanitized.append(value, index, index + characterCount)
            index += characterCount
        }
        byteCount += encodedSize
    }
    return sanitized.toString()
}

internal fun isRecoverySourceUriSupported(value: String): Boolean {
    var byteCount = 0
    var index = 0
    while (index < value.length) {
        val current = value[index]
        val (characterCount, encodedSize) = when {
            current.code <= 0x7F -> 1 to 1
            current.code <= 0x7FF -> 1 to 2
            Character.isHighSurrogate(current) &&
                index + 1 < value.length &&
                Character.isLowSurrogate(value[index + 1]) -> 2 to 4
            Character.isHighSurrogate(current) || Character.isLowSurrogate(current) -> return false
            else -> 1 to 3
        }
        if (byteCount > MAX_RECOVERY_URI_BYTES - encodedSize) return false
        byteCount += encodedSize
        index += characterCount
    }
    return true
}

data class DirtyDocumentRecovery(
    val recoveryId: String,
    val sourceUri: String?,
    val name: String,
    val managedPermissionFlags: Int,
    val baseVersion: DocumentVersion?,
    val contentRevision: Long,
    val recoveryGeneration: Long,
    val hasUtf8Bom: Boolean,
    val savedAt: Long,
    val content: String,
    val isOriginalBackup: Boolean = false,
)

data class PendingWriteRecovery(
    val recoveryId: String,
    val sourceUri: String,
    val name: String,
    val managedPermissionFlags: Int,
    val baseVersion: DocumentVersion,
    val intendedVersion: DocumentVersion,
    val contentRevision: Long,
    val recoveryGeneration: Long,
    val savedAt: Long,
    val previousBytes: ByteArray,
    val intendedBytes: ByteArray,
    val intendedDiscarded: Boolean = false,
) {
    override fun equals(other: Any?): Boolean = other is PendingWriteRecovery &&
        recoveryId == other.recoveryId &&
        sourceUri == other.sourceUri &&
        name == other.name &&
        managedPermissionFlags == other.managedPermissionFlags &&
        baseVersion == other.baseVersion &&
        intendedVersion == other.intendedVersion &&
        contentRevision == other.contentRevision &&
        recoveryGeneration == other.recoveryGeneration &&
        savedAt == other.savedAt &&
        previousBytes.contentEquals(other.previousBytes) &&
        intendedBytes.contentEquals(other.intendedBytes) &&
        intendedDiscarded == other.intendedDiscarded

    override fun hashCode(): Int {
        var result = recoveryId.hashCode()
        result = 31 * result + sourceUri.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + managedPermissionFlags
        result = 31 * result + baseVersion.hashCode()
        result = 31 * result + intendedVersion.hashCode()
        result = 31 * result + contentRevision.hashCode()
        result = 31 * result + recoveryGeneration.hashCode()
        result = 31 * result + savedAt.hashCode()
        result = 31 * result + previousBytes.contentHashCode()
        result = 31 * result + intendedBytes.contentHashCode()
        result = 31 * result + intendedDiscarded.hashCode()
        return result
    }
}

class DocumentRecoveryRepository private constructor(
    private val recoveryDirectory: File,
) {

    private val directoryState = stateFor(recoveryDirectory)

    constructor(context: Context) : this(
        File(context.noBackupFilesDir, RECOVERY_DIRECTORY_NAME),
    )

    internal constructor(directory: File, forTesting: Boolean) : this(directory) {
        check(forTesting)
    }

    fun saveDirty(record: DirtyDocumentRecovery): Boolean = withDirectoryLock {
        val stored = record.copy(
            sourceUri = record.sourceUri?.takeIf(::isRecoverySourceUriSupported),
            name = sanitizeRecoveryDocumentName(record.name),
        )
        val clearedThrough = directoryState.clearedThroughGenerations[stored.recoveryId] ?:
            Long.MIN_VALUE
        if (stored.recoveryGeneration <= clearedThrough) return@withDirectoryLock false
        val current = loadDirty()
        if (current != null) {
            if (current.recoveryId != stored.recoveryId) return@withDirectoryLock false
            if (current.recoveryGeneration >= stored.recoveryGeneration) {
                return@withDirectoryLock false
            }
        }
        val encodedContent = DocumentPayloadCodec.encodeUtf8(
            content = stored.content,
            includeUtf8Bom = stored.hasUtf8Bom,
        )
        atomicWrite(dirtyFile) { output ->
            output.writeInt(MAGIC)
            output.writeInt(FORMAT_VERSION)
            output.writeByte(RECORD_DIRTY)
            output.writeBoundedString(stored.recoveryId, MAX_ID_BYTES)
            output.writeNullableBoundedString(stored.sourceUri, MAX_RECOVERY_URI_BYTES)
            output.writeBoundedString(stored.name, MAX_RECOVERY_NAME_BYTES)
            output.writeInt(stored.managedPermissionFlags)
            output.writeNullableVersion(stored.baseVersion)
            output.writeLong(stored.contentRevision)
            output.writeLong(stored.recoveryGeneration)
            output.writeBoolean(stored.hasUtf8Bom)
            output.writeLong(stored.savedAt)
            output.writeBoundedBytes(encodedContent, MAX_DOCUMENT_PAYLOAD_BYTES)
            output.writeBoolean(stored.isOriginalBackup)
        }
        true
    }

    fun loadDirty(): DirtyDocumentRecovery? = withDirectoryLock {
        readSafely(dirtyFile) { input ->
            input.requireHeader(RECORD_DIRTY)
            val recoveryId = input.readBoundedString(MAX_ID_BYTES)
            val sourceUri = input.readNullableBoundedString(MAX_RECOVERY_URI_BYTES)
            val name = input.readBoundedString(MAX_RECOVERY_NAME_BYTES)
            val managedPermissionFlags = input.readInt()
            val baseVersion = input.readNullableVersion()
            val contentRevision = input.readLong().also { require(it >= 0L) }
            val recoveryGeneration = input.readLong().also { require(it >= 0L) }
            val hasUtf8Bom = input.readBoolean()
            val savedAt = input.readLong().also { require(it >= 0L) }
            val encodedContent = input.readBoundedBytes(MAX_DOCUMENT_PAYLOAD_BYTES)
            val isOriginalBackup = input.readBoolean()
            val payload = DocumentPayloadCodec.decodeUtf8(
                knownSizeBytes = encodedContent.size.toLong(),
                openInputStream = { ByteArrayInputStream(encodedContent) },
            )
            require(payload.hasUtf8Bom == hasUtf8Bom)
            input.requireEndOfRecord()
            DirtyDocumentRecovery(
                recoveryId = recoveryId,
                sourceUri = sourceUri,
                name = name,
                managedPermissionFlags = managedPermissionFlags,
                baseVersion = baseVersion,
                contentRevision = contentRevision,
                recoveryGeneration = recoveryGeneration,
                hasUtf8Bom = hasUtf8Bom,
                savedAt = savedAt,
                content = payload.content,
                isOriginalBackup = isOriginalBackup,
            )
        }
    }

    fun clearDirty(
        recoveryId: String,
        contentRevision: Long? = null,
        recoveryGeneration: Long? = null,
    ): Boolean = withDirectoryLock {
        val current = loadDirty() ?: return@withDirectoryLock false
        if (current.recoveryId != recoveryId) return@withDirectoryLock false
        if (contentRevision != null && current.contentRevision != contentRevision) {
            return@withDirectoryLock false
        }
        if (
            recoveryGeneration != null &&
            current.recoveryGeneration != recoveryGeneration
        ) {
            return@withDirectoryLock false
        }
        directoryState.clearedThroughGenerations[recoveryId] = maxOf(
            directoryState.clearedThroughGenerations[recoveryId] ?: Long.MIN_VALUE,
            recoveryGeneration ?: current.recoveryGeneration,
        )
        !dirtyFile.exists() || dirtyFile.delete()
    }

    fun clearDirtyAtOrBefore(recoveryId: String, maximumGeneration: Long): Boolean =
        withDirectoryLock {
            directoryState.clearedThroughGenerations[recoveryId] = maxOf(
                directoryState.clearedThroughGenerations[recoveryId] ?: Long.MIN_VALUE,
                maximumGeneration,
            )
            val current = loadDirty() ?: return@withDirectoryLock false
            if (current.recoveryId != recoveryId) return@withDirectoryLock false
            if (current.recoveryGeneration > maximumGeneration) return@withDirectoryLock false
            !dirtyFile.exists() || dirtyFile.delete()
        }

    fun savePendingWrite(record: PendingWriteRecovery): PendingWriteRecovery = withDirectoryLock {
        require(isRecoverySourceUriSupported(record.sourceUri)) {
            "The pending-write source URI is not safely recoverable."
        }
        require(DocumentPayloadCodec.versionOf(record.previousBytes) == record.baseVersion)
        require(DocumentPayloadCodec.versionOf(record.intendedBytes) == record.intendedVersion)
        val current = loadPendingWrite()
        val stored = if (current == null) {
            record.copy(
                name = sanitizeRecoveryDocumentName(record.name),
                intendedDiscarded = false,
            )
        } else {
            require(current.recoveryId == record.recoveryId) {
                "A different document already owns the pending-write backup."
            }
            require(current.sourceUri == record.sourceUri) {
                "The pending-write backup belongs to a different source."
            }
            require(record.contentRevision >= current.contentRevision) {
                "An older save cannot replace the pending-write intent."
            }
            require(record.recoveryGeneration >= current.recoveryGeneration) {
                "An older recovery generation cannot replace the pending-write intent."
            }
            record.copy(
                name = sanitizeRecoveryDocumentName(record.name),
                managedPermissionFlags = current.managedPermissionFlags or
                    record.managedPermissionFlags,
                baseVersion = current.baseVersion,
                previousBytes = current.previousBytes,
                intendedDiscarded = false,
            )
        }
        writePendingRecord(stored)
        stored
    }

    private fun writePendingRecord(record: PendingWriteRecovery) {
        atomicWrite(pendingWriteFile) { output ->
            output.writeInt(MAGIC)
            output.writeInt(FORMAT_VERSION)
            output.writeByte(RECORD_PENDING_WRITE)
            output.writeBoundedString(record.recoveryId, MAX_ID_BYTES)
            output.writeBoundedString(record.sourceUri, MAX_RECOVERY_URI_BYTES)
            output.writeBoundedString(record.name, MAX_RECOVERY_NAME_BYTES)
            output.writeInt(record.managedPermissionFlags)
            output.writeVersion(record.baseVersion)
            output.writeVersion(record.intendedVersion)
            output.writeLong(record.contentRevision)
            output.writeLong(record.recoveryGeneration)
            output.writeLong(record.savedAt)
            output.writeBoundedBytes(record.previousBytes, MAX_DOCUMENT_PAYLOAD_BYTES)
            output.writeBoundedBytes(record.intendedBytes, MAX_DOCUMENT_PAYLOAD_BYTES)
            output.writeBoolean(record.intendedDiscarded)
        }
    }

    fun loadPendingWrite(): PendingWriteRecovery? = withDirectoryLock {
        readSafely(pendingWriteFile) { input ->
            input.requireHeader(RECORD_PENDING_WRITE)
            val recoveryId = input.readBoundedString(MAX_ID_BYTES)
            val sourceUri = input.readBoundedString(MAX_RECOVERY_URI_BYTES)
            val name = input.readBoundedString(MAX_RECOVERY_NAME_BYTES)
            val managedPermissionFlags = input.readInt()
            val baseVersion = input.readVersion()
            val intendedVersion = input.readVersion()
            val contentRevision = input.readLong().also { require(it >= 0L) }
            val recoveryGeneration = input.readLong().also { require(it >= 0L) }
            val savedAt = input.readLong().also { require(it >= 0L) }
            val previousBytes = input.readBoundedBytes(MAX_DOCUMENT_PAYLOAD_BYTES)
            val intendedBytes = input.readBoundedBytes(MAX_DOCUMENT_PAYLOAD_BYTES)
            val intendedDiscarded = input.readBoolean()
            input.requireEndOfRecord()
            require(DocumentPayloadCodec.versionOf(previousBytes) == baseVersion)
            require(DocumentPayloadCodec.versionOf(intendedBytes) == intendedVersion)
            PendingWriteRecovery(
                recoveryId = recoveryId,
                sourceUri = sourceUri,
                name = name,
                managedPermissionFlags = managedPermissionFlags,
                baseVersion = baseVersion,
                intendedVersion = intendedVersion,
                contentRevision = contentRevision,
                recoveryGeneration = recoveryGeneration,
                savedAt = savedAt,
                previousBytes = previousBytes,
                intendedBytes = intendedBytes,
                intendedDiscarded = intendedDiscarded,
            )
        }
    }

    fun clearPendingWrite(
        sourceUri: String,
        intendedVersion: DocumentVersion? = null,
    ): Boolean = withDirectoryLock {
        val current = loadPendingWrite() ?: return@withDirectoryLock false
        if (current.sourceUri != sourceUri) return@withDirectoryLock false
        if (intendedVersion != null && current.intendedVersion != intendedVersion) {
            return@withDirectoryLock false
        }
        !pendingWriteFile.exists() || pendingWriteFile.delete()
    }

    fun clearPendingWriteForRecovery(recoveryId: String): Boolean = withDirectoryLock {
        val current = loadPendingWrite() ?: return@withDirectoryLock false
        if (current.recoveryId != recoveryId) return@withDirectoryLock false
        !pendingWriteFile.exists() || pendingWriteFile.delete()
    }

    fun markPendingIntentDiscarded(recoveryId: String): DirtyDocumentRecovery? = withDirectoryLock {
        val current = loadPendingWrite() ?: return@withDirectoryLock null
        if (current.recoveryId != recoveryId) return@withDirectoryLock null
        if (!current.intendedDiscarded) {
            writePendingRecord(current.copy(intendedDiscarded = true))
        }
        recoverPendingPrevious()
    }

    fun deleteOriginalBackup(recoveryId: String): Boolean = withDirectoryLock {
        val current = loadPendingWrite() ?: return@withDirectoryLock false
        if (current.recoveryId != recoveryId || !current.intendedDiscarded) {
            return@withDirectoryLock false
        }
        !pendingWriteFile.exists() || pendingWriteFile.delete()
    }

    fun referencedUriStrings(): Set<String> = withDirectoryLock {
        buildSet {
            loadDirty()?.sourceUri?.let(::add)
            loadPendingWrite()?.sourceUri?.let(::add)
        }
    }

    fun recoverPendingIntent(): DirtyDocumentRecovery? = withDirectoryLock {
        val pending = loadPendingWrite() ?: return@withDirectoryLock null
        pending.toDirtyRecovery(pending.intendedBytes, isOriginalBackup = false)
    }

    fun recoverPendingPrevious(): DirtyDocumentRecovery? = withDirectoryLock {
        val pending = loadPendingWrite() ?: return@withDirectoryLock null
        pending.toDirtyRecovery(pending.previousBytes, isOriginalBackup = true)
    }

    fun recoverPendingAvailable(): DirtyDocumentRecovery? = withDirectoryLock {
        val pending = loadPendingWrite() ?: return@withDirectoryLock null
        val bytes = if (pending.intendedDiscarded) {
            pending.previousBytes
        } else {
            pending.intendedBytes
        }
        pending.toDirtyRecovery(
            bytes = bytes,
            isOriginalBackup = pending.intendedDiscarded,
        )
    }

    private inline fun <T> withDirectoryLock(action: () -> T): T =
        synchronized(directoryState.monitor, action)

    private fun PendingWriteRecovery.toDirtyRecovery(
        bytes: ByteArray,
        isOriginalBackup: Boolean,
    ): DirtyDocumentRecovery {
        val payload = DocumentPayloadCodec.decodeUtf8(
            knownSizeBytes = bytes.size.toLong(),
            openInputStream = { ByteArrayInputStream(bytes) },
        )
        return DirtyDocumentRecovery(
            recoveryId = recoveryId,
            sourceUri = sourceUri,
            name = name,
            managedPermissionFlags = managedPermissionFlags,
            baseVersion = baseVersion,
            contentRevision = contentRevision,
            recoveryGeneration = recoveryGeneration,
            hasUtf8Bom = payload.hasUtf8Bom,
            savedAt = savedAt,
            content = payload.content,
            isOriginalBackup = isOriginalBackup,
        )
    }

    private inline fun <T> readSafely(file: File, read: (DataInputStream) -> T): T? {
        if (!file.isFile) return null
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use(read)
        }.getOrNull()
    }

    private fun atomicWrite(
        destination: File,
        write: (DataOutputStream) -> Unit,
    ) {
        check(recoveryDirectory.exists() || recoveryDirectory.mkdirs()) {
            "Recovery directory is unavailable."
        }
        val temporary = File.createTempFile(destination.name, ".tmp", recoveryDirectory)
        try {
            FileOutputStream(temporary).use { fileOutput ->
                DataOutputStream(BufferedOutputStream(fileOutput)).use { output ->
                    write(output)
                    output.flush()
                    fileOutput.fd.sync()
                }
            }
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private val dirtyFile: File
        get() = File(recoveryDirectory, DIRTY_FILE_NAME)

    private val pendingWriteFile: File
        get() = File(recoveryDirectory, PENDING_WRITE_FILE_NAME)

    companion object {
        private class RecoveryDirectoryState {
            val monitor = Any()
            val clearedThroughGenerations = mutableMapOf<String, Long>()
        }

        private val directoryStates = mutableMapOf<String, RecoveryDirectoryState>()

        private fun stateFor(directory: File): RecoveryDirectoryState {
            val key = runCatching { directory.canonicalPath }
                .getOrElse { directory.absoluteFile.normalize().path }
            return synchronized(directoryStates) {
                directoryStates.getOrPut(key, ::RecoveryDirectoryState)
            }
        }

        private const val RECOVERY_DIRECTORY_NAME = "document-recovery"
        private const val DIRTY_FILE_NAME = "dirty-v2.bin"
        private const val PENDING_WRITE_FILE_NAME = "pending-write-v2.bin"
        private const val MAGIC = 0x4D4F5241
        private const val FORMAT_VERSION = 2
        private const val RECORD_DIRTY = 1
        private const val RECORD_PENDING_WRITE = 2
        private const val MAX_ID_BYTES = 256
    }
}

private fun DataOutputStream.writeNullableVersion(version: DocumentVersion?) {
    writeBoolean(version != null)
    if (version != null) writeVersion(version)
}

private fun DataOutputStream.writeVersion(version: DocumentVersion) {
    writeBoundedString(version.sha256, 128)
    writeLong(version.byteCount)
}

private fun DataInputStream.readNullableVersion(): DocumentVersion? =
    if (readBoolean()) readVersion() else null

private fun DataInputStream.readVersion(): DocumentVersion {
    val sha256 = readBoundedString(128)
    require(sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' })
    val byteCount = readLong().also {
        require(it in 0..MAX_DOCUMENT_PAYLOAD_BYTES.toLong())
    }
    return DocumentVersion(sha256 = sha256, byteCount = byteCount)
}

private fun DataOutputStream.writeNullableBoundedString(value: String?, maximumBytes: Int) {
    writeBoolean(value != null)
    if (value != null) writeBoundedString(value, maximumBytes)
}

private fun DataInputStream.readNullableBoundedString(maximumBytes: Int): String? =
    if (readBoolean()) readBoundedString(maximumBytes) else null

private fun DataOutputStream.writeBoundedString(value: String, maximumBytes: Int) {
    val bytes = DocumentPayloadCodec.encodeUtf8(value)
    require(bytes.size <= maximumBytes)
    writeInt(bytes.size)
    write(bytes)
}

private fun DataInputStream.readBoundedString(maximumBytes: Int): String {
    val bytes = readBoundedBytes(maximumBytes)
    return DocumentPayloadCodec.decodeUtf8(
        knownSizeBytes = bytes.size.toLong(),
        openInputStream = { ByteArrayInputStream(bytes) },
    ).content
}

private fun DataOutputStream.writeBoundedBytes(value: ByteArray, maximumBytes: Int) {
    require(value.size <= maximumBytes)
    writeInt(value.size)
    write(value)
}

private fun DataInputStream.readBoundedBytes(maximumBytes: Int): ByteArray {
    val size = readInt()
    require(size in 0..maximumBytes)
    return ByteArray(size).also(::readFully)
}

private fun DataInputStream.requireHeader(expectedType: Int) {
    require(readInt() == 0x4D4F5241)
    require(readInt() == 2)
    require(readUnsignedByte() == expectedType)
}

private fun DataInputStream.requireEndOfRecord() {
    if (read() != -1) throw IllegalArgumentException("Unexpected recovery data.")
}
