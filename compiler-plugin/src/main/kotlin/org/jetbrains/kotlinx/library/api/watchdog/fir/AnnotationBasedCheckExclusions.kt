package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe

/** Configurable diagnostics suppressed by annotations on the checked declaration. */
internal class AnnotationBasedCheckExclusions(
    private val annotationNamesByDiagnostic: Map<String, Set<String>>,
) {
    val isEmpty: Boolean get() = annotationNamesByDiagnostic.isEmpty()

    fun ignores(diagnosticName: String, declaration: FirDeclaration, session: FirSession): Boolean {
        val ignoredAnnotations = annotationNamesByDiagnostic[diagnosticName] ?: return false
        return declaration.annotations.any { annotation ->
            annotation.toAnnotationClassIdSafe(session)
                ?.asSingleFqName()
                ?.asString() in ignoredAnnotations
        }
    }

    companion object {
        val NONE: AnnotationBasedCheckExclusions = AnnotationBasedCheckExclusions(emptyMap())
    }
}

/** Applies [AnnotationBasedCheckExclusions] before a diagnostic reaches recording and reporting. */
internal class AnnotationExcludingDeclarationChecker<D : FirDeclaration>(
    private val delegate: FirDeclarationChecker<D>,
    private val exclusions: AnnotationBasedCheckExclusions,
) : FirDeclarationChecker<D>(delegate.mppKind) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: D) {
        val filteringReporter = delegatingDiagnosticReporter(reporter) { diagnostic, diagnosticContext ->
            if (
                diagnostic == null ||
                !exclusions.ignores(diagnostic.factoryName, declaration, context.session)
            ) {
                reporter.report(diagnostic, diagnosticContext)
            }
        }
        context(context, filteringReporter) { delegate.check(declaration) }
    }
}
