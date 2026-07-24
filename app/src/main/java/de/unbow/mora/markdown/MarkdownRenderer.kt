package de.unbow.mora.markdown

import org.commonmark.Extension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.HtmlRenderer
import java.util.IdentityHashMap

data class ReaderPalette(
    val background: String,
    val text: String,
    val muted: String,
    val accent: String,
    val softSurface: String,
    val outline: String,
)

data class ReaderPreferences(
    val fontSizePx: Float = Default.fontSizePx,
    val lineHeight: Float = Default.lineHeight,
    val horizontalPaddingPx: Float = Default.horizontalPaddingPx,
) {
    companion object {
        val Default = ReaderPreferences(
            fontSizePx = 17f,
            lineHeight = 1.72f,
            horizontalPaddingPx = 20f,
        )
    }
}

data class MarkdownHeading(
    val id: String,
    val level: Int,
    val title: String,
)

data class RenderedMarkdown(
    val html: String,
    val headings: List<MarkdownHeading>,
)

object MarkdownRenderer {

    private val extensions: List<Extension> = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListItemsExtension.create(),
    )

    private val parser: Parser = Parser.builder()
        .extensions(extensions)
        .build()

    fun render(
        markdown: String,
        palette: ReaderPalette,
        preferences: ReaderPreferences,
    ): RenderedMarkdown {
        val document = parser.parse(markdown)
        val headingIds = IdentityHashMap<Node, String>()
        val headings = collectHeadings(document, headingIds)
        val renderer = HtmlRenderer.builder()
            .extensions(extensions)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .attributeProviderFactory {
                AttributeProvider { node, _, attributes ->
                    headingIds[node]?.let { attributes["id"] = it }
                }
            }
            .build()
        val body = renderer.render(document)

        return RenderedMarkdown(
            headings = headings,
            html = """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
                  <style>
                    :root { color-scheme: light dark; }
                    * { box-sizing: border-box; }
                    html, body { margin: 0; padding: 0; background: ${palette.background}; }
                    body {
                      color: ${palette.text};
                      font-family: system-ui, -apple-system, BlinkMacSystemFont, "Noto Sans CJK SC", "Noto Sans SC", "Segoe UI", sans-serif;
                      font-size: ${preferences.fontSizePx}px;
                      line-height: ${preferences.lineHeight};
                      overflow-wrap: anywhere;
                      -webkit-font-smoothing: antialiased;
                      text-rendering: optimizeLegibility;
                    }
                    #write {
                      width: 100%;
                      max-width: 760px;
                      margin: 0 auto;
                      padding: 96px ${preferences.horizontalPaddingPx}px 112px;
                    }
                    h1, h2, h3, h4, h5, h6 {
                      color: ${palette.text};
                      line-height: 1.3;
                      font-weight: 680;
                      letter-spacing: -0.016em;
                      scroll-margin-top: 94px;
                    }
                    h1 { font-size: 1.74em; margin: 0.38em 0 0.72em; }
                    h2 { font-size: 1.40em; margin: 1.62em 0 0.64em; }
                    h3 { font-size: 1.18em; margin: 1.46em 0 0.54em; }
                    h4 { font-size: 1.04em; margin: 1.35em 0 0.46em; }
                    p { margin: 0 0 0.96em; }
                    a { color: ${palette.accent}; text-decoration-thickness: 1px; text-underline-offset: 0.18em; }
                    strong { font-weight: 700; }
                    hr { border: 0; border-top: 1px solid ${palette.outline}; margin: 2.1em 0; }
                    blockquote {
                      margin: 1.3em 0;
                      padding: 0.08em 0 0.08em 1em;
                      border-left: 3px solid ${palette.accent};
                      color: ${palette.muted};
                    }
                    blockquote > :last-child { margin-bottom: 0; }
                    ul, ol { padding-left: 1.4em; margin: 0.26em 0 1em; }
                    li { margin: 0.22em 0; padding-left: 0.1em; }
                    li > p { margin: 0.3em 0; }
                    input[type="checkbox"] { accent-color: ${palette.accent}; transform: scale(1.06); margin-right: 0.5em; }
                    code {
                      font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", monospace;
                      font-size: 0.88em;
                      background: ${palette.softSurface};
                      border-radius: 5px;
                      padding: 0.13em 0.35em;
                    }
                    pre {
                      margin: 1.35em 0;
                      padding: 15px 16px;
                      overflow-x: auto;
                      background: ${palette.softSurface};
                      border: 1px solid ${palette.outline};
                      border-radius: 13px;
                      line-height: 1.55;
                      -webkit-overflow-scrolling: touch;
                    }
                    pre code { background: transparent; padding: 0; border-radius: 0; font-size: 0.86em; }
                    img {
                      display: block;
                      max-width: 100%;
                      height: auto;
                      margin: 1.45em auto;
                      border-radius: 12px;
                    }
                    table {
                      display: block;
                      width: 100%;
                      overflow-x: auto;
                      border-collapse: collapse;
                      margin: 1.35em 0;
                      -webkit-overflow-scrolling: touch;
                    }
                    th, td {
                      min-width: 112px;
                      padding: 10px 12px;
                      border: 1px solid ${palette.outline};
                      text-align: left;
                      vertical-align: top;
                    }
                    th { background: ${palette.softSurface}; font-weight: 650; }
                    del { color: ${palette.muted}; }
                    @media (min-width: 720px) {
                      #write {
                        padding-top: 104px;
                        padding-left: 34px;
                        padding-right: 34px;
                      }
                    }
                  </style>
                </head>
                <body>
                  <main id="write">$body</main>
                </body>
                </html>
            """.trimIndent(),
        )
    }

    private fun collectHeadings(
        document: Node,
        headingIds: IdentityHashMap<Node, String>,
    ): List<MarkdownHeading> {
        val headings = mutableListOf<MarkdownHeading>()
        document.accept(
            object : AbstractVisitor() {
                override fun visit(heading: Heading) {
                    if (heading.level in 1..3) {
                        val id = "heading-${headings.size}"
                        headingIds[heading] = id
                        headings += MarkdownHeading(
                            id = id,
                            level = heading.level,
                            title = heading.plainText().ifBlank { "未命名标题" },
                        )
                    }
                    visitChildren(heading)
                }
            },
        )
        return headings
    }

    private fun Node.plainText(): String {
        val text = StringBuilder()
        accept(
            object : AbstractVisitor() {
                override fun visit(textNode: Text) {
                    text.append(textNode.literal)
                }

                override fun visit(code: Code) {
                    text.append(code.literal)
                }

                override fun visit(softLineBreak: SoftLineBreak) {
                    text.append(' ')
                }
            },
        )
        return text.toString().trim()
    }
}
