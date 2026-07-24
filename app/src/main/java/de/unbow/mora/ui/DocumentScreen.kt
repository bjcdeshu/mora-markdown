package de.unbow.mora.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.unbow.mora.markdown.MarkdownRenderer
import de.unbow.mora.markdown.ReaderPalette
import de.unbow.mora.markdown.ReaderPreferences
import de.unbow.mora.markdown.RenderedMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class DocumentMode { READING, EDITING }

private data class ReaderRenderRequest(
    val markdown: String,
    val palette: ReaderPalette,
    val preferences: ReaderPreferences,
)

@Composable
internal fun DocumentScreen(
    documentKey: Long,
    documentUri: Uri?,
    name: String,
    dirty: Boolean,
    loading: Boolean,
    markdown: String,
    editorValue: TextFieldValue,
    mode: DocumentMode,
    palette: ReaderPalette,
    preferences: ReaderPreferences,
    readerScrollY: Int,
    snackbarHostState: SnackbarHostState,
    onReaderPositionChanged: (Uri, Int) -> Unit,
    onModeChanged: (DocumentMode) -> Unit,
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onSave: () -> Unit,
    onEditorChanged: (TextFieldValue) -> Unit,
) {
    var cachedRenderedMarkdown by remember(documentKey) {
        mutableStateOf<RenderedMarkdown?>(null)
    }
    var renderedRequest by remember(documentKey) {
        mutableStateOf<ReaderRenderRequest?>(null)
    }
    val requestedRender = remember(markdown, palette, preferences) {
        ReaderRenderRequest(markdown, palette, preferences)
    }

    LaunchedEffect(documentKey, mode, loading, requestedRender) {
        if (mode == DocumentMode.READING && !loading) {
            val rendered = withContext(Dispatchers.Default) {
                MarkdownRenderer.render(
                    markdown = requestedRender.markdown,
                    palette = requestedRender.palette,
                    preferences = requestedRender.preferences,
                )
            }
            cachedRenderedMarkdown = rendered
            renderedRequest = requestedRender
        }
    }
    val rendering = mode == DocumentMode.READING &&
        !loading &&
        renderedRequest != requestedRender
    val renderedMarkdown = cachedRenderedMarkdown
    val readerController = remember { MarkdownReaderController() }
    var toolbarVisible by rememberSaveable(documentKey) { mutableStateOf(true) }
    var showTableOfContents by rememberSaveable(documentKey) { mutableStateOf(false) }
    var showSearch by rememberSaveable(documentKey) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(documentKey) { mutableStateOf("") }
    var searchResult by remember(documentKey) { mutableStateOf(SearchResult()) }
    var currentHeadingId by rememberSaveable(documentKey) { mutableStateOf<String?>(null) }
    val immersiveReading = mode == DocumentMode.READING &&
        !loading &&
        !toolbarVisible &&
        !showSearch &&
        !showTableOfContents

    ImmersiveStatusBarEffect(hidden = immersiveReading)

    fun closeSearch() {
        showSearch = false
        searchQuery = ""
        searchResult = SearchResult()
        readerController.clearSearch()
        toolbarVisible = true
    }

    fun leaveDocument() {
        readerController.publishPosition()
        onBack()
    }

    BackHandler {
        if (showSearch) closeSearch() else leaveDocument()
    }

    LaunchedEffect(mode) {
        toolbarVisible = true
        if (mode == DocumentMode.EDITING && showSearch) closeSearch()
    }

    LaunchedEffect(showSearch, searchQuery, documentKey) {
        if (showSearch) readerController.search(searchQuery)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (mode == DocumentMode.EDITING) {
                DocumentTopBar(
                    name = name,
                    dirty = dirty,
                    onBack = ::leaveDocument,
                    onRead = { onModeChanged(DocumentMode.READING) },
                    onSave = onSave,
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = mode == DocumentMode.EDITING && !loading) {
                EditorToolbar(
                    value = editorValue,
                    onValueChange = onEditorChanged,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (loading || (mode == DocumentMode.READING && renderedMarkdown == null)) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "reader-editor",
                ) { currentMode ->
                    when (currentMode) {
                        DocumentMode.READING -> MarkdownReader(
                            documentKey = documentKey,
                            documentUri = documentUri,
                            renderedMarkdown = requireNotNull(renderedMarkdown),
                            initialScrollY = readerScrollY,
                            controller = readerController,
                            onPositionChanged = onReaderPositionChanged,
                            onToolbarVisibilityChanged = { visible ->
                                if (!showSearch) toolbarVisible = visible
                            },
                            onCurrentHeadingChanged = { currentHeadingId = it },
                            onSearchResult = { searchResult = it },
                        )

                        DocumentMode.EDITING -> MarkdownEditor(
                            value = editorValue,
                            onValueChange = onEditorChanged,
                        )
                    }
                }
            }

            if (rendering && renderedMarkdown != null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(18.dp)
                        .size(22.dp),
                    strokeWidth = 2.dp,
                )
            }

            AnimatedVisibility(
                visible = immersiveReading,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                enter = fadeIn(animationSpec = tween(durationMillis = 140)),
                exit = fadeOut(animationSpec = tween(durationMillis = 100)),
            ) {
                ReaderTopEdgeFade()
            }

            AnimatedVisibility(
                visible = mode == DocumentMode.READING &&
                    !loading &&
                    (toolbarVisible || showSearch),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
            ) {
                if (showSearch) {
                    DocumentSearchBar(
                        query = searchQuery,
                        result = searchResult,
                        onQueryChanged = { query ->
                            searchQuery = query
                            readerController.search(query)
                        },
                        onPrevious = { readerController.findNext(forward = false) },
                        onNext = { readerController.findNext(forward = true) },
                        onClose = ::closeSearch,
                    )
                } else {
                    ReaderToolbar(
                        onBack = ::leaveDocument,
                        onTableOfContents = { showTableOfContents = true },
                        onSearch = {
                            showSearch = true
                            toolbarVisible = true
                        },
                        onEdit = { onModeChanged(DocumentMode.EDITING) },
                        onAppearance = onAppearance,
                    )
                }
            }
        }
    }

    if (showTableOfContents) {
        TableOfContentsPanel(
            headings = renderedMarkdown?.headings.orEmpty(),
            currentHeadingId = currentHeadingId,
            onHeadingSelected = { heading ->
                currentHeadingId = heading.id
                showTableOfContents = false
                readerController.scrollToHeading(heading.id)
            },
            onDismiss = { showTableOfContents = false },
        )
    }
}

