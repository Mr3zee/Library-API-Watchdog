package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import java.util.HashMap
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCLP
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlinx.library.api.watchdog.fir.DependencyDeclaration
import org.jetbrains.kotlinx.library.api.watchdog.fir.WatchdogDiagnostics
import org.jetbrains.kotlinx.library.api.watchdog.fir.WatchdogSeverity

object WatchdogConfigurationKeys {
    /** Severity overrides keyed by diagnostic name. An unlisted diagnostic is an error. */
    val DIAGNOSTIC_SEVERITIES: CompilerConfigurationKey<Map<String, WatchdogSeverity>> =
        CompilerConfigurationKey.create("watchdog diagnostic severities")

    /** Annotation FQ names, each with the diagnostics it disables on the annotated declaration. */
    val ANNOTATION_IGNORE_RULES: CompilerConfigurationKey<Map<String, Set<String>>> =
        CompilerConfigurationKey.create("watchdog annotation ignore rules")

    /** FQ names of annotation classes that mark internal API without `@InternalAnnotationMarker`. */
    val INTERNAL_API_ANNOTATIONS: CompilerConfigurationKey<Set<String>> =
        CompilerConfigurationKey.create("watchdog internal API annotations")

    /** @see org.jetbrains.kotlinx.library.api.watchdog.fir.WatchdogDiagnosticsRecorder */
    val DIAGNOSTICS_OUTPUT_FILE: CompilerConfigurationKey<String> =
        CompilerConfigurationKey.create("watchdog diagnostics output file")

    val PUBLIC_TYPE_WITH_INTERNAL_API_ENABLED: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create("watchdog public type with internal API enabled")

    val UPDATING_BACKWARDS_COMPATIBILITY_EXEMPTS: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create("watchdog updating backwards-compatibility exempts")

    /** The compile classpath and the subset that consumers get. Only the Gradle plugin has them. */
    val COMPILE_DEPENDENCY_PATHS: CompilerConfigurationKey<Set<String>> =
        CompilerConfigurationKey.create("watchdog compile dependency paths")

    val TRANSITIVE_DEPENDENCY_PATHS: CompilerConfigurationKey<Set<String>> =
        CompilerConfigurationKey.create("watchdog transitive dependency paths")

    /** The build script declaration of each compile dependency path, for the exemption snippet. */
    internal val DEPENDENCY_DECLARATIONS: CompilerConfigurationKey<Map<String, DependencyDeclaration>> =
        CompilerConfigurationKey.create("watchdog dependency declarations")
}

