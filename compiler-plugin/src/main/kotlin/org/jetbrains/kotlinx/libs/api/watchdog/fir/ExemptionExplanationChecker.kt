package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.type.FirResolvedTypeRefChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.customAnnotations
import org.jetbrains.kotlin.fir.types.forEachType

/**
 * Reports watchdog exemptions whose reason requires a description and whose description is blank.
 * The declaration checker covers annotations attached to declarations; the type checker covers
 * annotations anywhere inside resolved types. The diagnostic is always an error in ordinary
 * compilations. The backwards-compatibility exemptions task temporarily omits both checkers because
 * it cannot repair explanations written by an API author.
 */
internal object ExemptionExplanationChecker {
    val declarationChecker: FirBasicDeclarationChecker = DeclarationChecker
    val typeChecker: FirResolvedTypeRefChecker = TypeChecker

    private object DeclarationChecker : FirBasicDeclarationChecker(MppCheckerKind.Common) {
        context(context: CheckerContext, reporter: DiagnosticReporter)
        override fun check(declaration: FirDeclaration) {
            for (annotation in declaration.symbol.resolvedAnnotationsWithArguments) {
                checkAnnotation(annotation)
            }
        }
    }

    private object TypeChecker : FirResolvedTypeRefChecker(MppCheckerKind.Common) {
        context(context: CheckerContext, reporter: DiagnosticReporter)
        override fun check(typeRef: FirResolvedTypeRef) {
            var checkedAnnotations: MutableSet<FirAnnotation>? = null
            typeRef.coneType.forEachType { type ->
                for (annotation in type.customAnnotations) {
                    val checked = checkedAnnotations
                    if (checked == null) {
                        checkedAnnotations = hashSetOf(annotation)
                        checkAnnotation(annotation)
                    } else if (checked.add(annotation)) {
                        checkAnnotation(annotation)
                    }
                }
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkAnnotation(annotation: FirAnnotation) {
        val classId = annotation.toAnnotationClassIdSafe(context.session) ?: return
        if (classId !in WatchdogClassIds.exemptionAnnotations) {
            return
        }

        val unexplainedReason = annotation.unexplainedExemptionReason() ?: return

        reporter.reportOn(
            source = annotation.source,
            factory = WatchdogDiagnostics.EXEMPTION_WITHOUT_EXPLANATION,
            a = classId.shortClassName,
            b = unexplainedReason,
        )
    }
}
