package org.jetbrains.kotlinx.library.api.watchdog.fir

import java.nio.file.Path
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.name.Name

context(context: SessionHolder)
internal expect fun FirAnnotation.getStringArgumentCompat(name: Name): String?

internal expect fun delegatingDiagnosticReporter(
    delegate: DiagnosticReporter,
    onReport: (diagnostic: KtDiagnostic?, context: DiagnosticContext) -> Unit,
): DiagnosticReporter

internal expect val ConeTypeParameterType.typeParameterSymbol: FirTypeParameterSymbol

internal expect fun SourceElement.klibPathCompat(): Path?

/** Source text of the element. The compiler moved it from an extension to a member in 2.5. */
internal expect val KtSourceElement.textCompat: CharSequence?
