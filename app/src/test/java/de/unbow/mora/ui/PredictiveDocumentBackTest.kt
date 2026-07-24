package de.unbow.mora.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PredictiveDocumentBackTest {

    @Test
    fun maximumTranslationKeepsTheDocumentNearHalfOfTheCurrentWindow() {
        assertEquals(
            486f,
            calculateMaximumPredictiveBackTranslation(
                windowWidth = 1080f,
            ),
            0.0001f,
        )
        assertEquals(
            54f,
            calculateMaximumPredictiveBackTranslation(
                windowWidth = 120f,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            calculateMaximumPredictiveBackTranslation(
                windowWidth = Float.NaN,
            ),
            0.0001f,
        )
    }

    @Test
    fun immersiveStatusBarStaysVisibleUntilThePreviewFinishesResetting() {
        assertEquals(
            true,
            shouldHideImmersiveStatusBar(
                immersiveReading = true,
                predictiveBackGestureActive = false,
                predictiveBackVisualActive = false,
            ),
        )
        assertEquals(
            false,
            shouldHideImmersiveStatusBar(
                immersiveReading = true,
                predictiveBackGestureActive = true,
                predictiveBackVisualActive = true,
            ),
        )
        assertEquals(
            false,
            shouldHideImmersiveStatusBar(
                immersiveReading = true,
                predictiveBackGestureActive = false,
                predictiveBackVisualActive = true,
            ),
        )
    }

    @Test
    fun zeroProgressStartsWithDocumentVisibleAndHomePreparedBehindIt() {
        assertEquals(
            PredictiveDocumentBackFrame(
                document = PredictiveDocumentBackTransform.Identity,
                home = PredictiveDocumentBackTransform(
                    scale = 1.1f,
                    translation = 0f,
                    cornerRadius = 0f,
                    alpha = 1f,
                ),
            ),
            calculatePredictiveDocumentBackFrame(
                progress = 0f,
                swipeEdge = DocumentBackSwipeEdge.LEFT,
                maximumTranslation = 24f,
                maximumCornerRadius = 28f,
            ),
        )
    }

    @Test
    fun completionReachesMoraSpatialPreviewTargets() {
        assertEquals(
            PredictiveDocumentBackFrame(
                document = PredictiveDocumentBackTransform(
                    scale = 0.9f,
                    translation = 24f,
                    cornerRadius = 28f,
                    alpha = 0f,
                ),
                home = PredictiveDocumentBackTransform.Identity,
            ),
            calculatePredictiveDocumentBackFrame(
                progress = 1f,
                swipeEdge = DocumentBackSwipeEdge.LEFT,
                maximumTranslation = 24f,
                maximumCornerRadius = 28f,
            ),
        )
    }

    @Test
    fun swipeEdgeOnlyChangesDocumentTranslationDirection() {
        val fromLeft = calculatePredictiveDocumentBackFrame(
            progress = 0.5f,
            swipeEdge = DocumentBackSwipeEdge.LEFT,
            maximumTranslation = 24f,
            maximumCornerRadius = 28f,
        )
        val fromRight = calculatePredictiveDocumentBackFrame(
            progress = 0.5f,
            swipeEdge = DocumentBackSwipeEdge.RIGHT,
            maximumTranslation = 24f,
            maximumCornerRadius = 28f,
        )

        assertEquals(
            -fromLeft.document.translation,
            fromRight.document.translation,
            0.0001f,
        )
        assertEquals(fromLeft.document.scale, fromRight.document.scale, 0.0001f)
        assertEquals(
            fromLeft.document.cornerRadius,
            fromRight.document.cornerRadius,
            0.0001f,
        )
        assertEquals(fromLeft.home, fromRight.home)
    }

    @Test
    fun documentStaysOpaqueUntilTheFinalTwentyPercent() {
        val beforeFade = calculatePredictiveDocumentBackFrame(
            progress = 0.79f,
            swipeEdge = DocumentBackSwipeEdge.LEFT,
            maximumTranslation = 24f,
            maximumCornerRadius = 28f,
        )
        val duringFade = calculatePredictiveDocumentBackFrame(
            progress = 0.9f,
            swipeEdge = DocumentBackSwipeEdge.LEFT,
            maximumTranslation = 24f,
            maximumCornerRadius = 28f,
        )
        val completed = calculatePredictiveDocumentBackFrame(
            progress = 1f,
            swipeEdge = DocumentBackSwipeEdge.LEFT,
            maximumTranslation = 24f,
            maximumCornerRadius = 28f,
        )

        assertEquals(1f, beforeFade.document.alpha, 0.0001f)
        assertEquals(true, duringFade.document.alpha in 0f..<0.5f)
        assertEquals(0f, completed.document.alpha, 0.0001f)
        assertEquals(1f, beforeFade.home.alpha, 0.0001f)
        assertEquals(1f, duringFade.home.alpha, 0.0001f)
        assertEquals(1f, completed.home.alpha, 0.0001f)
    }

    @Test
    fun systemInterpolatorMakesScaleRespondEarlyWithoutOvershooting() {
        val early = calculatePredictiveDocumentBackFrame(
            progress = 0.2f,
            swipeEdge = DocumentBackSwipeEdge.LEFT,
            maximumTranslation = 24f,
            maximumCornerRadius = 28f,
        )

        assertEquals(true, early.document.scale < 0.98f)
        assertEquals(true, early.document.scale in 0.9f..1f)
        assertEquals(true, early.home.scale in 1f..1.1f)
    }

    @Test
    fun invalidProgressAndDimensionsAreClamped() {
        val start = PredictiveDocumentBackFrame(
            document = PredictiveDocumentBackTransform.Identity,
            home = PredictiveDocumentBackTransform(
                scale = 1.1f,
                translation = 0f,
                cornerRadius = 0f,
                alpha = 1f,
            ),
        )
        val endWithoutMovement = PredictiveDocumentBackFrame(
            document = PredictiveDocumentBackTransform(
                scale = 0.9f,
                translation = 0f,
                cornerRadius = 0f,
                alpha = 0f,
            ),
            home = PredictiveDocumentBackTransform.Identity,
        )

        assertEquals(
            start,
            calculatePredictiveDocumentBackFrame(
                progress = Float.NaN,
                swipeEdge = DocumentBackSwipeEdge.LEFT,
                maximumTranslation = 24f,
                maximumCornerRadius = 28f,
            ),
        )
        assertEquals(
            PredictiveDocumentBackFrame(
                document = PredictiveDocumentBackTransform(
                    scale = 0.9f,
                    translation = -24f,
                    cornerRadius = 28f,
                    alpha = 0f,
                ),
                home = PredictiveDocumentBackTransform.Identity,
            ),
            calculatePredictiveDocumentBackFrame(
                progress = 2f,
                swipeEdge = DocumentBackSwipeEdge.RIGHT,
                maximumTranslation = 24f,
                maximumCornerRadius = 28f,
            ),
        )
        assertEquals(
            endWithoutMovement,
            calculatePredictiveDocumentBackFrame(
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
