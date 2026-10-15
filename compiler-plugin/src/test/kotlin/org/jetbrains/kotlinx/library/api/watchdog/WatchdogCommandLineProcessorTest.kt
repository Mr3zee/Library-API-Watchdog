package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlinx.library.api.watchdog.fir.DependencyDeclaration
import org.jetbrains.kotlinx.library.api.watchdog.fir.WatchdogDiagnostics
import org.jetbrains.kotlinx.library.api.watchdog.fir.WatchdogSeverity

class WatchdogCommandLineProcessorTest {
    private val processor = WatchdogCommandLineProcessor()
    @Suppress("OPT_IN_USAGE_ERROR") // The opt-in marker is unavailable on Kotlin 2.3.
    private val configuration = CompilerConfiguration()
    private val diagnostic = WatchdogDiagnostics.DATA_CLASS_PUBLIC_API.name
    private val otherDiagnostic = WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API.name

    @Test
    fun `unknown diagnostics list the configurable names`() {
        for ((option, description) in listOf(
            WatchdogCommandLineProcessor.DIAGNOSTIC_SEVERITY_OPTION to "Known diagnostics",
            WatchdogCommandLineProcessor.IGNORE_WHEN_ANNOTATED_OPTION to "Known configurable diagnostics",
        )) {
            for (name in listOf("UNKNOWN_DIAGNOSTIC", "PUBLIC_TYPE_WITH_INTERNAL_API", "")) {
                val error = assertFailsWith<CliOptionProcessingException> {
                    process(option, "$name:value")
                }
                assertEquals(
                    "Unknown watchdog diagnostic '$name'. $description: " +
                            WatchdogDiagnostics.allDiagnostics.joinToString { it.name },
                    error.message,
                )
            }
        }
        assertNull(configuration[WatchdogConfigurationKeys.DIAGNOSTIC_SEVERITIES])
        assertNull(configuration[WatchdogConfigurationKeys.ANNOTATION_IGNORE_RULES])
    }

    @Test
    fun `invalid severities preserve the existing override`() {
        val option = WatchdogCommandLineProcessor.DIAGNOSTIC_SEVERITY_OPTION
        process(option, "$diagnostic:error")

        for (value in listOf(diagnostic, "$diagnostic:", "$diagnostic:info", "$diagnostic: warning", "$diagnostic:error:warning")) {
            val severity = value.substringAfter(':', missingDelimiterValue = "")
            assertInvalid(
                option,
                value,
                "Invalid severity '$severity' for watchdog diagnostic '$diagnostic': " +
                        "expected 'error', 'warning', or 'none'.",
            )
            assertEquals(mapOf(diagnostic to WatchdogSeverity.ERROR), configuration[WatchdogConfigurationKeys.DIAGNOSTIC_SEVERITIES])
        }
    }

    @Test
    fun `severities accept mixed case and override only the repeated diagnostic`() {
        val option = WatchdogCommandLineProcessor.DIAGNOSTIC_SEVERITY_OPTION
        process(option, "$diagnostic:ErRoR")
        val original = configuration[WatchdogConfigurationKeys.DIAGNOSTIC_SEVERITIES]
        process(option, "$otherDiagnostic:WaRnInG")
        process(option, "$diagnostic:NoNe")

        assertEquals(mapOf(diagnostic to WatchdogSeverity.ERROR), original)
        assertEquals(
            mapOf(diagnostic to WatchdogSeverity.NONE, otherDiagnostic to WatchdogSeverity.WARNING),
            configuration[WatchdogConfigurationKeys.DIAGNOSTIC_SEVERITIES],
        )
    }

    @Test
    fun `boolean options accept true and false in mixed case`() {
        for ((option, key) in booleanOptions()) {
            process(option, "TrUe")
            assertEquals(true, configuration[key])
            process(option, "FaLsE")
            assertEquals(false, configuration[key])
        }
    }

    @Test
    fun `invalid boolean values preserve the configured value`() {
        for ((option, key) in booleanOptions()) {
            process(option, "true")
            for (value in listOf("", "yes", "1", " true", "false ")) {
                assertInvalid(
                    option,
                    value,
                    "Invalid value '$value' for watchdog option '${option.optionName}': expected 'true' or 'false'.",
                )
                assertEquals(true, configuration[key])
            }
        }
    }

