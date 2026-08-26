package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.isData

/** `data object`s are skipped because they have no constructor-property API. */
internal class DataClassChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        val factory = severities[WatchdogDiagnostics.DATA_CLASS_PUBLIC_API] ?: return

        if (declaration !is FirRegularClass || declaration.isExpectedDeclaration() ||
            !declaration.isWatchedPublicApi()
        ) {
            return
        }

        if (!declaration.isData || declaration.classKind != ClassKind.CLASS) {
            return
        }

        if (declaration.hasAnnotationOnActualOrExpect(WatchdogClassIds.IntentionallyDataClass)) {
            return
        }

        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = declaration.name,
        )
    }
}
