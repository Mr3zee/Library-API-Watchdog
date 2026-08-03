package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe

/**
 * Reports declaration-level watchdog exemptions whose reason requires a description and whose
 * description is blank. All declaration visibilities are checked, and the diagnostic is always an
 * error.
 *
 * Type-use exemptions do not reach declaration checkers and are validated by the checker that
 * honors them through [FirAnnotation.unexplainedExemptionReason].
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
