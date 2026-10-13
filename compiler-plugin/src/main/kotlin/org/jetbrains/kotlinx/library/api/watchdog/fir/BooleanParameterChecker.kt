package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory2
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.isLegacyContextReceiver
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.abbreviatedTypeOrSelf
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.varargElementType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

internal class BooleanParameterChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        if (declaration !is FirNamedFunction || declaration.isActualizedDeclaration() ||
            declaration.isOverride || declaration.isConstructorFunction()
        ) {
            return
        }

        if (!declaration.isWatchedPublicSourceApi() || declaration.isExempt()) {
            return
        }

        val factory = severities[WatchdogDiagnostics.BOOLEAN_PARAMETER_PUBLIC_API] ?: return
        for (parameter in declaration.contextParameters) {
            checkParameter(parameter, declaration, factory)
        }
        for (parameter in declaration.valueParameters) {
            checkParameter(parameter, declaration, factory)
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkParameter(
        parameter: FirValueParameter,
        declaration: FirNamedFunction,
        factory: KtDiagnosticFactory2<Name, Name>,
    ) {
        if (parameter.isLegacyContextReceiver() || parameter.isExempt()) return
        if (parameter.declaredType().classId != StandardClassIds.Boolean) return

        reporter.reportOn(
            source = parameter.source ?: declaration.source,
            factory = factory,
            a = declaration.name,
            b = parameter.sourceName,
        )
    }

    /** A constructor function is named after the declared return type, the alias when the source names one. */
    private fun FirNamedFunction.isConstructorFunction(): Boolean =
        returnTypeRef.coneType.abbreviatedTypeOrSelf.classId?.shortClassName == name

    /** The type of one argument: the element type for a `vararg` parameter. */
    context(context: CheckerContext)
    private fun FirValueParameter.declaredType(): ConeKotlinType {
        val type = returnTypeRef.coneType.fullyExpandedType()
        return if (isVararg) type.varargElementType() else type
    }

    context(context: CheckerContext)
    private fun FirDeclaration.isExempt(): Boolean =
        hasAnnotation(WatchdogClassIds.IntentionallyBooleanParameter, context.session)
}
