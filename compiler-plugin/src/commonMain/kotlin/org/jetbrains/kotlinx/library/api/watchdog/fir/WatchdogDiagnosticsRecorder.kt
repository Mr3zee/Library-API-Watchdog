package org.jetbrains.kotlinx.library.api.watchdog.fir

import java.io.File
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithParameters1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithParameters2
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithParameters3
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithParameters4
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithSource
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.type.FirTypeChecker
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.getSingleMatchedExpectForActualOrNull
import org.jetbrains.kotlin.fir.declarations.utils.isActual
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
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
 * Two line names aren't diagnostics:
 *
 * - [EXPECT_ACTUAL_PAIR]: fields 2 to 6 hold the `actual` declaration, in the fields of a source
 *   element. Fields 7 to 9 hold the path, the start offset, and the end offset of its `expect`
 *   declaration.
 * - [REDUNDANT_COPY]: the copy on an `actual` of a redundant `expect` exemption, in the fields of a
 *   `REDUNDANT_EXEMPTION` diagnostic. The compiler doesn't report the copy, and the fixer removes it
 *   with the original.
 *
 * The recorder never truncates the file, because several compiler sessions of one build write
 * to it. The caller creates an empty file before the compilation.
 */
class WatchdogDiagnosticsRecorder(private val outputFile: File) {
    fun record(diagnostic: KtDiagnostic, context: DiagnosticContext) {
        val element = (diagnostic as? KtDiagnosticWithSource)?.element ?: return
        val path = context.containingFilePath ?: return
        val range = firstRange(diagnostic)
        append(listOf(diagnostic.factoryName, path, element.startOffset, element.endOffset, range.first, range.second), parameters(diagnostic))
    }

    fun recordExpectActualPair(actualPath: String, actual: KtSourceElement, expectPath: String, expect: KtSourceElement) {
        append(
            listOf(EXPECT_ACTUAL_PAIR, actualPath, actual.startOffset, actual.endOffset, actual.startOffset, actual.endOffset),
            listOf(expectPath, expect.startOffset.toString(), expect.endOffset.toString()),
        )
    }

    /** [parameters] are the message parameters of the `REDUNDANT_EXEMPTION` diagnostic on the original. */
    fun recordRedundantCopy(path: String, copy: KtSourceElement, parameters: List<String>) {
        append(listOf(REDUNDANT_COPY, path, copy.startOffset, copy.endOffset, copy.startOffset, copy.endOffset), parameters)
    }

    private fun append(fields: List<Any>, parameters: List<String>) {
        val line = (fields.map { it.toString() } + parameters.map { it.escaped() })
            .joinToString(separator = "\t", postfix = "\n")
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

    companion object {
        const val EXPECT_ACTUAL_PAIR: String = "EXPECT_ACTUAL_PAIR"
        const val REDUNDANT_COPY: String = "REDUNDANT_COPY"

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

/**
 * Records each `actual` with its `expect`, so the fixer can copy an exemption of the `expect` to the
 * `actual`. Skips an `actual typealias`, because the compiler compares the expanded class instead.
 */
internal class ExpectActualPairRecorder(
    private val recorder: WatchdogDiagnosticsRecorder,
) : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirDeclaration) {
        if (declaration !is FirRegularClass && declaration !is FirCallableDeclaration) return
        if (declaration.isActual.not()) return
        val actualSource = declaration.source?.takeIf {
            it.kind == KtRealSourceElementKind || it.kind == KtFakeSourceElementKind.PropertyFromParameter
        } ?: return
        val actualPath = context.containingFilePath ?: return
        val expect = declaration.symbol.getSingleMatchedExpectForActualOrNull() ?: return
        val expectSource = expect.source?.takeIf { it.kind == KtRealSourceElementKind } ?: return
        val expectPath = expect.sourceFilePath() ?: return
        recorder.recordExpectActualPair(actualPath, actualSource, expectPath, expectSource)
    }
}

/** The path of the source file of the declaration, or null for a declaration without one. */
internal fun FirBasedSymbol<*>.sourceFilePath(): String? {
    val provider = moduleData.session.firProvider
    val file = when (this) {
        is FirClassLikeSymbol<*> -> provider.getFirClassifierContainerFileIfAny(this)
        is FirCallableSymbol<*> -> provider.getFirCallableContainerFile(this)
        else -> null
    }
    return file?.sourceFile?.path
}