@Suppress("unused") // Instantiated reflectively by the dev-kit entry point.
internal class WatchdogCommandLineProcessor : DevKitCLP {
    override val pluginOptions: Collection<CliOption> =
        listOf(
            DIAGNOSTIC_SEVERITY_OPTION,
            IGNORE_WHEN_ANNOTATED_OPTION,
            INTERNAL_API_ANNOTATION_OPTION,
            PUBLIC_TYPE_WITH_INTERNAL_API_OPTION,
            UPDATING_BACKWARDS_COMPATIBILITY_EXEMPTS_OPTION,
            DIAGNOSTICS_OUTPUT_FILE_OPTION,
            COMPILE_DEPENDENCY_PATHS_OPTION,
            TRANSITIVE_DEPENDENCY_PATHS_OPTION,
            DEPENDENCY_DECLARATIONS_OPTION,
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

        val IGNORE_WHEN_ANNOTATED_OPTION: CliOption = CliOption(
            optionName = "ignoreWhenAnnotated",
            valueDescription = "<diagnostic name>:<annotation fully qualified name>",
            description = "Do not report the named configurable watchdog diagnostic on a " +
                    "declaration carrying the annotation.",
            required = false,
            allowMultipleOccurrences = true,
        )

        val INTERNAL_API_ANNOTATION_OPTION: CliOption = CliOption(
            optionName = "internalApiAnnotation",
            valueDescription = "<annotation fully qualified name>",
            description = "Treat declarations carrying the annotation as internal API, exactly " +
                    "as if the annotation class were marked with @InternalAnnotationMarker. " +
                    "Meant for annotations the library can't mark itself.",
            required = false,
            allowMultipleOccurrences = true,
        )

        val DIAGNOSTICS_OUTPUT_FILE_OPTION: CliOption = CliOption(
            optionName = "diagnosticsOutputFile",
            valueDescription = "<path>",
            description = "Append every reported watchdog diagnostic to the given file as a " +
                    "tab-separated line: diagnostic name, source file path, start offset, " +
                    "end offset. Meant for tooling. The Gradle plugin's " +
                    "updateBackwardsCompatibilityExempts task consumes it.",
            required = false,
            // The blank placeholder of the Gradle plugin can meet a path from other tooling.
            allowMultipleOccurrences = true,
        )

        val PUBLIC_TYPE_WITH_INTERNAL_API_OPTION: CliOption = CliOption(
            optionName = "publicTypeWithInternalApi",
            valueDescription = "<true|false>",
            description = "Enable the always-error PUBLIC_TYPE_WITH_INTERNAL_API check.",
            required = false,
            allowMultipleOccurrences = false,
        )

        val UPDATING_BACKWARDS_COMPATIBILITY_EXEMPTS_OPTION: CliOption = CliOption(
            optionName = "updatingBackwardsCompatibilityExempts",
            valueDescription = "<true|false>",
            description = "Enable task-scoped collection for updateBackwardsCompatibilityExempts.",
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

        val DEPENDENCY_DECLARATIONS_OPTION: CliOption = CliOption(
            optionName = "dependencyDeclarations",
            valueDescription = "<path>\\t<configuration>\\t<notation>\\t<path>\\t<configuration>\\t<notation>...",
            description = "Tab-separated triples of a compile dependency path, the configuration " +
                    "that declares it, such as implementation, and the dependency notation as " +
                    "written, such as \"com.example:models:1.0\". " +
                    "PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY shows the declaration in its " +
                    "exemption snippet.",
            required = false,
            allowMultipleOccurrences = false,
        )

        private fun dependencyPathsOption(optionName: String, description: String): CliOption = CliOption(
            optionName = optionName,
            valueDescription = "<paths separated by the platform path separator>",
            description = description,
            required = false,
            allowMultipleOccurrences = false,
        )
    }
    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            DIAGNOSTIC_SEVERITY_OPTION.optionName -> {
                processDiagnosticSeverity(value, configuration)
            }
            IGNORE_WHEN_ANNOTATED_OPTION.optionName -> {
                processAnnotationIgnoreRule(value, configuration)
            }
            INTERNAL_API_ANNOTATION_OPTION.optionName -> {
                processInternalApiAnnotation(value, configuration)
            }
            DIAGNOSTICS_OUTPUT_FILE_OPTION.optionName -> {
                // The Gradle plugin passes a blank value outside collection.
                if (value.isNotBlank()) {
                    configuration.put(WatchdogConfigurationKeys.DIAGNOSTICS_OUTPUT_FILE, value)
                }
            }
            PUBLIC_TYPE_WITH_INTERNAL_API_OPTION.optionName -> {
                configuration.put(
                    WatchdogConfigurationKeys.PUBLIC_TYPE_WITH_INTERNAL_API_ENABLED,
                    parseBoolean(option.optionName, value),
                )
            }
            UPDATING_BACKWARDS_COMPATIBILITY_EXEMPTS_OPTION.optionName -> {
                configuration.put(
                    WatchdogConfigurationKeys.UPDATING_BACKWARDS_COMPATIBILITY_EXEMPTS,
                    parseBoolean(option.optionName, value),
                )
            }
            COMPILE_DEPENDENCY_PATHS_OPTION.optionName -> {
                // The Gradle plugin passes a blank value for a disabled exposure check.
                if (value.isNotBlank()) {
                    configuration.put(WatchdogConfigurationKeys.COMPILE_DEPENDENCY_PATHS, parsePaths(value))
                }
            }
            TRANSITIVE_DEPENDENCY_PATHS_OPTION.optionName -> {
                configuration.put(WatchdogConfigurationKeys.TRANSITIVE_DEPENDENCY_PATHS, parsePaths(value))
            }
            DEPENDENCY_DECLARATIONS_OPTION.optionName -> {
                configuration.put(WatchdogConfigurationKeys.DEPENDENCY_DECLARATIONS, parseDeclarations(value))
            }
            else -> error("Unexpected config option: '${option.optionName}'")
        }
    }

    private fun parsePaths(value: String): Set<String> =
        value.split(File.pathSeparatorChar).filterTo(linkedSetOf(), String::isNotEmpty)

    private fun parseDeclarations(value: String): Map<String, DependencyDeclaration> {
        if (value.isEmpty()) return emptyMap()
        val fields = value.split('\t')
        if (fields.size % 3 != 0) {
            throw CliOptionProcessingException(
                "Invalid value for watchdog option '${DEPENDENCY_DECLARATIONS_OPTION.optionName}': " +
                        "expected tab-separated path, configuration, and notation triples.",
            )
        }
        return fields.chunked(3).associate { (path, configuration, notation) ->
            path to DependencyDeclaration(configuration, notation)
        }
    }

    private fun parseBoolean(optionName: String, value: String): Boolean = when (value.lowercase()) {
        "true" -> true
        "false" -> false
        else -> throw CliOptionProcessingException(
            "Invalid value '$value' for watchdog option '$optionName': expected 'true' or 'false'.",
        )
    }

    private fun processDiagnosticSeverity(value: String, configuration: CompilerConfiguration) {
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
        val key = WatchdogConfigurationKeys.DIAGNOSTIC_SEVERITIES
        val severities = HashMap(configuration[key, emptyMap()])
        severities[diagnostic.name] = severity
        configuration.put(key, severities)
    }

    private fun processInternalApiAnnotation(value: String, configuration: CompilerConfiguration) {
        val annotationName = value.trim()
        if (annotationName.isEmpty() || annotationName.any { it.isWhitespace() || it == ':' }) {
            throw CliOptionProcessingException(
                "Invalid value '$value' for watchdog option '${INTERNAL_API_ANNOTATION_OPTION.optionName}': " +
                        "expected a fully qualified annotation class name.",
            )
        }

        val key = WatchdogConfigurationKeys.INTERNAL_API_ANNOTATIONS
        val annotations = LinkedHashSet(configuration[key, emptySet()])
        annotations.add(annotationName)
        configuration.put(key, annotations)
    }

    private fun processAnnotationIgnoreRule(value: String, configuration: CompilerConfiguration) {
        val diagnosticName = value.substringBefore(':')
        val diagnostic = WatchdogDiagnostics.allDiagnostics.find { it.name == diagnosticName }
            ?: throw CliOptionProcessingException(
                "Unknown watchdog diagnostic '$diagnosticName'. Known configurable diagnostics: " +
                        WatchdogDiagnostics.allDiagnostics.joinToString { it.name },
            )
        val annotationName = value.substringAfter(':', missingDelimiterValue = "")
        if (annotationName.isBlank() || ':' in annotationName) {
            throw CliOptionProcessingException(
                "Invalid annotation name '$annotationName' for watchdog diagnostic '${diagnostic.name}': " +
                        "expected a fully qualified class name.",
            )
        }

        val key = WatchdogConfigurationKeys.ANNOTATION_IGNORE_RULES
        val rules = configuration[key, emptyMap()].mapValuesTo(HashMap()) { (_, annotations) ->
            annotations.toMutableSet()
        }
        rules.getOrPut(diagnostic.name, ::linkedSetOf).add(annotationName)
        configuration.put(key, rules)
    }
}
