package de.unbow.mora.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.unbow.mora.markdown.RenderedMarkdown
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

internal data class SearchResult(
    val active: Int = 0,
    val total: Int = 0,
)

internal data class ReaderPositionMarker(
    val progress: Float = 0f,
    val isScrollable: Boolean = false,
)

internal fun calculateReaderPositionMarker(
    offset: Int,
    range: Int,
    extent: Int,
): ReaderPositionMarker {
    if (range <= 0 || extent <= 0 || range <= extent) return ReaderPositionMarker()

    val maximumOffset = range - extent
    return ReaderPositionMarker(
        progress = offset.coerceIn(0, maximumOffset).toFloat() / maximumOffset,
        isScrollable = true,
    )
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
    val readingPositionMarkerColor = MaterialTheme.colorScheme.onSurfaceVariant
        .copy(alpha = 0.56f)
        .toArgb()
    val webView = remember(context) {
        MoraReaderWebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.loadsImagesAutomatically = true
            isVerticalScrollBarEnabled = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            webViewClient = MoraReaderWebViewClient(context, this)
        }
    }

    SideEffect {
        webView.onPositionChanged = { uri, position ->
            runCatching { Uri.parse(uri) }
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
                htmlHash = renderedMarkdown.html.hashCode(),
            )
            if (view.pageKey != nextPageKey) {
                view.publishPosition()
                view.hideReadingPositionMarker()
                val sameDocument = view.pageKey?.documentKey == documentKey
                view.pendingRestoreY = if (sameDocument) view.scrollY else initialScrollY
                view.pageKey = nextPageKey
                view.loadDataWithBaseURL(
                    "https://app.local/",
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
    val htmlHash: Int,
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
    var suppressToolbarChanges: Boolean = false

    private val positionHandler = Handler(Looper.getMainLooper())
    private val positionRunnable = Runnable { publishPosition() }
    private val readingPositionMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val readingPositionMarkerBounds = RectF()
    private val markerWidth = (16f * resources.displayMetrics.density)
    private val markerHeight = (2f * resources.displayMetrics.density)
    private val markerEndMargin = (4f * resources.displayMetrics.density)
    private val markerVerticalMargin = (12f * resources.displayMetrics.density)
    private var readingPositionMarker = ReaderPositionMarker()
    private var readingPositionMarkerAlpha = 0f
    private val markerFadeAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
        duration = 180
        interpolator = LinearInterpolator()
        addUpdateListener { animator ->
            readingPositionMarkerAlpha = animator.animatedValue as Float
            postInvalidateOnAnimation()
        }
    }
    private val markerFadeRunnable = Runnable {
        markerFadeAnimator.cancel()
        markerFadeAnimator.setFloatValues(readingPositionMarkerAlpha, 0f)
        markerFadeAnimator.start()
    }
    private val hideThreshold = (22f * resources.displayMetrics.density).roundToInt()
    private val showThreshold = (14f * resources.displayMetrics.density).roundToInt()
    private var directionDistance = 0
    private var lastDirection = 0

    override fun onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
        super.onScrollChanged(left, top, oldLeft, oldTop)
        val delta = top - oldTop
        val direction = delta.compareTo(0)

        if (suppressToolbarChanges) {
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

        positionHandler.removeCallbacks(positionRunnable)
        positionHandler.postDelayed(positionRunnable, 280)
        showReadingPositionMarker()
        controller?.requestCurrentHeading()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        postOnAnimation { showReadingPositionMarker() }
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        if (!readingPositionMarker.isScrollable || readingPositionMarkerAlpha <= 0f) return

        updateReadingPositionMarkerBounds()
        readingPositionMarkerPaint.color = readingPositionMarkerColor
        readingPositionMarkerPaint.alpha =
            (Color.alpha(readingPositionMarkerColor) * readingPositionMarkerAlpha)
                .roundToInt()
                .coerceIn(0, 255)
        canvas.drawRoundRect(
            readingPositionMarkerBounds,
            markerHeight / 2f,
            markerHeight / 2f,
            readingPositionMarkerPaint,
        )
    }

    override fun onDetachedFromWindow() {
        positionHandler.removeCallbacks(positionRunnable)
        removeCallbacks(markerFadeRunnable)
        markerFadeAnimator.cancel()
        super.onDetachedFromWindow()
    }

    fun publishPosition() {
        positionHandler.removeCallbacks(positionRunnable)
        val documentUri = pageKey?.documentUri ?: return
        onPositionChanged(documentUri, scrollY)
    }

    fun showReadingPositionMarker() {
        readingPositionMarker = calculateReaderPositionMarker(
            offset = computeVerticalScrollOffset(),
            range = computeVerticalScrollRange(),
            extent = computeVerticalScrollExtent(),
        )
        if (!readingPositionMarker.isScrollable) {
            hideReadingPositionMarker()
            return
        }

        markerFadeAnimator.cancel()
        removeCallbacks(markerFadeRunnable)
        readingPositionMarkerAlpha = 1f
        postDelayed(markerFadeRunnable, 700)
        postInvalidateOnAnimation()
    }

    fun hideReadingPositionMarker() {
        removeCallbacks(markerFadeRunnable)
        markerFadeAnimator.cancel()
        readingPositionMarker = ReaderPositionMarker()
        readingPositionMarkerAlpha = 0f
        invalidate()
    }

    @Suppress("DEPRECATION")
    private fun updateReadingPositionMarkerBounds() {
        val windowInsets = rootWindowInsets
        val topInset = windowInsets?.systemWindowInsetTop ?: 0
        val rightInset = windowInsets?.systemWindowInsetRight ?: 0
        val bottomInset = windowInsets?.systemWindowInsetBottom ?: 0
        val viewportTop = scrollY.toFloat()
        val viewportLeft = scrollX.toFloat()
        val trackTop = (viewportTop + topInset + markerVerticalMargin)
            .coerceAtMost(
                (viewportTop + height - markerHeight).coerceAtLeast(viewportTop),
            )
        val trackBottom =
            (viewportTop + height - bottomInset - markerVerticalMargin - markerHeight)
            .coerceAtLeast(trackTop)
        val markerTop = trackTop +
            (trackBottom - trackTop) * readingPositionMarker.progress.coerceIn(0f, 1f)
        val markerRight = (viewportLeft + width - rightInset - markerEndMargin)
            .coerceAtLeast(viewportLeft + markerWidth)

        readingPositionMarkerBounds.set(
            markerRight - markerWidth,
            markerTop,
            markerRight,
            markerTop + markerHeight,
        )
    }
}

private class MoraReaderWebViewClient(
    private val context: Context,
    private val readerView: MoraReaderWebView,
) : WebViewClient() {

    override fun onPageFinished(view: WebView, url: String?) {
        val restoreY = readerView.pendingRestoreY.coerceAtLeast(0)
        readerView.suppressToolbarChanges = true
        view.post {
            view.scrollTo(0, restoreY)
            readerView.onToolbarVisibilityChanged(true)
            readerView.controller?.requestCurrentHeading()
            readerView.postOnAnimation { readerView.showReadingPositionMarker() }
            readerView.postDelayed(
                { readerView.suppressToolbarChanges = false },
                100,
            )
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        val uri = request.url
        if (uri.host == "app.local") return false

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
