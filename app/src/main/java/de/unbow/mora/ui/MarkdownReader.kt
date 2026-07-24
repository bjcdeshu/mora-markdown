package de.unbow.mora.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.animation.LinearInterpolator
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.unbow.mora.markdown.RenderedMarkdown
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val READER_BASE_URL = "https://mora.invalid/"
private const val READER_BASE_HOST = "mora.invalid"
private const val READER_MARKER_SCROLL_TOLERANCE_PX = 2
private const val READER_MARKER_IDLE_ALPHA = 0.10f
private const val READER_MARKER_SCROLL_ALPHA = 0.22f
private const val READER_MARKER_DRAG_ALPHA = 0.36f
private const val READER_MARKER_IDLE_DELAY_MS = 900L
private const val READER_MARKER_FADE_DURATION_MS = 180L

internal data class SearchResult(
    val active: Int = 0,
    val total: Int = 0,
)

internal data class FloatRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
) {
    val width: Float
        get() = (right - left).coerceAtLeast(0f)
    val height: Float
        get() = (bottom - top).coerceAtLeast(0f)

    fun contains(pointerX: Float, pointerY: Float): Boolean =
        pointerX >= left &&
            pointerX <= right &&
            pointerY >= top &&
            pointerY <= bottom
}

internal data class ReaderPositionMarker(
    val progress: Float = 0f,
    val currentOffset: Int = 0,
    val maximumOffset: Int = 0,
    val trackBounds: FloatRect = FloatRect(),
    val thumbBounds: FloatRect = FloatRect(),
    val hitBounds: FloatRect = FloatRect(),
    val isScrollable: Boolean = false,
)

internal fun resolveReaderPositionMarkerForPageState(
    marker: ReaderPositionMarker,
    pageLoadInProgress: Boolean,
): ReaderPositionMarker = if (pageLoadInProgress) {
    ReaderPositionMarker()
} else {
    marker
}

