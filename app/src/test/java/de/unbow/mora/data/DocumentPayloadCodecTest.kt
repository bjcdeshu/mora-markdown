package de.unbow.mora.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Arrays
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPayloadCodecTest {

    @Test
    fun `known oversized payload is rejected before opening its stream`() {
        var opened = false

        val failure = assertThrows(DocumentAccessException::class.java) {
            DocumentPayloadCodec.decodeUtf8(
                knownSizeBytes = MAX_DOCUMENT_PAYLOAD_BYTES.toLong() + 1,
                openInputStream = {
                    opened = true
                    ByteArrayInputStream(byteArrayOf())
                },
            )
        }

        assertEquals(DocumentFailure.FILE_TOO_LARGE, failure.failure)
        assertFalse(opened)
    }

    @Test
    fun `unknown payload at exactly five MiB succeeds`() {
        val input = RepeatingByteInputStream(MAX_DOCUMENT_PAYLOAD_BYTES + 1)

        val decoded = DocumentPayloadCodec.decodeUtf8(
            knownSizeBytes = null,
            openInputStream = {
                input.limit = MAX_DOCUMENT_PAYLOAD_BYTES
                input
            },
        )

        assertEquals(MAX_DOCUMENT_PAYLOAD_BYTES, decoded.content.length)
        assertEquals(MAX_DOCUMENT_PAYLOAD_BYTES.toLong(), decoded.version.byteCount)
        assertFalse(decoded.hasUtf8Bom)
        assertEquals(MAX_DOCUMENT_PAYLOAD_BYTES, input.bytesRead)
        assertTrue(input.closed)
    }

    @Test
    fun `unknown payload reads only limit plus one before rejecting`() {
        val input = RepeatingByteInputStream(MAX_DOCUMENT_PAYLOAD_BYTES + 100)

        val failure = assertThrows(DocumentAccessException::class.java) {
            DocumentPayloadCodec.decodeUtf8(null) { input }
        }

        assertEquals(DocumentFailure.FILE_TOO_LARGE, failure.failure)
        assertEquals(MAX_DOCUMENT_PAYLOAD_BYTES + 1, input.bytesRead)
        assertTrue(input.closed)
    }

    @Test
    fun `underreported payload is still bounded while streaming`() {
        val input = RepeatingByteInputStream(MAX_DOCUMENT_PAYLOAD_BYTES + 100)

        val failure = assertThrows(DocumentAccessException::class.java) {
            DocumentPayloadCodec.decodeUtf8(knownSizeBytes = 1L) { input }
        }

        assertEquals(DocumentFailure.FILE_TOO_LARGE, failure.failure)
        assertEquals(MAX_DOCUMENT_PAYLOAD_BYTES + 1, input.bytesRead)
    }

    @Test
    fun `strict decoding handles multibyte characters split across reads`() {
        val expected = "Mora 中文 🌙"
        val rawBytes = expected.toByteArray(Charsets.UTF_8)
        val oneByteAtATime = object : FilterInputStream(ByteArrayInputStream(rawBytes)) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                return super.read(bytes, offset, minOf(length, 1))
            }
        }

        val decoded = DocumentPayloadCodec.decodeUtf8(rawBytes.size.toLong()) {
            oneByteAtATime
        }

        assertEquals(expected, decoded.content)
        assertEquals(DocumentPayloadCodec.versionOf(rawBytes), decoded.version)
    }

    @Test
    fun `empty file and literal replacement character remain valid UTF-8`() {
        val empty = DocumentPayloadCodec.decodeUtf8(0L) {
            ByteArrayInputStream(byteArrayOf())
        }
        val replacement = "\uFFFD"
        val replacementBytes = replacement.toByteArray(Charsets.UTF_8)
        val decodedReplacement = DocumentPayloadCodec.decodeUtf8(
            replacementBytes.size.toLong(),
        ) {
            ByteArrayInputStream(replacementBytes)
        }

        assertEquals("", empty.content)
        assertFalse(empty.hasUtf8Bom)
        assertEquals(replacement, decodedReplacement.content)
    }

    @Test
    fun `leading UTF-8 BOM is metadata and round trips losslessly`() {
        val content = "# 文档"
        val rawBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            content.toByteArray(Charsets.UTF_8)

        val decoded = DocumentPayloadCodec.decodeUtf8(rawBytes.size.toLong()) {
            ByteArrayInputStream(rawBytes)
        }

        assertEquals(content, decoded.content)
        assertTrue(decoded.hasUtf8Bom)
        assertEquals(DocumentPayloadCodec.versionOf(rawBytes), decoded.version)
        assertArrayEquals(rawBytes, DocumentPayloadCodec.encodeUtf8(content, includeUtf8Bom = true))
    }

    @Test
    fun `malformed UTF-8 is rejected with its dedicated failure`() {
        val malformed = byteArrayOf(0xC3.toByte(), 0x28)

        val failure = assertThrows(DocumentAccessException::class.java) {
            DocumentPayloadCodec.decodeUtf8(malformed.size.toLong()) {
                ByteArrayInputStream(malformed)
            }
        }

        assertEquals(DocumentFailure.MALFORMED_UTF8, failure.failure)
        assertNull(failure.message)
    }

    @Test
    fun `truncated UTF-8 is rejected with its dedicated failure`() {
        val truncated = byteArrayOf(0xE2.toByte(), 0x82.toByte())

        val failure = assertThrows(DocumentAccessException::class.java) {
            DocumentPayloadCodec.decodeUtf8(truncated.size.toLong()) {
                ByteArrayInputStream(truncated)
            }
        }

        assertEquals(DocumentFailure.MALFORMED_UTF8, failure.failure)
    }

    @Test
    fun `encoded size is exact for ASCII BMP and supplementary characters`() {
        assertEquals(7, DocumentPayloadCodec.encodedSize("aé🌙"))
        assertEquals(10, DocumentPayloadCodec.encodedSize("aé🌙", includeUtf8Bom = true))
    }

    @Test
    fun `output at exactly five MiB succeeds including optional BOM`() {
        val withoutBom = "a".repeat(MAX_DOCUMENT_PAYLOAD_BYTES)
        val withBom = "a".repeat(MAX_DOCUMENT_PAYLOAD_BYTES - 3)

        assertEquals(
            MAX_DOCUMENT_PAYLOAD_BYTES,
            DocumentPayloadCodec.encodeUtf8(withoutBom).size,
        )
        val bomBytes = DocumentPayloadCodec.encodeUtf8(withBom, includeUtf8Bom = true)
        assertEquals(MAX_DOCUMENT_PAYLOAD_BYTES, bomBytes.size)
        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            bomBytes.copyOfRange(0, 3),
        )
    }

    @Test
    fun `oversized output is rejected before opening target stream`() {
        var opened = false

        val failure = assertThrows(DocumentAccessException::class.java) {
            DocumentPayloadCodec.writeUtf8(
                content = "a".repeat(MAX_DOCUMENT_PAYLOAD_BYTES + 1),
                openOutputStream = {
                    opened = true
                    ByteArrayOutputStream()
                },
            )
        }

        assertEquals(DocumentFailure.FILE_TOO_LARGE, failure.failure)
        assertFalse(opened)
    }

    @Test
    fun `BOM counts toward output limit before opening target stream`() {
        var opened = false

        val failure = assertThrows(DocumentAccessException::class.java) {
            DocumentPayloadCodec.writeUtf8(
                content = "a".repeat(MAX_DOCUMENT_PAYLOAD_BYTES - 2),
                includeUtf8Bom = true,
                openOutputStream = {
                    opened = true
                    ByteArrayOutputStream()
                },
            )
        }

        assertEquals(DocumentFailure.FILE_TOO_LARGE, failure.failure)
        assertFalse(opened)
    }

    @Test
    fun `write returns the version of exact bytes sent to output`() {
        val output = ByteArrayOutputStream()
        val version = DocumentPayloadCodec.writeUtf8(
            content = "Mora",
            includeUtf8Bom = true,
            openOutputStream = { output },
        )

        assertEquals(DocumentPayloadCodec.versionOf(output.toByteArray()), version)
        assertEquals(7L, version.byteCount)
    }

    @Test
    fun `document version hashes raw bytes and includes byte count`() {
        val version = DocumentPayloadCodec.versionOf(byteArrayOf())

        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb924" +
                "27ae41e4649b934ca495991b7852b855",
            version.sha256,
        )
        assertEquals(0L, version.byteCount)
    }

    private class RepeatingByteInputStream(initialLimit: Int) : InputStream() {
        var limit: Int = initialLimit
        var bytesRead: Int = 0
            private set
        var closed: Boolean = false
            private set

        override fun read(): Int {
            if (bytesRead >= limit) return -1
            bytesRead += 1
            return 'a'.code
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (bytesRead >= limit) return -1
            val count = minOf(length, limit - bytesRead)
            Arrays.fill(bytes, offset, offset + count, 'a'.code.toByte())
            bytesRead += count
            return count
        }

        override fun close() {
            closed = true
        }
    }
}