    @Test
    fun `incomplete dependency declaration triples preserve existing declarations`() {
        val option = WatchdogCommandLineProcessor.DEPENDENCY_DECLARATIONS_OPTION
        process(option, "library.jar\timplementation\tcom.example:library:1.0")
        val original = configuration[WatchdogConfigurationKeys.DEPENDENCY_DECLARATIONS]

        for (value in listOf("library.jar", "library.jar\timplementation", "a\tb\tc\td", "a\tb\tc\td\te")) {
            assertInvalid(
                option,
                value,
                "Invalid value for watchdog option 'dependencyDeclarations': " +
                        "expected tab-separated path, configuration, and notation triples.",
            )
            assertEquals(original, configuration[WatchdogConfigurationKeys.DEPENDENCY_DECLARATIONS])
        }
    }

    @Test
    fun `dependency declarations preserve fields and use the last declaration for a path`() {
        process(
            WatchdogCommandLineProcessor.DEPENDENCY_DECLARATIONS_OPTION,
            "a library.jar\timplementation\tcom.example:library:1.0\t" +
                    "other.jar\tapi\tproject(\":other\")\t" +
                    "a library.jar\tcompileOnly\tfiles(\"a library.jar\")",
        )

        assertEquals(
            mapOf(
                "a library.jar" to DependencyDeclaration("compileOnly", "files(\"a library.jar\")"),
                "other.jar" to DependencyDeclaration("api", "project(\":other\")"),
            ),
            configuration[WatchdogConfigurationKeys.DEPENDENCY_DECLARATIONS],
        )

        process(WatchdogCommandLineProcessor.DEPENDENCY_DECLARATIONS_OPTION, "")
        assertEquals(emptyMap(), configuration[WatchdogConfigurationKeys.DEPENDENCY_DECLARATIONS])
    }

    @Test
    fun `invalid internal API annotations preserve existing annotations`() {
        val option = WatchdogCommandLineProcessor.INTERNAL_API_ANNOTATION_OPTION
        process(option, "com.example.InternalApi")

        for (value in listOf("", " \t", "com.example.Internal Api", "com.example.\tInternalApi", "com.example:InternalApi")) {
            assertInvalid(
                option,
                value,
                "Invalid value '$value' for watchdog option 'internalApiAnnotation': " +
                        "expected a fully qualified annotation class name.",
            )
            assertEquals(setOf("com.example.InternalApi"), configuration[WatchdogConfigurationKeys.INTERNAL_API_ANNOTATIONS])
        }
    }

    @Test
    fun `internal API annotations trim accumulate and deduplicate`() {
        val option = WatchdogCommandLineProcessor.INTERNAL_API_ANNOTATION_OPTION
        process(option, " com.example.InternalApi\t")
        val original = configuration[WatchdogConfigurationKeys.INTERNAL_API_ANNOTATIONS]
        process(option, "com.example.OtherInternalApi")
        process(option, "com.example.InternalApi")

        assertEquals(setOf("com.example.InternalApi"), original)
        assertEquals(
            setOf("com.example.InternalApi", "com.example.OtherInternalApi"),
            configuration[WatchdogConfigurationKeys.INTERNAL_API_ANNOTATIONS],
        )
    }

    @Test
    fun `invalid ignore annotations preserve existing rules`() {
        val option = WatchdogCommandLineProcessor.IGNORE_WHEN_ANNOTATED_OPTION
        process(option, "$diagnostic:com.example.Exempt")

        for (value in listOf(diagnostic, "$diagnostic:", "$diagnostic: \t", "$diagnostic:com.example:Exempt")) {
            val annotation = value.substringAfter(':', missingDelimiterValue = "")
            assertInvalid(
                option,
                value,
                "Invalid annotation name '$annotation' for watchdog diagnostic '$diagnostic': " +
                        "expected a fully qualified class name.",
            )
            assertEquals(
                mapOf(diagnostic to setOf("com.example.Exempt")),
                configuration[WatchdogConfigurationKeys.ANNOTATION_IGNORE_RULES],
            )
        }
    }

