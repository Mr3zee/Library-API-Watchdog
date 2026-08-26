package org.jetbrains.kotlinx.library.api.watchdog

import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class BackwardsCompatibilityExemptsReportTest {
    @Test
    fun reportDataRoundTripsEscapedFields() {
        val report = BackwardsCompatibilityExemptsProjectReport(
            projectPath = ":library",
            applied = listOf(AppliedExemptionReportEntry("IntentionallyOpen", "src/Api.kt:4")),
            notApplied = listOf(
                NotAppliedExemptionReportEntry(
                    annotation = null,
                    diagnostic = "UNDOCUMENTED_PUBLIC_API",
                    location = "src/Tabbed\tApi.kt:7",
                    reason = "line one\nline two\\tail",
                ),
            ),
        )
        val file = Files.createTempFile("exempts-report", ".data").toFile()
        try {
            BackwardsCompatibilityExemptsReportData.write(report, file)
            assertEquals(report, BackwardsCompatibilityExemptsReportData.read(file))
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun htmlEscapesProjectAndEntryText() {
        val report = BackwardsCompatibilityExemptsProjectReport(
            projectPath = ":library<script>",
            applied = emptyList(),
            notApplied = listOf(
                NotAppliedExemptionReportEntry(
                    annotation = null,
                    diagnostic = "A&B",
                    location = "Api.kt:1",
                    reason = "needs <manual> attention",
                ),
            ),
        )
        val file = Files.createTempFile("exempts-report", ".html").toFile()
        try {
            BackwardsCompatibilityExemptsHtmlReport.write(listOf(report), file, aggregate = true)
            val html = file.readText()
            assertContains(html, ":library&lt;script&gt;")
            assertContains(html, "A&amp;B")
            assertContains(html, "needs &lt;manual&gt; attention")
        } finally {
            file.toPath().deleteIfExists()
        }
    }
}
