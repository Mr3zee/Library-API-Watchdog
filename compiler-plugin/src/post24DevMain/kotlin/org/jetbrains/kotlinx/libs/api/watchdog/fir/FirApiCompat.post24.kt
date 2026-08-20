package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.name.Name

internal actual fun FirAnnotation.getStringArgumentCompat(
    name: Name,
    session: FirSession,
): String? = getStringArgument(name)

internal actual fun recordingDiagnosticReporter(
    delegate: DiagnosticReporter,
    recorder: WatchdogDiagnosticsRecorder,
): DiagnosticReporter =
    object : DiagnosticReporter() {
        override val hasErrors: Boolean
            get() = delegate.hasErrors

        override val hasWarningsForWError: Boolean
            get() = delegate.hasWarningsForWError

        override fun report(diagnostic: KtDiagnostic?, context: DiagnosticContext) {
            recordAndDelegate(delegate, recorder, diagnostic, context)
        }
    }
