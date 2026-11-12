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
    /** Severity overrides keyed by diagnostic name. An unlisted diagnostic has its default severity. */
    val DIAGNOSTIC_SEVERITIES: CompilerConfigurationKey<Map<String, WatchdogSeverity>> =
        CompilerConfigurationKey.create("watchdog diagnostic severities")

    /** The annotation ignore rules of each diagnostic. */
    val ANNOTATION_IGNORE_RULES: CompilerConfigurationKey<Map<String, Set<AnnotationIgnoreRule>>> =
        CompilerConfigurationKey.create("watchdog annotation ignore rules")

    /** Name patterns of annotation classes that mark internal API without `@InternalAnnotationMarker`. */
    val INTERNAL_API_ANNOTATIONS: CompilerConfigurationKey<Set<String>> =
        CompilerConfigurationKey.create("watchdog internal API annotations")

    val EXCLUDED_NAMES: CompilerConfigurationKey<Set<String>> =
        CompilerConfigurationKey.create("watchdog excluded names")

    val INCLUDED_NAMES: CompilerConfigurationKey<Set<String>> =
        CompilerConfigurationKey.create("watchdog included names")

    val INCLUDED_ANNOTATIONS: CompilerConfigurationKey<Set<String>> =
        CompilerConfigurationKey.create("watchdog included annotations")

    /** @see org.jetbrains.kotlinx.library.api.watchdog.fir.WatchdogDiagnosticsRecorder */
    val DIAGNOSTICS_OUTPUT_FILE: CompilerConfigurationKey<String> =
        CompilerConfigurationKey.create("watchdog diagnostics output file")

    val PUBLIC_TYPE_WITH_INTERNAL_API_ENABLED: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create("watchdog public type with internal API enabled")

    val UPDATING_BACKWARDS_COMPATIBILITY_EXEMPTS: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create("watchdog exempting existing API")

    /** Whether `UNDOCUMENTED_PUBLIC_API` reports the member properties of a class. Default is `false`. */
    val REPORT_UNDOCUMENTED_CLASS_PROPERTIES: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create("watchdog report undocumented class properties")

    val REQUIRE_EXPLICIT_API: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create("watchdog require explicit API mode")

    /** The compile classpath and the subset that consumers get. The Gradle plugin and the benchmarks pass them. */
    val COMPILE_DEPENDENCY_PATHS: CompilerConfigurationKey<Set<String>> =
        CompilerConfigurationKey.create("watchdog compile dependency paths")

    val TRANSITIVE_DEPENDENCY_PATHS: CompilerConfigurationKey<Set<String>> =
        CompilerConfigurationKey.create("watchdog transitive dependency paths")

    /** The build script declaration of each compile dependency path, for the exemption snippet. */
    internal val DEPENDENCY_DECLARATIONS: CompilerConfigurationKey<Map<String, DependencyDeclaration>> =
        CompilerConfigurationKey.create("watchdog dependency declarations")

    val SOURCE_SETS_CHECKED_ELSEWHERE: CompilerConfigurationKey<Set<String>> =
        CompilerConfigurationKey.create("watchdog source sets checked elsewhere")

    val SOURCE_SETS_WITH_PLATFORM_CHECKS: CompilerConfigurationKey<Set<String>> =
        CompilerConfigurationKey.create("watchdog source sets with platform checks")
}

@Suppress("unused") // Instantiated reflectively by the dev-kit entry point.
internal class WatchdogCommandLineProcessor : DevKitCLP {
    override val pluginOptions: Collection<CliOption> =
        listOf(
            DIAGNOSTIC_SEVERITY_OPTION,
            IGNORE_WHEN_ANNOTATED_OPTION,
            INTERNAL_API_ANNOTATION_OPTION,
            EXCLUDED_NAME_OPTION,
            INCLUDED_NAME_OPTION,
            INCLUDED_ANNOTATION_OPTION,
            PUBLIC_TYPE_WITH_INTERNAL_API_OPTION,
            EXEMPTING_EXISTING_API_OPTION,
            REPORT_UNDOCUMENTED_CLASS_PROPERTIES_OPTION,
            REQUIRE_EXPLICIT_API_OPTION,
            DIAGNOSTICS_OUTPUT_FILE_OPTION,
            COMPILE_DEPENDENCY_PATHS_OPTION,
            TRANSITIVE_DEPENDENCY_PATHS_OPTION,
            DEPENDENCY_DECLARATIONS_OPTION,
            SOURCE_SETS_CHECKED_ELSEWHERE_OPTION,
            SOURCE_SETS_WITH_PLATFORM_CHECKS_OPTION,
        )

