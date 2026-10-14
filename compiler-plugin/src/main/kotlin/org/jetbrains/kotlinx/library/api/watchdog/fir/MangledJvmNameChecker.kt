package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.Name

internal class MangledJvmNameChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirCallableDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirCallableDeclaration) {
        if (declaration.isExpectedDeclaration()) return

        when (declaration) {
            is FirConstructor -> checkConstructor(declaration)
            is FirNamedFunction -> checkFunction(declaration)
            is FirProperty -> checkProperty(declaration)
            else -> return
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkFunction(declaration: FirNamedFunction) {
        if (declaration.isOverride || declaration.isSuspend || !declaration.isWatchedForMangling()) {
            return
        }

        if (declaration.hasAnnotationOnActualOrExpect(JvmStandardClassIds.Annotations.JvmName)) {
            return
        }

        val valueClass = declaration.mangledValueClassInSignature() ?: return
        report(declaration, "function", declaration.name, valueClass)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkProperty(declaration: FirProperty) {
        if (declaration.isOverride || !declaration.isWatchedForMangling()) {
            return
        }

        // A receiver or context parameter mangles both accessors. The property type is the return
        // type of the getter, mangled for a member only, and the parameter type of the setter,
        // mangled everywhere.
        val receiverValueClass = declaration.receiverParameter?.typeRef?.coneType?.mangledValueClass()
            ?: declaration.contextParameters.firstNotNullOfOrNull { it.returnTypeRef.coneType.mangledValueClass() }
        val getterValueClass = receiverValueClass ?: declaration.returnValueClassIfMember()
        val setterValueClass = if (declaration.isVar) {
            receiverValueClass ?: declaration.returnTypeRef.coneType.mangledValueClass()
        } else {
            null
        }

        val affectedGetterValueClass = getterValueClass
            ?.takeUnless { declaration.accessorHasJavaFacingName(declaration.getter, AnnotationUseSiteTarget.PROPERTY_GETTER) }
        val affectedSetterValueClass = setterValueClass
            ?.takeUnless { declaration.accessorHasJavaFacingName(declaration.setter, AnnotationUseSiteTarget.PROPERTY_SETTER) }

        val (valueClass, affectedAccessors) = when {
            affectedGetterValueClass != null && affectedSetterValueClass != null ->
                affectedGetterValueClass to AffectedPropertyAccessors.GETTER_AND_SETTER
            affectedGetterValueClass != null -> affectedGetterValueClass to AffectedPropertyAccessors.GETTER
            affectedSetterValueClass != null -> affectedSetterValueClass to AffectedPropertyAccessors.SETTER
            else -> return
        }
        report(declaration, "property", declaration.name, valueClass, affectedAccessors)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkConstructor(declaration: FirConstructor) {
        if (!declaration.isWatchedForMangling()) {
            return
        }

        val valueClass = declaration.contextParameters
            .firstNotNullOfOrNull { it.returnTypeRef.coneType.mangledValueClass() }
            ?: declaration.valueParameters
                .firstNotNullOfOrNull { it.returnTypeRef.coneType.mangledValueClass() }
            ?: return
        val className = declaration.reportedName() ?: return
        report(declaration, "constructor", className, valueClass)
    }

    context(context: CheckerContext)
    private fun FirCallableDeclaration.isWatchedForMangling(): Boolean {
        if (!isWatchedPublicSourceApi()) {
            return false
        }

        // `@JvmName` doesn't apply inside a value class.
        if (context.containingClassSymbol?.isValueClass() == true) {
            return false
        }

        if (hasAnnotationOnActualOrExpect(JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID)) {
            return false
        }

        if (hasEffectiveJvmExposeBoxed()) {
            return false
        }

        return !isExempt()
    }

    context(context: CheckerContext)
    private fun FirCallableDeclaration.isExempt(): Boolean =
        hasAnnotationOnSelfOrEnclosingClass(WatchdogClassIds.IntentionallyMangledJvmName) ||
                (this as? FirProperty)?.correspondingValueParameterFromPrimaryConstructor
                    ?.hasAnnotation(WatchdogClassIds.IntentionallyMangledJvmName, context.session) == true

    /** Whether `@JvmName` or `@JvmSynthetic` is on the accessor, by any of the three annotation sites. */
    context(context: CheckerContext)
    private fun FirProperty.accessorHasJavaFacingName(
        accessor: FirPropertyAccessor?,
        useSiteTarget: AnnotationUseSiteTarget,
    ): Boolean =
        JAVA_FACING_NAME_ANNOTATIONS.any { hasAccessorAnnotation(accessor, useSiteTarget, it) } ||
                correspondingValueParameterFromPrimaryConstructor?.resolvedAnnotationsWithClassIds?.any {
                    it.useSiteTarget == useSiteTarget &&
                            it.toAnnotationClassIdSafe(context.session) in JAVA_FACING_NAME_ANNOTATIONS
                } == true

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun report(
        declaration: FirCallableDeclaration,
        kind: String,
        name: Name,
        valueClass: Name,
        affectedAccessors: AffectedPropertyAccessors? = null,
    ) {
        val factory = severities[WatchdogDiagnostics.MANGLED_JVM_NAME_PUBLIC_API] ?: return
        val nonFinal = declaration.modality == Modality.OPEN || declaration.modality == Modality.ABSTRACT
        val fix = WatchdogDiagnosticMessages.parameterValueFor(
            diagnostic = WatchdogDiagnostics.MANGLED_JVM_NAME_PUBLIC_API.name,
            value = when {
                declaration is FirConstructor -> "constructorFix"
                declaration is FirProperty && nonFinal -> requireNotNull(affectedAccessors).nonFinalFix
                declaration is FirProperty -> requireNotNull(affectedAccessors).fix
                nonFinal -> "nonFinalFunctionFix"
                else -> "functionFix"
            },
        )
        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = WatchdogDiagnosticMessages.parameterValueFor(factory.name, kind),
            b = name,
            c = valueClass,
            d = fix,
        )
    }

    private enum class AffectedPropertyAccessors(val fix: String, val nonFinalFix: String) {
        GETTER("propertyGetterFix", "nonFinalPropertyGetterFix"),
        SETTER("propertySetterFix", "nonFinalPropertySetterFix"),
        GETTER_AND_SETTER("propertyGetterAndSetterFix", "nonFinalPropertyGetterAndSetterFix"),
    }

    private companion object {
        /** The annotations that give an accessor a Java-facing name of its own or hide it from Java. */
        val JAVA_FACING_NAME_ANNOTATIONS: Set<ClassId> = setOf(
            JvmStandardClassIds.Annotations.JvmName,
            JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID,
        )
    }
}
