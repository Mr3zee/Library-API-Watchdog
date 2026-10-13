package org.jetbrains.kotlinx.library.api.watchdog.fir

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

/** Reports one time for each file, on the first Java-visible top-level callable. */
internal class TopLevelJvmNameChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirFileChecker(MppCheckerKind.Common) {
    @OptIn(DirectDeclarationsAccess::class)
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFile) {
        if (declaration.hasAnnotation(JvmStandardClassIds.Annotations.JvmName, context.session) ||
            declaration.hasAnnotation(WatchdogClassIds.IntentionallyDefaultFacadeName, context.session)
        ) {
            return
        }

        val facadeMembers = declaration.declarations.filter { it.isJavaVisibleTopLevelCallable() }
        val firstFacadeMember = facadeMembers.firstOrNull() ?: return
        val factory = severities[WatchdogDiagnostics.TOP_LEVEL_API_WITHOUT_JVM_NAME] ?: return
        val hasFunctions = facadeMembers.any { it is FirNamedFunction }
        val hasProperties = facadeMembers.any { it is FirProperty }
        val callableKinds = when {
            hasFunctions && hasProperties -> "functionsAndProperties"
            hasFunctions -> "functions"
            else -> "properties"
        }
        reporter.reportOn(
            source = firstFacadeMember.source,
            factory = factory,
            a = PackagePartClassUtils.getFilePartShortName(declaration.name),
            b = WatchdogDiagnosticMessages.parameterValueFor(factory.name, callableKinds),
        )
    }

    context(context: CheckerContext)
    private fun FirDeclaration.isJavaVisibleTopLevelCallable(): Boolean = when (this) {
        is FirNamedFunction -> !isExpectedDeclaration() &&
                isWatchedPublicApi() && !isHiddenFromJavaWithJvmSynthetic()
        is FirProperty -> !isExpectedDeclaration() &&
                isWatchedPublicApi() && !isHiddenFromJavaWithJvmSynthetic()
        else -> false
    }
}