internal fun calculateReaderPositionMarker(
    offset: Int,
    range: Int,
    extent: Int,
    viewportWidth: Float,
    viewportHeight: Float,
    topInset: Float,
    rightInset: Float,
    bottomInset: Float,
    trackVerticalMargin: Float,
    thumbWidth: Float,
    thumbRightMargin: Float,
    minThumbHeight: Float,
    maxThumbHeight: Float,
    minHitSize: Float,
    scrollTolerance: Int = READER_MARKER_SCROLL_TOLERANCE_PX,
): ReaderPositionMarker {
    val dimensions = listOf(
        viewportWidth,
        viewportHeight,
        topInset,
        rightInset,
        bottomInset,
        trackVerticalMargin,
        thumbWidth,
        thumbRightMargin,
        minThumbHeight,
        maxThumbHeight,
        minHitSize,
    )
    if (
        range <= 0 ||
        extent <= 0 ||
        viewportWidth <= 0f ||
        viewportHeight <= 0f ||
        thumbWidth <= 0f ||
        dimensions.any { !it.isFinite() }
    ) {
        return ReaderPositionMarker()
    }

    val maximumOffset = range - extent
    if (maximumOffset <= scrollTolerance.coerceAtLeast(0)) return ReaderPositionMarker()

    val safeTop = topInset.coerceAtLeast(0f).coerceAtMost(viewportHeight)
    val safeBottom = (viewportHeight - bottomInset.coerceAtLeast(0f))
        .coerceIn(safeTop, viewportHeight)
    val verticalMargin = trackVerticalMargin.coerceAtLeast(0f)
    val trackTop = (safeTop + verticalMargin).coerceAtMost(safeBottom)
    val trackBottom = (safeBottom - verticalMargin).coerceAtLeast(trackTop)
    val trackHeight = trackBottom - trackTop
    if (trackHeight <= 0f) return ReaderPositionMarker()

    val safeRight = (viewportWidth - rightInset.coerceAtLeast(0f))
        .coerceIn(0f, viewportWidth)
    if (safeRight <= 0f) return ReaderPositionMarker()
    val resolvedThumbWidth = thumbWidth.coerceAtMost(safeRight)
    val trackRight = (safeRight - thumbRightMargin.coerceAtLeast(0f))
        .coerceIn(resolvedThumbWidth, safeRight)
    val trackLeft = (trackRight - resolvedThumbWidth).coerceAtLeast(0f)
    val trackBounds = FloatRect(
        left = trackLeft,
        top = trackTop,
        right = trackRight,
        bottom = trackBottom,
    )

    val minimumHeight = minThumbHeight
        .coerceAtLeast(0f)
        .coerceAtMost(trackHeight)
    val maximumHeight = maxThumbHeight
        .coerceAtLeast(minimumHeight)
        .coerceAtMost(trackHeight)
    val proportionalHeight = trackHeight * extent.toFloat() / range.toFloat()
    val resolvedThumbHeight = proportionalHeight.coerceIn(minimumHeight, maximumHeight)
    val currentOffset = offset.coerceIn(0, maximumOffset)
    val progress = currentOffset.toFloat() / maximumOffset.toFloat()
    val thumbTravel = (trackHeight - resolvedThumbHeight).coerceAtLeast(0f)
    val thumbTop = trackTop + (thumbTravel * progress)
    val thumbBounds = FloatRect(
        left = trackLeft,
        top = thumbTop,
        right = trackRight,
        bottom = thumbTop + resolvedThumbHeight,
    )

    val hitWidth = minHitSize
        .coerceAtLeast(resolvedThumbWidth)
        .coerceAtMost(safeRight)
    val hitHeight = minHitSize
        .coerceAtLeast(resolvedThumbHeight)
        .coerceAtMost(viewportHeight)
    val hitLeft = centeredStartWithinViewport(
        center = (thumbBounds.left + thumbBounds.right) / 2f,
        size = hitWidth,
        viewportEnd = safeRight,
    )
    val hitTop = centeredStartWithinViewport(
        center = (thumbBounds.top + thumbBounds.bottom) / 2f,
        size = hitHeight,
        viewportEnd = viewportHeight,
    )

    return ReaderPositionMarker(
        progress = progress,
        currentOffset = currentOffset,
        maximumOffset = maximumOffset,
        trackBounds = trackBounds,
        thumbBounds = thumbBounds,
        hitBounds = FloatRect(
            left = hitLeft,
            top = hitTop,
            right = hitLeft + hitWidth,
            bottom = hitTop + hitHeight,
        ),
        isScrollable = true,
    )
}

private fun centeredStartWithinViewport(
    center: Float,
    size: Float,
    viewportEnd: Float,
): Float {
    val maximumStart = (viewportEnd - size).coerceAtLeast(0f)
    return (center - (size / 2f)).coerceIn(0f, maximumStart)
}

internal fun calculateReaderThumbGrabOffset(
    pointerY: Float,
    marker: ReaderPositionMarker,
): Float = if (marker.isScrollable) {
    pointerY - marker.thumbBounds.top
} else {
    0f
}

internal fun calculateReaderScrollOffsetForDrag(
    pointerY: Float,
    grabOffset: Float,
    marker: ReaderPositionMarker,
): Int {
    if (!marker.isScrollable || marker.maximumOffset <= 0) return marker.currentOffset

    val thumbTravel = marker.trackBounds.height - marker.thumbBounds.height
    if (thumbTravel <= 0f || !thumbTravel.isFinite()) return marker.currentOffset

    val targetTop = (pointerY - grabOffset).coerceIn(
        marker.trackBounds.top,
        marker.trackBounds.bottom - marker.thumbBounds.height,
    )
    val progress = ((targetTop - marker.trackBounds.top) / thumbTravel).coerceIn(0f, 1f)
    return (progress * marker.maximumOffset)
        .roundToInt()
        .coerceIn(0, marker.maximumOffset)
}

internal fun isInternalReaderLocation(
    scheme: String?,
    host: String?,
    path: String?,
): Boolean =
    scheme.equals("https", ignoreCase = true) &&
        host.equals(READER_BASE_HOST, ignoreCase = true) &&
        (path.isNullOrEmpty() || path == "/")

