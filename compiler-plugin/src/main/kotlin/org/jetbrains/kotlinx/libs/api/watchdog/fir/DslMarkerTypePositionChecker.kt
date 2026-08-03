package org.jetbrains.kotlinx.libs.api.watchdog.fir

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

/**
 * Reports DSL marker annotations on callable type positions other than receiver types, context
 * parameter types, and function types with implicit values. The check is position-local: later
 * use of the annotated type as an inferred receiver does not change the reported position.
 *
 * All declaration visibilities are checked.
 */
internal class DslMarkerTypePositionChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirCallableDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirCallableDeclaration) {
        val factory = severities[WatchdogDiagnostics.DSL_MARKER_NOOP_TYPE_POSITION] ?: return

        // `val`/`var` constructor parameters also produce a property with a fake source pointing
        // at the same parameter text; skipping fake sources keeps the report single.
        if (declaration.source?.kind != KtRealSourceElementKind) {
            return
        }

        val position = when (declaration) {
            is FirValueParameter -> {
                // A context parameter is an implicit value, so a marker on its type is effective.
                if (declaration.valueParameterKind != FirValueParameterKind.Regular) return
                "parameter type"
            }
            is FirProperty -> if (declaration.isLocal) "variable type" else "property type"
            is FirFunction -> "return type"
            else -> return
        }
        val typeRef = declaration.returnTypeRef

        val session = context.session
        val markers = typeRef.annotations.filter { it.isDslMarker(session) }
        if (markers.isEmpty()) {
            return
        }

        // A marker on a function type propagates to its receiver and context parameters, so it is
        // effective as long as the type has at least one implicit value. Receiver types inside a
        // function type ((@M Tag).() -> Unit) are nested type refs and never reach this point.
        val coneType = typeRef.coneType
        if (coneType.receiverType(session) != null || coneType.contextParameterTypes(session).isNotEmpty()) {
            return
        }

        for (annotation in markers) {
            val markerName = annotation.annotationClassSymbol(session)?.name ?: continue
            reporter.reportOn(
                source = annotation.source,
                factory = factory,
                a = markerName,
                b = position,
            )
        }
    }

    private fun FirAnnotation.isDslMarker(session: FirSession): Boolean =
        annotationClassSymbol(session)
            ?.hasAnnotation(StandardClassIds.Annotations.DslMarker, session) == true

    private fun FirAnnotation.annotationClassSymbol(session: FirSession): FirClassSymbol<*>? =
        annotationTypeRef.coneType.fullyExpandedType(session).toClassSymbol(session)
}
