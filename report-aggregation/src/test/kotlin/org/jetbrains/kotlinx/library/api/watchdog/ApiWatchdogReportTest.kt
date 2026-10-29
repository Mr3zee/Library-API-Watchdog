package org.jetbrains.kotlinx.library.api.watchdog

import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiWatchdogReportTest {
    @Test
    fun `report data round-trips escaped fields`() {
        val report = ApiWatchdogProjectReport(
            projectPath = ":library",
            projectDirectory = "lib/tabbed\tlibrary",
            applied = listOf(AppliedExemptionReportEntry("IntentionallyOpen", "src/Api.kt:4")),
            notApplied = listOf(
                NotAppliedExemptionReportEntry(
                    annotation = null,
                    diagnostic = "UNDOCUMENTED_PUBLIC_API",
                    location = "src/Tabbed\tApi.kt:7",
                    reason = "line one\nline two\\tail",
                    severity = "warning",
                    messageParameters = listOf("function", "tabbed\tname\nwith\\escape", ""),
                    sourceRange = ApiWatchdogSourceRange(7, 12, 8, 5),
                    autoFixAvailable = true,
                ),
            ),
            sourceSnippets = mapOf(
                "src/Tabbed\tApi.kt:7" to ApiWatchdogSourceSnippet(
                    startLine = 4,
                    code = "/** A public API. */\npublic val path: String = \"C:\\\\temp\"\n\t// <context>",
                ),
            ),
        )
        val file = Files.createTempFile("exempts-report", ".data").toFile()
        try {
            ApiWatchdogReportData.write(report, file)
            assertEquals(report, ApiWatchdogReportData.read(file))
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `version 2 report data has no source snippets`() {
        val file = Files.createTempFile("exempts-report-v2", ".data").toFile()
        try {
            file.writeText(
                "version\t2\nproject\t:library\ndirectory\tlib\n" +
                    "applied\tIntentionallyOpen\tsrc/Api.kt:4\n" +
                    "notApplied\t\tUNDOCUMENTED_PUBLIC_API\tsrc/Api.kt:7\tManual fix.\twarning\n"
            )
            val report = ApiWatchdogReportData.read(file)
            assertEquals(":library", report.projectPath)
            assertEquals(1, report.applied.size)
            assertEquals(1, report.notApplied.size)
            assertTrue(report.sourceSnippets.isEmpty())
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `version 4 report data preserves parameters without a source range`() {
        val file = Files.createTempFile("exempts-report-v4", ".data").toFile()
        try {
            file.writeText(
                "version\t4\nproject\t:library\ndirectory\tlib\n" +
                    "notApplied\t\tUNDOCUMENTED_PUBLIC_API\tsrc/Api.kt:7\tManual fix.\twarning\tclass\tApi\n"
            )
            val entry = ApiWatchdogReportData.read(file).notApplied.single()
            assertEquals(listOf("class", "Api"), entry.messageParameters)
            assertEquals(null, entry.sourceRange)
            assertTrue(entry.autoFixAvailable)
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `version 5 report data preserves ranges and message parameters`() {
        val file = Files.createTempFile("exempts-report-v5", ".data").toFile()
        try {
            file.writeText(
                "version\t5\nproject\t:library\ndirectory\tlib\n" +
                    "notApplied\tIntentionallyOpen\tOPEN_API_WITHOUT_SUBCLASS_OPT_IN\tsrc/Api.kt:7\tManual fix.\twarning\t7\t19\t7\t22\tclass\tApi\n"
            )
            val entry = ApiWatchdogReportData.read(file).notApplied.single()
            assertEquals(ApiWatchdogSourceRange(7, 19, 7, 22), entry.sourceRange)
            assertEquals(listOf("class", "Api"), entry.messageParameters)
            assertTrue(entry.autoFixAvailable)
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `HTML groups by diagnostic and preserves each finding's fix status`() {
        val entries = listOf(
            NotAppliedExemptionReportEntry("IntentionallyOpen", "OPEN_API_WITHOUT_SUBCLASS_OPT_IN", "Api.kt:1", "Report only.", "error", autoFixAvailable = true),
            NotAppliedExemptionReportEntry("IntentionallyOpen", "OPEN_API_WITHOUT_SUBCLASS_OPT_IN", "Api.kt:2", "No target found.", "error"),
            NotAppliedExemptionReportEntry("IntentionallyOpen", "SUBCLASS_OPT_IN_WITHOUT_MARKERS", "Api.kt:3", "Report only.", "error", autoFixAvailable = true),
            NotAppliedExemptionReportEntry(null, "UNDOCUMENTED_PUBLIC_API", "Api.kt:4", "Warnings stay.", "warning", autoFixAvailable = true),
            NotAppliedExemptionReportEntry(null, "DSL_MARKER_NOOP_TYPE_POSITION", "Api.kt:5", "Manual fix.", "error"),
        )
        val report = ApiWatchdogProjectReport(":library", "", emptyList(), entries)
        val dataFile = Files.createTempFile("exempts-report-status", ".data").toFile()
        val htmlFile = Files.createTempFile("exempts-report-status", ".html").toFile()
        try {
            ApiWatchdogReportData.write(report, dataFile)
            val saved = ApiWatchdogReportData.read(dataFile)
            assertEquals(entries, saved.notApplied)
            ApiWatchdogHtmlReport.write(listOf(saved), htmlFile, aggregate = true)
            val html = htmlFile.readText()
            assertContains(html, "data-diagnostics=\"OPEN_API_WITHOUT_SUBCLASS_OPT_IN\" data-annotation=\"IntentionallyOpen\" open>")
            assertContains(html, "data-diagnostics=\"SUBCLASS_OPT_IN_WITHOUT_MARKERS\" data-annotation=\"IntentionallyOpen\" open>")
            assertContains(html, "data-diagnostics=\"UNDOCUMENTED_PUBLIC_API\" data-annotation=\"IntentionallyUndocumented\" open>")
            assertContains(html, "data-diagnostics=\"DSL_MARKER_NOOP_TYPE_POSITION\" data-annotation=\"\" open>")
            assertContains(html, "class=\"group-count\">(2)</span>")
            assertFalse(html.contains("No automatic annotation"))
            assertFalse(html.contains("<summary>@IntentionallyOpen"))
            assertEquals(3, Regex("class=\"fix-status not-fixed\">Wasn't auto-fixed</span>").findAll(html).count())
            assertEquals(2, Regex("class=\"fix-status unfixable\">Can't be auto-fixed</span>").findAll(html).count())
        } finally {
            dataFile.toPath().deleteIfExists()
            htmlFile.toPath().deleteIfExists()
        }
    }

    @Test
    fun `both sections use check titles and annotations with filterable diagnostic names`() {
        val report = ApiWatchdogProjectReport(
            ":library", "modules/library",
            applied = listOf(
                AppliedExemptionReportEntry("IntentionallyOpen", "src/Api.kt:1"),
                AppliedExemptionReportEntry("IntentionallyWithoutEqualsHashCodeOrToString", "src/model/State.kt:4"),
                AppliedExemptionReportEntry("IntentionallyCustom", "src/Other.kt:8"),
            ),
            notApplied = listOf(NotAppliedExemptionReportEntry("IntentionallyOpen", "OPEN_API_WITHOUT_SUBCLASS_OPT_IN", "src/Api.kt:5", "Manual fix.", "error")),
        )
        val file = Files.createTempFile("exempts-report-views", ".html").toFile()
        try {
            ApiWatchdogHtmlReport.write(listOf(report), file, aggregate = true)
            val html = file.readText()
            val heading = """<a href="${WatchdogCheckDescriptions.checks.getValue("OPEN_API_WITHOUT_SUBCLASS_OPT_IN").docsUrl}">Open API without subclass opt-in</a>"""
            val notApplied = html.substringAfter("<h2 id=\"not-applied\">").substringBefore("<h2 id=\"applied\">")
            val applied = html.substringAfter("<h2 id=\"applied\">")
            for (section in listOf(notApplied, applied)) {
                assertContains(section, heading)
                assertContains(section, "<code class=\"group-annotation\">@IntentionallyOpen</code>")
                assertContains(section, "class=\"directory-tree\" hidden")
                assertContains(section, "data-path=\"modules/library/src/Api.kt\"")
            }
            val combined = "STATEFUL_CLASS_WITHOUT_EQUALS STATEFUL_CLASS_WITHOUT_HASH_CODE STATEFUL_CLASS_WITHOUT_TO_STRING"
            assertContains(applied, "data-diagnostics=\"$combined\" data-path=\"modules/library/src/model/State.kt\"")
            assertEquals(1, Regex("data-path=\"modules/library/src/model/State.kt\"").findAll(applied).count())
            assertContains(applied, "Unknown diagnostic")
            assertContains(applied, "@IntentionallyCustom")
            assertContains(applied, "class=\"fix-status applied\">Applied</span>")
            assertContains(html, "<option value=\"directory\">Directory tree</option>")
            assertContains(html, "<option value=\"STATEFUL_CLASS_WITHOUT_HASH_CODE\">Stateful classes without hashCode</option>")
            assertContains(html, "id=\"diagnostic-filter\"")
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `HTML escapes project and entry text`() {
        val report = ApiWatchdogProjectReport(
            projectPath = ":library<script>",
            projectDirectory = "",
            applied = emptyList(),
            notApplied = listOf(
                NotAppliedExemptionReportEntry(
                    annotation = null,
                    diagnostic = "A&B",
                    location = "Api.kt:1",
                    reason = "needs <manual> attention",
                    severity = "error",
                ),
            ),
            sourceSnippets = mapOf(
                "Api.kt:1" to ApiWatchdogSourceSnippet(1, "public val text = \"</code><script>alert('x')</script>\""),
            ),
        )
        val file = Files.createTempFile("exempts-report", ".html").toFile()
        try {
            ApiWatchdogHtmlReport.write(listOf(report), file, aggregate = true)
            val html = file.readText()
            assertContains(html, ":library&lt;script&gt;")
            assertContains(html, "A&amp;B")
            assertContains(html, "needs &lt;manual&gt; attention")
            assertContains(html, "&lt;/code&gt;&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;")
            assertFalse(html.contains("<script>alert('x')</script>"))
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `aggregate HTML uses saved snippets with line numbers and check links`() {
        val file = Files.createTempFile("exempts-report", ".data").toFile()
        val htmlFile = Files.createTempFile("exempts-report", ".html").toFile()
        val report = ApiWatchdogProjectReport(
            projectPath = ":library",
            projectDirectory = "modules/library",
            applied = listOf(AppliedExemptionReportEntry("IntentionallyOpen", "src/Existing.kt:10")),
            notApplied = listOf(
                NotAppliedExemptionReportEntry(
                    "IntentionallyOpen", "OPEN_API_WITHOUT_SUBCLASS_OPT_IN", "src/Api.kt:8", "Manual fix.", "warning",
                    messageParameters = listOf("class", "Api", "Apply `@SubclassOptInRequired`."),
                    sourceRange = ApiWatchdogSourceRange(8, 19, 8, 22),
                ),
            ),
            sourceSnippets = mapOf("src/Api.kt:8" to ApiWatchdogSourceSnippet(7, "/** An open API. */\npublic open class Api")),
        )
        try {
            ApiWatchdogReportData.write(report, file)
            ApiWatchdogHtmlReport.write(listOf(ApiWatchdogReportData.read(file)), htmlFile, aggregate = true)
            val html = htmlFile.readText()
            assertContains(html, "Aggregate report for 1 project")
            assertContains(html, "class=\"line-numbers\" data-start=\"7\" data-line=\"8\" data-line-offset=\"6\"")
            assertContains(html, "<code class=\"language-kotlin\">/** An open API. */\npublic open class Api</code>")
            assertContains(html, "checks/open-api-without-subclass-opt-in\">Open API without subclass opt-in</a>")
            assertContains(html, "class=\"severity warning\">warning</span>")
            assertContains(html, "data-severity=\"warning\" data-range-start=\"38\" data-range-end=\"41\"")
            assertContains(html, "<details class=\"diagnostic-message\"><summary>Diagnostic message</summary>")
            assertFalse(html.contains("<details open class=\"diagnostic-message\""))
            assertContains(html, "Users can subclass the class <code>Api</code> outside the library.")
            assertContains(html, "Apply <code>@SubclassOptInRequired</code>.")
            assertFalse(html.contains("{0}"))
            assertContains(html, "Source unavailable.")
            assertContains(html, "PrismJS 1.30.0")
            assertFalse(html.contains("<script src="))
            assertFalse(html.contains("@import"))
        } finally {
            file.toPath().deleteIfExists()
            htmlFile.toPath().deleteIfExists()
        }
    }

    @Test
    fun `HTML clips multiline ranges to the saved source context`() {
        val snippet = ApiWatchdogSourceSnippet(5, "public fun configure(\n    enabled: Boolean,\n    name: String,\n) = Unit")
        val entries = listOf(
            ApiWatchdogSourceRange(6, 5, 7, 17),
            ApiWatchdogSourceRange(1, 1, 6, 12),
            ApiWatchdogSourceRange(7, 5, 20, 1),
            ApiWatchdogSourceRange(6, 5, 6, 5),
            ApiWatchdogSourceRange(7, 200, 7, 205),
        ).mapIndexed { index, range ->
            NotAppliedExemptionReportEntry(null, "UNKNOWN", "Api$index.kt:5", "Manual fix.", "error", sourceRange = range)
        }
        val report = ApiWatchdogProjectReport(":library", "", emptyList(), entries, entries.associate { it.location to snippet })
        val file = Files.createTempFile("exempts-report-ranges", ".html").toFile()
        try {
            ApiWatchdogHtmlReport.write(listOf(report), file, aggregate = false)
            val html = file.readText()
            assertContains(html, "data-range-start=\"26\" data-range-end=\"60\"")
            assertContains(html, "data-range-start=\"0\" data-range-end=\"33\"")
            assertContains(html, "data-range-start=\"48\" data-range-end=\"70\"")
            assertEquals(3, Regex("data-range-start=\"\\d+\"").findAll(html).count())
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `empty HTML report has zero totals and no findings`() {
        val file = Files.createTempFile("exempts-report", ".html").toFile()
        try {
            ApiWatchdogHtmlReport.write(emptyList(), file, aggregate = true)
            val html = file.readText()
            assertContains(html, "Aggregate report for 0 projects")
            assertContains(html, "0 not applied")
            assertContains(html, "No diagnostics without an exemption.")
            assertContains(html, "No Watchdog @Intentionally* annotations found.")
            assertFalse(html.contains("<details"))
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `SARIF lists one rule for each diagnostic and one result for each entry`() {
        val library = ApiWatchdogProjectReport(
            projectPath = ":library",
            projectDirectory = "modules/library",
            applied = listOf(AppliedExemptionReportEntry("IntentionallyOpen", "src/Applied.kt:1")),
            notApplied = listOf(
                NotAppliedExemptionReportEntry(
                    annotation = "IntentionallyOpen",
                    diagnostic = "OPEN_API_WITHOUT_SUBCLASS_OPT_IN",
                    location = "src/main/kotlin/Open Api.kt:12",
                    reason = "exemptExistingApi can apply it.",
                    severity = "error",
                ),
                NotAppliedExemptionReportEntry(
                    annotation = null,
                    diagnostic = "UNDOCUMENTED_PUBLIC_API",
                    location = "src/main/kotlin/Open Api.kt:3",
                    reason = "exemptExistingApi can add a placeholder KDoc.",
                    severity = "warning",
                ),
            ),
        )
        val root = ApiWatchdogProjectReport(
            projectPath = ":",
            projectDirectory = "",
            applied = emptyList(),
            notApplied = listOf(
                NotAppliedExemptionReportEntry(
                    annotation = null,
                    diagnostic = "UNDOCUMENTED_PUBLIC_API",
                    location = "Root.kt:5",
                    reason = "exemptExistingApi can add a placeholder KDoc.",
                    severity = "warning",
                ),
            ),
        )
        val file = Files.createTempFile("exempts-report", ".sarif").toFile()
        try {
            ApiWatchdogSarifReport.write(listOf(library, root), file)
            val sarif = file.readText()
            assertContains(sarif, "\"version\": \"2.1.0\"")
            assertContains(sarif, "\"name\": \"Library API Watchdog\"")
            assertContains(sarif, "\"version\": \"${WatchdogCheckDescriptions.TOOL_VERSION}\"")
            assertEquals(2, Regex("\"shortDescription\"").findAll(sarif).count())
            assertContains(sarif, "\"id\": \"OPEN_API_WITHOUT_SUBCLASS_OPT_IN\"")
            assertContains(sarif, "\"text\": \"Open API without subclass opt-in\"")
            assertContains(sarif, "\"helpUri\": \"${WatchdogCheckDescriptions.DOCS_URL}checks/open-api-without-subclass-opt-in\"")
            assertEquals(3, Regex("\"ruleId\"").findAll(sarif).count())
            assertContains(sarif, "\"uri\": \"Root.kt\"")
            assertContains(sarif, "\"uri\": \"modules/library/src/main/kotlin/Open%20Api.kt\"")
            assertContains(sarif, "\"startLine\": 12")
            assertContains(sarif, "\"level\": \"error\"")
            assertContains(sarif, "\"level\": \"warning\"")
            assertContains(
                sarif,
                "\"text\": \"Open API without subclass opt-in. Exemption: @IntentionallyOpen. exemptExistingApi can apply it.\"",
            )
            // The root result comes first, then the library results ordered by location.
            val rootIndex = sarif.indexOf("Root.kt")
            val line3Index = sarif.indexOf("\"startLine\": 3")
            val line12Index = sarif.indexOf("\"startLine\": 12")
            assertTrue(rootIndex < line12Index && line12Index < line3Index, sarif)
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `SARIF escapes text and has no results without diagnostics`() {
        val report = ApiWatchdogProjectReport(
            projectPath = ":library",
            projectDirectory = "",
            applied = emptyList(),
            notApplied = listOf(
                NotAppliedExemptionReportEntry(
                    annotation = null,
                    diagnostic = "UNKNOWN_CHECK",
                    location = "Api.kt:1",
                    reason = "quote \" backslash \\ newline\n tab\t control\u0001",
                    severity = "warning",
                ),
            ),
        )
        val file = Files.createTempFile("exempts-report", ".sarif").toFile()
        try {
            ApiWatchdogSarifReport.write(listOf(report), file)
            val sarif = file.readText()
            assertContains(sarif, "quote \\\" backslash \\\\ newline\\n tab\\t control\\u0001")
            assertContains(sarif, "\"text\": \"UNKNOWN_CHECK\"")
            assertFalse(sarif.contains("helpUri"))

            ApiWatchdogSarifReport.write(listOf(report.copy(notApplied = emptyList())), file)
            val empty = file.readText()
            assertContains(empty, "\"rules\": []")
            assertContains(empty, "\"results\": []")
        } finally {
            file.toPath().deleteIfExists()
        }
    }
}