@Suppress("DEPRECATION")
private fun WebSettings.disableLegacyFileUrlAccess() {
    allowFileAccessFromFileURLs = false
    allowUniversalAccessFromFileURLs = false
}

@Composable
internal fun MarkdownReader(
    documentKey: Long,
    documentUri: Uri?,
    renderedMarkdown: RenderedMarkdown,
    initialScrollY: Int,
    controller: MarkdownReaderController,
    onPositionChanged: (Uri, Int) -> Unit,
    onToolbarVisibilityChanged: (Boolean) -> Unit,
    onCurrentHeadingChanged: (String?) -> Unit,
    onSearchResult: (SearchResult) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val readingPositionMarkerColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val webView = remember(context) {
        MoraReaderWebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.disableLegacyFileUrlAccess()
            settings.domStorageEnabled = false
            settings.loadsImagesAutomatically = true
            isVerticalScrollBarEnabled = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            webViewClient = MoraReaderWebViewClient(context, this)
        }
    }

    SideEffect {
        webView.onPositionChanged = { uri, position ->
            runCatching { uri.toUri() }
                .onSuccess { onPositionChanged(it, position) }
        }
        webView.onToolbarVisibilityChanged = onToolbarVisibilityChanged
        webView.readingPositionMarkerColor = readingPositionMarkerColor
        controller.updateCallbacks(
            onCurrentHeadingChanged = onCurrentHeadingChanged,
            onSearchResult = onSearchResult,
        )
    }

    DisposableEffect(webView, lifecycleOwner) {
        controller.attach(webView)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                webView.publishPosition()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.publishPosition()
            controller.detach(webView)
            webView.destroy()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { webView },
        update = { view ->
            val nextPageKey = ReaderPageKey(
                documentKey = documentKey,
                documentUri = documentUri?.toString(),
                html = renderedMarkdown.html,
            )
            if (view.pageKey != nextPageKey) {
                view.publishPosition()
                view.beginPageLoad()
                val sameDocument = view.pageKey?.documentKey == documentKey
                view.pendingRestoreY = if (sameDocument) view.scrollY else initialScrollY
                view.pageKey = nextPageKey
                view.loadDataWithBaseURL(
                    READER_BASE_URL,
                    renderedMarkdown.html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
    )
}

internal class MarkdownReaderController {

    private var webView: WebView? = null
    private var onCurrentHeadingChanged: (String?) -> Unit = {}
    private var onSearchResult: (SearchResult) -> Unit = {}
    private val headingHandler = Handler(Looper.getMainLooper())
    private val headingRunnable = Runnable { evaluateCurrentHeading() }

    fun attach(view: WebView) {
        webView = view
        (view as? MoraReaderWebView)?.controller = this
        view.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                onSearchResult(
                    SearchResult(
                        active = if (numberOfMatches == 0) 0 else activeMatchOrdinal + 1,
                        total = numberOfMatches,
                    ),
                )
            }
        }
    }

    fun detach(view: WebView) {
        if (webView === view) {
            headingHandler.removeCallbacks(headingRunnable)
            view.setFindListener(null)
            (view as? MoraReaderWebView)?.controller = null
            webView = null
        }
    }

    fun updateCallbacks(
        onCurrentHeadingChanged: (String?) -> Unit,
        onSearchResult: (SearchResult) -> Unit,
    ) {
        this.onCurrentHeadingChanged = onCurrentHeadingChanged
        this.onSearchResult = onSearchResult
    }

    fun scrollToHeading(id: String) {
        val quotedId = JSONObject.quote(id)
        webView?.evaluateJavascript(
            """
                (() => {
                  const heading = document.getElementById($quotedId);
                  if (heading) heading.scrollIntoView({ block: "start", behavior: "smooth" });
                })();
            """.trimIndent(),
            null,
        )
    }

    fun search(query: String) {
        val view = webView ?: return
        if (query.isBlank()) {
            view.clearMatches()
            onSearchResult(SearchResult())
        } else {
            view.findAllAsync(query)
        }
    }

    fun findNext(forward: Boolean) {
        webView?.findNext(forward)
    }

    fun clearSearch() {
        webView?.clearMatches()
        onSearchResult(SearchResult())
    }

    fun publishPosition() {
        (webView as? MoraReaderWebView)?.publishPosition()
    }

    fun requestCurrentHeading() {
        headingHandler.removeCallbacks(headingRunnable)
        headingHandler.postDelayed(headingRunnable, 120)
    }

    private fun evaluateCurrentHeading() {
        webView?.evaluateJavascript(
            """
                (() => {
                  const headings = [...document.querySelectorAll("h1[id],h2[id],h3[id]")];
                  let current = headings.length ? headings[0].id : null;
                  for (const heading of headings) {
                    if (heading.getBoundingClientRect().top <= 86) current = heading.id;
                    else break;
                  }
                  return current;
                })();
            """.trimIndent(),
        ) { result ->
            onCurrentHeadingChanged(decodeJavaScriptString(result))
        }
    }

    private fun decodeJavaScriptString(result: String?): String? {
        if (result == null || result == "null") return null
        return runCatching { JSONArray("[$result]").getString(0) }.getOrNull()
    }
}

