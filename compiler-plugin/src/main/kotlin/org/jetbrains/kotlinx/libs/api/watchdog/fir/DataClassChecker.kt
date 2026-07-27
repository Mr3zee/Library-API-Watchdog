package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isData

/**
 * Reports publicly visible data classes: the generated `copy` and `componentN` functions and the
 * constructor bake the exact property list into the compiled API, so adding, removing, or
 * reordering a property later breaks users. Authors acknowledge the contract with
 * `@IntentionallyDataClass`.
 *
 * `data object`s are exempt: without constructor properties none of the hazardous members are
 * generated.
 */
internal class DataClassChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        val factory = severities[WatchdogDiagnostics.DATA_CLASS_PUBLIC_API] ?: return

        if (declaration !is FirRegularClass || !declaration.isWatchedPublicApi()) {
            return
        }

        if (!declaration.isData || declaration.classKind != ClassKind.CLASS) {
            return
        }

        if (declaration.hasAnnotation(WatchdogClassIds.IntentionallyDataClass, context.session)) {
            return
        }

        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = declaration.name,
        )
    }
}
