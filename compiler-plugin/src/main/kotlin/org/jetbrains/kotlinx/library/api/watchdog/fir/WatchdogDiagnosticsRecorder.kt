package org.jetbrains.kotlinx.library.api.watchdog.fir

import java.io.File
import org.jetbrains.kotlin.diagnostics.DiagnosticContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithParameters1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithParameters2
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithParameters3
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithParameters4
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithSource
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.type.FirTypeChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.types.FirTypeRef

/**
 * Adds every reported diagnostic to the end of [outputFile] as a tab-separated line:
 *
 * 1. The diagnostic name.
 * 2. The absolute source file path.
 * 3. The start offset of the source element.
 * 4. The end offset of the source element.
 * 5. The start offset of the reported range.
 * 6. The end offset of the reported range.
 * 7. One field for each rendered message parameter. The recorder writes a backslash, a tab, a
 *    line feed, and a carriage return as `\\`, `\t`, `\n`, and `\r`.
 *
 * The recorder never truncates the file, because several compiler sessions of one build write
 * to it. The caller creates an empty file before the compilation.
 */
class WatchdogDiagnosticsRecorder(private val outputFile: File) {
    fun record(diagnostic: KtDiagnostic, context: DiagnosticContext) {
        val element = (diagnostic as? KtDiagnosticWithSource)?.element ?: return
        val path = context.containingFilePath ?: return
        val fields = buildList {
            add(diagnostic.factoryName)
            add(path)
            add(element.startOffset.toString())
            add(element.endOffset.toString())
            val range = firstRange(diagnostic)
            add(range.first.toString())
            add(range.second.toString())
            parameters(diagnostic).mapTo(this) { it.escaped() }
        }
        val line = fields.joinToString(separator = "\t", postfix = "\n")
        synchronized(APPEND_LOCK) {
            outputFile.parentFile?.mkdirs()
            outputFile.appendText(line)
        }
    }

    /**
     * The offsets of `KtDiagnosticWithSource.firstRange`. The getter puts
     * `com.intellij.openapi.util.TextRange` into the bytecode, which links differently in the CLI
     * and in `kotlin-compiler-embeddable`, so the recorder calls it through reflection.
     */
    private fun firstRange(diagnostic: KtDiagnosticWithSource): Pair<Int, Int> {
        val range = FIRST_RANGE.invoke(diagnostic)
        val start = range.javaClass.getMethod("getStartOffset").invoke(range) as Int
        val end = range.javaClass.getMethod("getEndOffset").invoke(range) as Int
        return start to end
    }

    private fun parameters(diagnostic: KtDiagnostic): List<String> = when (diagnostic) {
        is KtDiagnosticWithParameters1<*> -> listOf(diagnostic.a)
        is KtDiagnosticWithParameters2<*, *> -> listOf(diagnostic.a, diagnostic.b)
        is KtDiagnosticWithParameters3<*, *, *> -> listOf(diagnostic.a, diagnostic.b, diagnostic.c)
        is KtDiagnosticWithParameters4<*, *, *, *> -> listOf(diagnostic.a, diagnostic.b, diagnostic.c, diagnostic.d)
        else -> emptyList()
    }.map { it.toString() }

    private fun String.escaped(): String = replace("\\", "\\\\")
        .replace("\t", "\\t")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private companion object {
        /** The sessions of a compilation share the output file. */
        private val APPEND_LOCK = Any()

        private val FIRST_RANGE = KtDiagnosticWithSource::class.java.getMethod("getFirstRange")
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
