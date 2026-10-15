package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiWatchdogReportDataTest {
    @Test
    fun `version 3 preserves source snippets and infers available fixes`() {
        val report = read(
            "version\t3\nproject\t:library\ndirectory\tmodules/library\n" +
                "applied\tIntentionallyOpen\tsrc/Api.kt:2\n" +
                "notApplied\tIntentionallyOpen\tOPEN_API_WITHOUT_SUBCLASS_OPT_IN\tsrc/Api.kt:7\tManual fix.\twarning\n" +
                "notApplied\t\tUNDOCUMENTED_PUBLIC_API\tsrc/Api.kt:8\tAdd documentation.\terror\n" +
                "notApplied\t\tUNKNOWN\tsrc/Api.kt:9\tManual fix.\twarning\n" +
                "source\tsrc/Api.kt:7\t6\t/** API. */\\npublic open class Api\n"
        )

        assertEquals(
            ApiWatchdogProjectReport(
                projectPath = ":library",
                projectDirectory = "modules/library",
                applied = listOf(AppliedExemptionReportEntry("IntentionallyOpen", "src/Api.kt:2")),
                notApplied = listOf(
                    NotAppliedExemptionReportEntry(
                        "IntentionallyOpen", "OPEN_API_WITHOUT_SUBCLASS_OPT_IN", "src/Api.kt:7", "Manual fix.", "warning",
                        autoFixAvailable = true,
                    ),
                    NotAppliedExemptionReportEntry(
                        null, "UNDOCUMENTED_PUBLIC_API", "src/Api.kt:8", "Add documentation.", "error",
                        autoFixAvailable = true,
                    ),
                    NotAppliedExemptionReportEntry(null, "UNKNOWN", "src/Api.kt:9", "Manual fix.", "warning"),
                ),
                sourceSnippets = mapOf("src/Api.kt:7" to ApiWatchdogSourceSnippet(6, "/** API. */\npublic open class Api")),
            ),
            report,
        )
    }

    @Test
    fun `supported versions accept an empty root directory and blank lines`() {
        for (version in 2..6) {
            val report = read("\nversion\t$version\n\nproject\t:\n \ndirectory\t\n\n")
            assertEquals(ApiWatchdogProjectReport(":", "", emptyList(), emptyList()), report)
        }
    }

    @Test
    fun `versions 5 and 6 preserve absent empty and multiline ranges`() {
        for (version in 5..6) {
            for (range in listOf(null, ApiWatchdogSourceRange(7, 3, 7, 3), ApiWatchdogSourceRange(7, 19, 8, 2))) {
                val coordinates = range?.let {
                    listOf(it.startLine, it.startColumn, it.endLine, it.endColumn).joinToString("\t")
                } ?: "\t\t\t"
                val autoFix = if (version == 6) "\tfalse" else ""
                val report = read(headers(version) + "notApplied\t\tUNKNOWN\tsrc/Api.kt:7\tManual fix.\twarning\t$coordinates$autoFix\tclass\t\n")
                assertEquals(
                    NotAppliedExemptionReportEntry(
                        null, "UNKNOWN", "src/Api.kt:7", "Manual fix.", "warning",
                        messageParameters = listOf("class", ""), sourceRange = range,
                    ),
                    report.notApplied.single(),
                )
            }
        }
    }

    @Test
    fun `current version preserves explicit fix flags regardless of annotation`() {
        val report = read(headers() +
            "notApplied\tIntentionallyOpen\tUNKNOWN\tsrc/Api.kt:7\tManual fix.\twarning\t\t\t\t\tfalse\n" +
            "notApplied\t\tUNKNOWN\tsrc/Api.kt:8\tManual fix.\twarning\t\t\t\t\ttrue\n")
        assertEquals(listOf(false, true), report.notApplied.map { it.autoFixAvailable })
    }

    @Test
    fun `report fields round trip every escape and trailing empty parameters`() = withFile("") { file ->
        val text = "tab\tline\nreturn\rcarriage\\tail\\"
        val report = ApiWatchdogProjectReport(
            projectPath = text,
            projectDirectory = text,
            applied = listOf(AppliedExemptionReportEntry(text, text)),
            notApplied = listOf(NotAppliedExemptionReportEntry(text, text, text, text, text, listOf(text, ""))),
            sourceSnippets = mapOf(text to ApiWatchdogSourceSnippet(Int.MAX_VALUE, text)),
        )
        ApiWatchdogReportData.write(report, file)
        assertEquals(report, ApiWatchdogReportData.read(file))
    }

    @Test
    fun `unsupported and malformed version entries identify the file and line`() {
        for (entry in listOf("version\t1", "version\t7", "version\tsix", "version", "version\t", "version\t6\textra")) {
            assertInvalid("$entry\nproject\t:library\ndirectory\tlib\n", "Unsupported API Watchdog report version", 1)
        }
    }

    @Test
    fun `required headers cannot be omitted`() {
        for ((data, message) in listOf(
            "" to "No version entry",
            "project\t:library\ndirectory\tlib\n" to "No version entry",
            "version\t6\ndirectory\tlib\n" to "No project entry",
            "version\t6\nproject\t:library\n" to "No directory entry",
        )) {
            assertInvalid(data, message)
        }
    }

    @Test
    fun `diagnostics require a preceding version entry`() {
        assertInvalid(
            "project\t:library\ndirectory\tlib\n" +
                "notApplied\t\tUNKNOWN\tsrc/Api.kt:7\tManual fix.\twarning\nversion\t6\n",
            "No version entry before notApplied",
            3,
        )
    }

    @Test
    fun `fixed width records reject missing and extra fields`() {
        for ((entry, message) in listOf(
            "project" to "Malformed project entry",
            "project\t:library\textra" to "Malformed project entry",
            "directory" to "Malformed directory entry",
            "directory\tlib\textra" to "Malformed directory entry",
            "applied" to "Malformed applied entry",
            "applied\tIntentionallyOpen" to "Malformed applied entry",
            "applied\tIntentionallyOpen\tsrc/Api.kt:7\textra" to "Malformed applied entry",
            "source" to "Malformed source entry",
            "source\tsrc/Api.kt:7\t7" to "Malformed source entry",
            "source\tsrc/Api.kt:7\t7\tcode\textra" to "Malformed source entry",
        )) {
            assertInvalid(headers() + "$entry\n", message, 4)
        }
    }

    @Test
    fun `every supported version rejects truncated diagnostic records`() {
        val fields = listOf("notApplied", "", "UNKNOWN", "src/Api.kt:7", "Manual fix.", "warning", "", "", "", "", "false")
        for ((version, requiredFields) in listOf(2 to 6, 3 to 6, 4 to 6, 5 to 10, 6 to 11)) {
            for (size in 1 until requiredFields) {
                assertInvalid(headers(version) + fields.take(size).joinToString("\t") + "\n", "Malformed not-applied entry", 4)
            }
        }
    }

    @Test
    fun `unknown record kinds identify their physical line after blank lines`() = withFile(
        headers() + "\n \nunknown\tvalue\n"
    ) { file ->
        val failure = assertFailsWith<IllegalStateException> { ApiWatchdogReportData.read(file) }
        assertContains(failure.message.orEmpty(), "Unknown API Watchdog report entry")
        assertContains(failure.message.orEmpty(), "$file:6")
    }

    @Test
    fun `source lines require positive integers within the integer range`() {
        for (line in listOf("", "seven", "0", "-1", "2147483648")) {
            assertInvalid(headers() + "source\tsrc/Api.kt:7\t$line\tcode\n", "Invalid source line", 4)
        }
    }

    @Test
    fun `source ranges reject partial coordinates`() {
        for (version in 5..6) {
            for (index in 0..3) {
                val coordinates = mutableListOf("7", "19", "8", "2").apply { this[index] = "" }
                assertInvalid(diagnosticWithRange(version, coordinates), "Invalid source range", 4)
            }
        }
    }

    @Test
    fun `source range coordinates require positive integers within the integer range`() {
        for (version in 5..6) {
            for (index in 0..3) {
                for (value in listOf("seven", "0", "-1", "2147483648")) {
                    val coordinates = mutableListOf("7", "19", "8", "2").apply { this[index] = value }
                    assertInvalid(diagnosticWithRange(version, coordinates), "Invalid source range", 4)
                }
            }
        }
    }

    @Test
    fun `source ranges cannot end before their start`() {
        for (version in 5..6) {
            for (coordinates in listOf(listOf("8", "1", "7", "10"), listOf("7", "10", "7", "1"))) {
                assertInvalid(diagnosticWithRange(version, coordinates), "Invalid source range", 4)
            }
        }
    }

    @Test
    fun `fix flags accept only lowercase true or false`() {
        for (flag in listOf("", "TRUE", "False", "yes", "0")) {
            assertInvalid(
                headers() + "notApplied\t\tUNKNOWN\tsrc/Api.kt:7\tManual fix.\twarning\t\t\t\t\t$flag\n",
                "Invalid auto-fix flag",
                4,
            )
        }
    }

    @Test
    fun `malformed records identify their physical line after blank lines`() {
        assertInvalid(headers() + "\n\nsource\tmissing fields\n", "Malformed source entry", 6)
    }

    private fun headers(version: Int = 6) = "version\t$version\nproject\t:library\ndirectory\tlib\n"

    private fun diagnosticWithRange(version: Int, coordinates: List<String>): String =
        headers(version) + "notApplied\t\tUNKNOWN\tsrc/Api.kt:7\tManual fix.\twarning\t" +
            coordinates.joinToString("\t") + (if (version == 6) "\tfalse" else "") + "\n"

    private fun read(data: String): ApiWatchdogProjectReport = withFile(data, ApiWatchdogReportData::read)

    private fun assertInvalid(data: String, message: String, line: Int? = null) = withFile(data) { file ->
        val failure = assertFailsWith<IllegalArgumentException>(data) { ApiWatchdogReportData.read(file) }
        assertContains(failure.message.orEmpty(), message)
        assertContains(failure.message.orEmpty(), if (line == null) file.toString() else "$file:$line")
    }

    private fun <T> withFile(data: String, action: (File) -> T): T {
        val file = Files.createTempFile("watchdog-report-data", ".data").toFile()
        try {
            file.writeText(data)
            return action(file)
        } finally {
            file.toPath().deleteIfExists()
        }
    }
}
