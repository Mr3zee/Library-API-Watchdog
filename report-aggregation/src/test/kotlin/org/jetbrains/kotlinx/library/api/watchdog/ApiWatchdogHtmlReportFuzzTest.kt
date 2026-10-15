package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import java.net.URLDecoder
import java.nio.file.Files
import java.util.UUID
import org.jsoup.Jsoup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApiWatchdogHtmlReportFuzzTest {
    private val seed = 0x5EED_2026
    private val payloads = listOf(
        "", "plain", "<script>alert(1)</script>", "</code></pre><img src=x onerror=alert(1)>",
        "\" autofocus onfocus=alert(1) x=\"", "' onclick='alert(1)", "&lt;script&gt; &amp; &#39;",
        "<!-- --> </style> </script>", "<svg/onload=alert(1)>", "<math><mtext><img src=x onerror=alert(1)>",
        "[link](javascript:alert%281%29)", "[link](java&#x73;cript:alert%281%29)",
        "[link](JAVASCRIPT:alert%281%29)", "[link](vbscript:msgbox%281%29)",
        "[link](data:text/html,hello)", "![image](javascript:alert%281%29)",
        "[link](java&#x0a;script:alert%281%29)", "[link](javascript&#58;alert%281%29)",
        "[link](https://example.com/\"onmouseover=\"alert%281%29)",
        "`x` **bold** _text_ \\ {0} {999999999999999999999}",
        "\n\n```kotlin\n</code><script>alert(1)</script>\n```\n",
        "\r\n\t# Heading\n\n> quote\n\n- item", "C:\\src\\Api.kt:2147483647", "../../a:b/c",
        "日本語 café e\u0301 😀 \u202Ehidden\u202C", "\u0001\u000b\u000c\u001f\u007f",
    )
    private val boundaries = listOf(Int.MIN_VALUE, -1, 0, 1, 2, 3, Int.MAX_VALUE - 1, Int.MAX_VALUE)

    @Test
    fun `seeded report inputs preserve findings and escape HTML`() = withOutput { output ->
        val random = Random(seed)
        ApiWatchdogHtmlReport.write(emptyList(), output, aggregate = true)
        val trustedScripts = Jsoup.parse(output.readText()).select("script").map { it.data() }
        repeat(256) { iteration ->
            val reports = List(random.nextInt(4)) { project -> randomReport(random, project) }
            for (aggregate in listOf(false, true)) {
                val context = "seed=$seed iteration=$iteration aggregate=$aggregate"
                try {
                    ApiWatchdogHtmlReport.write(reports, output, aggregate)
                    val html = output.readText()
                    val document = Jsoup.parse(normalize(html))
                    val applied = reports.sumOf { it.applied.size }
                    val notApplied = reports.sumOf { it.notApplied.size }
                    assertEquals(applied, document.select("#applied + .diagnostic-groups .finding").size)
                    assertEquals(notApplied, document.select("#not-applied + .diagnostic-groups .finding").size)
                    assertEquals(trustedScripts, document.select("script").map { it.data() })
                    assertEquals(1, document.select("style").size)
                    assertTrue(document.select("script[src], iframe, object, embed, svg, math, base, img[onerror]").isEmpty())
                    document.allElements.forEach { element ->
                        assertFalse(element.attributes().any { it.key.startsWith("on", ignoreCase = true) })
                    }
                    for (report in reports) {
                        val findings = document.select(".finding").filter { finding ->
                            finding.selectFirst(".project code")?.wholeText() == normalize(report.projectPath) || !aggregate
                        }
                        for (entry in report.applied) {
                            val finding = assertNotNull(findings.singleOrNull {
                                it.selectFirst(".location code")?.wholeText() == normalize(entry.location)
                            })
                            assertEquals("@${normalize(entry.annotation)}", finding.selectFirst(".finding-heading .group-annotation")?.wholeText())
                            assertNotNull(finding.selectFirst(".fix-status.applied"))
                        }
                        for (entry in report.notApplied) {
                            val finding = assertNotNull(findings.singleOrNull {
                                it.selectFirst(".location code")?.wholeText() == normalize(entry.location) &&
                                    diagnosticName(it.attr("data-diagnostics")) == entry.diagnostic
                            })
                            assertEquals(normalize(entry.reason), finding.selectFirst(".reason")?.wholeText())
                            assertEquals(normalize(entry.severity), finding.selectFirst(".severity")?.wholeText())
                            assertEquals(normalize(entry.location), finding.selectFirst(".location code")?.wholeText())
                            val line = entry.location.substringAfterLast(':', "").toIntOrNull()
                            val sourcePath = if (line != null && line > 0) entry.location.substringBeforeLast(':') else entry.location
                            val path = listOf(if (aggregate) report.projectDirectory else "", sourcePath)
                                .filter(String::isNotEmpty).joinToString("/").replace('\\', '/')
                            assertEquals(normalize(path), finding.attr("data-path"))
                            report.sourceSnippets[entry.location]?.let { snippet ->
                                assertEquals(normalize(snippet.code), finding.selectFirst("pre.line-numbers > code")?.wholeText())
                            }
                        }
                    }
                    ApiWatchdogHtmlReport.write(reports, output, aggregate)
                    assertEquals(html, output.readText())
                } catch (failure: Throwable) {
                    throw AssertionError(context, failure)
                }
            }
        }
    }

    @Test
    fun `seeded source ranges match UTF-16 offsets without integer overflow`() = withOutput { output ->
        val random = Random(seed)
        val snippets = mutableMapOf<String, ApiWatchdogSourceSnippet>()
        val entries = List(2048) { index ->
            val startLine = if (index % 4 == 0) Int.MAX_VALUE else random.nextInt(1, 100)
            val code = List(random.nextInt(1, 8)) { randomText(random) }.joinToString(listOf("\n", "\r\n", "\r").random(random))
            val location = "src/Range$index.kt:$startLine"
            snippets[location] = ApiWatchdogSourceSnippet(startLine, code)
            val range = when (index % 8) {
                0 -> ApiWatchdogSourceRange(startLine, 1, startLine, 2)
                1 -> ApiWatchdogSourceRange(1, 1, Int.MAX_VALUE, 1)
                2 -> ApiWatchdogSourceRange(startLine, 1, startLine, 1)
                3 -> null
                else -> ApiWatchdogSourceRange(
                    (boundaries + startLine).random(random), (boundaries + code.length).random(random),
                    (boundaries + startLine).random(random), (boundaries + code.length).random(random),
                )
            }
            NotAppliedExemptionReportEntry(null, "FUZZ_RANGE", location, "", "error", sourceRange = range)
        }
        ApiWatchdogHtmlReport.write(listOf(ApiWatchdogProjectReport(":ranges", "", emptyList(), entries, snippets)), output, aggregate = false)
        val findings = Jsoup.parse(output.readText()).select(".finding").associateBy { it.selectFirst(".location code")!!.wholeText() }
        for ((index, entry) in entries.withIndex()) {
            val context = "seed=$seed range=$index ${entry.sourceRange}"
            val snippet = snippets.getValue(entry.location)
            val code = normalize(snippet.code)
            val pre = assertNotNull(findings.getValue(entry.location).selectFirst("pre"), context)
            val expected = expectedOffsets(entry.sourceRange, snippet.startLine, code)
            val actual = if (pre.hasAttr("data-range-start")) {
                pre.attr("data-range-start").toInt() to pre.attr("data-range-end").toInt()
            } else null
            assertEquals(expected, actual, context)
            val line = entry.sourceRange?.startLine ?: snippet.startLine
            val visibleLine = line.toLong() in snippet.startLine.toLong()..(snippet.startLine.toLong() + code.count { it == '\n' })
            assertEquals(if (visibleLine) line.toString() else "", pre.attr("data-line"), context)
        }
    }

    @Test
    fun `seeded Markdown parameters use safe elements and URLs`() {
        val random = Random(seed)
        for ((diagnostic, description) in WatchdogCheckDescriptions.checks) {
            val parameterCount = Regex("\\{(\\d+)}").findAll(description.message).maxOfOrNull { it.groupValues[1].toInt() + 1 } ?: 0
            repeat(64) { iteration ->
                val context = "seed=$seed diagnostic=$diagnostic iteration=$iteration"
                val parameters = List(parameterCount) { if (iteration < payloads.size) payloads[iteration] else randomText(random) }
                val entry = NotAppliedExemptionReportEntry(null, diagnostic, "Api.kt:1", "", "error", parameters)
                val html = assertNotNull(DiagnosticMessageHtml.render(entry), context)
                Jsoup.parseBodyFragment(html).body().getAllElements().drop(1).forEach { element ->
                    assertTrue(element.normalName() in setOf(
                        "p", "a", "img", "em", "strong", "code", "pre", "blockquote", "ul", "ol", "li",
                        "hr", "br", "h1", "h2", "h3", "h4", "h5", "h6",
                    ), "$context tag=${element.normalName()}")
                    element.attributes().forEach { attribute ->
                        assertFalse(attribute.key.startsWith("on", ignoreCase = true), context)
                        if (attribute.key == "href" || attribute.key == "src") {
                            val url = attribute.value.filterNot { it <= ' ' }.lowercase()
                            assertFalse(url.startsWith("javascript:") || url.startsWith("vbscript:") || url.startsWith("data:text/html"), context)
                        }
                    }
                }
                if (parameterCount > 0) assertEquals(null, DiagnosticMessageHtml.render(entry.copy(messageParameters = parameters.dropLast(1))), context)
            }
        }
    }

    @Test
    fun `Markdown preserves web mail and relative links and rejects active URL schemes`() {
        val allowed = listOf("http://example.com", "https://example.com", "mailto:api@example.com", "/checks/open-api", "#details")
        val rejected = listOf("javascript:alert%281%29", "vbscript:msgbox%281%29", "data:text/html,hello", "data:image/svg+xml,hello")
        for (url in allowed + rejected) {
            val entry = NotAppliedExemptionReportEntry(
                null, "OPEN_API_WITHOUT_SUBCLASS_OPT_IN", "Api.kt:1", "", "error",
                messageParameters = listOf("class", "Api", "[link]($url) ![image]($url)"),
            )
            val document = Jsoup.parseBodyFragment(assertNotNull(DiagnosticMessageHtml.render(entry)))
            val expected = if (url in allowed) url else ""
            assertEquals(expected, document.selectFirst("a")?.attr("href"), url)
            assertEquals(expected, document.selectFirst("img")?.attr("src"), url)
        }
    }

    @Test
    fun `HTML accepts an output filename without a parent directory`() {
        val output = File("html-report-fuzz-${UUID.randomUUID()}.html")
        try {
            ApiWatchdogHtmlReport.write(emptyList(), output, aggregate = true)
            assertEquals("API Watchdog report", Jsoup.parse(output.readText()).title())
        } finally {
            output.delete()
        }
    }

    private fun randomReport(random: Random, project: Int): ApiWatchdogProjectReport {
        val snippets = mutableMapOf<String, ApiWatchdogSourceSnippet>()
        fun location(index: Int): String {
            val line = (boundaries + random.nextInt(1, 100)).random(random)
            val suffix = listOf(":$line", ":$line", "", ":", ":NaN", ":999999999999999999").random(random)
            val location = "src/$project/Api$index-${randomText(random)}.kt$suffix"
            if (random.nextBoolean()) snippets[location] = ApiWatchdogSourceSnippet(random.nextInt(1, 100), randomText(random))
            return location
        }
        val annotations = WatchdogCheckDescriptions.diagnosticsByAnnotation.keys.toList()
        val diagnostics = WatchdogCheckDescriptions.checks.keys.toList()
        val applied = List(random.nextInt(6)) { index ->
            AppliedExemptionReportEntry(if (random.nextBoolean()) annotations.random(random) else randomText(random), location(index))
        }
        val notApplied = List(random.nextInt(8)) { index ->
            NotAppliedExemptionReportEntry(
                annotation = if (random.nextBoolean()) null else randomText(random),
                diagnostic = if (random.nextBoolean()) diagnostics.random(random) else randomText(random),
                location = location(index + applied.size),
                reason = randomText(random),
                severity = listOf("error", "warning", randomText(random)).random(random),
                messageParameters = List(random.nextInt(6)) { randomText(random) },
                autoFixAvailable = random.nextBoolean(),
            )
        }
        return ApiWatchdogProjectReport(":project$project-${randomText(random)}", randomText(random), applied, notApplied, snippets)
    }

    private fun randomText(random: Random): String = buildString {
        repeat(random.nextInt(1, 5)) {
            append(payloads.random(random))
            if (random.nextBoolean()) append(random.nextInt(32, 0xD800).toChar())
        }
    }

    private fun normalize(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

    private fun diagnosticName(filterKey: String): String =
        if (filterKey == "~") "" else URLDecoder.decode(filterKey, Charsets.UTF_8)

    private fun expectedOffsets(range: ApiWatchdogSourceRange?, startLine: Int, code: String): Pair<Int, Int>? {
        if (range == null || range.startLine < 1 || range.startColumn < 1 || range.endLine < range.startLine || range.endColumn < 1) return null
        val lineStarts = listOf(0) + code.indices.filter { code[it] == '\n' }.map { it + 1 }
        fun position(line: Int, column: Int): Int? {
            val relativeLine = line.toLong() - startLine.toLong()
            if (relativeLine < 0) return 0
            if (relativeLine >= lineStarts.size) return code.length
            val offset = lineStarts[relativeLine.toInt()]
            val end = code.indexOf('\n', offset).let { if (it < 0) code.length else it }
            val result = offset.toLong() + column.toLong() - 1
            return result.takeIf { it <= end }?.toInt()
        }
        val start = position(range.startLine, range.startColumn) ?: return null
        val end = position(range.endLine, range.endColumn) ?: return null
        return (start to end).takeIf { start < end }
    }

    private fun withOutput(test: (File) -> Unit) {
        val output = Files.createTempFile("html-report-fuzz", ".html").toFile()
        try {
            test(output)
        } finally {
            output.delete()
        }
    }
}
