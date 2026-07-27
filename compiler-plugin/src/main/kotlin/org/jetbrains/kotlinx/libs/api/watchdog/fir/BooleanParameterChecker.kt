package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.FirValueParameterKind
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.abbreviatedTypeOrSelf
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.varargElementType
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * Reports [Boolean value parameters](https://kotlinlang.org/docs/api-guidelines-readability.html#avoid-using-the-boolean-type-as-an-argument)
 * of publicly visible functions. At the call site a positional `true`/`false` argument reveals
 * nothing about what it controls, and users cannot be forced to use named arguments, so the API
 * should model the modes as separate, descriptively named functions or as an enum class instead.
 * Nullable Booleans are three-state flags and count too, a type alias does not change what
 * users pass, and a `vararg` Boolean parameter takes the same positional `true`/`false`
 * arguments (only the declared element type matters there, not the array carrying it). Authors
 * acknowledge a deliberate Boolean parameter with `@IntentionallyBooleanParameter` - on the
 * function, where it covers every parameter, or on a single parameter.
 *
 * Deliberate exceptions:
 * - Constructors: a construction site stores data in the named type rather than switching an
 *   operation mode, and there is no behavior to split into descriptively named constructors.
 * - Constructor functions - factory functions named after the type they create, as in
 *   `fun Widget(visible: Boolean): Widget` (the alias name counts for a factory returning a type
 *   alias): they share the constructor call shape by design.
 * - Overrides: their signature is fixed by the overridden declaration and is reported there.
 * - Non-regular parameters (context parameters): implicit values are never passed as positional
 *   arguments.
 *
 * Boolean return types and Boolean properties are not arguments and are not checked.
 */
internal class BooleanParameterChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        if (declaration !is FirNamedFunction || declaration.isOverride || declaration.isConstructorFunction()) {
            return
        }

        if (!declaration.isWatchedPublicApi() || declaration.isExempt()) {
            return
        }

        val factory = severities[WatchdogDiagnostics.BOOLEAN_PARAMETER_PUBLIC_API] ?: return
        for (parameter in declaration.valueParameters) {
            if (parameter.valueParameterKind != FirValueParameterKind.Regular || parameter.isExempt()) {
                continue
            }
            if (parameter.declaredType().classId != StandardClassIds.Boolean) {
                continue
            }
            reporter.reportOn(
                source = parameter.source ?: declaration.source,
                factory = factory,
                a = declaration.name,
                b = parameter.name,
            )
        }
    }

    /**
     * A constructor function is named after the type it creates - the alias, not its expansion,
     * when the declared return type is a type alias, since that is the name the call site reads.
     */
    private fun FirNamedFunction.isConstructorFunction(): Boolean =
        returnTypeRef.coneType.abbreviatedTypeOrSelf.classId?.shortClassName == name

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
