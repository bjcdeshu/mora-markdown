package de.unbow.mora.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentRecreationStateTest {

    @Test
    fun `recreation preserves the current reading position for the same session`() {
        val current = ReaderScrollSession(
            sessionId = 42L,
            scrollY = 1_600,
        )

        assertEquals(
            current,
            resolveReaderScrollSession(
                current = current,
                hasDocument = true,
                sessionId = 42L,
                initialScrollY = 240,
            ),
        )
    }

    @Test
    fun `a new document session starts from its persisted position`() {
        assertEquals(
            ReaderScrollSession(
                sessionId = 43L,
                scrollY = 720,
            ),
            resolveReaderScrollSession(
                current = ReaderScrollSession(
                    sessionId = 42L,
                    scrollY = 1_600,
                ),
                hasDocument = true,
                sessionId = 43L,
                initialScrollY = 720,
            ),
        )
    }

    @Test
    fun `closing the document clears the reading session`() {
        assertEquals(
            ReaderScrollSession(),
            resolveReaderScrollSession(
                current = ReaderScrollSession(
                    sessionId = 42L,
                    scrollY = 1_600,
                ),
                hasDocument = false,
                sessionId = 43L,
                initialScrollY = 720,
            ),
        )
    }

    @Test
    fun `search requested before the reader is ready replays the latest query`() {
        val pendingSearch = PendingReaderSearch()
        val replayed = mutableListOf<String>()

        pendingSearch.update("first")
        pendingSearch.update("latest")
        pendingSearch.replay(replayed::add)

        assertEquals(listOf("latest"), replayed)
    }

    @Test
    fun `closing search prevents a later page load from replaying it`() {
        val pendingSearch = PendingReaderSearch()
        val replayed = mutableListOf<String>()

        pendingSearch.update("mora")
        pendingSearch.clear()
        pendingSearch.replay(replayed::add)

        assertEquals(emptyList<String>(), replayed)
    }

    @Test
    fun `a new document search session does not replay the previous query`() {
        val previousDocumentSearch = PendingReaderSearch().apply {
            update("belongs to the previous document")
        }
        val newDocumentSearch = PendingReaderSearch()
        val replayed = mutableListOf<String>()

        previousDocumentSearch.replay(replayed::add)
        newDocumentSearch.replay(replayed::add)

        assertEquals(listOf("belongs to the previous document"), replayed)
    }
}
