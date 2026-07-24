package de.unbow.mora.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
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
) {
    companion object {
        val Identity = PredictiveDocumentBackTransform(
            scale = 1f,
            translation = 0f,
            cornerRadius = 0f,
        )
    }
}

internal fun calculatePredictiveDocumentBackTransform(
    progress: Float,
    swipeEdge: DocumentBackSwipeEdge,
    maximumTranslation: Float,
    maximumCornerRadius: Float,
): PredictiveDocumentBackTransform {
    val resolvedProgress = if (progress.isFinite()) {
        progress.coerceIn(0f, 1f)
    } else {
        0f
    }
    val translationDirection = when (swipeEdge) {
        DocumentBackSwipeEdge.LEFT -> 1f
        DocumentBackSwipeEdge.RIGHT -> -1f
    }

    return PredictiveDocumentBackTransform(
        scale = 1f - (0.04f * resolvedProgress),
        translation = maximumTranslation.coerceAtLeast(0f) *
            resolvedProgress *
            translationDirection,
        cornerRadius = maximumCornerRadius.coerceAtLeast(0f) * resolvedProgress,
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
