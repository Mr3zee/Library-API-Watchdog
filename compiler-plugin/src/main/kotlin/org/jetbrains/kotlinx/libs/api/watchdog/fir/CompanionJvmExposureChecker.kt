package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.Name

/**
 * Reports watched companion functions without `@JvmStatic` and constant-shaped companion `val`s
 * without `@JvmField`. A constant-shaped property is final, initialized in place, uses the default
 * getter, and is neither `const` nor delegated; a `@JvmStatic` getter also satisfies the check.
 *
 * Exemptions are honored on the member and enclosing classes. Overrides, `suspend` functions,
 * Java-hidden members, mutable properties, and properties with custom accessors or delegates are
 * skipped. [WatchdogFirCheckers] registers this checker only for JVM compilations.
 */
internal class CompanionJvmExposureChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirCallableDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirCallableDeclaration) {
        val companion = context.containingClassSymbol?.takeIf { it.isCompanion } ?: return
        val outerClass = companion.classId.outerClassId?.shortClassName ?: return
        if (declaration.isExempt()) {
            return
        }
        when (declaration) {
            is FirFunction -> if (declaration.isNamedFunction()) checkFunction(declaration, outerClass)
            is FirProperty -> checkProperty(declaration, outerClass)
            else -> return
        }
    }

    /**
     * The exemption is honored on the member itself and on any enclosing class - the companion
     * object or its outer class - where it acknowledges every member inside.
     */
    context(context: CheckerContext)
    private fun FirCallableDeclaration.isExempt(): Boolean =
        hasAnnotation(WatchdogClassIds.IntentionallyNonStaticCompanionApi, context.session) ||
                context.containingDeclarations.any {
                    it is FirClassSymbol<*> &&
                            it.hasAnnotation(WatchdogClassIds.IntentionallyNonStaticCompanionApi, context.session)
                }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkFunction(declaration: FirFunction, outerClass: Name) {
        if (declaration.isOverride || declaration.isSuspend || !declaration.isWatchedPublicSourceApi()) {
            return
        }

        val session = context.session
        if (declaration.hasAnnotation(JvmStandardClassIds.Annotations.JvmStatic, session) ||
            declaration.hasAnnotation(JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID, session)
        ) {
            return
        }

        val factory = severities[WatchdogDiagnostics.COMPANION_API_WITHOUT_JVM_STATIC] ?: return
        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = outerClass,
            b = declaration.namedFunctionName,
        )
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkProperty(declaration: FirProperty, outerClass: Name) {
        if (declaration.isVar || declaration.isConst || declaration.isOverride) {
            return
        }

        if (declaration.initializer == null || declaration.delegate != null || declaration.hasCustomAccessor()) {
            return
        }

        if (!declaration.isWatchedPublicSourceApi()) {
            return
        }

        if (declaration.isExposedToJavaStatically() || declaration.isHiddenFromJavaWithJvmSynthetic()) {
            return
        }

        val factory = severities[WatchdogDiagnostics.COMPANION_CONSTANT_WITHOUT_JVM_FIELD] ?: return
        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = outerClass,
            b = declaration.name,
        )
    }

    /** Default accessors carry a fake source pointing at the property they are generated for. */
    private fun FirProperty.hasCustomAccessor(): Boolean =
        (getter != null && getter?.source?.kind !is KtFakeSourceElementKind) ||
                (setter != null && setter?.source?.kind !is KtFakeSourceElementKind)

    /** Whether `@JvmField` or a `@JvmStatic` getter already puts the value on the outer class. */
    context(context: CheckerContext)
    private fun FirProperty.isExposedToJavaStatically(): Boolean {
        val session = context.session
        if (hasJvmFieldAnnotation() ||
            hasAnnotation(JvmStandardClassIds.Annotations.JvmStatic, session) ||
            getter?.hasAnnotation(JvmStandardClassIds.Annotations.JvmStatic, session) == true
        ) {
            return true
        }
        return annotations.any {
            it.useSiteTarget == AnnotationUseSiteTarget.PROPERTY_GETTER &&
                    it.toAnnotationClassIdSafe(session) == JvmStandardClassIds.Annotations.JvmStatic
        }
    }
}
