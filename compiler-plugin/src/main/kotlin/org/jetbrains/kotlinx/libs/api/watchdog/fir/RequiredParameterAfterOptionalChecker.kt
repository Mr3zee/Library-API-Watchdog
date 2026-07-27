package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isFun
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.functionTypeKind

/**
 * Reports required parameters of publicly visible functions and constructors that are declared
 * after an optional (defaulted or `vararg`) parameter:
 * [parameters should go from the general to the specific](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage),
 * essential inputs first and optional inputs last. A required parameter behind optional ones
 * can't be passed positionally without re-stating the defaults in front of it. Authors
 * acknowledge a deliberate order with `@IntentionallyRequiredParameterAfterOptional`.
 *
 * A required function-type or `fun interface` parameter in the last position is exempt: keeping
 * it last is what makes trailing-lambda call syntax available, and the stdlib itself places such
 * parameters after defaulted ones (`joinToString(separator = ..., transform)`).
 *
 * Overrides are exempt as well: they can't declare default values, and their parameter order is
 * fixed by the overridden declaration, which is reported where it is declared.
 */
internal class RequiredParameterAfterOptionalChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        val factory = severities[WatchdogDiagnostics.REQUIRED_PARAMETER_AFTER_OPTIONAL] ?: return

        if (declaration !is FirNamedFunction && declaration !is FirConstructor) {
            return
        }

        if (!declaration.isWatchedPublicApi() || declaration.isOverride) {
            return
        }

        if (declaration.hasAnnotation(WatchdogClassIds.IntentionallyRequiredParameterAfterOptional, context.session)) {
            return
        }

        val callableName = declaration.reportedName() ?: return
        val parameters = declaration.valueParameters
        var seenOptional = false
        for ((index, parameter) in parameters.withIndex()) {
            if (parameter.defaultValue != null || parameter.isVararg) {
                seenOptional = true
                continue
            }

            if (!seenOptional) {
                continue
            }

            if (index == parameters.lastIndex && parameter.acceptsTrailingLambda()) {
                continue
            }

            reporter.reportOn(
                source = parameter.source ?: declaration.source,
                factory = factory,
                a = parameter.name,
                b = callableName,
            )
        }
    }

    /**
     * Whether a lambda literal can be passed for this parameter in trailing position: its
     * (fully expanded) type is a function type - suspend and nullable variants included, but not
     * the `KFunction` reflection kinds, which no lambda literal satisfies - or a `fun interface`,
     * where SAM conversion keeps the same call syntax.
     */
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
