package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.utils.isFun
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.functionTypeKind
import org.jetbrains.kotlin.name.ClassId

/**
 * A last required parameter that accepts a trailing lambda isn't reported. Context parameters
 * aren't checked, because the call site doesn't pass them.
 */
internal class RequiredParameterAfterOptionalChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirFunctionChecker(MppCheckerKind.Common), ExemptingChecker {
    override val exemptions: Set<ClassId> = setOf(WatchdogClassIds.IntentionallyRequiredParameterAfterOptional)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        val factory = severities[WatchdogDiagnostics.REQUIRED_PARAMETER_AFTER_OPTIONAL] ?: return

        if (declaration !is FirConstructor && declaration !is FirNamedFunction) {
            return
        }

        if (declaration.isActualizedDeclaration() ||
            !declaration.isWatchedPublicSourceApi() || declaration.isOverride
        ) {
            return
        }

        if (declaration.hasExemption(WatchdogClassIds.IntentionallyRequiredParameterAfterOptional)) {
            return
        }

        val callableName = declaration.reportedName() ?: return
        val parameters = declaration.valueParameters
        var seenOptional = false
        var index = 0
        while (index < parameters.size) {
            val parameter = parameters[index]
            index++
            if (parameter.defaultValue != null || parameter.isVararg) {
                seenOptional = true
                continue
            }

            if (!seenOptional) {
                continue
            }

            if (index == parameters.size && parameter.acceptsTrailingLambda()) {
                continue
            }

            reporter.reportOn(
                source = parameter.source ?: declaration.source,
                factory = factory,
                a = parameter.name,
                b = callableName,
                c = WatchdogDiagnosticMessages.parameterValueFor(
                    factory.name,
                    if (declaration is FirConstructor) "constructor" else "function",
                ),
            )
        }
    }

    /** No lambda literal satisfies a `KFunction` reflection type. */
    context(context: CheckerContext)
    private fun FirValueParameter.acceptsTrailingLambda(): Boolean {
        val type = returnTypeRef.coneType.let {
            if (it is ConeClassLikeType) it.fullyExpandedType() else it
        }

        val functionTypeKind = type.functionTypeKind(context.session)
        if (functionTypeKind != null) {
            return !functionTypeKind.isReflectType
        }

        val classSymbol = (type as? ConeClassLikeType)?.toClassSymbol() ?: return false
        return classSymbol.isFun
    }
}
