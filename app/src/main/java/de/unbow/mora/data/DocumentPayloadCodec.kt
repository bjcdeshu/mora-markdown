package de.unbow.mora.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.MalformedInputException
import java.security.MessageDigest

internal const val MAX_DOCUMENT_PAYLOAD_BYTES: Int = 5 * 1024 * 1024

data class DocumentVersion(
    val sha256: String,
    val byteCount: Long,
)

internal data class DecodedDocumentPayload(
    val content: String,
    val hasUtf8Bom: Boolean,
    val version: DocumentVersion,
)

/**
 * Pure document-byte handling shared by Android providers and JVM tests.
 *
 * The payload limit includes an optional UTF-8 BOM. Reads never consume more
 * than [MAX_DOCUMENT_PAYLOAD_BYTES] plus one byte, even when provider metadata
 * is absent or incorrect.
 */
internal object DocumentPayloadCodec {

    private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun decodeUtf8(
        knownSizeBytes: Long?,
        openInputStream: () -> InputStream,
    ): DecodedDocumentPayload {
        rejectOversizedKnownSize(knownSizeBytes)

        val rawBytes = openInputStream().use { input ->
            readBounded(input, knownSizeBytes)
        }
        val hasUtf8Bom = rawBytes.startsWithUtf8Bom()
        val contentOffset = if (hasUtf8Bom) utf8Bom.size else 0
        val content = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(
                    ByteBuffer.wrap(
                        rawBytes,
                        contentOffset,
                        rawBytes.size - contentOffset,
                    ),
                )
                .toString()
        } catch (error: CharacterCodingException) {
            throw DocumentAccessException(DocumentFailure.MALFORMED_UTF8, error)
        }

        return DecodedDocumentPayload(
            content = content,
            hasUtf8Bom = hasUtf8Bom,
            version = versionOf(rawBytes),
        )
    }

    /** Returns the exact UTF-8 payload size, including [includeUtf8Bom]. */
    fun encodedSize(content: String, includeUtf8Bom: Boolean = false): Int {
        var byteCount = if (includeUtf8Bom) utf8Bom.size else 0
        var index = 0

        while (index < content.length) {
            val current = content[index]
            val encodedCodePointSize = when {
                current.code <= 0x7F -> 1
                current.code <= 0x7FF -> 2
                Character.isHighSurrogate(current) -> {
                    if (
                        index + 1 >= content.length ||
                        !Character.isLowSurrogate(content[index + 1])
                    ) {
                        throw DocumentAccessException(
                            DocumentFailure.WRITE_FAILED,
                            MalformedInputException(1),
                        )
                    }
                    index += 1
                    4
                }

                Character.isLowSurrogate(current) -> throw DocumentAccessException(
                    DocumentFailure.WRITE_FAILED,
                    MalformedInputException(1),
                )

                else -> 3
            }

            if (byteCount > MAX_DOCUMENT_PAYLOAD_BYTES - encodedCodePointSize) {
                throw DocumentAccessException(DocumentFailure.FILE_TOO_LARGE)
            }
            byteCount += encodedCodePointSize
            index += 1
        }

        return byteCount
    }

    /** Encodes strictly after validating the complete output size. */
    fun encodeUtf8(content: String, includeUtf8Bom: Boolean = false): ByteArray {
        val output = ByteBuffer.allocate(encodedSize(content, includeUtf8Bom))
        if (includeUtf8Bom) output.put(utf8Bom)

        val encoder = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        try {
            val input = CharBuffer.wrap(content)
            val encodeResult = encoder.encode(input, output, true)
            if (encodeResult.isError) encodeResult.throwException()
            check(encodeResult.isUnderflow)

            val flushResult = encoder.flush(output)
            if (flushResult.isError) flushResult.throwException()
            check(flushResult.isUnderflow)
        } catch (error: CharacterCodingException) {
            throw DocumentAccessException(DocumentFailure.WRITE_FAILED, error)
        }

        check(output.position() == output.capacity())
        return output.array()
    }

    /** Validates and encodes before invoking [openOutputStream]. */
    fun writeUtf8(
        content: String,
        includeUtf8Bom: Boolean = false,
        openOutputStream: () -> OutputStream,
    ): DocumentVersion {
        val encodedBytes = encodeUtf8(content, includeUtf8Bom)
        val version = versionOf(encodedBytes)
        openOutputStream().use { output ->
            output.write(encodedBytes)
            output.flush()
        }
        return version
    }

    fun versionOf(bytes: ByteArray): DocumentVersion {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hexadecimal = CharArray(digest.size * 2)
        digest.forEachIndexed { index, value ->
            val unsigned = value.toInt() and 0xFF
            hexadecimal[index * 2] = HEX_DIGITS[unsigned ushr 4]
            hexadecimal[index * 2 + 1] = HEX_DIGITS[unsigned and 0x0F]
        }
        return DocumentVersion(
            sha256 = String(hexadecimal),
            byteCount = bytes.size.toLong(),
        )
    }

    private fun rejectOversizedKnownSize(knownSizeBytes: Long?) {
        if (knownSizeBytes != null && knownSizeBytes > MAX_DOCUMENT_PAYLOAD_BYTES) {
            throw DocumentAccessException(DocumentFailure.FILE_TOO_LARGE)
        }
    }

    private fun readBounded(input: InputStream, knownSizeBytes: Long?): ByteArray {
        val initialCapacity = knownSizeBytes
            ?.takeIf { it in 1L..MAX_DOCUMENT_PAYLOAD_BYTES.toLong() }
            ?.toInt()
            ?: DEFAULT_BUFFER_SIZE
        val collected = ByteArrayOutputStream(initialCapacity)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0

        while (totalBytes <= MAX_DOCUMENT_PAYLOAD_BYTES) {
            val remainingProbeBytes = MAX_DOCUMENT_PAYLOAD_BYTES + 1 - totalBytes
            val count = input.read(buffer, 0, minOf(buffer.size, remainingProbeBytes))
            if (count < 0) break
            if (count == 0) {
                val nextByte = input.read()
                if (nextByte < 0) break
                if (totalBytes == MAX_DOCUMENT_PAYLOAD_BYTES) {
                    throw DocumentAccessException(DocumentFailure.FILE_TOO_LARGE)
                }
                collected.write(nextByte)
                totalBytes += 1
            } else {
                if (totalBytes + count > MAX_DOCUMENT_PAYLOAD_BYTES) {
                    throw DocumentAccessException(DocumentFailure.FILE_TOO_LARGE)
                }
                collected.write(buffer, 0, count)
                totalBytes += count
            }
        }

        return collected.toByteArray()
    }

    private fun ByteArray.startsWithUtf8Bom(): Boolean {
        return size >= utf8Bom.size && utf8Bom.indices.all { index ->
            this[index] == utf8Bom[index]
        }
    }

    private const val HEX_DIGITS = "0123456789abcdef"
}
