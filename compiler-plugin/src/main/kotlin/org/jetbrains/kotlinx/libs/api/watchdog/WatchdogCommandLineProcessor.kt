package org.jetbrains.kotlinx.libs.api.watchdog

import java.io.File
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCLP
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.library.api.watchdog.PluginInfo
import org.jetbrains.kotlinx.libs.api.watchdog.fir.WatchdogDiagnostics
import org.jetbrains.kotlinx.libs.api.watchdog.fir.WatchdogSeverity

object WatchdogConfigurationKeys {
    /**
     * Severity overrides keyed by diagnostic name; diagnostics not listed here are errors, and
     * [WatchdogSeverity.NONE] disables a diagnostic entirely.
     */
    val DIAGNOSTIC_SEVERITIES: CompilerConfigurationKey<Map<String, WatchdogSeverity>> =
        CompilerConfigurationKey.create("watchdog diagnostic severities")

    /**
     * Path of the file every reported watchdog diagnostic is appended to as a tab-separated
     * line; see [org.jetbrains.kotlinx.libs.api.watchdog.fir.WatchdogDiagnosticsRecorder].
     */
    val DIAGNOSTICS_OUTPUT_FILE: CompilerConfigurationKey<String> =
        CompilerConfigurationKey.create("watchdog diagnostics output file")

    /**
     * The compilation classpath and the subset published transitively to consumers. These are
     * supplied only by the Gradle plugin: without the build model the compiler cannot tell an
     * `api` dependency from an `implementation` dependency.
     */
    val COMPILE_DEPENDENCY_PATHS: CompilerConfigurationKey<Set<String>> =
        CompilerConfigurationKey.create("watchdog compile dependency paths")

    val TRANSITIVE_DEPENDENCY_PATHS: CompilerConfigurationKey<Set<String>> =
        CompilerConfigurationKey.create("watchdog transitive dependency paths")
}

class WatchdogCommandLineProcessor : DevKitCommandLineProcessor(WatchdogCLP::class) {
    override val pluginId: String = PluginInfo.PLUGIN_ID
    override val pluginOptions: Collection<CliOption> =
        listOf(
            DIAGNOSTIC_SEVERITY_OPTION,
            DIAGNOSTICS_OUTPUT_FILE_OPTION,
            COMPILE_DEPENDENCY_PATHS_OPTION,
            TRANSITIVE_DEPENDENCY_PATHS_OPTION,
        )

    companion object {
        val DIAGNOSTIC_SEVERITY_OPTION: CliOption = CliOption(
            optionName = "diagnosticSeverity",
            valueDescription = "<diagnostic name>:error|warning|none",
            description = "Report the named watchdog diagnostic with the given severity, " +
                    "or disable its check with 'none'. " +
                    "Every diagnostic not mentioned is reported as an error.",
            required = false,
            allowMultipleOccurrences = true,
        )

        val DIAGNOSTICS_OUTPUT_FILE_OPTION: CliOption = CliOption(
            optionName = "diagnosticsOutputFile",
            valueDescription = "<path>",
            description = "Append every reported watchdog diagnostic to the given file as a " +
                    "tab-separated line: diagnostic name, source file path, start offset, " +
                    "end offset. Meant for tooling; the Gradle plugin's " +
                    "updateBackwardsCompatibilityExempts task consumes it.",
            required = false,
            allowMultipleOccurrences = false,
        )

        val COMPILE_DEPENDENCY_PATHS_OPTION: CliOption = dependencyPathsOption(
            optionName = "compileDependencyPaths",
            description = "Compilation dependency paths used by the public dependency exposure check.",
        )

        val TRANSITIVE_DEPENDENCY_PATHS_OPTION: CliOption = dependencyPathsOption(
            optionName = "transitiveDependencyPaths",
            description = "Dependency paths exposed transitively to consumers.",
        )

        private fun dependencyPathsOption(optionName: String, description: String): CliOption = CliOption(
            optionName = optionName,
            valueDescription = "<paths separated by the platform path separator>",
            description = description,
            required = false,
            allowMultipleOccurrences = false,
        )
    }
}

class WatchdogCLP : DevKitCLP {
    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            WatchdogCommandLineProcessor.DIAGNOSTIC_SEVERITY_OPTION.optionName -> {
                val override = parseDiagnosticSeverity(value)
                val severities = configuration[WatchdogConfigurationKeys.DIAGNOSTIC_SEVERITIES, emptyMap()]
                configuration.put(WatchdogConfigurationKeys.DIAGNOSTIC_SEVERITIES, severities + override)
            }
            WatchdogCommandLineProcessor.DIAGNOSTICS_OUTPUT_FILE_OPTION.optionName -> {
                configuration.put(WatchdogConfigurationKeys.DIAGNOSTICS_OUTPUT_FILE, value)
            }
            WatchdogCommandLineProcessor.COMPILE_DEPENDENCY_PATHS_OPTION.optionName -> {
                configuration.put(WatchdogConfigurationKeys.COMPILE_DEPENDENCY_PATHS, parsePaths(value))
            }
            WatchdogCommandLineProcessor.TRANSITIVE_DEPENDENCY_PATHS_OPTION.optionName -> {
                configuration.put(WatchdogConfigurationKeys.TRANSITIVE_DEPENDENCY_PATHS, parsePaths(value))
            }
            else -> error("Unexpected config option: '${option.optionName}'")
        }
    }

    private fun parsePaths(value: String): Set<String> =
        value.split(File.pathSeparatorChar).filterTo(linkedSetOf(), String::isNotEmpty)

    private fun parseDiagnosticSeverity(value: String): Pair<String, WatchdogSeverity> {
        val diagnosticName = value.substringBefore(':')
        val diagnostic = WatchdogDiagnostics.allDiagnostics.find { it.name == diagnosticName }
            ?: throw CliOptionProcessingException(
                "Unknown watchdog diagnostic '$diagnosticName'. Known diagnostics: " +
                        WatchdogDiagnostics.allDiagnostics.joinToString { it.name },
            )
        val level = value.substringAfter(':', missingDelimiterValue = "")
        val severity = when (level.lowercase()) {
            "error" -> WatchdogSeverity.ERROR
            "warning" -> WatchdogSeverity.WARNING
            "none" -> WatchdogSeverity.NONE
            else -> throw CliOptionProcessingException(
                "Invalid severity '$level' for watchdog diagnostic '$diagnosticName': " +
                        "expected 'error', 'warning', or 'none'.",
            )
        }
        return diagnostic.name to severity
    }
}
