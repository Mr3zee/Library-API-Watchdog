package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class WatchdogFirExtensionRegistrar(
    private val severities: WatchdogDiagnosticSeverities = WatchdogDiagnosticSeverities.DEFAULT,
    private val recorder: WatchdogDiagnosticsRecorder? = null,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +{ session: FirSession -> WatchdogFirCheckers(session, severities, recorder) }
    }
}
