package de.unbow.mora.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPositionMarkerTest {

    @Test
    fun nonScrollableContentDoesNotShowMarker() {
        val marker = calculateReaderPositionMarker(
            offset = 0,
            range = 1_000,
            extent = 1_000,
        )

        assertFalse(marker.isScrollable)
        assertEquals(0f, marker.progress)
    }

    @Test
    fun markerMapsOffsetAcrossScrollableRange() {
        val marker = calculateReaderPositionMarker(
            offset = 1_000,
            range = 3_000,
            extent = 1_000,
        )

        assertTrue(marker.isScrollable)
        assertEquals(0.5f, marker.progress, 0.0001f)
    }

    @Test
    fun markerClampsOffsetsOutsideScrollableRange() {
        val beforeStart = calculateReaderPositionMarker(
            offset = -80,
            range = 3_000,
            extent = 1_000,
        )
        val afterEnd = calculateReaderPositionMarker(
            offset = 2_300,
            range = 3_000,
            extent = 1_000,
        )

        assertEquals(0f, beforeStart.progress)
        assertEquals(1f, afterEnd.progress)
    }

    @Test
    fun invalidScrollMetricsDoNotShowMarker() {
        assertFalse(calculateReaderPositionMarker(offset = 0, range = 0, extent = 1).isScrollable)
        assertFalse(calculateReaderPositionMarker(offset = 0, range = 1, extent = 0).isScrollable)
        assertFalse(calculateReaderPositionMarker(offset = 0, range = -1, extent = 1).isScrollable)
        assertFalse(calculateReaderPositionMarker(offset = 0, range = 1, extent = -1).isScrollable)
    }

    @Test
    fun onlyTheFixedHttpsReaderOriginIsInternal() {
        assertTrue(isInternalReaderLocation("https", "mora.invalid", "/"))
        assertTrue(isInternalReaderLocation("HTTPS", "MORA.INVALID", ""))
        assertFalse(isInternalReaderLocation("http", "mora.invalid", "/"))
        assertFalse(isInternalReaderLocation("file", "mora.invalid", "/"))
        assertFalse(isInternalReaderLocation("https", "example.com", "/"))
        assertFalse(isInternalReaderLocation("https", "mora.invalid", "/another-document"))
        assertFalse(isInternalReaderLocation(null, "mora.invalid", "/"))
    }
}