private data class ReaderPageKey(
    val documentKey: Long,
    val documentUri: String?,
    val html: String,
)

private class MoraReaderWebView(context: Context) : WebView(context) {

    var pageKey: ReaderPageKey? = null
    var pendingRestoreY: Int = 0
    var onPositionChanged: (String, Int) -> Unit = { _, _ -> }
    var onToolbarVisibilityChanged: (Boolean) -> Unit = {}
    var readingPositionMarkerColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            invalidate()
        }
    var controller: MarkdownReaderController? = null

    private val positionHandler = Handler(Looper.getMainLooper())
    private val positionRunnable = Runnable { publishPosition() }
    private val readingPositionMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val readingPositionMarkerBounds = RectF()
    private val markerWidth = (2f * resources.displayMetrics.density)
    private val markerEndMargin = (2f * resources.displayMetrics.density)
    private val markerVerticalMargin = (12f * resources.displayMetrics.density)
    private val markerMinimumHeight = (28f * resources.displayMetrics.density)
    private val markerMaximumHeight = (64f * resources.displayMetrics.density)
    private val markerMinimumHitSize = (48f * resources.displayMetrics.density)
    private val accessibilityManager =
        context.getSystemService(AccessibilityManager::class.java)
    private var readingPositionMarker = ReaderPositionMarker()
    private var readingPositionMarkerAlpha = READER_MARKER_IDLE_ALPHA
    private val markerFadeAnimator = ValueAnimator().apply {
        duration = READER_MARKER_FADE_DURATION_MS
        interpolator = LinearInterpolator()
        addUpdateListener { animator ->
            readingPositionMarkerAlpha = animator.animatedValue as Float
            postInvalidateOnAnimation()
        }
    }
    private val markerFadeRunnable = Runnable {
        markerFadeAnimator.cancel()
        if (readingPositionMarker.isScrollable) {
            markerFadeAnimator.setFloatValues(
                readingPositionMarkerAlpha,
                READER_MARKER_IDLE_ALPHA,
            )
            markerFadeAnimator.start()
        }
    }
    private val hideThreshold = (22f * resources.displayMetrics.density).roundToInt()
    private val showThreshold = (14f * resources.displayMetrics.density).roundToInt()
    private var directionDistance = 0
    private var lastDirection = 0
    private var pageGeneration = 0
    private var pageLoadInProgress = false
    private var readingPositionDragInProgress = false
    private var activePointerId = INVALID_POINTER_ID
    private var dragGrabOffset = 0f

    override fun onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
        super.onScrollChanged(left, top, oldLeft, oldTop)
        val delta = top - oldTop
        val direction = delta.compareTo(0)

        if (pageLoadInProgress || readingPositionDragInProgress) {
            directionDistance = 0
        } else if (top <= 8) {
            directionDistance = 0
            onToolbarVisibilityChanged(true)
        } else if (direction != 0) {
            if (direction != lastDirection) directionDistance = 0
            directionDistance += delta
            lastDirection = direction
            when {
                directionDistance >= hideThreshold -> {
                    directionDistance = 0
                    onToolbarVisibilityChanged(false)
                }

                directionDistance <= -showThreshold -> {
                    directionDistance = 0
                    onToolbarVisibilityChanged(true)
                }
            }
        }

        refreshReadingPositionMarkerGeometry(revealIdleIfNew = true)
        when {
            readingPositionDragInProgress -> setReadingPositionMarkerAlpha(
                READER_MARKER_DRAG_ALPHA,
            )

            pageLoadInProgress || delta == 0 -> setReadingPositionMarkerIdle()
            else -> showReadingPositionMarkerForScroll()
        }

        if (!readingPositionDragInProgress) {
            positionHandler.removeCallbacks(positionRunnable)
            positionHandler.postDelayed(positionRunnable, 280)
            if (!pageLoadInProgress) controller?.requestCurrentHeading()
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        postOnAnimation {
            refreshReadingPositionMarkerGeometry(revealIdleIfNew = true)
            if (!readingPositionDragInProgress) setReadingPositionMarkerIdle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postOnAnimation {
            refreshReadingPositionMarkerGeometry(revealIdleIfNew = true)
            updateSystemGestureExclusion()
        }
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        refreshReadingPositionMarkerGeometry(revealIdleIfNew = true)
        if (!readingPositionMarker.isScrollable || readingPositionMarkerAlpha <= 0f) return

        readingPositionMarkerPaint.color = readingPositionMarkerColor
        readingPositionMarkerPaint.alpha =
            (Color.alpha(readingPositionMarkerColor) * readingPositionMarkerAlpha)
                .roundToInt()
                .coerceIn(0, 255)
        val thumb = readingPositionMarker.thumbBounds
        readingPositionMarkerBounds.set(thumb.left, thumb.top, thumb.right, thumb.bottom)
        canvas.withTranslation(scrollX.toFloat(), scrollY.toFloat()) {
            drawRoundRect(
                readingPositionMarkerBounds,
                markerWidth / 2f,
                markerWidth / 2f,
                readingPositionMarkerPaint,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (
                pageLoadInProgress ||
                accessibilityManager?.isTouchExplorationEnabled == true
            ) {
                return super.onTouchEvent(event)
            }

            refreshReadingPositionMarkerGeometry(revealIdleIfNew = true)
            if (
                !readingPositionMarker.isScrollable ||
                !readingPositionMarker.hitBounds.contains(event.x, event.y)
            ) {
                return super.onTouchEvent(event)
            }

            beginReadingPositionDrag(
                pointerId = event.getPointerId(event.actionIndex),
                pointerY = event.getY(event.actionIndex),
            )
            return true
        }

        if (!readingPositionDragInProgress) return super.onTouchEvent(event)
        if (accessibilityManager?.isTouchExplorationEnabled == true) {
            finishReadingPositionDrag(publish = true)
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) {
                    finishReadingPositionDrag(publish = true)
                } else {
                    dragReadingPositionTo(event.getY(pointerIndex))
                }
            }

            MotionEvent.ACTION_POINTER_UP -> switchActivePointer(event)

            MotionEvent.ACTION_UP -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex >= 0) dragReadingPositionTo(event.getY(pointerIndex))
                performClick()
                finishReadingPositionDrag(publish = true)
            }

            MotionEvent.ACTION_CANCEL -> finishReadingPositionDrag(publish = true)
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        positionHandler.removeCallbacks(positionRunnable)
        removeCallbacks(markerFadeRunnable)
        markerFadeAnimator.cancel()
        resetReadingPositionDrag()
        clearSystemGestureExclusion()
        super.onDetachedFromWindow()
    }

    fun publishPosition() {
        positionHandler.removeCallbacks(positionRunnable)
        val documentUri = pageKey?.documentUri ?: return
        onPositionChanged(documentUri, scrollY)
    }

    fun beginPageLoad() {
        pageGeneration += 1
        pageLoadInProgress = true
        resetReadingPositionDrag()
        hideReadingPositionMarker()
    }

    fun currentPageGeneration(): Int = pageGeneration

    fun isCurrentPageGeneration(generation: Int): Boolean = generation == pageGeneration

    fun completePageRestore(generation: Int) {
        if (!isCurrentPageGeneration(generation)) return
        pageLoadInProgress = false
        updateReadingPositionMarkerIdle()
    }

    fun updateReadingPositionMarkerIdle() {
        refreshReadingPositionMarkerGeometry(revealIdleIfNew = true)
        if (readingPositionDragInProgress) {
            setReadingPositionMarkerAlpha(READER_MARKER_DRAG_ALPHA)
        } else {
            setReadingPositionMarkerIdle()
        }
    }

    fun hideReadingPositionMarker() {
        removeCallbacks(markerFadeRunnable)
        markerFadeAnimator.cancel()
        readingPositionMarker = ReaderPositionMarker()
        readingPositionMarkerAlpha = 0f
        updateSystemGestureExclusion()
        invalidate()
    }

    private fun refreshReadingPositionMarkerGeometry(revealIdleIfNew: Boolean) {
        if (pageLoadInProgress) {
            val hiddenMarker = resolveReaderPositionMarkerForPageState(
                marker = readingPositionMarker,
                pageLoadInProgress = true,
            )
            if (
                readingPositionMarker.isScrollable ||
                readingPositionMarkerAlpha != 0f
            ) {
                readingPositionMarker = hiddenMarker
                readingPositionMarkerAlpha = 0f
                updateSystemGestureExclusion()
                postInvalidateOnAnimation()
            }
            return
        }

        val windowInsets = ViewCompat.getRootWindowInsets(this)?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        val nextMarker = calculateReaderPositionMarker(
            offset = computeVerticalScrollOffset(),
            range = computeVerticalScrollRange(),
            extent = computeVerticalScrollExtent(),
            viewportWidth = width.toFloat(),
            viewportHeight = height.toFloat(),
            topInset = (windowInsets?.top ?: 0).toFloat(),
            rightInset = (windowInsets?.right ?: 0).toFloat(),
            bottomInset = (windowInsets?.bottom ?: 0).toFloat(),
            trackVerticalMargin = markerVerticalMargin,
            thumbWidth = markerWidth,
            thumbRightMargin = markerEndMargin,
            minThumbHeight = markerMinimumHeight,
            maxThumbHeight = markerMaximumHeight,
            minHitSize = markerMinimumHitSize,
        )
        if (nextMarker == readingPositionMarker) return

        val wasScrollable = readingPositionMarker.isScrollable
        readingPositionMarker = nextMarker
        when {
            !nextMarker.isScrollable -> {
                removeCallbacks(markerFadeRunnable)
                markerFadeAnimator.cancel()
                readingPositionMarkerAlpha = 0f
            }

            !wasScrollable && revealIdleIfNew -> {
                removeCallbacks(markerFadeRunnable)
                markerFadeAnimator.cancel()
                readingPositionMarkerAlpha = READER_MARKER_IDLE_ALPHA
            }
        }
        updateSystemGestureExclusion()
        postInvalidateOnAnimation()
    }

    private fun showReadingPositionMarkerForScroll() {
        if (!readingPositionMarker.isScrollable) return
        removeCallbacks(markerFadeRunnable)
        markerFadeAnimator.cancel()
        setReadingPositionMarkerAlpha(READER_MARKER_SCROLL_ALPHA)
        postDelayed(markerFadeRunnable, READER_MARKER_IDLE_DELAY_MS)
    }

    private fun setReadingPositionMarkerIdle() {
        removeCallbacks(markerFadeRunnable)
        markerFadeAnimator.cancel()
        setReadingPositionMarkerAlpha(READER_MARKER_IDLE_ALPHA)
    }

    private fun setReadingPositionMarkerAlpha(alpha: Float) {
        readingPositionMarkerAlpha = if (readingPositionMarker.isScrollable) alpha else 0f
        postInvalidateOnAnimation()
    }

    private fun beginReadingPositionDrag(pointerId: Int, pointerY: Float) {
        readingPositionDragInProgress = true
        activePointerId = pointerId
        dragGrabOffset = calculateReaderThumbGrabOffset(pointerY, readingPositionMarker)
        directionDistance = 0
        positionHandler.removeCallbacks(positionRunnable)
        removeCallbacks(markerFadeRunnable)
        markerFadeAnimator.cancel()
        setReadingPositionMarkerAlpha(READER_MARKER_DRAG_ALPHA)
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun dragReadingPositionTo(pointerY: Float) {
        refreshReadingPositionMarkerGeometry(revealIdleIfNew = true)
        if (!readingPositionMarker.isScrollable) {
            finishReadingPositionDrag(publish = true)
            return
        }
        val targetOffset = calculateReaderScrollOffsetForDrag(
            pointerY = pointerY,
            grabOffset = dragGrabOffset,
            marker = readingPositionMarker,
        )
        scrollTo(scrollX, targetOffset.coerceIn(0, readingPositionMarker.maximumOffset))
        setReadingPositionMarkerAlpha(READER_MARKER_DRAG_ALPHA)
    }

    private fun switchActivePointer(event: MotionEvent) {
        if (event.getPointerId(event.actionIndex) != activePointerId) return
        val replacementIndex = (0 until event.pointerCount)
            .firstOrNull { it != event.actionIndex }
        if (replacementIndex == null) {
            finishReadingPositionDrag(publish = true)
            return
        }

        refreshReadingPositionMarkerGeometry(revealIdleIfNew = true)
        activePointerId = event.getPointerId(replacementIndex)
        dragGrabOffset = calculateReaderThumbGrabOffset(
            pointerY = event.getY(replacementIndex),
            marker = readingPositionMarker,
        )
    }

    private fun finishReadingPositionDrag(publish: Boolean) {
        if (!readingPositionDragInProgress) return
        resetReadingPositionDrag()
        refreshReadingPositionMarkerGeometry(revealIdleIfNew = true)
        setReadingPositionMarkerIdle()
        if (publish) {
            publishPosition()
            controller?.requestCurrentHeading()
        }
    }

    private fun resetReadingPositionDrag() {
        readingPositionDragInProgress = false
        activePointerId = INVALID_POINTER_ID
        dragGrabOffset = 0f
        directionDistance = 0
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun updateSystemGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!isAttachedToWindow || !readingPositionMarker.isScrollable) {
            systemGestureExclusionRects = emptyList()
            return
        }

        val hit = readingPositionMarker.hitBounds
        if (hit.width <= 0f || hit.height <= 0f) {
            systemGestureExclusionRects = emptyList()
            return
        }
        systemGestureExclusionRects = listOf(
            Rect(
                floor(hit.left).toInt().coerceIn(0, width),
                floor(hit.top).toInt().coerceIn(0, height),
                ceil(hit.right).toInt().coerceIn(0, width),
                ceil(hit.bottom).toInt().coerceIn(0, height),
            ),
        )
    }

    private fun clearSystemGestureExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            systemGestureExclusionRects = emptyList()
        }
    }

    private companion object {
        const val INVALID_POINTER_ID = -1
    }
}

private class MoraReaderWebViewClient(
    private val context: Context,
    private val readerView: MoraReaderWebView,
) : WebViewClient() {

    override fun onPageFinished(view: WebView, url: String?) {
        val generation = readerView.currentPageGeneration()
        val restoreY = readerView.pendingRestoreY.coerceAtLeast(0)
        view.post {
            if (!readerView.isCurrentPageGeneration(generation)) return@post
            view.scrollTo(0, restoreY)
            readerView.onToolbarVisibilityChanged(true)
            readerView.controller?.requestCurrentHeading()
            readerView.postDelayed(
                { readerView.completePageRestore(generation) },
                100,
            )
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        val uri = request.url
        if (isInternalReaderLocation(uri.scheme, uri.host, uri.path)) return false

        return if (uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "mailto") {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
            true
        } else {
            true
        }
    }
}
