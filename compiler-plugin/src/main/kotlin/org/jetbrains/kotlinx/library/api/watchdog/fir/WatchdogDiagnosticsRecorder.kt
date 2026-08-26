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
 * Appends every reported watchdog diagnostic to [outputFile] as a tab-separated line:
 * diagnostic name, absolute source file path, start offset, end offset. The offsets are those of
 * the source element the diagnostic was reported on, so tooling can locate the exact declaration
 * without re-running the analysis.
 *
 * The format is machine-readable on purpose: the Gradle plugin's
 * `updateBackwardsCompatibilityExempts` task injects this option into the regular KGP compilation
 * tasks and turns their merged diagnostics into `@Intentionally*` exemption annotations in the
 * sources. Tab is a safe separator because tabs can't appear in diagnostic names or offsets and
 * are pathological in file paths.
 *
 * The recorder only ever appends. Whoever passes the option owns the file's lifecycle and is
 * expected to hand in a fresh path, because a single build may write from several compiler
 * sessions (common and platform fragments of a multiplatform compilation, for example).
 */
class WatchdogDiagnosticsRecorder(private val outputFile: File) {
    fun record(diagnostic: KtDiagnostic, context: DiagnosticContext) {
        // The element offsets, not the rendering range: KtDiagnosticWithSource.firstRange would
        // pull com.intellij.openapi.util.TextRange into the bytecode, which links differently in
        // the CLI and kotlin-compiler-embeddable worlds this jar runs in.
        val element = (diagnostic as? KtDiagnosticWithSource)?.element ?: return
        val path = context.containingFilePath ?: return
        val line = "${diagnostic.factoryName}\t$path\t${element.startOffset}\t${element.endOffset}\n"
        synchronized(APPEND_LOCK) {
            outputFile.parentFile?.mkdirs()
            outputFile.appendText(line)
        }
    }

    private companion object {
        /** One lock per process: the sessions of a compilation share the output file. */
        private val APPEND_LOCK = Any()
    }
}

/**
 * Runs the wrapped declaration checker with a [DiagnosticReporter] that records every diagnostic
 * into [recorder] before handing it to the real reporter. One generic wrapper covers every
 * declaration checker category [WatchdogFirCheckers] registers.
 */
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

/** The type-checker counterpart of [RecordingDeclarationChecker]. */
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
        // The suppression check sees the annotations in scope at report time. A @Suppress on an
        // element the checkers have not visited yet (a constructor reported on while checking its
        // class) is resolved later by the framework's pending reporter and can slip through here.
        // that only costs a recorded entry for a diagnostic the compiler ends up not reporting.
        if (diagnostic != null && !context.isDiagnosticSuppressed(diagnostic)) {
            recorder.record(diagnostic, context)
        }
        delegate.report(diagnostic, context)
    }
