package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic

internal actual fun delegatingDiagnosticReporter(
    delegate: DiagnosticReporter,
    onReport: (diagnostic: KtDiagnostic?, context: DiagnosticContext) -> Unit,
): DiagnosticReporter =
    object : DiagnosticReporter() {
        override val hasErrors: Boolean
            get() = delegate.hasErrors

        override val hasWarningsForWError: Boolean
            get() = delegate.hasWarningsForWError

        override fun report(diagnostic: KtDiagnostic?, context: DiagnosticContext) = onReport(diagnostic, context)
    }
