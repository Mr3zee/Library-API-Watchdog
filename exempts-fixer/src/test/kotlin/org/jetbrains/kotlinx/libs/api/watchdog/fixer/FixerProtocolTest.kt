package org.jetbrains.kotlinx.libs.api.watchdog.fixer

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Comparator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    fun requestRoundTripsListsAndValuesWithEqualsSigns() {
        val request = FixerRequest.parse(
            file(
                "request.txt",
                """
                    reportFile=/project/build/reports/jvm=main.tsv
                    reportFile=/project/build/reports/js=main.tsv
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
        assertEquals(Paths.get("/tmp/response.txt"), request.responseFile)
    }

    @Test
    fun requestRejectsMissingAndDuplicatedSingleKeys() {
        assertFailsWith<IllegalArgumentException> {
            FixerRequest.parse(file("missing.txt", "reportFile=/tmp/report"))
        }
        assertFailsWith<IllegalArgumentException> {
            FixerRequest.parse(
                file("duplicated.txt", "responseFile=/tmp/a\nresponseFile=/tmp/b")
            )
        }
    }

    @Test
    fun requestRejectsMalformedLines() {
        assertFailsWith<IllegalArgumentException> {
            FixerRequest.parse(file("malformed.txt", "just some text"))
        }
    }

    @Test
    fun reportParsesAndDeduplicates() {
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
    fun reportsAreMergedAndDeduplicatedAcrossTargets() {
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
    fun missingReportMeansNoDiagnostics() {
        assertEquals(emptyList(), RecordedDiagnostic.parseReports(listOf(tempDir.resolve("absent.tsv"))))
    }

    @Test
    fun malformedReportLineFailsLoudly() {
        val report = file("broken.tsv", "UNDOCUMENTED_PUBLIC_API\t/p/A.kt\t10\n")
        assertFailsWith<IllegalArgumentException> { RecordedDiagnostic.parseReports(listOf(report)) }
    }

    @Test
    fun responseSerializesEveryEntryKind() {
        val response = FixerResponse().apply {
            applied += AppliedFix("DATA_CLASS_PUBLIC_API", "IntentionallyDataClass", "/p/A.kt", 3)
            skipped += SkippedDiagnostic("EXEMPTION_WITHOUT_EXPLANATION", "/p/B.kt", 7, "needs a human")
            modifiedFiles += "/p/A.kt"
        }
        val target = tempDir.resolve("response.txt")

        response.writeTo(target)

        val lines = Files.readAllLines(target).filter { it.isNotBlank() }
        assertEquals(
            listOf(
                "fixed=DATA_CLASS_PUBLIC_API\tIntentionallyDataClass\t3\t/p/A.kt",
                "skipped=EXEMPTION_WITHOUT_EXPLANATION\t7\t/p/B.kt\tneeds a human",
                "modifiedFile=/p/A.kt",
            ),
            lines,
        )
    }

    @Test
    fun skippedReasonWithLineBreaksStaysOnOneProtocolLine() {
        val response = FixerResponse().apply {
            skipped += SkippedDiagnostic("SOME_DIAGNOSTIC", "/p/B.kt", 7, "line one\nline two")
        }
        val target = tempDir.resolve("multiline.txt")

        response.writeTo(target)

        assertEquals(
            listOf("skipped=SOME_DIAGNOSTIC\t7\t/p/B.kt\tline one\\nline two"),
            Files.readAllLines(target).filter { it.isNotBlank() },
        )
    }

    @Test
    fun everyKnownDiagnosticHasAResolution() {
        val expected = setOf(
            "OPEN_API_WITHOUT_SUBCLASS_OPT_IN",
            "SUBCLASS_OPT_IN_WITHOUT_MARKERS",
            "EXHAUSTIVE_PUBLIC_API",
            "UNDOCUMENTED_PUBLIC_API",
            "FUNCTION_TYPE_ALIAS_PUBLIC_API",
            "DATA_CLASS_PUBLIC_API",
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
            "COMPANION_CONSTANT_WITHOUT_JVM_FIELD",
            "TOP_LEVEL_API_WITHOUT_JVM_NAME",
            "DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS",
            "EXEMPTION_WITHOUT_EXPLANATION",
            "DSL_MARKER_NOOP_TARGET",
            "DSL_MARKER_WITHOUT_EXPLICIT_TARGETS",
            "DSL_MARKER_NOOP_TYPE_POSITION",
        )
        assertEquals(expected, ExemptionRegistry.knownDiagnostics)

        val unfixable = expected.filter {
            ExemptionRegistry.resolutionFor(it) is FixResolution.Unfixable
        }
        assertEquals(
            setOf(
                "UNDOCUMENTED_PUBLIC_API",
                "EXEMPTION_WITHOUT_EXPLANATION",
                "DSL_MARKER_NOOP_TYPE_POSITION",
            ),
            unfixable.toSet(),
        )
    }

    @Test
    fun unknownDiagnosticResolvesToUnfixable() {
        val resolution = ExemptionRegistry.resolutionFor("NOT_A_DIAGNOSTIC")
        assertContains((resolution as FixResolution.Unfixable).reason, "Unknown diagnostic")
    }
}