@Composable
private fun ReaderTopEdgeFade() {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to surface,
                        0.45f to surface.copy(alpha = 0.56f),
                        1f to surface.copy(alpha = 0f),
                    ),
                ),
            ),
    )
}

@Composable
private fun ImmersiveStatusBarEffect(hidden: Boolean) {
    val view = LocalView.current
    val window = remember(view) { view.context.findActivity()?.window }
    val controller = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    DisposableEffect(controller) {
        val previousBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            controller?.show(WindowInsetsCompat.Type.statusBars())
            if (previousBehavior != null) {
                controller.systemBarsBehavior = previousBehavior
            }
        }
    }

    LaunchedEffect(controller, hidden) {
        if (hidden) {
            controller?.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DocumentTopBar(
    name: String,
    dirty: Boolean,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onSave: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        title = {
            Column {
                Text(
                    text = name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (dirty) {
                    Text(
                        text = "未保存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            IconButton(onClick = onRead) {
                Icon(Icons.Outlined.Visibility, contentDescription = "阅读")
            }
            if (dirty) {
                IconButton(onClick = onSave) {
                    Icon(Icons.Outlined.Save, contentDescription = "保存")
                }
            }
        },
    )
}

@Composable
private fun ReaderToolbar(
    onBack: () -> Unit,
    onTableOfContents: () -> Unit,
    onSearch: () -> Unit,
    onEdit: () -> Unit,
    onAppearance: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 3.dp,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onTableOfContents) {
                    Icon(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = "目录",
                    )
                }
                IconButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = "搜索")
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = onAppearance) {
                    Icon(Icons.Outlined.TextFields, contentDescription = "排版")
                }
            }
        }
    }
}

@Composable
private fun DocumentSearchBar(
    query: String,
    result: SearchResult,
    onQueryChanged: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("在文档中搜索") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        Text(
                            text = if (result.total > 0) {
                                "${result.active}/${result.total}"
                            } else {
                                "0/0"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            IconButton(
                onClick = onPrevious,
                enabled = result.total > 0,
            ) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上一个结果")
            }
            IconButton(
                onClick = onNext,
                enabled = result.total > 0,
            ) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下一个结果")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭搜索")
            }
        }
    }
}
