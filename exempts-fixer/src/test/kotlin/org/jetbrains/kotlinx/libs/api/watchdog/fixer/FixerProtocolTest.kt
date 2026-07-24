package org.jetbrains.kotlinx.libs.api.watchdog.fixer

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FixerProtocolTest {
    private val tempDir = createTempDirectory("fixer-protocol-test").toFile()

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun file(name: String, content: String): File =
        File(tempDir, name).apply { writeText(content) }

    @Test
    fun requestRoundTripsListsAndValuesWithEqualsSigns() {
        val request = FixerRequest.parse(
            file(
                "request.txt",
                """
                    source=/project/src/A.kt
                    source=/project/src/B.kt
                    compilerArg=-Xexplicit-api=warning
                    compilerArg=-P
                    compilerArg=plugin:some.id:diagnosticSeverity=UNDOCUMENTED_PUBLIC_API:none
                    pluginId=some.id
                    workDir=/tmp/work
                    responseFile=/tmp/response.txt
                """.trimIndent(),
            )
        )

        assertEquals(listOf(File("/project/src/A.kt"), File("/project/src/B.kt")), request.sources)
        assertEquals(
            listOf("-Xexplicit-api=warning", "-P", "plugin:some.id:diagnosticSeverity=UNDOCUMENTED_PUBLIC_API:none"),
            request.compilerArgs,
        )
        assertEquals("some.id", request.pluginId)
        assertEquals(File("/tmp/work"), request.workDir)
        assertEquals(File("/tmp/response.txt"), request.responseFile)
    }

    @Test
    fun requestRejectsMissingAndDuplicatedSingleKeys() {
        assertFailsWith<IllegalArgumentException> {
            FixerRequest.parse(file("missing.txt", "workDir=/tmp\nresponseFile=/tmp/r"))
        }
        assertFailsWith<IllegalArgumentException> {
            FixerRequest.parse(
                file("duplicated.txt", "pluginId=a\npluginId=b\nworkDir=/tmp\nresponseFile=/tmp/r")
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
            RecordedDiagnostic.parseReport(report),
        )
    }

    @Test
    fun missingReportMeansNoDiagnostics() {
        assertEquals(emptyList(), RecordedDiagnostic.parseReport(File(tempDir, "absent.tsv")))
    }

    @Test
    fun malformedReportLineFailsLoudly() {
        val report = file("broken.tsv", "UNDOCUMENTED_PUBLIC_API\t/p/A.kt\t10\n")
        assertFailsWith<IllegalArgumentException> { RecordedDiagnostic.parseReport(report) }
    }

    @Test
    fun responseSerializesEveryEntryKind() {
        val response = FixerResponse().apply {
            compilationResult = "COMPILATION_ERROR"
            applied += AppliedFix("DATA_CLASS_PUBLIC_API", "IntentionallyDataClass", "/p/A.kt", 3)
            skipped += SkippedDiagnostic("EXEMPTION_WITHOUT_EXPLANATION", "/p/B.kt", 7, "needs a human")
            modifiedFiles += "/p/A.kt"
            compilerMessages += "e: something\nbroke"
        }
        val target = File(tempDir, "response.txt")

        response.writeTo(target)

        val lines = target.readLines().filter { it.isNotBlank() }
        assertEquals(
            listOf(
                "compilationResult=COMPILATION_ERROR",
                "fixed=DATA_CLASS_PUBLIC_API\tIntentionallyDataClass\t3\t/p/A.kt",
                "skipped=EXEMPTION_WITHOUT_EXPLANATION\t7\t/p/B.kt\tneeds a human",
                "modifiedFile=/p/A.kt",
                "compilerMessage=e: something\\nbroke",
            ),
            lines,
        )
    }

    @Test
    fun skippedReasonWithLineBreaksStaysOnOneProtocolLine() {
        val response = FixerResponse().apply {
            skipped += SkippedDiagnostic("SOME_DIAGNOSTIC", "/p/B.kt", 7, "line one\nline two")
        }
        val target = File(tempDir, "multiline.txt")

        response.writeTo(target)

        assertEquals(
            listOf("skipped=SOME_DIAGNOSTIC\t7\t/p/B.kt\tline one\\nline two"),
            target.readLines().filter { it.isNotBlank() },
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
            setOf("SUBCLASS_OPT_IN_WITHOUT_MARKERS", "EXEMPTION_WITHOUT_EXPLANATION", "DSL_MARKER_NOOP_TYPE_POSITION"),
            unfixable.toSet(),
        )
    }

    @Test
    fun unknownDiagnosticResolvesToUnfixable() {
        val resolution = ExemptionRegistry.resolutionFor("NOT_A_DIAGNOSTIC")
        assertContains((resolution as FixResolution.Unfixable).reason, "unknown diagnostic")
    }
}
