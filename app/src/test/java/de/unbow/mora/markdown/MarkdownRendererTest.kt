package de.unbow.mora.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    private val palette = ReaderPalette(
        background = "#FFFFFF",
        text = "#111111",
        muted = "#666666",
        accent = "#3455CC",
        softSurface = "#F5F5F5",
        outline = "#DDDDDD",
    )

    @Test
    fun `extracts a hierarchical table of contents and stable ids`() {
        val rendered = MarkdownRenderer.render(
            markdown = """
                # 第一章

                ## 第二节 **重点**

                ### `代码` 标题

                #### 不进入目录
            """.trimIndent(),
            palette = palette,
            preferences = ReaderPreferences.Default,
        )

        assertEquals(
            listOf(
                MarkdownHeading("heading-0", 1, "第一章"),
                MarkdownHeading("heading-1", 2, "第二节 重点"),
                MarkdownHeading("heading-2", 3, "代码 标题"),
            ),
            rendered.headings,
        )
        assertTrue(rendered.html.contains("""<h1 id="heading-0">"""))
        assertTrue(rendered.html.contains("""<h2 id="heading-1">"""))
        assertTrue(rendered.html.contains("""<h3 id="heading-2">"""))
    }

    @Test
    fun `escapes raw html and sanitizes dangerous links`() {
        val rendered = MarkdownRenderer.render(
            markdown = """
                <script>alert("x")</script>

                [危险链接](javascript:alert(1))
            """.trimIndent(),
            palette = palette,
            preferences = ReaderPreferences.Default,
        )

        assertFalse(rendered.html.contains("<script>"))
        assertFalse(rendered.html.contains("href=\"javascript:"))
        assertTrue(rendered.html.contains("&lt;script&gt;"))
    }

    @Test
    fun `uses the reading first default rhythm`() {
        val rendered = MarkdownRenderer.render(
            markdown = "# 标题\n\n正文",
            palette = palette,
            preferences = ReaderPreferences.Default,
        )

        assertTrue(rendered.html.contains("font-size: 17.0px"))
        assertTrue(rendered.html.contains("line-height: 1.72"))
        assertTrue(rendered.html.contains("padding: 96px 20.0px 112px"))
        assertTrue(rendered.html.contains("h1 { font-size: 1.74em"))
    }

    @Test
    fun `does not assume the language of user documents`() {
        val rendered = MarkdownRenderer.render(
            markdown = "# A document in an unknown language",
            palette = palette,
            preferences = ReaderPreferences.Default,
        )

        assertTrue(rendered.html.contains("<html>"))
        assertFalse(rendered.html.contains("<html lang="))
    }
}
