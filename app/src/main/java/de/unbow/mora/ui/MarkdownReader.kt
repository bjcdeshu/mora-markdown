package de.unbow.mora.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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
    var controller: MarkdownReaderController? = null
    var suppressToolbarChanges: Boolean = false

    private val positionHandler = Handler(Looper.getMainLooper())
    private val positionRunnable = Runnable { publishPosition() }
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
        controller?.requestCurrentHeading()
    }

    fun publishPosition() {
        positionHandler.removeCallbacks(positionRunnable)
        val documentUri = pageKey?.documentUri ?: return
        onPositionChanged(documentUri, scrollY)
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
