package de.unbow.mora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MarkdownEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
) {
    val scrollState = rememberScrollState()
    val cursorColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                lineHeight = 25.sp,
            ),
            cursorBrush = SolidColor(cursorColor),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxHeight()) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = "开始写 Markdown…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

private data class MarkdownAction(
    val label: String,
    val prefix: String,
    val suffix: String = "",
    val placeholder: String = "文本",
    val linePrefix: Boolean = false,
)

@Composable
internal fun EditorToolbar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
) {
    val actions = remember { markdownActions() }
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(actions) { action ->
                FilledTonalButton(
                    onClick = { onValueChange(applyMarkdownAction(value, action)) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(action.label, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun markdownActions(): List<MarkdownAction> = listOf(
    MarkdownAction("H1", "# ", placeholder = "标题", linePrefix = true),
    MarkdownAction("B", "**", "**", "加粗文字"),
    MarkdownAction("I", "*", "*", "斜体文字"),
    MarkdownAction("链接", "[", "](https://)", "链接文字"),
    MarkdownAction("引用", "> ", placeholder = "引用", linePrefix = true),
    MarkdownAction("列表", "- ", placeholder = "列表项", linePrefix = true),
    MarkdownAction("任务", "- [ ] ", placeholder = "待办事项", linePrefix = true),
    MarkdownAction("代码", "`", "`", "code"),
)

private fun applyMarkdownAction(
    value: TextFieldValue,
    action: MarkdownAction,
): TextFieldValue {
    val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val selected = value.text.substring(start, end)

    if (action.linePrefix) {
        val lineStart = value.text.lastIndexOf('\n', startIndex = (start - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val updated = value.text.substring(0, lineStart) +
            action.prefix +
            value.text.substring(lineStart)
        val selection = if (selected.isEmpty()) {
            val cursor = start + action.prefix.length
            TextRange(cursor, cursor)
        } else {
            TextRange(start + action.prefix.length, end + action.prefix.length)
        }
        return TextFieldValue(updated, selection)
    }

    val body = selected.ifEmpty { action.placeholder }
    val inserted = action.prefix + body + action.suffix
    val updated = value.text.replaceRange(start, end, inserted)
    val bodyStart = start + action.prefix.length
    val selection = if (selected.isEmpty()) {
        TextRange(bodyStart, bodyStart + body.length)
    } else {
        TextRange(start + inserted.length, start + inserted.length)
    }
    return TextFieldValue(updated, selection)
}
