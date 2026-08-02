package org.jetbrains.kotlinx.libs.api.watchdog

import java.io.File
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlinx.libs.api.watchdog.fir.DependencyExposureCheckConfiguration
import org.jetbrains.kotlinx.libs.api.watchdog.fir.WatchdogDiagnosticSeverities
import org.jetbrains.kotlinx.libs.api.watchdog.fir.WatchdogDiagnosticsRecorder
import org.jetbrains.kotlinx.libs.api.watchdog.fir.WatchdogFirExtensionRegistrar

class WatchdogCompilerPluginRegistrar : DevKitCompilerPluginRegistrar(
    registrarClass = WatchdogComponentRegistrar::class,
) {
    override val pluginId: String = PluginInfo.PLUGIN_ID
    override val supportsK2: Boolean = true
}

class WatchdogComponentRegistrar : DevKitComponentRegistrar {
    override fun CompilerPluginRegistrar.ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val severities = WatchdogDiagnosticSeverities(
            configuration[WatchdogConfigurationKeys.DIAGNOSTIC_SEVERITIES, emptyMap()],
        )
        val recorder = configuration[WatchdogConfigurationKeys.DIAGNOSTICS_OUTPUT_FILE]
            ?.let { WatchdogDiagnosticsRecorder(File(it)) }
        val dependencyExposure = configuration[WatchdogConfigurationKeys.COMPILE_DEPENDENCY_PATHS]
            ?.let { compileDependencies ->
                DependencyExposureCheckConfiguration(
                    compileDependencies = compileDependencies,
                    transitiveDependencies =
                        configuration[WatchdogConfigurationKeys.TRANSITIVE_DEPENDENCY_PATHS, emptySet()],
                )
            }
        FirExtensionRegistrarAdapter.registerExtension(
            WatchdogFirExtensionRegistrar(severities, recorder, dependencyExposure),
        )
    }
}
