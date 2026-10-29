package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.util.Base64
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApiWatchdogHtmlReportTest {
    private fun entry(
        location: String = "src/Api.kt:2",
        diagnostic: String = "DATA_CLASS_PUBLIC_API",
        annotation: String? = null,
        severity: String = "error",
        range: ApiWatchdogSourceRange? = null,
    ) = NotAppliedExemptionReportEntry(annotation, diagnostic, location, "Manual fix.", severity, listOf("Api"), range)

    private fun report(
        entries: List<NotAppliedExemptionReportEntry> = emptyList(),
        applied: List<AppliedExemptionReportEntry> = emptyList(),
        snippets: Map<String, ApiWatchdogSourceSnippet> = emptyMap(),
        project: String = ":library",
        directory: String = "modules/library",
    ) = ApiWatchdogProjectReport(project, directory, applied, entries, snippets)

    @Test
    fun `report embeds the repository logo as an SVG favicon`() {
        val icon = assertNotNull(render(listOf(report())).selectFirst("head > link[rel=icon]"))
        assertEquals("image/svg+xml", icon.attr("type"))
        val href = icon.attr("href")
        assertTrue(href.startsWith("data:image/svg+xml;base64,"), href)
        val svg = Base64.getDecoder().decode(href.substringAfter("base64,")).toString(Charsets.UTF_8)
        assertEquals(File("../assets/logo.svg").readText(), svg)
        assertTrue(svg.trimEnd().endsWith("</svg>"))
    }

    @Test
    fun `project reports show their scope without aggregate paths or project labels`() {
        for (project in listOf(":", ":library<&\"'>")) {
            val document = render(listOf(report(entries = listOf(entry()), project = project)), aggregate = false)
            assertEquals("Project $project", document.selectFirst(".scope")?.text())
            assertTrue(document.select(".project").isEmpty())
            assertEquals("src/Api.kt", document.selectFirst(".finding")?.attr("data-path"))
        }
    }

    @Test
    fun `totals count findings rather than groups or annotation diagnostics`() {
        val document = render(listOf(report(
            entries = listOf(entry(severity = "warning"), entry(), entry(location = "src/Api.kt:3")),
            applied = listOf(AppliedExemptionReportEntry("IntentionallyWithoutEqualsHashCodeOrToString", "src/Api.kt:2")),
        )))
        assertEquals(listOf("3 not applied", "2 errors", "1 warning", "1 applied annotation"), document.select(".totals li").map { it.text() })
        assertEquals(4, document.select(".finding").size)
        assertEquals("(3)", document.selectFirst("#not-applied + .diagnostic-groups .group-count")?.text())
        val options = document.select("#diagnostic-filter option").drop(1)
        assertEquals(options.map { it.text() }.sorted(), options.map { it.text() })
        assertEquals(options.size, options.map { it.attr("value") }.distinct().size)
        assertEquals(4, options.size)
    }

    @Test
    fun `project filter lists the projects of an aggregate report with two or more projects`() {
        val special = ":library<&\"'>"
        val reports = listOf(report(entries = listOf(entry()), project = special), report(entries = listOf(entry()), project = ":a"))
        val document = render(reports)
        val options = document.select("#project-filter option")
        assertEquals(listOf("", ":a", special), options.map { it.attr("value") })
        assertEquals(listOf("All projects", ":a", special), options.map { it.text() })
        assertEquals(listOf(":a", special), document.select(".finding").map { it.attr("data-project") })

        val single = render(listOf(report(entries = listOf(entry()))))
        assertTrue(single.select("#project-filter").isEmpty())
        assertEquals(":library", single.selectFirst(".finding")?.attr("data-project"))

        val project = render(reports, aggregate = false)
        assertTrue(project.select("#project-filter").isEmpty())
        assertTrue(project.select(".finding[data-project]").isEmpty())
    }

    @Test
    fun `an applied annotation that a redundant-exemption diagnostic reports gets the redundant tag`() {
        val redundant = entry(
            location = "src/Api.kt:2",
            diagnostic = "REDUNDANT_EXEMPTION",
            annotation = "IntentionallyDataClass",
            severity = "warning",
        ).copy(messageParameters = listOf("IntentionallyDataClass", "declaration"))
        val document = render(listOf(report(
            entries = listOf(redundant),
            applied = listOf(
                AppliedExemptionReportEntry("IntentionallyDataClass", "src/Api.kt:2"),
                AppliedExemptionReportEntry("IntentionallyDataClass", "src/Api.kt:9"),
                AppliedExemptionReportEntry("IntentionallyOpen", "src/Api.kt:2"),
            ),
        )))
        assertEquals(
            listOf("1 not applied", "0 errors", "1 warning", "3 applied annotations", "1 redundant annotation"),
            document.select(".totals li").map { it.text() },
        )
        val tagged = document.select("#applied ~ .diagnostic-groups .finding:has(.fix-status.redundant)")
        assertEquals(listOf("src/Api.kt:2"), tagged.map { it.selectFirst(".location")?.text() })
        val taggedDiagnostics = tagged.single().attr("data-diagnostics").split(" ")
        assertTrue("REDUNDANT_EXEMPTION" in taggedDiagnostics, taggedDiagnostics.toString())
        assertTrue(tagged.single().selectFirst(".group-title, .finding-heading")?.text()?.contains("Redundant exemptions") == true)
        assertEquals(2, document.select("#applied ~ .diagnostic-groups .fix-status.applied").size)
    }

    @Test
    fun `identical findings and shared source locations remain separate entries`() {
        val same = entry()
        val document = render(listOf(report(
            entries = listOf(same, same, same.copy(diagnostic = "UNDOCUMENTED_PUBLIC_API", messageParameters = listOf("class", "Api"))),
            applied = listOf(AppliedExemptionReportEntry("IntentionallyDataClass", same.location)),
            snippets = mapOf(same.location to ApiWatchdogSourceSnippet(2, "data class Api(val x: Int)")),
        )))
        assertEquals(4, document.select(".finding").size)
        assertEquals(4, document.select(".finding pre").size)
        assertEquals(1, document.select(".fix-status.applied").size)
        assertEquals(3, document.select(".diagnostic-message").size)
    }

    @Test
    fun `groups separate explicit annotations and infer only missing annotations`() {
        val document = render(listOf(report(entries = listOf(
            entry(annotation = null), entry(annotation = "Custom"), entry(annotation = ""),
            entry(diagnostic = "FUTURE_CHECK"), entry(diagnostic = "DSL_MARKER_NOOP_TYPE_POSITION"),
        ))))
        val annotations = document.select(".diagnostic-group").map { it.attr("data-annotation") }
        assertEquals(5, annotations.size)
        assertTrue("IntentionallyDataClass" in annotations)
        assertTrue("Custom" in annotations)
        assertEquals(3, annotations.count { it.isEmpty() })
        assertEquals(2, document.select(".finding-heading span.group-annotation").size)
        assertEquals("FUTURE_CHECK", document.selectFirst(".finding[data-diagnostics=FUTURE_CHECK] .group-title")?.text())
    }

    @Test
    fun `report and group order is stable across shuffled projects and findings`() {
        val reports = listOf(
            report(entries = listOf(entry("Z.kt:2"), entry("A.kt:1")), project = ":z"),
            report(entries = listOf(entry("Z.kt:1"), entry("A.kt:3")), project = ":a"),
        )
        val expected = render(reports).outerHtml()
        assertEquals(expected, render(reports.reversed().map { it.copy(notApplied = it.notApplied.reversed()) }).outerHtml())
        assertEquals(listOf("A.kt:3", "Z.kt:1", "A.kt:1", "Z.kt:2"), Jsoup.parse(expected).select(".location code").map { it.text() })
    }

    @Test
    fun `location parsing preserves colons and does not treat a drive letter as a line separator`() {
        val paths = mapOf(
            "src/Api.kt:12" to ("src/Api.kt" to "12"),
            "C:\\source\\Api.kt:12" to ("C:/source/Api.kt" to "12"),
            "C:\\source\\Api.kt" to ("C:/source/Api.kt" to "0"),
            "src/a:b/Api.kt" to ("src/a:b/Api.kt" to "0"),
            "src/Api.kt:NaN" to ("src/Api.kt:NaN" to "0"),
            "src/Api.kt:2147483648" to ("src/Api.kt:2147483648" to "0"),
            "src/Api.kt:0" to ("src/Api.kt:0" to "0"),
            "src/Api.kt:-1" to ("src/Api.kt:-1" to "0"),
            "src/Api.kt:" to ("src/Api.kt:" to "0"),
            "Api.kt" to ("Api.kt" to "0"),
            "" to ("" to "0"),
        )
        for ((location, expected) in paths) {
            val finding = assertNotNull(render(listOf(report(entries = listOf(entry(location)))), aggregate = false).selectFirst(".finding"))
            assertEquals(expected.first, finding.attr("data-path"), location)
            assertEquals(expected.second, finding.attr("data-line"), location)
            assertEquals(location, finding.selectFirst(".location code")?.wholeText(), location)
        }
    }

    @Test
    fun `aggregate paths normalize Windows separators and keep root project paths relative`() {
        val document = render(listOf(
            report(entries = listOf(entry("src\\Api.kt:1")), project = ":", directory = ""),
            report(entries = listOf(entry("src\\Api.kt:1")), directory = "modules\\library"),
        ))
        assertEquals(listOf("src/Api.kt", "modules/library/src/Api.kt"), document.select(".finding").map { it.attr("data-path") })
        assertEquals(listOf("Project :", "Project :library"), document.select(".project").map { it.text() })
    }

    @Test
    fun `source positions use normalized newlines and UTF-16 columns`() {
        val code = "\t😀 e\u0301<&>\nnext"
        for (newline in listOf("\n", "\r\n", "\r")) {
            val diagnostic = entry(range = ApiWatchdogSourceRange(2, 2, 3, 3))
            val document = render(listOf(report(entries = listOf(diagnostic), snippets = mapOf(diagnostic.location to ApiWatchdogSourceSnippet(2, code.replace("\n", newline))))))
            val pre = assertNotNull(document.selectFirst("pre"))
            assertEquals(code, pre.selectFirst("code")?.wholeText())
            assertEquals("1", pre.attr("data-range-start"))
            assertEquals((code.indexOf('\n') + 3).toString(), pre.attr("data-range-end"))
            assertEquals("2", pre.attr("data-line"))
            assertEquals("1", pre.attr("data-line-offset"))
        }
    }

    @Test
    fun `source ranges reject reversed invalid empty and nonoverlapping positions`() {
        val invalid = listOf(
            ApiWatchdogSourceRange(0, 1, 5, 2), ApiWatchdogSourceRange(5, 0, 5, 2),
            ApiWatchdogSourceRange(5, 1, 4, 2), ApiWatchdogSourceRange(5, 1, 5, 0),
            ApiWatchdogSourceRange(5, 3, 5, 2), ApiWatchdogSourceRange(5, 2, 5, 2),
            ApiWatchdogSourceRange(5, 5, 5, 6), ApiWatchdogSourceRange(1, 1, 4, 3),
            ApiWatchdogSourceRange(7, 1, 8, 1), ApiWatchdogSourceRange(Int.MIN_VALUE, 1, Int.MAX_VALUE, 1),
        )
        for (range in invalid) {
            val diagnostic = entry(range = range)
            val pre = assertNotNull(render(listOf(report(entries = listOf(diagnostic), snippets = mapOf(diagnostic.location to ApiWatchdogSourceSnippet(5, "abc\ndef"))))).selectFirst("pre"))
            assertFalse(pre.hasAttr("data-range-start"), range.toString())
            assertFalse(pre.hasAttr("data-range-end"), range.toString())
        }
    }

    @Test
    fun `source ranges include the last character and clip at both context boundaries`() {
        val cases = mapOf(
            ApiWatchdogSourceRange(5, 3, 5, 4) to (2 to 3),
            ApiWatchdogSourceRange(5, 4, 6, 1) to (3 to 4),
            ApiWatchdogSourceRange(6, 1, 6, 4) to (4 to 7),
            ApiWatchdogSourceRange(1, 1, Int.MAX_VALUE, Int.MAX_VALUE) to (0 to 7),
        )
        for ((range, offsets) in cases) {
            val diagnostic = entry(range = range)
            val pre = assertNotNull(render(listOf(report(entries = listOf(diagnostic), snippets = mapOf(diagnostic.location to ApiWatchdogSourceSnippet(5, "abc\ndef"))))).selectFirst("pre"))
            assertEquals(offsets.first.toString(), pre.attr("data-range-start"), range.toString())
            assertEquals(offsets.second.toString(), pre.attr("data-range-end"), range.toString())
        }
    }

    @Test
    fun `line highlights use the range start and stay within saved source lines`() {
        for ((line, expected) in listOf(4 to "", 5 to "5", 6 to "6", 7 to "")) {
            val diagnostic = entry("src/Api.kt:99", range = ApiWatchdogSourceRange(line, 1, line, 2))
            val pre = assertNotNull(render(listOf(report(entries = listOf(diagnostic), snippets = mapOf(diagnostic.location to ApiWatchdogSourceSnippet(5, "abc\ndef"))))).selectFirst("pre"))
            assertEquals(expected, pre.attr("data-line"), line.toString())
            assertEquals(if (expected.isEmpty()) "" else "4", pre.attr("data-line-offset"))
        }
    }

    @Test
    fun `empty and trailing newline snippets remain distinct from missing source`() {
        for (code in listOf("", "\n", "abc\n", "\n\n")) {
            val diagnostic = entry()
            val document = render(listOf(report(entries = listOf(diagnostic), snippets = mapOf(diagnostic.location to ApiWatchdogSourceSnippet(2, code)))))
            assertEquals(code, document.selectFirst("pre code")?.wholeText())
            assertTrue(document.select(".source-unavailable").isEmpty())
            assertFalse(assertNotNull(document.selectFirst("pre")).hasAttr("data-range-start"))
        }
        assertEquals(1, render(listOf(report(entries = listOf(entry())))).select(".source-unavailable").size)
    }

    @Test
    fun `invalid snippet line numbers preserve source without position metadata`() {
        for (startLine in listOf(Int.MIN_VALUE, -1, 0)) {
            val diagnostic = entry(range = ApiWatchdogSourceRange(1, 1, Int.MAX_VALUE, 1))
            val document = render(listOf(report(entries = listOf(diagnostic), snippets = mapOf(diagnostic.location to ApiWatchdogSourceSnippet(startLine, "val x = 1")))))
            val pre = assertNotNull(document.selectFirst("pre"))
            assertEquals("val x = 1", pre.selectFirst("code")?.wholeText())
            assertFalse(pre.hasClass("line-numbers"))
            for (attribute in listOf("data-start", "data-line", "data-line-offset", "data-range-start", "data-range-end")) {
                assertFalse(pre.hasAttr(attribute), "$startLine $attribute")
            }
        }
    }

    @Test
    fun `missing ranges use valid location lines without underlining source`() {
        for ((location, expectedLine) in mapOf("Api.kt:5" to "5", "Api.kt:6" to "6", "Api.kt:4" to "", "Api.kt:7" to "", "Api.kt" to "")) {
            val diagnostic = entry(location)
            val pre = assertNotNull(render(listOf(report(entries = listOf(diagnostic), snippets = mapOf(location to ApiWatchdogSourceSnippet(5, "abc\ndef"))))).selectFirst("pre"))
            assertEquals(expectedLine, pre.attr("data-line"), location)
            assertFalse(pre.hasAttr("data-range-start"))
        }
    }

    @Test
    fun `output failures propagate when the file or its parent is a directory or a regular file`() {
        val directory = Files.createTempDirectory("html-report-output-failure").toFile()
        try {
            assertFailsWith<IOException> { ApiWatchdogHtmlReport.write(emptyList(), directory, aggregate = true) }
            val parent = File(directory, "file").apply { writeText("existing") }
            assertFailsWith<IOException> { ApiWatchdogHtmlReport.write(emptyList(), File(parent, "report.html"), aggregate = true) }
            assertEquals("existing", parent.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `missing bundled assets fail without replacing an existing report`() {
        val reportClass = ApiWatchdogHtmlReport::class.java
        val output = Files.createTempFile("html-report-missing-asset", ".html").toFile()
        try {
            for (asset in listOf("report.css", "prism-kotlin.js", "report.js")) {
                output.writeText("previous report")
                val isolated = object : ClassLoader(reportClass.classLoader) {
                    override fun loadClass(name: String, resolve: Boolean): Class<*> {
                        if (!name.startsWith(reportClass.name)) return super.loadClass(name, resolve)
                        synchronized(getClassLoadingLock(name)) {
                            val loaded = findLoadedClass(name) ?: run {
                                val bytes = checkNotNull(parent.getResourceAsStream(name.replace('.', '/') + ".class")).use { it.readBytes() }
                                defineClass(name, bytes, 0, bytes.size)
                            }
                            if (resolve) resolveClass(loaded)
                            return loaded
                        }
                    }

                    override fun getResourceAsStream(name: String) =
                        if (name == "org/jetbrains/kotlinx/library/api/watchdog/$asset") null else super.getResourceAsStream(name)
                }
                val type = isolated.loadClass(reportClass.name)
                val instance = type.getField("INSTANCE").get(null)
                val write = type.getMethod("write", List::class.java, File::class.java, Boolean::class.javaPrimitiveType)
                val failure = assertFailsWith<InvocationTargetException>(asset) {
                    write.invoke(instance, emptyList<ApiWatchdogProjectReport>(), output, true)
                }
                assertIs<IllegalStateException>(failure.cause, asset)
                assertEquals("previous report", output.readText(), asset)
            }
        } finally {
            output.delete()
        }
    }

    @Test
    fun `HTML creates parent directories and replaces previous report content`() {
        val directory = Files.createTempDirectory("html-report-output").toFile()
        try {
            val output = File(directory, "nested/reports/report.html")
            ApiWatchdogHtmlReport.write(listOf(report(entries = listOf(entry()))), output, aggregate = true)
            assertTrue(output.isFile)
            ApiWatchdogHtmlReport.write(emptyList(), output, aggregate = true)
            val document = Jsoup.parse(output.readText())
            assertTrue(document.select(".finding, .report-controls").isEmpty())
            assertEquals(2, document.select(".empty").size)
            assertEquals(2, document.select("script:not([src])").size)
            assertEquals(1, document.select("style").size)
            assertEquals(WatchdogCheckDescriptions.TOOL_VERSION, document.selectFirst("footer")?.ownText()?.trim())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `generate fixtures for report JavaScript tests`() {
        val output = File(System.getProperty("htmlReportFixtures", "build/html-report-fixtures"))
        val code = "public data class Api(\n    val text: String = \"😀<&>\",\n)\n"
        val entries = listOf(
            entry("src/Api.kt:12", range = ApiWatchdogSourceRange(12, 5, 12, 27)),
            entry("src/Api.kt:3", diagnostic = "UNDOCUMENTED_PUBLIC_API", severity = "warning").copy(messageParameters = listOf("class", "Api")),
            entry("src/z/Other.kt:1", diagnostic = "FUTURE_CHECK"),
        )
        val library = report(
            entries = entries,
            applied = listOf(
                AppliedExemptionReportEntry("IntentionallyWithoutEqualsHashCodeOrToString", "src/Api.kt:12"),
                AppliedExemptionReportEntry("IntentionallyCustom", "src/<script>&\".kt:1"),
            ),
            snippets = mapOf(entries.first().location to ApiWatchdogSourceSnippet(11, code)),
        )
        val root = report(entries = listOf(entry("src/Api.kt:2")), project = ":", directory = "")
        for ((name, reports) in mapOf(
            "mixed" to listOf(library, root), "empty" to emptyList(),
            "applied-only" to listOf(library.copy(notApplied = emptyList())),
            "not-applied-only" to listOf(library.copy(applied = emptyList())),
            "malformed" to listOf(report(entries = listOf(entry(""), entry("/"), entry("Api.kt:NaN"), entry("C:\\Api.kt"), entry("Unknown file:1")), directory = "")),
            "unusual-diagnostics" to listOf(report(entries = listOf(
                "FUTURE CHECK", "FUTURE\tCHECK", "FUTURE\nCHECK", "FUTURE%20CHECK", "FUTURE+CHECK", "", "a\"<&>'", "日本語", "~",
            ).mapIndexed { index, diagnostic -> entry("src/Api$index.kt:1", diagnostic) })),
            "duplicates" to listOf(report(
                entries = listOf(entries.first(), entries.first(), entries.first().copy(diagnostic = "FUTURE_CHECK")),
                applied = listOf(library.applied.first(), library.applied.first()),
            )),
        )) {
            ApiWatchdogHtmlReport.write(reports, File(output, "$name.html"), aggregate = true)
        }
    }

    private fun render(reports: List<ApiWatchdogProjectReport>, aggregate: Boolean = true): Document {
        val output = Files.createTempFile("html-report", ".html").toFile()
        return try {
            ApiWatchdogHtmlReport.write(reports, output, aggregate)
            Jsoup.parse(output.readText())
        } finally {
            output.delete()
        }
    }
}
