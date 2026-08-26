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
        val recorder = configuration[WatchdogConfigurationKeys.DIAGNOSTICS_OUTPUT_FILE]
            ?.let { WatchdogDiagnosticsRecorder(File(it)) }
        val publicTypeWithInternalApiEnabled =
            configuration[WatchdogConfigurationKeys.PUBLIC_TYPE_WITH_INTERNAL_API_ENABLED, true]
        val updatingBackwardsCompatibilityExempts =
            configuration[WatchdogConfigurationKeys.UPDATING_BACKWARDS_COMPATIBILITY_EXEMPTS, false]
        val dependencyExposure = configuration[WatchdogConfigurationKeys.COMPILE_DEPENDENCY_PATHS]
            ?.let { compileDependencies ->
                DependencyExposureCheckConfiguration(
                    compileDependencies = compileDependencies,
                    transitiveDependencies =
                        configuration[WatchdogConfigurationKeys.TRANSITIVE_DEPENDENCY_PATHS, emptySet()],
                )
            }
        FirExtensionRegistrarAdapter.registerExtension(
            WatchdogFirExtensionRegistrar(
                severities = severities,
                annotationBasedExclusions = annotationBasedExclusions,
                recorder = recorder,
                dependencyExposure = dependencyExposure,
                publicTypeWithInternalApiEnabled = publicTypeWithInternalApiEnabled,
                updatingBackwardsCompatibilityExempts = updatingBackwardsCompatibilityExempts,
            ),
        )
    }
}
