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
 * Reports watched functions and constructors with default parameter values and no `@JvmOverloads`.
 * Abstract and interface members, annotation constructors, `suspend` functions, value-class
 * members, overrides, and Java-hidden declarations are skipped.
 *
 * [WatchdogFirCheckers] registers this checker only for JVM compilations.
 */
internal class JvmOverloadsChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        when {
            declaration is FirConstructor ->
                if (context.containingClassSymbol?.classKind == ClassKind.ANNOTATION_CLASS) return
            declaration is FirNamedFunction ->
                if (declaration.isOverride || declaration.isAbstract || declaration.isSuspend) return
            else -> return
        }

        val containingClass = context.containingClassSymbol
        if (containingClass?.classKind == ClassKind.INTERFACE || containingClass?.isValueClass() == true) {
            return
        }

        if (declaration.valueParameters.none { it.defaultValue != null }) {
            return
        }

        if (!declaration.isWatchedPublicSourceApi()) {
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
