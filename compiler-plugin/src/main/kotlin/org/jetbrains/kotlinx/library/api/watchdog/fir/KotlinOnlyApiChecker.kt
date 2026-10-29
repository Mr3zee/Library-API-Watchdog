package org.jetbrains.kotlinx.library.api.watchdog.fir

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
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.contextParameterNumberForFunctionType
import org.jetbrains.kotlin.fir.types.functionTypeKind
import org.jetbrains.kotlin.fir.types.isExtensionFunctionType
import org.jetbrains.kotlin.fir.types.isUnit
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.varargElementType
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.ClassId

internal class KotlinOnlyApiChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirFunctionChecker(MppCheckerKind.Common), ExemptingChecker {
    override val exemptions: Set<ClassId> = setOf(WatchdogClassIds.IntentionallyKotlinOnlyApi)

    override val enclosingClassExemptions: Set<ClassId> = setOf(WatchdogClassIds.IntentionallyKotlinOnlyApi)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        if (declaration !is FirNamedFunction || declaration.isExpectedDeclaration() ||
            declaration.isOverride || declaration.isAbstract
        ) {
            return
        }

        val containingClass = context.containingClassSymbol
        if (containingClass?.classKind == ClassKind.INTERFACE || containingClass?.isValueClass() == true) {
            return
        }

        if (!declaration.isWatchedPublicSourceApi()) {
            return
        }

        if (declaration.hasAnnotationOnActualOrExpect(JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID)) {
            return
        }

        // MangledJvmNameChecker reports a mangled function, unless it is suspend. `@JvmName` or
        // `@JvmExposeBoxed` resolves the mangling, and then this check reports the Kotlin-only shape.
        if (!declaration.isSuspend && declaration.hasMangledJvmName()) {
            return
        }

        if (declaration.hasAnnotationOnSelfOrEnclosingClass(WatchdogClassIds.IntentionallyKotlinOnlyApi)) {
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

    /** The Kotlin-only part of the shape, in words, or null. */
    context(context: CheckerContext)
    private fun FirNamedFunction.kotlinOnlyShape(): String? {
        if (isSuspend) {
            return sharedShape("suspend")
        }
        if (isInline && typeParameters.any { it.symbol.isReified }) {
            return sharedShape("reified")
        }
        return contextParameters.firstNotNullOfOrNull { it.kotlinOnlyFunctionType() }
            ?: valueParameters.firstNotNullOfOrNull { it.kotlinOnlyFunctionType() }
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
            type.contextParameterNumberForFunctionType > 0 ->
                sharedShape("contextFunctionType", name.asString())
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
