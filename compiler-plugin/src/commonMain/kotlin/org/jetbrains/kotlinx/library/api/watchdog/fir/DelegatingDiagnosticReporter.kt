package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic

/** The abstract members of `DiagnosticReporter` differ between the Kotlin versions. */
internal expect fun delegatingDiagnosticReporter(
    delegate: DiagnosticReporter,
    onReport: (diagnostic: KtDiagnostic?, context: DiagnosticContext) -> Unit,
): DiagnosticReporter
