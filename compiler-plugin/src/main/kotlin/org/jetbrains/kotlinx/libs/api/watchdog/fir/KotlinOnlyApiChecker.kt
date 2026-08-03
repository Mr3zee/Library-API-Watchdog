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
import org.jetbrains.kotlin.fir.types.varargElementType
import org.jetbrains.kotlin.name.JvmStandardClassIds

/**
 * Reports watched functions that are `suspend`, have a reified type parameter, or take a suspend,
 * extension, or `Unit`-returning function type.
 *
 * Abstract and interface members, overrides, constructors, value-class members, value-class-mangled
 * signatures, and Java-hidden declarations are skipped. A class-level exemption covers its
 * functions. [WatchdogFirCheckers] registers this checker only for JVM compilations.
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

        if (!declaration.isWatchedPublicSourceApi()) {
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
        var type = returnTypeRef.coneType.fullyExpandedType()
        if (isVararg) {
            type = type.varargElementType().fullyExpandedType()
        }
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
