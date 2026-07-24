package de.unbow.mora.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPositionMarkerTest {

    @Test
    fun shortContentAndTwoPixelScrollToleranceHideMarker() {
        assertFalse(marker(range = 1_000, extent = 1_000).isScrollable)
        assertFalse(marker(range = 1_000, extent = 999).isScrollable)
        assertFalse(marker(range = 1_000, extent = 998).isScrollable)
        assertTrue(marker(range = 1_000, extent = 997).isScrollable)
    }

    @Test
    fun pageLoadForcesMarkerHiddenUntilRestoreCompletes() {
        val scrollableMarker = marker(range = 1_000, extent = 100)

        assertFalse(
            resolveReaderPositionMarkerForPageState(
                marker = scrollableMarker,
                pageLoadInProgress = true,
            ).isScrollable,
        )
        assertEquals(
            scrollableMarker,
            resolveReaderPositionMarkerForPageState(
                marker = scrollableMarker,
                pageLoadInProgress = false,
            ),
        )
    }

    @Test
    fun thumbHeightIsProportionalAndClampedToMinimumAndMaximum() {
        val proportional = marker(
            range = 1_000,
            extent = 100,
            viewportHeight = 400f,
            topInset = 20f,
            bottomInset = 20f,
            trackVerticalMargin = 20f,
        )
        val minimum = marker(
            range = 10_000,
            extent = 100,
            viewportHeight = 400f,
            topInset = 20f,
            bottomInset = 20f,
            trackVerticalMargin = 20f,
        )
        val maximum = marker(
            range = 1_000,
            extent = 500,
            viewportHeight = 400f,
            topInset = 20f,
            bottomInset = 20f,
            trackVerticalMargin = 20f,
        )

        assertEquals(320f, proportional.trackBounds.height, 0.0001f)
        assertEquals(32f, proportional.thumbBounds.height, 0.0001f)
        assertEquals(28f, minimum.thumbBounds.height, 0.0001f)
        assertEquals(64f, maximum.thumbBounds.height, 0.0001f)
    }

    @Test
    fun thumbMapsToTrackStartMiddleAndEnd() {
        val start = marker(
            offset = 0,
            range = 1_000,
            extent = 100,
            viewportHeight = 400f,
        )
        val middle = marker(
            offset = 450,
            range = 1_000,
            extent = 100,
            viewportHeight = 400f,
        )
        val end = marker(
            offset = 900,
            range = 1_000,
            extent = 100,
            viewportHeight = 400f,
        )

        assertEquals(0f, start.progress, 0.0001f)
        assertEquals(0f, start.thumbBounds.top, 0.0001f)
        assertEquals(0.5f, middle.progress, 0.0001f)
        assertEquals(180f, middle.thumbBounds.top, 0.0001f)
        assertEquals(1f, end.progress, 0.0001f)
        assertEquals(360f, end.thumbBounds.top, 0.0001f)
    }

    @Test
    fun geometryUsesViewportCoordinatesWithoutScrollOffsetDrift() {
        val geometry = marker(
            offset = 1_000,
            range = 3_000,
            extent = 1_000,
            viewportWidth = 360f,
            viewportHeight = 800f,
            topInset = 24f,
            rightInset = 8f,
            bottomInset = 32f,
            trackVerticalMargin = 12f,
            thumbRightMargin = 2f,
        )

        assertEquals(FloatRect(348f, 36f, 350f, 756f), geometry.trackBounds)
        assertTrue(geometry.thumbBounds.top in geometry.trackBounds.top..geometry.trackBounds.bottom)
        assertTrue(geometry.thumbBounds.bottom <= geometry.trackBounds.bottom)
        assertEquals(48f, geometry.hitBounds.width, 0.0001f)
        assertEquals(352f, geometry.hitBounds.right, 0.0001f)
        assertTrue(geometry.hitBounds.top >= 0f)
        assertTrue(geometry.hitBounds.bottom <= 800f)
    }

    @Test
    fun hitBoundsAreAtLeastFortyEightDpAndClampToViewportEdges() {
        val start = marker(
            offset = 0,
            range = 1_000,
            extent = 100,
            viewportWidth = 360f,
            viewportHeight = 400f,
        )
        val end = marker(
            offset = 900,
            range = 1_000,
            extent = 100,
            viewportWidth = 360f,
            viewportHeight = 400f,
        )
        val tallThumb = marker(
            range = 625,
            extent = 100,
            viewportWidth = 360f,
            viewportHeight = 400f,
        )

        assertEquals(48f, start.hitBounds.width, 0.0001f)
        assertEquals(48f, start.hitBounds.height, 0.0001f)
        assertEquals(0f, start.hitBounds.top, 0.0001f)
        assertEquals(360f, start.hitBounds.right, 0.0001f)
        assertEquals(48f, end.hitBounds.height, 0.0001f)
        assertEquals(400f, end.hitBounds.bottom, 0.0001f)
        assertEquals(64f, tallThumb.thumbBounds.height, 0.0001f)
        assertEquals(64f, tallThumb.hitBounds.height, 0.0001f)
    }

    @Test
    fun grabOffsetKeepsInitialDownFromJumping() {
        val geometry = marker(
            offset = 450,
            range = 1_000,
            extent = 100,
            viewportHeight = 400f,
        )
        val pointerY = geometry.thumbBounds.top - 4f
        assertTrue(geometry.hitBounds.contains(pointerY = pointerY, pointerX = geometry.thumbBounds.left))
        val grabOffset = calculateReaderThumbGrabOffset(pointerY, geometry)

        assertEquals(-4f, grabOffset, 0.0001f)
        assertEquals(
            450,
            calculateReaderScrollOffsetForDrag(
                pointerY = pointerY,
                grabOffset = grabOffset,
                marker = geometry,
            ),
        )
    }

    @Test
    fun dragMapsStartMiddleEndAndClampsOutOfBounds() {
        val geometry = marker(
            range = 1_000,
            extent = 100,
            viewportHeight = 400f,
        )
        val grabOffset = geometry.thumbBounds.height / 2f
        val travel = geometry.trackBounds.height - geometry.thumbBounds.height

        assertEquals(
            0,
            calculateReaderScrollOffsetForDrag(
                pointerY = geometry.trackBounds.top + grabOffset,
                grabOffset = grabOffset,
                marker = geometry,
            ),
        )
        assertEquals(
            450,
            calculateReaderScrollOffsetForDrag(
                pointerY = geometry.trackBounds.top + (travel / 2f) + grabOffset,
                grabOffset = grabOffset,
                marker = geometry,
            ),
        )
        assertEquals(
            900,
            calculateReaderScrollOffsetForDrag(
                pointerY = geometry.trackBounds.bottom - geometry.thumbBounds.height + grabOffset,
                grabOffset = grabOffset,
                marker = geometry,
            ),
        )
        assertEquals(
            0,
            calculateReaderScrollOffsetForDrag(
                pointerY = -1_000f,
                grabOffset = grabOffset,
                marker = geometry,
            ),
        )
        assertEquals(
            900,
            calculateReaderScrollOffsetForDrag(
                pointerY = 1_000f,
                grabOffset = grabOffset,
                marker = geometry,
            ),
        )
    }

    @Test
    fun zeroThumbTravelDoesNotDivideByZero() {
        val geometry = marker(
            offset = 450,
            range = 1_000,
            extent = 100,
            viewportWidth = 20f,
            viewportHeight = 20f,
        )

        assertTrue(geometry.isScrollable)
        assertEquals(20f, geometry.thumbBounds.height, 0.0001f)
        assertEquals(0f, geometry.trackBounds.height - geometry.thumbBounds.height, 0.0001f)
        assertEquals(
            450,
            calculateReaderScrollOffsetForDrag(
                pointerY = 10f,
                grabOffset = 10f,
                marker = geometry,
            ),
        )
    }

    @Test
    fun markerClampsOffsetsOutsideScrollableRange() {
        val beforeStart = marker(offset = -80, range = 3_000, extent = 1_000)
        val afterEnd = marker(offset = 2_300, range = 3_000, extent = 1_000)

        assertEquals(0f, beforeStart.progress)
        assertEquals(1f, afterEnd.progress)
    }

    @Test
    fun invalidScrollMetricsOrViewportDoNotShowMarker() {
        assertFalse(marker(range = 0, extent = 1).isScrollable)
        assertFalse(marker(range = 1, extent = 0).isScrollable)
        assertFalse(marker(range = -1, extent = 1).isScrollable)
        assertFalse(marker(range = 1, extent = -1).isScrollable)
        assertFalse(marker(range = 1_000, extent = 100, viewportWidth = 0f).isScrollable)
        assertFalse(marker(range = 1_000, extent = 100, viewportHeight = 0f).isScrollable)
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

    private fun marker(
        offset: Int = 0,
        range: Int = 3_000,
        extent: Int = 1_000,
        viewportWidth: Float = 360f,
        viewportHeight: Float = 400f,
        topInset: Float = 0f,
        rightInset: Float = 0f,
        bottomInset: Float = 0f,
        trackVerticalMargin: Float = 0f,
        thumbWidth: Float = 2f,
        thumbRightMargin: Float = 2f,
        minThumbHeight: Float = 28f,
        maxThumbHeight: Float = 64f,
        minHitSize: Float = 48f,
    ): ReaderPositionMarker = calculateReaderPositionMarker(
        offset = offset,
        range = range,
        extent = extent,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        topInset = topInset,
        rightInset = rightInset,
        bottomInset = bottomInset,
        trackVerticalMargin = trackVerticalMargin,
        thumbWidth = thumbWidth,
        thumbRightMargin = thumbRightMargin,
        minThumbHeight = minThumbHeight,
        maxThumbHeight = maxThumbHeight,
        minHitSize = minHitSize,
    )
}
