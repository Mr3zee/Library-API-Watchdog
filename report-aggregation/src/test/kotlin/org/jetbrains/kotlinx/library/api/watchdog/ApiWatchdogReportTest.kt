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
    fun reportDataRoundTripsEscapedFields() {
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
    fun htmlEscapesProjectAndEntryText() {
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
        )
        val file = Files.createTempFile("exempts-report", ".html").toFile()
        try {
            ApiWatchdogHtmlReport.write(listOf(report), file, aggregate = true)
            val html = file.readText()
            assertContains(html, ":library&lt;script&gt;")
            assertContains(html, "A&amp;B")
            assertContains(html, "needs &lt;manual&gt; attention")
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun sarifListsOneRuleForEachDiagnosticAndOneResultForEachEntry() {
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
    fun sarifEscapesTextAndHasNoResultsWithoutDiagnostics() {
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
