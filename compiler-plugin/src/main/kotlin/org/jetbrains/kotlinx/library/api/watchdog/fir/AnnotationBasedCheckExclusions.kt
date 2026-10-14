package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol

/**
 * The annotation FQ names that disable each configurable diagnostic on the annotated declaration.
 * A rule has the scope of the diagnostic's exemption annotation: for a diagnostic with
 * [ConfigurableWatchdogDiagnostic.exemptionCoversEnclosingClass], the annotation on an enclosing
 * class also counts.
 */
internal class AnnotationBasedCheckExclusions(
    private val annotationNamesByDiagnostic: Map<String, Set<String>>,
) {
    val isEmpty: Boolean get() = annotationNamesByDiagnostic.isEmpty()

    context(context: CheckerContext)
    fun ignores(diagnosticName: String, declaration: FirDeclaration): Boolean {
        val ignoredAnnotations = annotationNamesByDiagnostic[diagnosticName] ?: return false
        if (declaration.hasAnyAnnotationOf(ignoredAnnotations)) return true
        return diagnosticName in diagnosticsCoveringEnclosingClass &&
                context.containingDeclarations.any { it is FirClassSymbol<*> && it.hasAnyAnnotationOf(ignoredAnnotations) }
    }

    context(context: CheckerContext)
    private fun FirAnnotationContainer.hasAnyAnnotationOf(names: Set<String>): Boolean =
        annotations.any { annotation ->
            annotation.toAnnotationClassIdSafe(context.session)?.asSingleFqName()?.asString() in names
        }

    context(context: CheckerContext)
    private fun FirClassSymbol<*>.hasAnyAnnotationOf(names: Set<String>): Boolean =
        resolvedAnnotationsWithClassIds.any { annotation ->
            annotation.toAnnotationClassIdSafe(context.session)?.asSingleFqName()?.asString() in names
        }

    companion object {
        val NONE: AnnotationBasedCheckExclusions = AnnotationBasedCheckExclusions(emptyMap())

        private val diagnosticsCoveringEnclosingClass: Set<String> =
            WatchdogDiagnostics.allDiagnostics.filter { it.exemptionCoversEnclosingClass }.mapTo(hashSetOf()) { it.name }
    }
}

internal class AnnotationExcludingDeclarationChecker<D : FirDeclaration>(
    private val delegate: FirDeclarationChecker<D>,
    private val exclusions: AnnotationBasedCheckExclusions,
) : FirDeclarationChecker<D>(delegate.mppKind) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: D) {
        val filteringReporter = delegatingDiagnosticReporter(reporter) { diagnostic, diagnosticContext ->
            if (
                diagnostic == null ||
                !exclusions.ignores(diagnostic.factoryName, declaration)
            ) {
                reporter.report(diagnostic, diagnosticContext)
            }
        }
        context(context, filteringReporter) { delegate.check(declaration) }
    }
}
