package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory2
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
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

/**
 * Reports Boolean value and context parameters of watched functions, including nullable and
 * aliased Booleans. For `vararg` parameters it inspects the declared element type rather than the
 * generated array type.
 *
 * Constructors, constructor functions, overrides, and legacy context receivers are skipped.
 * Exemptions may cover the whole function or one parameter. Return types and properties are not
 * inspected.
 */
internal class BooleanParameterChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        if (!declaration.isNamedFunction() || declaration.isOverride || declaration.isConstructorFunction()) {
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
        declaration: FirFunction,
        factory: KtDiagnosticFactory2<Name, Name>,
    ) {
        if (parameter.isLegacyContextReceiver() || parameter.isExempt()) return
        if (parameter.declaredType().classId != StandardClassIds.Boolean) return

        reporter.reportOn(
            source = parameter.source ?: declaration.source,
            factory = factory,
            a = declaration.namedFunctionName,
            b = parameter.name,
        )
    }

    /**
     * A constructor function is named after the type it creates - the alias, not its expansion,
     * when the declared return type is a type alias, since that is the name the call site reads.
     */
    private fun FirFunction.isConstructorFunction(): Boolean =
        returnTypeRef.coneType.abbreviatedTypeOrSelf.classId?.shortClassName == namedFunctionName

    /**
     * The type users pass arguments as: the fully expanded parameter type, unwrapped to the
     * declared element type for `vararg` parameters (`vararg flags: Boolean` is typed as
     * `BooleanArray`, but every argument is a plain Boolean).
     */
    context(context: CheckerContext)
    private fun FirValueParameter.declaredType(): ConeKotlinType {
        val type = returnTypeRef.coneType.fullyExpandedType()
        return if (isVararg) type.varargElementType() else type
    }

    context(context: CheckerContext)
    private fun FirDeclaration.isExempt(): Boolean =
        hasAnnotation(WatchdogClassIds.IntentionallyBooleanParameter, context.session)
}
