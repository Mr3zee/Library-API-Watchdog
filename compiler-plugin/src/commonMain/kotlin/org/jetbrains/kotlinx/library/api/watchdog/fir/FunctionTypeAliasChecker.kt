package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirTypeAliasChecker
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.functionTypeKind
import org.jetbrains.kotlin.name.ClassId

internal class FunctionTypeAliasChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirTypeAliasChecker(MppCheckerKind.Common), ExemptingChecker {
    override val exemptions: Set<ClassId> = setOf(WatchdogClassIds.IntentionallyFunctionTypeAlias)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirTypeAlias) {
        val factory = severities[WatchdogDiagnostics.FUNCTION_TYPE_ALIAS_PUBLIC_API] ?: return

        if (declaration.isActualizedDeclaration() || !declaration.isWatchedPublicSourceApi()) {
            return
        }

        // A fun interface can't replace a reflection kind (`KFunction`, `KSuspendFunction`).
        val kind = declaration.expandedTypeRef.coneType.functionTypeKind(context.session)
        if (kind == null || kind.isReflectType) {
            return
        }

        if (declaration.hasExemption(WatchdogClassIds.IntentionallyFunctionTypeAlias)) {
            return
        }

        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = declaration.name,
        )
    }
}
