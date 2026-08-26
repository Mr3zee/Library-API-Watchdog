package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class WatchdogFirExtensionRegistrar internal constructor(
    private val severities: WatchdogDiagnosticSeverities = WatchdogDiagnosticSeverities.DEFAULT,
    private val annotationBasedExclusions: AnnotationBasedCheckExclusions = AnnotationBasedCheckExclusions.NONE,
    private val recorder: WatchdogDiagnosticsRecorder? = null,
    private val dependencyExposure: DependencyExposureCheckConfiguration? = null,
    private val publicTypeWithInternalApiEnabled: Boolean = true,
    private val updatingBackwardsCompatibilityExempts: Boolean = false,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +{ session: FirSession ->
            WatchdogFirCheckers(
                session,
                severities,
                annotationBasedExclusions,
                recorder,
                dependencyExposure,
                publicTypeWithInternalApiEnabled,
                updatingBackwardsCompatibilityExempts,
            )
        }
    }
}
