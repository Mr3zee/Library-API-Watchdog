package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
import org.jetbrains.kotlin.name.JvmStandardClassIds

/**
 * Reports publicly visible functions and constructors that declare default parameter values
 * without `@JvmOverloads`. Defaults are a Kotlin-frontend feature: only the full signature is
 * compiled as a callable JVM entry point, so for Java callers the defaults don't exist and
 * every argument must be spelled out. `@JvmOverloads` additionally compiles the overloads that
 * omit defaulted parameters from the right.
 *
 * The recommendation is honest about its limits: `@JvmOverloads` generates right-truncated
 * overloads only - a defaulted parameter in the middle of the list still cannot be skipped from
 * Java (which is why [RequiredParameterAfterOptionalChecker] pushes optional parameters to the
 * end) - and it only improves Java call sites; it doesn't make adding a parameter later binary
 * compatible for Kotlin callers.
 *
 * Authors acknowledge deliberately Kotlin-only defaults with `@IntentionallyWithoutJvmOverloads`
 * on the function or constructor.
 *
 * Deliberate exceptions:
 * - Non-JVM compilations: overload generation is a JVM-interop concern, so
 *   [WatchdogFirCheckers] only registers this checker when the platform is JVM.
 * - Abstract and interface members: `@JvmOverloads` is not applicable to them.
 * - Annotation class constructors: not applicable there either - Java sources see annotation
 *   attributes with their own default handling instead.
 * - `suspend` functions and members of a value class: not Java-callable regardless of
 *   overloads - [KotlinOnlyApiChecker] and [MangledJvmNameChecker] report them with fitting
 *   fixes.
 * - Overrides: they cannot re-declare default values.
 * - `@JvmSynthetic` functions: they are hidden from Java on purpose.
 */
internal class JvmOverloadsChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        when (declaration) {
            is FirNamedFunction ->
                if (declaration.isOverride || declaration.isAbstract || declaration.isSuspend) return
            is FirConstructor ->
                if (context.containingClassSymbol?.classKind == ClassKind.ANNOTATION_CLASS) return
            else -> return
        }

        val containingClass = context.containingClassSymbol
        if (containingClass?.classKind == ClassKind.INTERFACE || containingClass?.isValueClass() == true) {
            return
        }

        if (declaration.valueParameters.none { it.defaultValue != null }) {
            return
        }

        if (!declaration.isWatchedPublicApi()) {
            return
        }

        val session = context.session
        if (declaration.hasAnnotation(JvmStandardClassIds.JVM_OVERLOADS_CLASS_ID, session) ||
            declaration.hasAnnotation(JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID, session) ||
            declaration.hasAnnotation(WatchdogClassIds.IntentionallyWithoutJvmOverloads, session)
        ) {
            return
        }

        val name = declaration.reportedName() ?: return
        val factory = severities[WatchdogDiagnostics.DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS] ?: return
        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = if (declaration is FirConstructor) "constructor" else "function",
            b = name,
        )
    }
}
