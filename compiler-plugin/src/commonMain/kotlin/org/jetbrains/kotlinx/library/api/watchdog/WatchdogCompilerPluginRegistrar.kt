package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlinx.library.api.watchdog.fir.AnnotationBasedCheckExclusions
import org.jetbrains.kotlinx.library.api.watchdog.fir.DependencyExposureCheckConfiguration
import org.jetbrains.kotlinx.library.api.watchdog.fir.WatchdogDiagnosticSeverities
import org.jetbrains.kotlinx.library.api.watchdog.fir.WatchdogDiagnosticsRecorder
import org.jetbrains.kotlinx.library.api.watchdog.fir.WatchdogFirExtensionRegistrar

class WatchdogComponentRegistrar : DevKitComponentRegistrar {
    override fun CompilerPluginRegistrar.ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val severities = WatchdogDiagnosticSeverities(
            configuration[WatchdogConfigurationKeys.DIAGNOSTIC_SEVERITIES, emptyMap()],
        )
        val annotationBasedExclusions = AnnotationBasedCheckExclusions(
            configuration[WatchdogConfigurationKeys.ANNOTATION_IGNORE_RULES, emptyMap()],
        )
        val internalApiAnnotations = configuration[WatchdogConfigurationKeys.INTERNAL_API_ANNOTATIONS, emptySet()]
        val recorder = configuration[WatchdogConfigurationKeys.DIAGNOSTICS_OUTPUT_FILE]
            ?.let { WatchdogDiagnosticsRecorder(File(it)) }
        val publicTypeWithInternalApiEnabled =
            configuration[WatchdogConfigurationKeys.PUBLIC_TYPE_WITH_INTERNAL_API_ENABLED, true]
        val exemptingExistingApi =
            configuration[WatchdogConfigurationKeys.UPDATING_BACKWARDS_COMPATIBILITY_EXEMPTS, false]
        val reportUndocumentedClassProperties =
            configuration[WatchdogConfigurationKeys.REPORT_UNDOCUMENTED_CLASS_PROPERTIES, false]
        val requireExplicitApi = configuration[WatchdogConfigurationKeys.REQUIRE_EXPLICIT_API, true]
        val dependencyExposure = configuration[WatchdogConfigurationKeys.COMPILE_DEPENDENCY_PATHS]
            ?.let { compileDependencies ->
                DependencyExposureCheckConfiguration(
                    compileDependencies = compileDependencies,
                    transitiveDependencies =
                        configuration[WatchdogConfigurationKeys.TRANSITIVE_DEPENDENCY_PATHS, emptySet()],
                    dependencyDeclarations =
                        configuration[WatchdogConfigurationKeys.DEPENDENCY_DECLARATIONS, emptyMap()],
                )
            }
        FirExtensionRegistrarAdapter.registerExtension(
            WatchdogFirExtensionRegistrar(
                severities = severities,
                annotationBasedExclusions = annotationBasedExclusions,
                internalApiAnnotations = internalApiAnnotations,
                recorder = recorder,
                dependencyExposure = dependencyExposure,
                publicTypeWithInternalApiEnabled = publicTypeWithInternalApiEnabled,
                exemptingExistingApi = exemptingExistingApi,
                reportUndocumentedClassProperties = reportUndocumentedClassProperties,
                requireExplicitApi = requireExplicitApi,
                excludedNames = configuration[WatchdogConfigurationKeys.EXCLUDED_NAMES, emptySet()],
                includedNames = configuration[WatchdogConfigurationKeys.INCLUDED_NAMES, emptySet()],
                includedAnnotations = configuration[WatchdogConfigurationKeys.INCLUDED_ANNOTATIONS, emptySet()],
            ),
        )
    }
}
