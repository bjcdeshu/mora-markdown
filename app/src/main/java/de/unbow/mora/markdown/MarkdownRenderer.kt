package de.unbow.mora.markdown

import org.commonmark.Extension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlRenderer
import java.util.IdentityHashMap

enum class ReaderFontFamily {
    SANS,
    SERIF,
}

enum class ReaderSurfaceTone {
    DEFAULT,
    WARM_PAPER,
}

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
    val fontFamily: ReaderFontFamily = Default.fontFamily,
    val surfaceTone: ReaderSurfaceTone = Default.surfaceTone,
) {
    companion object {
        val Default = ReaderPreferences(
            fontSizePx = 17f,
            lineHeight = 1.72f,
            horizontalPaddingPx = 20f,
            fontFamily = ReaderFontFamily.SANS,
            surfaceTone = ReaderSurfaceTone.DEFAULT,
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

internal fun resolveEffectiveReaderPalette(
    base: ReaderPalette,
    surfaceTone: ReaderSurfaceTone,
    effectiveDark: Boolean,
): ReaderPalette = when (surfaceTone) {
    ReaderSurfaceTone.DEFAULT -> base
    ReaderSurfaceTone.WARM_PAPER -> if (effectiveDark) {
        base.copy(
            background = "#191612",
            text = "#E8DFD1",
            muted = "#A89E8E",
            softSurface = "#24201A",
            outline = "#3A332A",
        )
    } else {
        base.copy(
            background = "#F6F1E6",
            text = "#221E18",
            muted = "#635B4E",
            softSurface = "#ECE4D4",
            outline = "#D8CEBC",
        )
    }
}

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
        effectiveDark: Boolean,
        untitledHeading: String,
        copyLabel: String = "Copy",
        copiedLabel: String = "Copied",
    ): RenderedMarkdown {
        val effectivePalette = resolveEffectiveReaderPalette(
            base = palette,
            surfaceTone = preferences.surfaceTone,
            effectiveDark = effectiveDark,
        )
        val document = parser.parse(markdown)
        val headingIds = IdentityHashMap<Node, String>()
        val headings = collectHeadings(document, headingIds, untitledHeading)
        val renderer = HtmlRenderer.builder()
            .extensions(extensions)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .attributeProviderFactory {
                AttributeProvider { node, _, attributes ->
                    headingIds[node]?.let { attributes["id"] = it }
                }
            }
            .nodeRendererFactory { context ->
                CodeBlockNodeRenderer(
                    context = context,
                    copyLabel = copyLabel,
                    copiedLabel = copiedLabel,
                )
            }
            .build()
        val body = renderer.render(document)
        val fontFamilyCss = when (preferences.fontFamily) {
            ReaderFontFamily.SANS ->
                """system-ui, -apple-system, BlinkMacSystemFont, "Noto Sans CJK SC", "Noto Sans SC", "Segoe UI", sans-serif"""
            ReaderFontFamily.SERIF ->
                """"Noto Serif CJK SC", "Noto Serif SC", "Source Han Serif SC", "Songti SC", Georgia, "Times New Roman", serif"""
        }
        val syntaxKeywordColor = if (effectiveDark) "#8AB4F8" else "#1E50A2"
        val syntaxStringColor = if (effectiveDark) "#81C995" else "#266E3F"
        val syntaxCommentColor = effectivePalette.muted
        val syntaxNumberColor = if (effectiveDark) "#F6AEA9" else "#9E3B24"
        val syntaxTypeColor = if (effectiveDark) "#C58AF9" else "#6B3FA0"

        return RenderedMarkdown(
            headings = headings,
            html = """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
                  <style>
                    :root { color-scheme: ${if (effectiveDark) "dark" else "light"}; }
                    * { box-sizing: border-box; }
                    html, body { margin: 0; padding: 0; background: ${effectivePalette.background}; }
                    body {
                      color: ${effectivePalette.text};
                      font-family: $fontFamilyCss;
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
                      color: ${effectivePalette.text};
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
                    a { color: ${effectivePalette.accent}; text-decoration-thickness: 1px; text-underline-offset: 0.18em; }
                    strong { font-weight: 700; }
                    hr { border: 0; border-top: 1px solid ${effectivePalette.outline}; margin: 2.1em 0; }
                    blockquote {
                      margin: 1.3em 0;
                      padding: 0.08em 0 0.08em 1em;
                      border-left: 3px solid ${effectivePalette.accent};
                      color: ${effectivePalette.muted};
                    }
                    blockquote > :last-child { margin-bottom: 0; }
                    ul, ol { padding-left: 1.4em; margin: 0.26em 0 1em; }
                    li { margin: 0.22em 0; padding-left: 0.1em; }
                    li > p { margin: 0.3em 0; }
                    input[type="checkbox"] { accent-color: ${effectivePalette.accent}; transform: scale(1.06); margin-right: 0.5em; }
                    code {
                      font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", monospace;
                      font-size: 0.88em;
                      background: ${effectivePalette.softSurface};
                      border-radius: 5px;
                      padding: 0.13em 0.35em;
                    }
                    .code-block {
                      margin: 1.35em 0;
                      background: ${effectivePalette.softSurface};
                      border: 1px solid ${effectivePalette.outline};
                      border-radius: 13px;
                      overflow: hidden;
                    }
                    .code-header {
                      display: flex;
                      align-items: center;
                      justify-content: space-between;
                      padding: 6px 12px;
                      border-bottom: 1px solid ${effectivePalette.outline};
                      font-family: system-ui, -apple-system, sans-serif;
                      font-size: 0.76em;
                      line-height: 1.2;
                      color: ${effectivePalette.muted};
                      user-select: none;
                    }
                    .code-lang {
                      font-weight: 600;
                      letter-spacing: 0.02em;
                      text-transform: lowercase;
                    }
                    .code-copy {
                      appearance: none;
                      border: 1px solid ${effectivePalette.outline};
                      background: ${effectivePalette.background};
                      color: ${effectivePalette.text};
                      font-family: inherit;
                      font-size: 0.96em;
                      font-weight: 500;
                      padding: 3px 9px;
                      border-radius: 6px;
                      cursor: pointer;
                    }
                    .code-copy:active { opacity: 0.72; }
                    pre {
                      margin: 0;
                      padding: 14px 16px;
                      overflow-x: auto;
                      background: transparent;
                      border: 0;
                      line-height: 1.55;
                      -webkit-overflow-scrolling: touch;
                    }
                    pre code { background: transparent; padding: 0; border-radius: 0; font-size: 0.86em; }
                    .tok-kw { color: $syntaxKeywordColor; font-weight: 600; }
                    .tok-str { color: $syntaxStringColor; }
                    .tok-com { color: $syntaxCommentColor; font-style: italic; }
                    .tok-num { color: $syntaxNumberColor; }
                    .tok-typ { color: $syntaxTypeColor; }
                    img {
                      display: block;
                      max-width: 100%;
                      height: auto;
                      margin: 1.45em auto;
                      border-radius: 12px;
                      cursor: zoom-in;
                    }
                    table {
                      display: block;
                      width: 100%;
                      overflow-x: auto;
                      border-collapse: collapse;
                      margin: 1.35em 0;
                      border-radius: 10px;
                      -webkit-overflow-scrolling: touch;
                    }
                    th, td {
                      min-width: 112px;
                      padding: 10px 12px;
                      border: 1px solid ${effectivePalette.outline};
                      text-align: left;
                      vertical-align: top;
                    }
                    th { background: ${effectivePalette.softSurface}; font-weight: 650; }
                    tbody tr:nth-child(even) td { background: ${effectivePalette.softSurface}66; }
                    del { color: ${effectivePalette.muted}; }
                    #mora-lightbox {
                      position: fixed;
                      inset: 0;
                      z-index: 9999;
                      background: rgba(10, 12, 16, 0.92);
                      display: flex;
                      flex-direction: column;
                      align-items: center;
                      justify-content: center;
                      padding: 20px;
                      overflow: auto;
                      -webkit-overflow-scrolling: touch;
                    }
                    #mora-lightbox[hidden] { display: none; }
                    #mora-lightbox img {
                      max-width: 96vw;
                      max-height: 86vh;
                      margin: 0;
                      border-radius: 8px;
                      cursor: zoom-out;
                      transition: transform 0.18s ease;
                    }
                    #mora-lightbox img.zoomed {
                      max-width: none;
                      max-height: none;
                      width: 175vw;
                    }
                    #mora-lightbox-caption {
                      margin-top: 12px;
                      color: #E5E8EE;
                      font-family: system-ui, sans-serif;
                      font-size: 13px;
                      text-align: center;
                      max-width: 88vw;
                    }
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
                  <div id="mora-lightbox" hidden>
                    <img id="mora-lightbox-img" alt="" />
                    <div id="mora-lightbox-caption"></div>
                  </div>
                  <script id="mora-runtime">
                    function moraCopyCode(button) {
                      var block = button.closest('.code-block');
                      if (!block) return;
                      var codeEl = block.querySelector('code');
                      if (!codeEl) return;
                      var text = codeEl.textContent || '';
                      var copiedLabel = button.getAttribute('data-copied') || 'Copied';
                      var copyLabel = button.getAttribute('data-copy') || 'Copy';
                      if (window.MoraBridge && typeof window.MoraBridge.copyText === 'function') {
                        window.MoraBridge.copyText(text);
                      } else if (navigator.clipboard && navigator.clipboard.writeText) {
                        navigator.clipboard.writeText(text).catch(function() {});
                      }
                      button.textContent = copiedLabel;
                      setTimeout(function() { button.textContent = copyLabel; }, 1400);
                    }
                    (function() {
                      var lightbox = document.getElementById('mora-lightbox');
                      var lightboxImg = document.getElementById('mora-lightbox-img');
                      var caption = document.getElementById('mora-lightbox-caption');
                      if (!lightbox || !lightboxImg) return;
                      document.getElementById('write').addEventListener('click', function(e) {
                        var target = e.target;
                        if (target && target.tagName === 'IMG' && target.src) {
                          lightboxImg.src = target.src;
                          lightboxImg.classList.remove('zoomed');
                          caption.textContent = target.alt || '';
                          lightbox.removeAttribute('hidden');
                        }
                      });
                      lightboxImg.addEventListener('click', function(e) {
                        e.stopPropagation();
                        lightboxImg.classList.toggle('zoomed');
                      });
                      lightbox.addEventListener('click', function() {
                        lightbox.setAttribute('hidden', '');
                        lightboxImg.removeAttribute('src');
                      });
                    })();
                  </script>
                </body>
                </html>
            """.trimIndent(),
        )
    }

    private fun collectHeadings(
        document: Node,
        headingIds: IdentityHashMap<Node, String>,
        untitledHeading: String,
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
                            title = heading.plainText().ifBlank { untitledHeading },
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

private class CodeBlockNodeRenderer(
    private val context: HtmlNodeRendererContext,
    private val copyLabel: String,
    private val copiedLabel: String,
) : NodeRenderer {

    override fun getNodeTypes(): Set<Class<out Node>> = setOf(
        FencedCodeBlock::class.java,
        IndentedCodeBlock::class.java,
    )

    override fun render(node: Node) {
        val literal = when (node) {
            is FencedCodeBlock -> node.literal ?: ""
            is IndentedCodeBlock -> node.literal ?: ""
            else -> return
        }
        val info = (node as? FencedCodeBlock)?.info?.trim().orEmpty()
        val language = info.substringBefore(' ').trim().take(24)
        val writer = context.writer
        writer.line()
        writer.raw("""<div class="code-block">""")
        writer.raw("""<div class="code-header">""")
        writer.raw("""<span class="code-lang">${escapeHtml(language.ifEmpty { "code" })}</span>""")
        writer.raw(
            """<button type="button" class="code-copy" data-copy="${escapeHtml(copyLabel)}" data-copied="${escapeHtml(copiedLabel)}" onclick="moraCopyCode(this)">${escapeHtml(copyLabel)}</button>""",
        )
        writer.raw("""</div>""")
        writer.raw("<pre><code>")
        writer.raw(highlightCodeHtml(literal, language))
        writer.raw("</code></pre></div>")
        writer.line()
    }
}

internal fun escapeHtml(input: String): String = buildString(input.length) {
    for (ch in input) {
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}

private val COMMON_KEYWORDS = setOf(
    "fun", "val", "var", "class", "object", "interface", "package", "import", "return",
    "if", "else", "when", "for", "while", "do", "try", "catch", "finally", "throw",
    "true", "false", "null", "private", "public", "internal", "protected", "override",
    "data", "sealed", "enum", "companion", "suspend", "inline", "const", "final",
    "def", "from", "as", "in", "is", "not", "and", "or", "lambda", "with", "yield",
    "async", "await", "function", "let", "export", "default", "switch", "case", "break",
    "continue", "new", "this", "super", "extends", "implements", "static", "void",
    "int", "long", "float", "double", "boolean", "char", "byte", "short", "struct",
    "impl", "fn", "pub", "use", "mod", "match", "mut", "where", "select", "from", "where",
)

internal fun highlightCodeHtml(source: String, language: String): String {
    if (language.equals("text", ignoreCase = true) || language.equals("plain", ignoreCase = true)) {
        return escapeHtml(source)
    }
    val out = StringBuilder(source.length + 64)
    val n = source.length
    var i = 0
    val supportsHashComment = !language.equals("c", ignoreCase = true) &&
        !language.equals("cpp", ignoreCase = true) &&
        !language.equals("java", ignoreCase = true) &&
        !language.equals("kotlin", ignoreCase = true) &&
        !language.equals("javascript", ignoreCase = true) &&
        !language.equals("js", ignoreCase = true) &&
        !language.equals("typescript", ignoreCase = true) &&
        !language.equals("ts", ignoreCase = true)

    while (i < n) {
        val c = source[i]
        // Line comments: // or #
        if (c == '/' && i + 1 < n && source[i + 1] == '/') {
            val end = source.indexOf('\n', i).let { if (it < 0) n else it }
            out.append("""<span class="tok-com">""")
                .append(escapeHtml(source.substring(i, end)))
                .append("</span>")
            i = end
            continue
        }
        if (c == '/' && i + 1 < n && source[i + 1] == '*') {
            val end = source.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }
            out.append("""<span class="tok-com">""")
                .append(escapeHtml(source.substring(i, end)))
                .append("</span>")
            i = end
            continue
        }
        if (c == '#' && supportsHashComment) {
            val end = source.indexOf('\n', i).let { if (it < 0) n else it }
            out.append("""<span class="tok-com">""")
                .append(escapeHtml(source.substring(i, end)))
                .append("</span>")
            i = end
            continue
        }
        // Strings: " or '
        if (c == '"' || c == '\'') {
            val quote = c
            var j = i + 1
            while (j < n && source[j] != '\n') {
                if (source[j] == '\\') {
                    j += 2
                    continue
                }
                if (source[j] == quote) {
                    j += 1
                    break
                }
                j += 1
            }
            out.append("""<span class="tok-str">""")
                .append(escapeHtml(source.substring(i, j.coerceAtMost(n))))
                .append("</span>")
            i = j.coerceAtMost(n)
            continue
        }
        // Annotations / decorators
        if (c == '@' && i + 1 < n && source[i + 1].isLetter()) {
            var j = i + 1
            while (j < n && (source[j].isLetterOrDigit() || source[j] == '_')) j++
            out.append("""<span class="tok-typ">""")
                .append(escapeHtml(source.substring(i, j)))
                .append("</span>")
            i = j
            continue
        }
        // Numbers
        if (c.isDigit() && (i == 0 || !source[i - 1].isLetterOrDigit())) {
            var j = i + 1
            while (j < n && (source[j].isLetterOrDigit() || source[j] == '.' || source[j] == '_')) j++
            out.append("""<span class="tok-num">""")
                .append(escapeHtml(source.substring(i, j)))
                .append("</span>")
            i = j
            continue
        }
        // Identifiers / Keywords / Types
        if (c.isLetter() || c == '_') {
            var j = i + 1
            while (j < n && (source[j].isLetterOrDigit() || source[j] == '_')) j++
            val word = source.substring(i, j)
            when {
                word in COMMON_KEYWORDS ->
                    out.append("""<span class="tok-kw">""").append(escapeHtml(word)).append("</span>")
                word.first().isUpperCase() && word.length > 1 ->
                    out.append("""<span class="tok-typ">""").append(escapeHtml(word)).append("</span>")
                else -> out.append(escapeHtml(word))
            }
            i = j
            continue
        }
        out.append(escapeHtml(c.toString()))
        i += 1
    }
    return out.toString()
}
