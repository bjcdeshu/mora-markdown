package de.unbow.mora.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

internal enum class DocumentBackSwipeEdge {
    LEFT,
    RIGHT,
}

internal data class PredictiveDocumentBackTransform(
    val scale: Float,
    val translation: Float,
    val cornerRadius: Float,
    val alpha: Float,
) {
    companion object {
        val Identity = PredictiveDocumentBackTransform(
            scale = 1f,
            translation = 0f,
            cornerRadius = 0f,
            alpha = 1f,
        )
    }
}

internal data class PredictiveDocumentBackFrame(
    val document: PredictiveDocumentBackTransform,
    val home: PredictiveDocumentBackTransform,
) {
    companion object {
        val Idle = PredictiveDocumentBackFrame(
            document = PredictiveDocumentBackTransform.Identity,
            home = PredictiveDocumentBackTransform.Identity,
        )
    }
}

// Mora uses a product-specific spatial reveal for its text-dense Document-to-Home
// transition. These values intentionally differ from Android's full-screen
// fade-through motion spec; the destination remains visible underneath from the
// start of the gesture.
private const val PredictiveBackMaximumTranslationFraction = 0.45f
private const val PredictiveBackDocumentFadeStart = 0.8f
private val PredictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

internal fun calculateMaximumPredictiveBackTranslation(
    windowWidth: Float,
): Float {
    val resolvedWidth = windowWidth.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
    return resolvedWidth * PredictiveBackMaximumTranslationFraction
}

internal fun calculatePredictiveDocumentBackFrame(
    progress: Float,
    swipeEdge: DocumentBackSwipeEdge,
    maximumTranslation: Float,
    maximumCornerRadius: Float,
): PredictiveDocumentBackFrame {
    val resolvedProgress = if (progress.isFinite()) {
        progress.coerceIn(0f, 1f)
    } else {
        0f
    }
    val easedProgress = PredictiveBackEasing.transform(resolvedProgress)
    val translationDirection = when (swipeEdge) {
        DocumentBackSwipeEdge.LEFT -> 1f
        DocumentBackSwipeEdge.RIGHT -> -1f
    }
    val documentFadeProgress = (
        (resolvedProgress - PredictiveBackDocumentFadeStart) /
            (1f - PredictiveBackDocumentFadeStart)
        ).coerceIn(0f, 1f)
    val documentAlpha = 1f - PredictiveBackEasing.transform(documentFadeProgress)

    return PredictiveDocumentBackFrame(
        document = PredictiveDocumentBackTransform(
            scale = 1f - (0.1f * easedProgress),
            translation = maximumTranslation.coerceAtLeast(0f) *
                easedProgress *
                translationDirection,
            cornerRadius = maximumCornerRadius.coerceAtLeast(0f) * easedProgress,
            alpha = documentAlpha,
        ),
        home = PredictiveDocumentBackTransform(
            scale = 1.1f - (0.1f * easedProgress),
            translation = 0f,
            cornerRadius = 0f,
            alpha = 1f,
        ),
    )
}

internal fun canCompletePredictiveDocumentBack(
    gestureKey: Long,
    currentDocumentKey: Long,
    dirty: Boolean,
    searchVisible: Boolean,
    tableOfContentsVisible: Boolean,
    parentOverlayVisible: Boolean,
): Boolean = gestureKey == currentDocumentKey &&
    !dirty &&
    !searchVisible &&
    !tableOfContentsVisible &&
    !parentOverlayVisible

@Composable
internal fun PredictiveDocumentBackHandler(
    gestureKey: Long,
    enabled: Boolean,
    onProgress: (Long, Float, DocumentBackSwipeEdge) -> Unit,
    onGestureActiveChanged: (Long, Boolean) -> Unit,
    onCompleted: (Long) -> Unit,
    onCancelled: (Long) -> Unit,
) {
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnGestureActiveChanged by rememberUpdatedState(onGestureActiveChanged)
    val currentOnCompleted by rememberUpdatedState(onCompleted)
    val currentOnCancelled by rememberUpdatedState(onCancelled)
    var gestureActive by remember(gestureKey) { mutableStateOf(false) }

    DisposableEffect(gestureKey) {
        onDispose {
            if (gestureActive) {
                currentOnCancelled(gestureKey)
                currentOnGestureActiveChanged(gestureKey, false)
            }
        }
    }

    PredictiveBackHandler(enabled = enabled || gestureActive) { events ->
        val startedGestureKey = gestureKey
        gestureActive = true
        currentOnGestureActiveChanged(startedGestureKey, true)
        try {
            events.collect { event ->
                currentOnProgress(
                    startedGestureKey,
                    event.progress,
                    if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) {
                        DocumentBackSwipeEdge.RIGHT
                    } else {
                        DocumentBackSwipeEdge.LEFT
                    },
                )
            }
            gestureActive = false
            currentOnCompleted(startedGestureKey)
            currentOnGestureActiveChanged(startedGestureKey, false)
        } catch (cancelled: CancellationException) {
            gestureActive = false
            currentOnCancelled(startedGestureKey)
            currentOnGestureActiveChanged(startedGestureKey, false)
            throw cancelled
        }
    }
}
