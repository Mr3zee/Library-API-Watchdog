package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class WatchdogFirExtensionRegistrar internal constructor(
    private val severities: WatchdogDiagnosticSeverities = WatchdogDiagnosticSeverities.DEFAULT,
    private val annotationBasedExclusions: AnnotationBasedCheckExclusions = AnnotationBasedCheckExclusions.NONE,
    private val internalApiAnnotations: Set<String> = emptySet(),
    private val recorder: WatchdogDiagnosticsRecorder? = null,
    private val dependencyExposure: DependencyExposureCheckConfiguration? = null,
    private val publicTypeWithInternalApiEnabled: Boolean = true,
    private val exemptingExistingApi: Boolean = false,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        if (internalApiAnnotations.isNotEmpty()) {
            +{ session: FirSession -> ConfiguredInternalApiAnnotations(session, internalApiAnnotations) }
        }
        +{ session: FirSession ->
            WatchdogFirCheckers(
                session,
                severities,
                annotationBasedExclusions,
                recorder,
                dependencyExposure,
                publicTypeWithInternalApiEnabled,
                exemptingExistingApi,
            )
        }
    }
}
