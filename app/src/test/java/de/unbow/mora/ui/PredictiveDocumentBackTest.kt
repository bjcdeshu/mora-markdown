package de.unbow.mora.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PredictiveDocumentBackTest {

    @Test
    fun zeroProgressIsIdentity() {
        assertEquals(
            PredictiveDocumentBackTransform.Identity,
            calculatePredictiveDocumentBackTransform(
                progress = 0f,
                swipeEdge = DocumentBackSwipeEdge.LEFT,
                maximumTranslation = 24f,
                maximumCornerRadius = 28f,
            ),
        )
    }

    @Test
    fun completionReachesQuietDocumentTransform() {
        assertEquals(
            PredictiveDocumentBackTransform(
                scale = 0.96f,
                translation = 24f,
                cornerRadius = 28f,
            ),
            calculatePredictiveDocumentBackTransform(
                progress = 1f,
                swipeEdge = DocumentBackSwipeEdge.LEFT,
                maximumTranslation = 24f,
                maximumCornerRadius = 28f,
            ),
        )
    }

    @Test
    fun swipeEdgeChangesTranslationDirection() {
        val fromLeft = calculatePredictiveDocumentBackTransform(
            progress = 0.5f,
            swipeEdge = DocumentBackSwipeEdge.LEFT,
            maximumTranslation = 24f,
            maximumCornerRadius = 28f,
        )
        val fromRight = calculatePredictiveDocumentBackTransform(
            progress = 0.5f,
            swipeEdge = DocumentBackSwipeEdge.RIGHT,
            maximumTranslation = 24f,
            maximumCornerRadius = 28f,
        )

        assertEquals(12f, fromLeft.translation, 0.0001f)
        assertEquals(-12f, fromRight.translation, 0.0001f)
        assertEquals(fromLeft.scale, fromRight.scale, 0.0001f)
        assertEquals(fromLeft.cornerRadius, fromRight.cornerRadius, 0.0001f)
    }

    @Test
    fun invalidProgressAndDimensionsAreClamped() {
        assertEquals(
            PredictiveDocumentBackTransform.Identity,
            calculatePredictiveDocumentBackTransform(
                progress = Float.NaN,
                swipeEdge = DocumentBackSwipeEdge.LEFT,
                maximumTranslation = 24f,
                maximumCornerRadius = 28f,
            ),
        )
        assertEquals(
            PredictiveDocumentBackTransform(
                scale = 0.96f,
                translation = -24f,
                cornerRadius = 28f,
            ),
            calculatePredictiveDocumentBackTransform(
                progress = 2f,
                swipeEdge = DocumentBackSwipeEdge.RIGHT,
                maximumTranslation = 24f,
                maximumCornerRadius = 28f,
            ),
        )
        assertEquals(
            PredictiveDocumentBackTransform(
                scale = 0.96f,
                translation = 0f,
                cornerRadius = 0f,
            ),
            calculatePredictiveDocumentBackTransform(
                progress = 1f,
                swipeEdge = DocumentBackSwipeEdge.LEFT,
                maximumTranslation = -24f,
                maximumCornerRadius = -28f,
            ),
        )
    }

    @Test
    fun completionRequiresSameCleanDocumentWithNoOverlay() {
        fun eligible(
            gestureKey: Long = 7L,
            currentDocumentKey: Long = 7L,
            dirty: Boolean = false,
            searchVisible: Boolean = false,
            tableOfContentsVisible: Boolean = false,
            parentOverlayVisible: Boolean = false,
        ) = canCompletePredictiveDocumentBack(
            gestureKey = gestureKey,
            currentDocumentKey = currentDocumentKey,
            dirty = dirty,
            searchVisible = searchVisible,
            tableOfContentsVisible = tableOfContentsVisible,
            parentOverlayVisible = parentOverlayVisible,
        )

        assertEquals(true, eligible())
        assertEquals(false, eligible(currentDocumentKey = 8L))
        assertEquals(false, eligible(dirty = true))
        assertEquals(false, eligible(searchVisible = true))
        assertEquals(false, eligible(tableOfContentsVisible = true))
        assertEquals(false, eligible(parentOverlayVisible = true))
    }
}
