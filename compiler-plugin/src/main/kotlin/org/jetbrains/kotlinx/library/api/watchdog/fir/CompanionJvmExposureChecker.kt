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
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.declarations.utils.isLateInit
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.ClassId

internal class CompanionJvmExposureChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirCallableDeclarationChecker(MppCheckerKind.Common), ExemptingChecker {
    override val exemptions: Set<ClassId> = setOf(WatchdogClassIds.IntentionallyNonStaticCompanionApi)

    override val enclosingClassExemptions: Set<ClassId> = setOf(WatchdogClassIds.IntentionallyNonStaticCompanionApi)

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

    context(context: CheckerContext)
    private fun FirCallableDeclaration.isExempt(): Boolean =
        hasAnnotationOnSelfOrEnclosingClass(WatchdogClassIds.IntentionallyNonStaticCompanionApi)

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
        // The backing field of a `lateinit` property is a static field on the outer class with the
        // visibility of the setter.
        if (declaration.isLateInit && declaration.setter?.effectiveVisibility?.publicApi == true) {
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
        val accessors = instanceAccessors.singleOrNull() ?: "getterAndSetter"
        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = "${outerClass.asString()}.${companionName.asString()}",
            b = declaration.name,
            c = WatchdogDiagnosticMessages.parameterValueFor(factory.name, accessors),
            d = WatchdogDiagnosticMessages.parameterValueFor(factory.name, "${accessors}Fix", outerClass.asString()),
        )
    }

    context(context: CheckerContext)
    private fun FirProperty.accessorRemainsOnCompanion(
        accessor: FirPropertyAccessor?,
        useSiteTarget: AnnotationUseSiteTarget,
    ): Boolean = accessor != null && accessor.effectiveVisibility.publicApi &&
            !hasAccessorAnnotation(accessor, useSiteTarget, JvmStandardClassIds.Annotations.JvmStatic) &&
            !hasAccessorAnnotation(accessor, useSiteTarget, JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID)
}