    @Test
    fun `ignore annotations accumulate and deduplicate per diagnostic`() {
        val option = WatchdogCommandLineProcessor.IGNORE_WHEN_ANNOTATED_OPTION
        process(option, "$diagnostic:com.example.Exempt")
        val original = configuration[WatchdogConfigurationKeys.ANNOTATION_IGNORE_RULES]
        process(option, "$diagnostic:com.example.OtherExempt")
        process(option, "$otherDiagnostic:com.example.Exempt")
        process(option, "$diagnostic:com.example.Exempt")

        assertEquals(mapOf(diagnostic to setOf("com.example.Exempt")), original)
        assertEquals(
            mapOf(
                diagnostic to setOf("com.example.Exempt", "com.example.OtherExempt"),
                otherDiagnostic to setOf("com.example.Exempt"),
            ),
            configuration[WatchdogConfigurationKeys.ANNOTATION_IGNORE_RULES],
        )
    }

    @Test
    fun `blank diagnostics output values preserve the tooling path`() {
        val option = WatchdogCommandLineProcessor.DIAGNOSTICS_OUTPUT_FILE_OPTION
        process(option, "")
        assertNull(configuration[WatchdogConfigurationKeys.DIAGNOSTICS_OUTPUT_FILE])
        process(option, "tooling/report.tsv")
        process(option, " \t")
        assertEquals("tooling/report.tsv", configuration[WatchdogConfigurationKeys.DIAGNOSTICS_OUTPUT_FILE])
        process(option, "other/report.tsv")
        assertEquals("other/report.tsv", configuration[WatchdogConfigurationKeys.DIAGNOSTICS_OUTPUT_FILE])
        assertTrue(option.allowMultipleOccurrences)
    }

    @Test
    fun `dependency paths discard empty entries and duplicates`() {
        for ((option, key) in listOf(
            WatchdogCommandLineProcessor.COMPILE_DEPENDENCY_PATHS_OPTION to WatchdogConfigurationKeys.COMPILE_DEPENDENCY_PATHS,
            WatchdogCommandLineProcessor.TRANSITIVE_DEPENDENCY_PATHS_OPTION to WatchdogConfigurationKeys.TRANSITIVE_DEPENDENCY_PATHS,
        )) {
            process(option, listOf("", "first.jar", "", "second library.jar", "first.jar", "").joinToString(File.pathSeparator))
            assertEquals(setOf("first.jar", "second library.jar"), configuration[key])
        }
    }

    @Test
    fun `blank dependency paths leave compile paths absent and transitive paths empty`() {
        process(WatchdogCommandLineProcessor.COMPILE_DEPENDENCY_PATHS_OPTION, " \t")
        assertNull(configuration[WatchdogConfigurationKeys.COMPILE_DEPENDENCY_PATHS])
        process(WatchdogCommandLineProcessor.TRANSITIVE_DEPENDENCY_PATHS_OPTION, "")
        assertEquals(emptySet(), configuration[WatchdogConfigurationKeys.TRANSITIVE_DEPENDENCY_PATHS])
    }

    private fun booleanOptions() = listOf(
        WatchdogCommandLineProcessor.PUBLIC_TYPE_WITH_INTERNAL_API_OPTION to WatchdogConfigurationKeys.PUBLIC_TYPE_WITH_INTERNAL_API_ENABLED,
        WatchdogCommandLineProcessor.EXEMPTING_EXISTING_API_OPTION to WatchdogConfigurationKeys.UPDATING_BACKWARDS_COMPATIBILITY_EXEMPTS,
        WatchdogCommandLineProcessor.REPORT_UNDOCUMENTED_CLASS_PROPERTIES_OPTION to WatchdogConfigurationKeys.REPORT_UNDOCUMENTED_CLASS_PROPERTIES,
    )

    private fun process(option: CliOption, value: String) {
        processor.processOption(option, value, configuration)
    }

    private fun assertInvalid(option: CliOption, value: String, expectedMessage: String) {
        val error = assertFailsWith<CliOptionProcessingException> { process(option, value) }
        assertEquals(expectedMessage, error.message)
    }
}
