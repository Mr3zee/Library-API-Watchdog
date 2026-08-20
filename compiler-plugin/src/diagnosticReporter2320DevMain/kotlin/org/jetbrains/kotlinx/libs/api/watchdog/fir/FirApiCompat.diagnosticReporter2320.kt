package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic

internal actual fun recordingDiagnosticReporter(
    delegate: DiagnosticReporter,
    recorder: WatchdogDiagnosticsRecorder,
): DiagnosticReporter =
    object : DiagnosticReporter() {
        override val hasErrors: Boolean
            get() = delegate.hasErrors

        override fun report(diagnostic: KtDiagnostic?, context: DiagnosticContext) {
            recordAndDelegate(delegate, recorder, diagnostic, context)
        }
    }
