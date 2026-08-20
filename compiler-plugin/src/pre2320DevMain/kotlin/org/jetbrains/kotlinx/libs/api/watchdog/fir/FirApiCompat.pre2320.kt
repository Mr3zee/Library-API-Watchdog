package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.fir.declarations.FirProperty

internal actual val FirProperty.isLocalCompat: Boolean
    get() = symbol.isLocal

internal actual fun recordingDiagnosticReporter(
    delegate: DiagnosticReporter,
    recorder: WatchdogDiagnosticsRecorder,
): DiagnosticReporter =
    object : DiagnosticReporter() {
        override fun report(diagnostic: KtDiagnostic?, context: DiagnosticContext) {
            recordAndDelegate(delegate, recorder, diagnostic, context)
        }
    }
