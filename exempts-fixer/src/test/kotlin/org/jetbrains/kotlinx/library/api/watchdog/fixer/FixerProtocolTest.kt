package org.jetbrains.kotlinx.library.api.watchdog.fixer

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Comparator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FixerProtocolTest {
    private val tempDir = Files.createTempDirectory("fixer-protocol-test")

    @AfterTest
    fun tearDown() {
        Files.walk(tempDir).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private fun file(name: String, content: String): Path =
        tempDir.resolve(name).also { Files.writeString(it, content) }

    @Test
    fun `request round-trips lists and values with equals signs`() {
        val request = FixerRequest.parse(
            file(
                "request.txt",
                """
                    reportFile=/project/build/reports/jvm=main.tsv
                    reportFile=/project/build/reports/js=main.tsv
                    sourceFile=/project/src/commonMain/kotlin/Common.kt
                    diagnosticSeverity=UNDOCUMENTED_PUBLIC_API:warning
                    diagnosticSeverity=DATA_CLASS_PUBLIC_API:error
                    updateSources=false
                    responseFile=/tmp/response.txt
                """.trimIndent(),
            )
        )

        assertEquals(
            listOf(
                Paths.get("/project/build/reports/jvm=main.tsv"),
                Paths.get("/project/build/reports/js=main.tsv"),
            ),
            request.reportFiles,
        )
        assertEquals(
            listOf(Paths.get("/project/src/commonMain/kotlin/Common.kt")),
            request.sourceFiles,
        )
        assertEquals(
            mapOf("UNDOCUMENTED_PUBLIC_API" to "warning", "DATA_CLASS_PUBLIC_API" to "error"),
            request.diagnosticSeverities,
        )
        assertEquals(false, request.updateSources)
        assertEquals(Paths.get("/tmp/response.txt"), request.responseFile)
    }

    @Test
    fun `request fixes errors only`() {
        val request = FixerRequest.parse(
            file(
                "severities.txt",
                """
                    diagnosticSeverity=UNDOCUMENTED_PUBLIC_API:warning
                    diagnosticSeverity=DATA_CLASS_PUBLIC_API:error
                    updateSources=true
                    responseFile=/tmp/response.txt
                """.trimIndent(),
            )
        )

        assertEquals(false, request.fixes("UNDOCUMENTED_PUBLIC_API"))
        assertEquals(true, request.fixes("DATA_CLASS_PUBLIC_API"))
        assertEquals(true, request.fixes("EXEMPTION_WITHOUT_EXPLANATION"))
        assertEquals(true, request.fixes("REDUNDANT_EXEMPTION"))
    }

    @Test
    fun `request rejects malformed severity`() {
        assertFailsWith<IllegalArgumentException> {
            FixerRequest.parse(
                file(
                    "badSeverity.txt",
                    "diagnosticSeverity=UNDOCUMENTED_PUBLIC_API\nupdateSources=true\nresponseFile=/tmp/a",
                )
            )
        }
    }

    @Test
    fun `request rejects missing and duplicated single keys`() {
        assertFailsWith<IllegalArgumentException> {
            FixerRequest.parse(
                file("missing.txt", "reportFile=/tmp/report\nupdateSources=true")
            )
        }
        assertFailsWith<IllegalArgumentException> {
            FixerRequest.parse(
                file(
                    "duplicated.txt",
                    "updateSources=true\nresponseFile=/tmp/a\nresponseFile=/tmp/b",
                )
            )
        }
    }

    @Test
    fun `request rejects malformed lines`() {
        assertFailsWith<IllegalArgumentException> {
            FixerRequest.parse(file("malformed.txt", "just some text"))
        }
    }

    @Test
    fun `report parses and deduplicates`() {
        val report = file(
            "report.tsv",
            "UNDOCUMENTED_PUBLIC_API\t/p/A.kt\t10\t20\n" +
                    "UNDOCUMENTED_PUBLIC_API\t/p/A.kt\t10\t20\n" +
                    "DATA_CLASS_PUBLIC_API\t/p/B.kt\t0\t5\n",
        )

        assertEquals(
            listOf(
                RecordedDiagnostic("UNDOCUMENTED_PUBLIC_API", "/p/A.kt", 10, 20),
                RecordedDiagnostic("DATA_CLASS_PUBLIC_API", "/p/B.kt", 0, 5),
            ),
            RecordedDiagnostic.parseReports(listOf(report)),
        )
    }

    @Test
    fun `report decodes message parameters after the reported range`() {
        val report = file(
            "parameters.tsv",
            "UNDOCUMENTED_PUBLIC_API\t/p/A.kt\t10\t20\t12\t18\tfunction\tline\\tone\\nline\\rtwo\\\\tail\t\n",
        )
        val diagnostic = RecordedDiagnostic.parseReports(listOf(report)).single()
        assertEquals(12, diagnostic.reportedStartOffset)
        assertEquals(18, diagnostic.reportedEndOffset)
        assertEquals(
            listOf("function", "line\tone\nline\rtwo\\tail", ""),
            diagnostic.messageParameters,
        )
    }

    @Test
    fun `reports are merged and deduplicated across targets`() {
        val jvm = file("jvm.tsv", "UNDOCUMENTED_PUBLIC_API\t/p/A.kt\t10\t20\n")
        val js = file(
            "js.tsv",
            "UNDOCUMENTED_PUBLIC_API\t/p/A.kt\t10\t20\n" +
                    "DATA_CLASS_PUBLIC_API\t/p/B.kt\t0\t5\n",
        )

        assertEquals(
            listOf(
                RecordedDiagnostic("UNDOCUMENTED_PUBLIC_API", "/p/A.kt", 10, 20),
                RecordedDiagnostic("DATA_CLASS_PUBLIC_API", "/p/B.kt", 0, 5),
            ),
            RecordedDiagnostic.parseReports(listOf(jvm, js)),
        )
    }

    @Test
    fun `missing report means no diagnostics`() {
        assertEquals(emptyList(), RecordedDiagnostic.parseReports(listOf(tempDir.resolve("absent.tsv"))))
    }

    @Test
    fun `malformed report line throws`() {
        val report = file("broken.tsv", "UNDOCUMENTED_PUBLIC_API\t/p/A.kt\t10\n")
        assertFailsWith<IllegalArgumentException> { RecordedDiagnostic.parseReports(listOf(report)) }
    }

    @Test
    fun `response serializes every entry kind`() {
        val response = FixerResponse().apply {
            applied += AppliedFix("DATA_CLASS_PUBLIC_API", "IntentionallyDataClass", "/p/A.kt", 3)
            skipped += SkippedDiagnostic("EXEMPTION_WITHOUT_EXPLANATION", "/p/B.kt", 7, "needs a human")
            warnings += WarningDiagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", "/p/C.kt", 4)
            exemptions += AppliedExemption("IntentionallyDataClass", "/p/A.kt", 2)
            modifiedFiles += "/p/A.kt"
        }
        val target = tempDir.resolve("response.txt")

        response.writeTo(target)

        val lines = Files.readAllLines(target).filter { it.isNotBlank() }
        assertEquals(
            listOf(
                "fixed=DATA_CLASS_PUBLIC_API\tIntentionallyDataClass\t3\t/p/A.kt\t\t\t\t",
                "skipped=EXEMPTION_WITHOUT_EXPLANATION\t\t7\t/p/B.kt\tneeds a human\t\t\t\t",
                "warning=OPEN_API_WITHOUT_SUBCLASS_OPT_IN\tIntentionallyOpen\t4\t/p/C.kt\t\t\t\t",
                "exemption=IntentionallyDataClass\t2\t/p/A.kt",
                "modifiedFile=/p/A.kt",
            ),
            lines,
        )
    }

    @Test
    fun `skipped and warned redundant exemptions name the annotation of the message`() {
        val response = FixerResponse().apply {
            skipped += SkippedDiagnostic(
                "REDUNDANT_EXEMPTION", "/p/B.kt", 7, "No source element found at the recorded position",
                listOf("IntentionallyOpen", "declaration"),
            )
            warnings += WarningDiagnostic("REDUNDANT_EXEMPTION", "/p/C.kt", 4, listOf("IntentionallyDataClass", "declaration"))
        }
        val target = tempDir.resolve("redundant.txt")

        response.writeTo(target)

        val lines = Files.readAllLines(target).filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("skipped=REDUNDANT_EXEMPTION\tIntentionallyOpen\t7\t/p/B.kt\t"), lines[0])
        assertTrue(lines[1].startsWith("warning=REDUNDANT_EXEMPTION\tIntentionallyDataClass\t4\t/p/C.kt\t"), lines[1])
    }

    @Test
    fun `skipped reason with line breaks stays on one protocol line`() {
        val response = FixerResponse().apply {
            skipped += SkippedDiagnostic("SOME_DIAGNOSTIC", "/p/B.kt", 7, "line one\nline two")
        }
        val target = tempDir.resolve("multiline.txt")

        response.writeTo(target)

        assertEquals(
            listOf("skipped=SOME_DIAGNOSTIC\t\t7\t/p/B.kt\tline one\\nline two\t\t\t\t"),
            Files.readAllLines(target).filter { it.isNotBlank() },
        )
    }

    @Test
    fun `response escapes message parameters for fixes skips and warnings`() {
        val response = FixerResponse().apply {
            val parameters = listOf("class", "Name\twith\nline\\tail")
            val range = DiagnosticSourceRange(3, 19, 3, 23)
            applied += AppliedFix("DATA_CLASS_PUBLIC_API", "IntentionallyDataClass", "/p/A.kt", 3, parameters, range)
            skipped += SkippedDiagnostic("UNKNOWN", "/p/B.kt", 7, "Manual\tfix.", parameters, range)
            warnings += WarningDiagnostic("UNDOCUMENTED_PUBLIC_API", "/p/C.kt", 4, parameters, range)
        }
        val target = tempDir.resolve("parameters.txt")
        response.writeTo(target)
        val lines = Files.readAllLines(target)
        assertEquals(3, lines.size)
        lines.forEach { assertTrue(it.endsWith("\t3\t19\t3\t23\tclass\tName\\twith\\nline\\\\tail")) }
        assertContains(lines[1], "Manual\\tfix.")
    }

    @Test
    fun `every known diagnostic has a resolution`() {
        val expected = setOf(
            "OPEN_API_WITHOUT_SUBCLASS_OPT_IN",
            "SUBCLASS_OPT_IN_WITHOUT_MARKERS",
            "EXHAUSTIVE_PUBLIC_API",
            "UNDOCUMENTED_PUBLIC_API",
            "FUNCTION_TYPE_ALIAS_PUBLIC_API",
            "DATA_CLASS_PUBLIC_API",
            "STATEFUL_CLASS_WITHOUT_EQUALS",
            "STATEFUL_CLASS_WITHOUT_HASH_CODE",
            "STATEFUL_CLASS_WITHOUT_TO_STRING",
            "MUTABLE_COLLECTION_PUBLIC_API",
            "PAIR_OR_TRIPLE_PUBLIC_API",
            "BOOLEAN_PARAMETER_PUBLIC_API",
            "NULLABLE_BOOLEAN_PUBLIC_API",
            "REQUIRED_PARAMETER_AFTER_OPTIONAL",
            "INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS",
            "INLINE_FUNCTION_WITH_LOGIC",
            "MANGLED_JVM_NAME_PUBLIC_API",
            "KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC",
            "COMPANION_API_WITHOUT_JVM_STATIC",
            "COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS",
            "TOP_LEVEL_API_WITHOUT_JVM_NAME",
            "DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS",
            "EXEMPTION_WITHOUT_EXPLANATION",
            "REDUNDANT_EXEMPTION",
            "DSL_MARKER_NOOP_TARGET",
            "DSL_MARKER_WITHOUT_EXPLICIT_TARGETS",
            "DSL_MARKER_NOOP_TYPE_POSITION",
            "PUBLIC_TYPE_WITH_INTERNAL_API",
            "PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY",
        )
        assertEquals(expected, ExemptionRegistry.knownDiagnostics)

        val unfixable = expected.filter {
            ExemptionRegistry.resolutionFor(it) is FixResolution.Unfixable
        }
        assertEquals(
            setOf(
                "EXEMPTION_WITHOUT_EXPLANATION",
                "DSL_MARKER_NOOP_TYPE_POSITION",
                "PUBLIC_TYPE_WITH_INTERNAL_API",
                "PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY",
            ),
            unfixable.toSet(),
        )
    }

    @Test
    fun `unknown diagnostic resolves to unfixable`() {
        val resolution = ExemptionRegistry.resolutionFor("NOT_A_DIAGNOSTIC")
        assertContains((resolution as FixResolution.Unfixable).reason, "Unknown diagnostic")
    }
}
