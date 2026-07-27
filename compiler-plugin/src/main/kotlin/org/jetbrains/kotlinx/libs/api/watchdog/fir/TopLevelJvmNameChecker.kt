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
 * Reports files whose public top-level functions or properties compile into a file facade class
 * without an explicit `@file:JvmName`. The facade's name is derived from the file name
 * (`foo.kt` → `FooKt`), so the file name leaks into the Java API surface - Java callers write
 * `FooKt.topFun()` - and renaming the file, invisible to Kotlin callers, renames the facade and
 * breaks Java sources and binaries compiled against it. `@file:JvmName` decouples the facade
 * name from the file name and lets the author choose a deliberate, Java-idiomatic one.
 *
 * The diagnostic fires once per file, anchored on the first public top-level function or
 * property. Files exposing only classifiers don't produce a facade worth naming, and neither do
 * files whose every top-level callable is hidden from Java with `@JvmSynthetic`. Authors
 * acknowledge a deliberately derived facade name with `@file:IntentionallyDefaultFacadeName`.
 * Non-JVM compilations have no facade classes at all, so [WatchdogFirCheckers] only registers
 * this checker when the platform is JVM.
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
