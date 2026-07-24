package de.unbow.mora.ui

const val SAMPLE_MARKDOWN = """# 让 Markdown 回到文档本身

> Material You 原生外壳，Typora 风格阅读区，以及一个稳定、克制的源码编辑器。

这是一版用于验证产品方向的 **Mora Demo**。它不做知识库，不做双链，也不把正文塞进一层层卡片。

## 这版已经覆盖

- [x] 打开本地 `.md` 文件
- [x] 阅读与编辑即时切换
- [x] Material You 动态配色
- [x] Typora 风格的单栏排版
- [x] 表格、任务列表、引用与代码块
- [x] 保存与另存为
- [ ] 相对路径本地图片
- [ ] CodeMirror 6 编辑器

## 产品边界

| 维度 | 当前策略 |
|---|---|
| 阅读 | Markdown → HTML → CSS → WebView |
| 编辑 | 先用原生编辑器验证交互，正式版替换为 CodeMirror 6 |
| 文件 | Android Storage Access Framework |
| 视觉 | Material You 外壳 + 文档优先正文 |

## 一段代码

```kotlin
@Composable
fun MarkdownDocument() {
    MaterialTheme {
        ReaderAndEditor()
    }
}
```

阅读体验主要由 **正文宽度、字体、行高、段距与标题节奏** 决定，而不是 Markdown 解析器本身。

---

切换到“编辑”后，可以直接修改这篇文档，再回到“阅读”查看排版结果。
"""
