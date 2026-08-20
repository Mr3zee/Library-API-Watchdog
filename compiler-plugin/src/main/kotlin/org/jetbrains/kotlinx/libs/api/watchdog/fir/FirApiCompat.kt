package org.jetbrains.kotlinx.libs.api.watchdog.fir

import java.nio.file.Path
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.name.Name

internal expect fun FirAnnotation.getStringArgumentCompat(
    name: Name,
    session: FirSession,
): String?

internal expect fun recordingDiagnosticReporter(
    delegate: DiagnosticReporter,
    recorder: WatchdogDiagnosticsRecorder,
): DiagnosticReporter

internal expect fun ConeTypeParameterType.typeParameterSymbolCompat(
    session: FirSession,
): FirTypeParameterSymbol

internal expect fun SourceElement.klibPathCompat(): Path?
