package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlinx.library.api.watchdog.AnnotationIgnoreRule

/**
 * The annotation ignore rules of each configurable diagnostic. A non-recursive rule has the scope
 * of the diagnostic's exemption annotation, so the annotation on an enclosing class counts for a
 * diagnostic with [ConfigurableWatchdogDiagnostic.exemptionCoversEnclosingClass].
 */
internal class AnnotationBasedCheckExclusions(
    rulesByDiagnostic: Map<String, Set<AnnotationIgnoreRule>>,
) {
    private class Annotations(val all: Set<String>, val recursive: Set<String>)

    private val annotationsByDiagnostic: Map<String, Annotations> = rulesByDiagnostic.mapValues { (_, rules) ->
        Annotations(
            all = rules.mapTo(hashSetOf()) { it.annotationName },
            recursive = rules.filter { it.recursive }.mapTo(hashSetOf()) { it.annotationName },
        )
    }

    val isEmpty: Boolean get() = annotationsByDiagnostic.isEmpty()

    context(context: CheckerContext)
    fun ignores(diagnosticName: String, declaration: FirDeclaration): Boolean {
        val annotations = annotationsByDiagnostic[diagnosticName] ?: return false
        if (declaration.hasAnyAnnotationOf(annotations.all)) return true
        if (
            diagnosticName in diagnosticsCoveringEnclosingClass &&
            context.containingDeclarations.any { it is FirClassSymbol<*> && it.hasAnyAnnotationOf(annotations.all) }
        ) {
            return true
        }
        return annotations.recursive.isNotEmpty() &&
                context.containingDeclarations.any { it.hasAnyAnnotationOf(annotations.recursive) }
    }

    context(context: CheckerContext)
    private fun FirAnnotationContainer.hasAnyAnnotationOf(names: Set<String>): Boolean =
        annotations.any { annotation ->
            annotation.toAnnotationClassIdSafe(context.session)?.asSingleFqName()?.asString() in names
        }

    context(context: CheckerContext)
    private fun FirBasedSymbol<*>.hasAnyAnnotationOf(names: Set<String>): Boolean =
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
