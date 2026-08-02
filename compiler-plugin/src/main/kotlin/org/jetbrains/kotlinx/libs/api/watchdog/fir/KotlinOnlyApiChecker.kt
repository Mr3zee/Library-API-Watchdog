package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.functionTypeKind
import org.jetbrains.kotlin.fir.types.isExtensionFunctionType
import org.jetbrains.kotlin.fir.types.isUnit
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.JvmStandardClassIds

/**
 * Reports publicly visible functions whose shape only Kotlin callers can use idiomatically while
 * the function still lands in the API surface Java sources see:
 * - a `suspend` function, which Java sees with a trailing `Continuation` parameter it can't
 *   provide idiomatically,
 * - an `inline` function with a `reified` type parameter, whose compiled method can't be called
 *   from Java at all - the call fails at runtime,
 * - a function taking a Kotlin-specific function type: a suspend function type (no Java lambda
 *   can implement it), a function type with receiver (a Java lambda has to take the receiver as
 *   an explicit first argument), or a `Unit`-returning function type (a Java lambda has to
 *   return the `Unit.INSTANCE` token explicitly).
 *
 * The fix is either hiding the Kotlin-only shape from Java with `@JvmSynthetic` or providing a
 * Java-friendly alternative alongside - a blocking or `CompletableFuture`-returning bridge for a
 * suspend function, a `fun interface` parameter in place of a Kotlin function type. Authors
 * acknowledge a deliberately Java-visible Kotlin-only shape with `@IntentionallyKotlinOnlyApi` -
 * on the function, or on a class, where it covers every function inside.
 *
 * Deliberate exceptions:
 * - Non-JVM compilations: what Java sources see is a JVM-only concern, so [WatchdogFirCheckers]
 *   only registers this checker when the platform is JVM.
 * - Abstract and interface members: `@JvmSynthetic` can't hide a member that implementations
 *   must provide, so there is no non-breaking fix to suggest on the declaration itself.
 * - Overrides: their signature is fixed by the overridden declaration and is reported there.
 * - Constructors: `@JvmSynthetic` is not applicable to them, and their Kotlin-specific
 *   function-type parameters read the same for Java as any other overload's.
 * - Signatures mangled by a value class, and members of a value class: they are already
 *   invisible to Java sources and reported by [MangledJvmNameChecker].
 * - `@JvmSynthetic` declarations: the Kotlin-only shape is already hidden on purpose.
 */
internal class KotlinOnlyApiChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        if (declaration !is FirNamedFunction || declaration.isOverride || declaration.isAbstract) {
            return
        }

        val containingClass = context.containingClassSymbol
        if (containingClass?.classKind == ClassKind.INTERFACE || containingClass?.isValueClass() == true) {
            return
        }

        if (!declaration.isWatchedPublicApi()) {
            return
        }

        if (declaration.hasAnnotation(JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID, context.session)) {
            return
        }

        if (declaration.mangledValueClassInSignature() != null) {
            return
        }

        // The exemption is honored on the function itself and on any enclosing class, where it
        // acknowledges every function inside as deliberately Kotlin-only.
        if (declaration.hasAnnotation(WatchdogClassIds.IntentionallyKotlinOnlyApi, context.session) ||
            context.containingDeclarations.any {
                it is FirClassSymbol<*> && it.hasAnnotation(WatchdogClassIds.IntentionallyKotlinOnlyApi, context.session)
            }
        ) {
            return
        }

        val kotlinOnlyShape = declaration.kotlinOnlyShape() ?: return
        val factory = severities[WatchdogDiagnostics.KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC] ?: return
        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = declaration.name,
            b = kotlinOnlyShape,
        )
    }

    /** What makes the function's shape Kotlin-only, in words, or null for a Java-usable shape. */
    context(context: CheckerContext)
    private fun FirNamedFunction.kotlinOnlyShape(): String? {
        if (isSuspend) {
            return sharedShape("suspend")
        }
        if (isInline && typeParameters.any { it.symbol.isReified }) {
            return sharedShape("reified")
        }
        return valueParameters.firstNotNullOfOrNull { it.kotlinOnlyFunctionType() }
    }

    context(context: CheckerContext)
    private fun FirValueParameter.kotlinOnlyFunctionType(): String? {
        val session = context.session
        val type = returnTypeRef.coneType.fullyExpandedType()
        val functionTypeKind = type.functionTypeKind(session) ?: return null
        if (functionTypeKind.isReflectType) {
            return null
        }
        return when {
            functionTypeKind == FunctionTypeKind.SuspendFunction ->
                sharedShape("suspendFunctionType", name.asString())
            type.isExtensionFunctionType ->
                sharedShape("extensionFunctionType", name.asString())
            type.typeArguments.lastOrNull()?.type?.fullyExpandedType()?.isUnit == true ->
                sharedShape("unitFunctionType", name.asString())
            else -> null
        }
    }

    private fun sharedShape(name: String, vararg parameters: String): String =
        WatchdogDiagnosticMessages.parameterValueFor(
            diagnostic = "KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC",
            value = name,
            parameters = parameters,
        )
}
