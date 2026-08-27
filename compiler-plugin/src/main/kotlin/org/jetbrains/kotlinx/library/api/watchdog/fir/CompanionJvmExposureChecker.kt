package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.Name

/**
 * Reports watched companion functions without static access and companion properties whose
 * Java-visible accessors are not all exposed statically or hidden from Java. `@JvmStatic` applies
 * to functions and every property shape, while `const val` and `@JvmField` provide static fields.
 *
 * Exemptions are honored on the member and enclosing classes. Java-hidden members are skipped.
 * [WatchdogFirCheckers] registers this checker only for JVM compilations.
 */
internal class CompanionJvmExposureChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirCallableDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirCallableDeclaration) {
        if (declaration.isExpectedDeclaration()) return

        val companion = context.containingClassSymbol?.takeIf { it.isCompanion } ?: return
        val outerClass = companion.classId.outerClassId?.shortClassName ?: return
        val companionName = companion.classId.shortClassName
        if (declaration.isExempt()) {
            return
        }
        when (declaration) {
            is FirNamedFunction -> checkFunction(declaration, outerClass, companionName)
            is FirProperty -> checkProperty(declaration, outerClass, companionName)
            else -> return
        }
    }

    /**
     * The exemption is honored on the member itself and on any enclosing class - the companion
     * object or its outer class - where it acknowledges every member inside.
     */
    context(context: CheckerContext)
    private fun FirCallableDeclaration.isExempt(): Boolean =
        hasAnnotationOnActualOrExpect(WatchdogClassIds.IntentionallyNonStaticCompanionApi) ||
                context.containingDeclarations.any {
                    it is FirClassSymbol<*> &&
                            it.hasAnnotationOnActualOrExpect(WatchdogClassIds.IntentionallyNonStaticCompanionApi)
                }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkFunction(declaration: FirNamedFunction, outerClass: Name, companionName: Name) {
        if (!declaration.isWatchedPublicSourceApi()) {
            return
        }

        if (declaration.hasAnnotationOnActualOrExpect(JvmStandardClassIds.Annotations.JvmStatic) ||
            declaration.hasAnnotationOnActualOrExpect(JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID)
        ) {
            return
        }

        val factory = severities[WatchdogDiagnostics.COMPANION_API_WITHOUT_JVM_STATIC] ?: return
        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = outerClass,
            b = declaration.name,
            c = companionName,
        )
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkProperty(declaration: FirProperty, outerClass: Name, companionName: Name) {
        if (!declaration.isWatchedPublicSourceApi()) {
            return
        }

        if (declaration.isConst || declaration.hasJvmFieldAnnotation() ||
            declaration.hasAnnotationOnActualOrExpect(JvmStandardClassIds.Annotations.JvmStatic)
        ) {
            return
        }

        val instanceAccessors = buildList {
            if (declaration.accessorRemainsOnCompanion(
                    declaration.getter,
                    AnnotationUseSiteTarget.PROPERTY_GETTER,
                )
            ) {
                add("getter")
            }
            if (declaration.accessorRemainsOnCompanion(
                    declaration.setter,
                    AnnotationUseSiteTarget.PROPERTY_SETTER,
                )
            ) {
                add("setter")
            }
        }
        if (instanceAccessors.isEmpty()) return

        val factory = severities[WatchdogDiagnostics.COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS] ?: return
        val fix = WatchdogDiagnosticMessages.parameterValueFor(
            diagnostic = WatchdogDiagnostics.COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS.name,
            value = when (instanceAccessors.singleOrNull()) {
                "getter" -> "getterFix"
                "setter" -> "setterFix"
                else -> "getterAndSetterFix"
            },
            outerClass.asString(),
        )
        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = "${outerClass.asString()}.${companionName.asString()}",
            b = declaration.name,
            c = instanceAccessors.joinToString(" and "),
            d = fix,
        )
    }

    /** Whether a supported Java accessor still exists only on the companion instance. */
    context(context: CheckerContext)
    private fun FirProperty.accessorRemainsOnCompanion(
        accessor: FirPropertyAccessor?,
        useSiteTarget: AnnotationUseSiteTarget,
    ): Boolean = accessor != null && accessor.effectiveVisibility.publicApi &&
            !hasAccessorAnnotation(accessor, useSiteTarget, JvmStandardClassIds.Annotations.JvmStatic) &&
            !hasAccessorAnnotation(accessor, useSiteTarget, JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID)

    /** Accessor annotations may resolve onto the accessor or remain use-site-targeted on the property. */
    context(context: CheckerContext)
    private fun FirProperty.hasAccessorAnnotation(
        accessor: FirPropertyAccessor,
        useSiteTarget: AnnotationUseSiteTarget,
        annotationClassId: ClassId,
    ): Boolean = accessor.hasAnnotation(annotationClassId, context.session) || annotations.any {
        it.useSiteTarget == useSiteTarget && it.toAnnotationClassIdSafe(context.session) == annotationClassId
    }
}
