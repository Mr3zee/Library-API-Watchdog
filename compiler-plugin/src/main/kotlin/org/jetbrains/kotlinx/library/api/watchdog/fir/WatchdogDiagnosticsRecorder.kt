package org.jetbrains.kotlinx.library.api.watchdog.fir

import java.io.File
import org.jetbrains.kotlin.diagnostics.DiagnosticContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithSource
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.type.FirTypeChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.types.FirTypeRef

/**
 * Adds every reported diagnostic to the end of [outputFile] as a tab-separated line: diagnostic
 * name, absolute source file path, start offset, end offset of the source element. The recorder
 * never truncates the file, because several compiler sessions of one build write to it. The
 * caller passes a path that doesn't exist yet.
 */
class WatchdogDiagnosticsRecorder(private val outputFile: File) {
    fun record(diagnostic: KtDiagnostic, context: DiagnosticContext) {
        // `KtDiagnosticWithSource.firstRange` puts `com.intellij.openapi.util.TextRange` into the
        // bytecode, which links differently in the CLI and in `kotlin-compiler-embeddable`.
        val element = (diagnostic as? KtDiagnosticWithSource)?.element ?: return
        val path = context.containingFilePath ?: return
        val line = "${diagnostic.factoryName}\t$path\t${element.startOffset}\t${element.endOffset}\n"
        synchronized(APPEND_LOCK) {
            outputFile.parentFile?.mkdirs()
            outputFile.appendText(line)
        }
    }

    private companion object {
        /** The sessions of a compilation share the output file. */
        private val APPEND_LOCK = Any()
    }
}

internal class RecordingDeclarationChecker<D : FirDeclaration>(
    private val delegate: FirDeclarationChecker<D>,
    private val recorder: WatchdogDiagnosticsRecorder,
) : FirDeclarationChecker<D>(delegate.mppKind) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: D) {
        val recording = recordingDiagnosticReporter(reporter, recorder)
        context(context, recording) { delegate.check(declaration) }
    }
}

internal class RecordingTypeChecker<T : FirTypeRef>(
    private val delegate: FirTypeChecker<T>,
    private val recorder: WatchdogDiagnosticsRecorder,
) : FirTypeChecker<T>(delegate.mppKind) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(typeRef: T) {
        val recording = recordingDiagnosticReporter(reporter, recorder)
        context(context, recording) { delegate.check(typeRef) }
    }
}

private fun recordingDiagnosticReporter(delegate: DiagnosticReporter, recorder: WatchdogDiagnosticsRecorder) =
    delegatingDiagnosticReporter(delegate) { diagnostic, context ->
        // A `@Suppress` on an element the checkers haven't visited yet resolves later in the
        // pending reporter. The recorder then records a diagnostic the compiler doesn't report.
        if (diagnostic != null && !context.isDiagnosticSuppressed(diagnostic)) {
            recorder.record(diagnostic, context)
        }
        delegate.report(diagnostic, context)
    }
