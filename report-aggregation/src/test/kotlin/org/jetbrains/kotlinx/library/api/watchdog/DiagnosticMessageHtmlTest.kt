package org.jetbrains.kotlinx.library.api.watchdog

import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DiagnosticMessageHtmlTest {
    private fun entry(diagnostic: String, vararg parameters: String) = NotAppliedExemptionReportEntry(
        annotation = null,
        diagnostic = diagnostic,
        location = "Api.kt:1",
        reason = "Manual fix.",
        severity = "error",
        messageParameters = parameters.toList(),
    )

    @Test
    fun `messages render inline code paragraphs and links`() {
        val html = assertNotNull(DiagnosticMessageHtml.render(entry("DATA_CLASS_PUBLIC_API", "Result")))
        assertContains(html, "The <code>data class</code> <code>Result</code>")
        assertContains(html, "href=\"https://github.com/drewhamilton/Poko\">Poko</a>")
        assertContains(html, "<p>If this is intended, apply <code>@IntentionallyDataClass</code>")
        assertFalse(html.contains("{0}"))
    }

    @Test
    fun `message code examples use Kotlin highlighting`() {
        val html = assertNotNull(DiagnosticMessageHtml.render(entry(
            "OPEN_API_WITHOUT_SUBCLASS_OPT_IN", "class", "Api",
            "Use this declaration:\n\n    public class Api",
        )))
        assertContains(html, "<pre><code class=\"language-kotlin\">public class Api")
    }

    @Test
    fun `messages escape HTML and reject script links`() {
        val html = assertNotNull(DiagnosticMessageHtml.render(entry(
            "UNDOCUMENTED_PUBLIC_API", "<img src=x onerror=alert(1)> [link](javascript:alert%281%29)", "Api",
        )))
        assertContains(html, "&lt;img src=x onerror=alert(1)&gt;")
        assertFalse(html.contains("<img"))
        assertFalse(html.contains("href=\"javascript:"))
    }

    @Test
    fun `unknown checks and missing parameters have no message`() {
        assertNull(DiagnosticMessageHtml.render(entry("UNKNOWN_CHECK")))
        assertNull(DiagnosticMessageHtml.render(entry("DATA_CLASS_PUBLIC_API")))
    }

    @Test
    fun `parameter substitution preserves dollar signs backslashes and placeholder text`() {
        val parameter = "Api{0}{1}{999999999999999999999}\\path\u0024value"
        val document = Jsoup.parseBodyFragment(assertNotNull(DiagnosticMessageHtml.render(entry("DATA_CLASS_PUBLIC_API", parameter))))
        assertEquals(parameter, document.select("code")[1].wholeText())
    }

    @Test
    fun `empty parameters render and extra parameters do not change the message`() {
        val normal = DiagnosticMessageHtml.render(entry("DATA_CLASS_PUBLIC_API", "Api"))
        assertEquals(normal, DiagnosticMessageHtml.render(entry("DATA_CLASS_PUBLIC_API", "Api", "<script>ignored</script>")))
        assertNotNull(DiagnosticMessageHtml.render(entry("DATA_CLASS_PUBLIC_API", "")))
    }

    @Test
    fun `repeated parameters are replaced consistently without recursive substitution`() {
        val document = Jsoup.parseBodyFragment(assertNotNull(DiagnosticMessageHtml.render(entry(
            "OPEN_API_WITHOUT_SUBCLASS_OPT_IN", "kind{1}", "Name{0}", "Hint{2}",
        ))))
        assertEquals(2, Regex("kind\\{1}").findAll(document.text()).count())
        assertContains(document.text(), "Name{0}")
        assertContains(document.text(), "Hint{2}")
    }

    @Test
    fun `code blocks keep explicit languages and default unlabelled blocks to Kotlin`() {
        for ((snippet, language) in mapOf(
            "```\nval x = 1\n```" to "kotlin",
            "```java\nint x = 1;\n```" to "java",
            "```kotlin title\nval x = 1\n```" to "kotlin",
            "    val x = 1" to "kotlin",
        )) {
            val html = assertNotNull(DiagnosticMessageHtml.render(entry("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", "class", "Api", "\n\n$snippet")))
            val block = assertNotNull(Jsoup.parseBodyFragment(html).selectFirst("pre > code"), snippet)
            assertEquals("language-$language", block.className(), snippet)
        }
    }

    @Test
    fun `raw HTML blocks comments and code fence attributes cannot add active markup`() {
        val payloads = listOf(
            "\n\n<script>alert(1)</script>\n\n",
            "\n\n<!-- hidden -->\n\n<iframe src=javascript:alert(1)></iframe>\n\n",
            "\n\n```kotlin\"onmouseover=\"alert(1)\n<script>alert(1)</script>\n```\n",
            "[link](https://example.com \"title\\\" onmouseover=\\\"alert(1)\")",
        )
        for (payload in payloads) {
            val html = assertNotNull(DiagnosticMessageHtml.render(entry("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", "class", "Api", payload)))
            val document = Jsoup.parseBodyFragment(html)
            assertTrueNoActiveMarkup(document)
        }
    }

    @Test
    fun `all diagnostic templates render with their exact parameter count`() {
        for ((diagnostic, description) in WatchdogCheckDescriptions.checks) {
            val count = Regex("\\{(\\d+)}").findAll(description.message).maxOfOrNull { it.groupValues[1].toInt() + 1 } ?: 0
            val parameters = Array(count) { "parameter$it" }
            assertNotNull(DiagnosticMessageHtml.render(entry(diagnostic, *parameters)), diagnostic)
            for (missing in 0 until count) {
                assertNull(DiagnosticMessageHtml.render(entry(diagnostic, *parameters.take(missing).toTypedArray())), "$diagnostic parameters=$missing")
            }
        }
    }

    private fun assertTrueNoActiveMarkup(document: org.jsoup.nodes.Document) {
        assertEquals(0, document.select("script, iframe, object, embed, style").size)
        document.allElements.forEach { element ->
            assertFalse(element.attributes().any { it.key.startsWith("on", ignoreCase = true) })
        }
    }
}