    companion object {
        private const val NAME_PATTERN_DESCRIPTION =
            "The value is a fully qualified name or a pattern: '**' matches any characters, " +
                    "'*' matches any characters except '.', and '?' matches one character."

        val DIAGNOSTIC_SEVERITY_OPTION: CliOption = CliOption(
            optionName = "diagnosticSeverity",
            valueDescription = "<diagnostic name>:error|warning|none",
            description = "Report the named watchdog diagnostic with the given severity, " +
                    "or disable its check with 'none'. " +
                    "Every other diagnostic has its default severity.",
            required = false,
            allowMultipleOccurrences = true,
        )

        val IGNORE_WHEN_ANNOTATED_OPTION: CliOption = CliOption(
            optionName = "ignoreWhenAnnotated",
            valueDescription = "<diagnostic name>:<annotation fully qualified name>[:recursive]",
            description = "Don't report the named configurable watchdog diagnostic on a " +
                    "declaration with the annotation. With ':recursive', the annotation on an " +
                    "enclosing declaration or on the file also covers the nested declarations.",
            required = false,
            allowMultipleOccurrences = true,
        )

        val INTERNAL_API_ANNOTATION_OPTION: CliOption = CliOption(
            optionName = "internalApiAnnotation",
            valueDescription = "<annotation name pattern>",
            description = "Treat a declaration with the annotation as internal API, as if the " +
                    "annotation class had @InternalAnnotationMarker. " +
                    "For annotations that the library can't mark itself. " +
                    NAME_PATTERN_DESCRIPTION,
            required = false,
            allowMultipleOccurrences = true,
        )

        val EXCLUDED_NAME_OPTION: CliOption = CliOption(
            optionName = "excludedName",
            valueDescription = "<declaration name pattern>",
            description = "Don't check a declaration with the name or the declarations nested in it. " +
                    "PUBLIC_TYPE_WITH_INTERNAL_API reports a checked declaration that exposes a type " +
                    "with the name. A name is fully qualified and has '.' between all parts, such as " +
                    "'com.example.Container.Value' or 'com.example.topLevelFunction'. " +
                    NAME_PATTERN_DESCRIPTION,
            required = false,
            allowMultipleOccurrences = true,
        )

        val INCLUDED_NAME_OPTION: CliOption = CliOption(
            optionName = "includedName",
            valueDescription = "<declaration name pattern>",
            description = "Check only a declaration with the name, the declarations nested in it, and a " +
                    "class with a function, property, or enum entry of that name, without its other " +
                    "members. The includedAnnotation option adds declarations. The name format is the " +
                    "same as in excludedName, and excludedName has priority.",
            required = false,
            allowMultipleOccurrences = true,
        )

        val INCLUDED_ANNOTATION_OPTION: CliOption = CliOption(
            optionName = "includedAnnotation",
            valueDescription = "<annotation name pattern>",
            description = "Check only a declaration with the annotation, the declarations nested in it, " +
                    "and a class with a function, property, enum entry, or constructor that has the " +
                    "annotation, without its other members. The includedName option adds declarations. " +
                    NAME_PATTERN_DESCRIPTION,
            required = false,
            allowMultipleOccurrences = true,
        )

        val DIAGNOSTICS_OUTPUT_FILE_OPTION: CliOption = CliOption(
            optionName = "diagnosticsOutputFile",
            valueDescription = "<path>",
            description = "Append every reported watchdog diagnostic to the given file as a " +
                    "tab-separated line: diagnostic name, source file path, start and end " +
                    "offset of the source element, start and end offset of the reported " +
                    "range, then one field for each message parameter. For tooling. " +
                    "The exemptExistingApi and generateApiWatchdogReport tasks of the " +
                    "Gradle plugin read it.",
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

        val EXEMPTING_EXISTING_API_OPTION: CliOption = CliOption(
            optionName = "exemptingExistingApi",
            valueDescription = "<true|false>",
            description = "Enable the task-scoped collection mode of exemptExistingApi.",
            required = false,
            allowMultipleOccurrences = false,
        )

        val REPORT_UNDOCUMENTED_CLASS_PROPERTIES_OPTION: CliOption = CliOption(
            optionName = "reportUndocumentedClassProperties",
            valueDescription = "<true|false>",
            description = "Report UNDOCUMENTED_PUBLIC_API on the member properties of a class. " +
                    "Off by default, because the check doesn't read the @property tags of the class KDoc.",
            required = false,
            allowMultipleOccurrences = false,
        )

        val REQUIRE_EXPLICIT_API_OPTION: CliOption = CliOption(
            optionName = "requireExplicitApi",
            valueDescription = "<true|false>",
            description = "Run the checks only in a module with explicit API mode. On by default.",
            required = false,
            allowMultipleOccurrences = false,
        )

        val COMPILE_DEPENDENCY_PATHS_OPTION: CliOption = dependencyPathsOption(
            optionName = "compileDependencyPaths",
            description = "Compile dependency paths that PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY reads.",
        )

        val TRANSITIVE_DEPENDENCY_PATHS_OPTION: CliOption = dependencyPathsOption(
            optionName = "transitiveDependencyPaths",
            description = "Dependency paths that the project publishes transitively to consumers.",
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

        val SOURCE_SETS_CHECKED_ELSEWHERE_OPTION: CliOption = sourceSetsOption(
            optionName = "sourceSetsCheckedElsewhere",
            description = "Parent source sets of a multiplatform compilation that another compilation " +
                    "checks. This compilation reports watchdog diagnostics on such a source set only when " +
                    "sourceSetsWithPlatformChecks also lists it.",
        )

        val SOURCE_SETS_WITH_PLATFORM_CHECKS_OPTION: CliOption = sourceSetsOption(
            optionName = "sourceSetsWithPlatformChecks",
            description = "The subset of sourceSetsCheckedElsewhere that still gets the checks that a " +
                    "metadata compilation can't run in this compilation: the Java interop checks and the " +
                    "REDUNDANT_EXEMPTION verdicts that need the actual declarations.",
        )

        private fun sourceSetsOption(optionName: String, description: String): CliOption = CliOption(
            optionName = optionName,
            // A comma splits the `-P` argument of the JS compiler.
            valueDescription = "<source set names separated by colons>",
            description = description,
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
                processNamePattern(option, value, WatchdogConfigurationKeys.INTERNAL_API_ANNOTATIONS, configuration)
            }
            EXCLUDED_NAME_OPTION.optionName -> {
                processNamePattern(option, value, WatchdogConfigurationKeys.EXCLUDED_NAMES, configuration)
            }
            INCLUDED_NAME_OPTION.optionName -> {
                processNamePattern(option, value, WatchdogConfigurationKeys.INCLUDED_NAMES, configuration)
            }
            INCLUDED_ANNOTATION_OPTION.optionName -> {
                processNamePattern(option, value, WatchdogConfigurationKeys.INCLUDED_ANNOTATIONS, configuration)
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
            EXEMPTING_EXISTING_API_OPTION.optionName -> {
                configuration.put(
                    WatchdogConfigurationKeys.UPDATING_BACKWARDS_COMPATIBILITY_EXEMPTS,
                    parseBoolean(option.optionName, value),
                )
            }
            REPORT_UNDOCUMENTED_CLASS_PROPERTIES_OPTION.optionName -> {
                configuration.put(
                    WatchdogConfigurationKeys.REPORT_UNDOCUMENTED_CLASS_PROPERTIES,
                    parseBoolean(option.optionName, value),
                )
            }
            REQUIRE_EXPLICIT_API_OPTION.optionName -> {
                configuration.put(WatchdogConfigurationKeys.REQUIRE_EXPLICIT_API, parseBoolean(option.optionName, value))
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
            SOURCE_SETS_CHECKED_ELSEWHERE_OPTION.optionName -> {
                configuration.put(WatchdogConfigurationKeys.SOURCE_SETS_CHECKED_ELSEWHERE, parseSourceSets(value))
            }
            SOURCE_SETS_WITH_PLATFORM_CHECKS_OPTION.optionName -> {
                configuration.put(WatchdogConfigurationKeys.SOURCE_SETS_WITH_PLATFORM_CHECKS, parseSourceSets(value))
            }
            else -> error("Unexpected config option: '${option.optionName}'")
        }
    }

    private fun parsePaths(value: String): Set<String> =
        value.split(File.pathSeparatorChar).filterTo(linkedSetOf(), String::isNotEmpty)

    private fun parseSourceSets(value: String): Set<String> =
        value.split(':').filterTo(linkedSetOf(), String::isNotBlank)

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

    private fun processNamePattern(
        option: AbstractCliOption,
        value: String,
        key: CompilerConfigurationKey<Set<String>>,
        configuration: CompilerConfiguration,
    ) {
        val pattern = value.trim()
        if (pattern.isEmpty() || pattern.any { it.isWhitespace() || it == ':' }) {
            throw CliOptionProcessingException(
                "Invalid value '$value' for watchdog option '${option.optionName}': " +
                        "expected a fully qualified name or a name pattern without whitespace and ':'.",
            )
        }

        val patterns = LinkedHashSet(configuration[key, emptySet()])
        patterns.add(pattern)
        configuration.put(key, patterns)
    }

    private fun processAnnotationIgnoreRule(value: String, configuration: CompilerConfiguration) {
        val diagnosticName = value.substringBefore(':')
        val diagnostic = WatchdogDiagnostics.allDiagnostics.find { it.name == diagnosticName }
            ?: throw CliOptionProcessingException(
                "Unknown watchdog diagnostic '$diagnosticName'. Known configurable diagnostics: " +
                        WatchdogDiagnostics.allDiagnostics.joinToString { it.name },
            )
        val ruleSpec = value.substringAfter(':', missingDelimiterValue = "")
        val rule = AnnotationIgnoreRule.parse(ruleSpec)
            ?: throw CliOptionProcessingException(
                "Invalid rule '$ruleSpec' for watchdog diagnostic '${diagnostic.name}': " +
                        "expected a fully qualified class name, with or without ':${AnnotationIgnoreRule.RECURSIVE_FLAG}'.",
            )

        val key = WatchdogConfigurationKeys.ANNOTATION_IGNORE_RULES
        val rules = configuration[key, emptyMap()].mapValuesTo(HashMap()) { (_, diagnosticRules) ->
            diagnosticRules.toMutableSet()
        }
        rules.getOrPut(diagnostic.name, ::linkedSetOf).add(rule)
        configuration.put(key, rules)
    }
}

/**
 * The part of a [WatchdogCommandLineProcessor.IGNORE_WHEN_ANNOTATED_OPTION] value after the
 * diagnostic name: `<annotation fully qualified name>[:recursive]`.
 */
data class AnnotationIgnoreRule(val annotationName: String, val recursive: Boolean) {
    companion object {
        const val RECURSIVE_FLAG: String = "recursive"

        fun parse(spec: String): AnnotationIgnoreRule? {
            val annotationName = spec.substringBefore(':')
            val flag = spec.substringAfter(':', missingDelimiterValue = "")
            if (annotationName.isBlank() || flag.isNotEmpty() && flag != RECURSIVE_FLAG) return null
            return AnnotationIgnoreRule(annotationName, recursive = flag == RECURSIVE_FLAG)
        }
    }
}
