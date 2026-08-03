package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.JvmStandardClassIds

/**
 * Reports a file with watched top-level functions or properties and no `@file:JvmName` or file-level
 * exemption. The diagnostic is anchored on the first qualifying callable and emitted once per
 * file. Files with only classifiers or only Java-hidden callables are skipped.
 *
 * [WatchdogFirCheckers] registers this checker only for JVM compilations.
 */
internal class TopLevelJvmNameChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirFileChecker(MppCheckerKind.Common) {
    // Direct declaration access is what a file checker is for: the file's own top-level list.
    @OptIn(DirectDeclarationsAccess::class)
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFile) {
        if (declaration.hasAnnotation(JvmStandardClassIds.Annotations.JvmName, context.session) ||
            declaration.hasAnnotation(WatchdogClassIds.IntentionallyDefaultFacadeName, context.session)
        ) {
            return
        }

        val firstFacadeMember = declaration.declarations.firstOrNull { it.isJavaVisibleTopLevelCallable() } ?: return
        val factory = severities[WatchdogDiagnostics.TOP_LEVEL_API_WITHOUT_JVM_NAME] ?: return
        reporter.reportOn(
            source = firstFacadeMember.source,
            factory = factory,
            a = PackagePartClassUtils.getFilePartShortName(declaration.name),
        )
    }

    context(context: CheckerContext)
    private fun FirDeclaration.isJavaVisibleTopLevelCallable(): Boolean = when (this) {
        is FirNamedFunction -> isWatchedPublicApi() && !isHiddenFromJavaWithJvmSynthetic()
        is FirProperty -> isWatchedPublicApi() && !isHiddenFromJavaWithJvmSynthetic()
        else -> false
    }
}
