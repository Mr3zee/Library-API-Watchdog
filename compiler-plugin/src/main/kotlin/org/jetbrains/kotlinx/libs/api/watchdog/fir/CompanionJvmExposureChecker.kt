package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
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
 * Reports publicly visible companion object members that Java callers can only reach through the
 * companion instance - `Outer.Companion.member` - although a JVM annotation would put them on the
 * outer class where Java expects statics:
 * - a function without `@JvmStatic` ([WatchdogDiagnostics.COMPANION_API_WITHOUT_JVM_STATIC]),
 * - a constant-shaped `val` - final, initialized in place, with the default getter, not `const`
 *   and not delegated - without `@JvmField`
 *   ([WatchdogDiagnostics.COMPANION_CONSTANT_WITHOUT_JVM_FIELD]); `const val`, a `@JvmStatic`
 *   getter, and hiding with `@get:JvmSynthetic` settle the Java-facing shape as well.
 *
 * Authors acknowledge a deliberately companion-instance-only access path with
 * `@IntentionallyNonStaticCompanionApi` - on the member, or on a class (the companion object
 * itself or its outer class), where it covers every member inside.
 *
 * Deliberate exceptions:
 * - Non-JVM compilations: how Java reaches a companion member is a JVM-only concern, so
 *   [WatchdogFirCheckers] only registers this checker when the platform is JVM.
 * - Overrides: their Java-facing shape is fixed by the overridden declaration, and `@JvmStatic`
 *   members can't override anything.
 * - `suspend` functions: not Java-callable regardless of placement - [KotlinOnlyApiChecker]
 *   reports them with the fitting fix.
 * - `@JvmSynthetic` members: they are hidden from Java on purpose.
 * - `var` properties and properties with custom accessors or delegates: they expose behavior
 *   rather than a constant, and `@JvmField` is not applicable to most of these shapes anyway.
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
            is FirNamedFunction -> checkFunction(declaration, outerClass)
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
    private fun checkFunction(declaration: FirNamedFunction, outerClass: Name) {
        if (declaration.isOverride || declaration.isSuspend || !declaration.isWatchedPublicApi()) {
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
            b = declaration.name,
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

        if (!declaration.isWatchedPublicApi()) {
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
