package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.FirValueParameterKind
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.contextParameterTypes
import org.jetbrains.kotlin.fir.types.receiverType
import org.jetbrains.kotlin.name.StandardClassIds

/** Checks every visibility, because a marker on a private type doesn't have any effect either. */
internal class DslMarkerTypePositionChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirCallableDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirCallableDeclaration) {
        val factory = severities[WatchdogDiagnostics.DSL_MARKER_NOOP_TYPE_POSITION] ?: return

        if (declaration.isActualizedDeclaration()) {
            return
        }

        // The property of a val/var constructor parameter has a fake source over the same text.
        if (declaration.source?.kind != KtRealSourceElementKind) {
            return
        }

        val position = when (declaration) {
            is FirValueParameter -> {
                if (declaration.valueParameterKind != FirValueParameterKind.Regular) return
                "parameterType"
            }
            is FirProperty -> if (declaration.isLocal) "variableType" else "propertyType"
            is FirFunction -> "returnType"
            else -> return
        }
        val typeRef = declaration.returnTypeRef

        val session = context.session
        var checkedFunctionTypePosition = false
        for (annotation in typeRef.annotations) {
            if (!annotation.isDslMarker(session)) continue

            if (!checkedFunctionTypePosition) {
                // A marker on a function type applies to its receiver and context parameters.
                val coneType = typeRef.coneType
                if (coneType.receiverType(session) != null ||
                    coneType.contextParameterTypes(session).isNotEmpty()
                ) {
                    return
                }
                checkedFunctionTypePosition = true
            }

            val markerName = annotation.annotationClassSymbol(session)?.name ?: continue
            reporter.reportOn(
                source = annotation.source,
                factory = factory,
                a = markerName,
                b = WatchdogDiagnosticMessages.parameterValueFor(factory.name, position),
            )
        }
    }

    private fun FirAnnotation.isDslMarker(session: FirSession): Boolean =
        annotationClassSymbol(session)
            ?.hasAnnotation(StandardClassIds.Annotations.DslMarker, session) == true

    private fun FirAnnotation.annotationClassSymbol(session: FirSession): FirClassSymbol<*>? =
        annotationTypeRef.coneType.fullyExpandedType(session).toClassSymbol(session)
}
