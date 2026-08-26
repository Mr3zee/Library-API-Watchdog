package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.modality

internal class ExhaustiveApiChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        val factory = severities[WatchdogDiagnostics.EXHAUSTIVE_PUBLIC_API] ?: return

        if (declaration !is FirRegularClass || declaration.isActualizedDeclaration() ||
            !declaration.isWatchedPublicSourceApi()
        ) {
            return
        }

        val exhaustivelyMatchable = declaration.classKind == ClassKind.ENUM_CLASS ||
                declaration.modality == Modality.SEALED

        if (!exhaustivelyMatchable) {
            return
        }

        if (declaration.hasAnnotation(WatchdogClassIds.IntentionallyExhaustive, context.session)) {
            return
        }

        val fix = WatchdogDiagnosticMessages.parameterValueFor(
            diagnostic = WatchdogDiagnostics.EXHAUSTIVE_PUBLIC_API.name,
            value = if (declaration.classKind == ClassKind.ENUM_CLASS) "enumFix" else "sealedFix",
        )
        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = declaration.classKind,
            b = declaration.name,
            c = declaration.classKind,
            d = fix,
        )
    }
}
