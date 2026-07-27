package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe

/**
 * Reports watchdog exemption annotations that don't explain why they are applied. Every
 * exemption defaults to `reason = ExemptionReason.OTHER` with an empty `description`, and such a
 * bare acknowledgement documents nothing: authors either pick a reason that speaks for itself or
 * spell the motivation out in `description`. Only `FOR_BACKWARDS_COMPATIBILITY` and `API_DESIGN`
 * explain an exemption on their own; the other reasons merely categorize it and keep the
 * description shorter, so they still require one. Unlike the API-surface checks, this one
 * validates the annotation call itself, so it fires on every usage regardless of the annotated
 * declaration's visibility, and it is always an error: the explanation requirement is what keeps
 * the other exemptions honest.
 *
 * Exemptions written on type usages (`List<@IntentionallyMutableCollection MutableList<Int>>`)
 * are not declaration annotations and never reach this checker; they are validated by the
 * checker that honors them ([MutableCollectionChecker]) instead.
 */
internal class ExemptionExplanationChecker : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirDeclaration) {
        for (annotation in declaration.symbol.resolvedAnnotationsWithArguments) {
            val classId = annotation.toAnnotationClassIdSafe(context.session) ?: continue
            if (classId !in WatchdogClassIds.exemptionAnnotations) {
                continue
            }

            val unexplainedReason = annotation.unexplainedExemptionReason() ?: continue

            reporter.reportOn(
                source = annotation.source,
                factory = WatchdogDiagnostics.EXEMPTION_WITHOUT_EXPLANATION,
                a = classId.shortClassName,
                b = unexplainedReason,
            )
        }
    }
}
