package org.jetbrains.kotlinx.libs.api.watchdog.fir

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
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.Name

/**
 * Reports watched JVM callables whose signature is mangled by a value class, plus constructors
 * hidden behind a synthetic constructor for the same reason. Parameters, extension and context
 * receivers, and member return types are inspected through nullable types and erased type-parameter
 * bounds. Value classes nested in type arguments and top-level return types do not count.
 *
 * Members of the value class itself, `suspend` functions, overrides, and Java-hidden declarations
 * are skipped. Exemptions may be placed on the callable, a constructor property parameter, or an
 * enclosing class. [WatchdogFirCheckers] registers this checker only for JVM compilations.
 */
internal class MangledJvmNameChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirCallableDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirCallableDeclaration) {
        when (declaration) {
            is FirNamedFunction -> checkFunction(declaration)
            is FirProperty -> checkProperty(declaration)
            is FirConstructor -> checkConstructor(declaration)
            else -> return
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkFunction(declaration: FirNamedFunction) {
        if (declaration.isOverride || declaration.isSuspend || !declaration.isWatchedForMangling()) {
            return
        }

        if (declaration.hasAnnotation(JvmStandardClassIds.Annotations.JvmName, context.session)) {
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

        // A receiver or context parameter is passed to both accessors, so it mangles both; the
        // property type itself is the getter's return type - mangled for members only - and the
        // setter's parameter type - mangled everywhere.
        val receiverValueClass = declaration.receiverParameter?.typeRef?.coneType?.mangledValueClass()
            ?: declaration.contextParameters.firstNotNullOfOrNull { it.returnTypeRef.coneType.mangledValueClass() }
        val getterValueClass = receiverValueClass ?: declaration.returnValueClassIfMember()
        val setterValueClass = if (declaration.isVar) {
            receiverValueClass ?: declaration.returnTypeRef.coneType.mangledValueClass()
        } else {
            null
        }

        val valueClass = getterValueClass
            ?.takeUnless { declaration.accessorHasJavaFacingName(declaration.getter, AnnotationUseSiteTarget.PROPERTY_GETTER) }
            ?: setterValueClass
                ?.takeUnless { declaration.accessorHasJavaFacingName(declaration.setter, AnnotationUseSiteTarget.PROPERTY_SETTER) }
            ?: return
        report(declaration, "property", declaration.name, valueClass)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkConstructor(declaration: FirConstructor) {
        if (!declaration.isWatchedForMangling()) {
            return
        }

        val valueClass = declaration.valueParameters
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

        // Everything declared inside a value class is Java-hostile by construction, and @JvmName
        // is not applicable there: the public value class itself is the deliberate choice.
        if (context.containingClassSymbol?.isValueClass() == true) {
            return false
        }

        val session = context.session
        if (hasAnnotation(JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID, session)) {
            return false
        }

        // @JvmExposeBoxed - on the declaration or anywhere up the class nesting - generates
        // Java-callable boxed variants next to the mangled entry points.
        if (hasAnnotation(JvmStandardClassIds.JVM_EXPOSE_BOXED_ANNOTATION_CLASS_ID, session) ||
            context.containingDeclarations.any {
                it is FirClassSymbol<*> && it.hasAnnotation(JvmStandardClassIds.JVM_EXPOSE_BOXED_ANNOTATION_CLASS_ID, session)
            }
        ) {
            return false
        }

        return !isExempt()
    }

    /**
     * The exemption is honored on the declaration itself, on the primary constructor parameter a
     * property was made from, and on any enclosing class, where it acknowledges every
     * declaration inside as deliberately Kotlin-only.
     */
    context(context: CheckerContext)
    private fun FirCallableDeclaration.isExempt(): Boolean {
        val session = context.session
        return hasAnnotation(WatchdogClassIds.IntentionallyMangledJvmName, session) ||
                (this as? FirProperty)?.correspondingValueParameterFromPrimaryConstructor
                    ?.hasAnnotation(WatchdogClassIds.IntentionallyMangledJvmName, session) == true ||
                context.containingDeclarations.any {
                    it is FirClassSymbol<*> && it.hasAnnotation(WatchdogClassIds.IntentionallyMangledJvmName, session)
                }
    }

    /**
     * Whether the accessor's Java-facing shape is already settled - renamed with `@JvmName` or
     * hidden with `@JvmSynthetic`. The annotation sits on an explicit accessor directly; the
     * `@get:`/`@set:` use-site form stays on the property - or on the primary constructor
     * parameter for a `val`/`var` parameter - with the accessor as its use-site target.
     */
    context(context: CheckerContext)
    private fun FirProperty.accessorHasJavaFacingName(
        accessor: FirPropertyAccessor?,
        useSiteTarget: AnnotationUseSiteTarget,
    ): Boolean {
        val session = context.session
        if (accessor != null &&
            (accessor.hasAnnotation(JvmStandardClassIds.Annotations.JvmName, session) ||
                    accessor.hasAnnotation(JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID, session))
        ) {
            return true
        }

        val targeted = annotations.asSequence() +
                (correspondingValueParameterFromPrimaryConstructor?.resolvedAnnotationsWithClassIds?.asSequence()
                    ?: emptySequence())
        return targeted.any { it.useSiteTarget == useSiteTarget && it.isJavaFacingNameAnnotation() }
    }

    context(context: CheckerContext)
    private fun FirAnnotation.isJavaFacingNameAnnotation(): Boolean =
        toAnnotationClassIdSafe(context.session).let { classId: ClassId? ->
            classId == JvmStandardClassIds.Annotations.JvmName ||
                    classId == JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID
        }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun report(declaration: FirCallableDeclaration, kind: String, name: Name, valueClass: Name) {
        val factory = severities[WatchdogDiagnostics.MANGLED_JVM_NAME_PUBLIC_API] ?: return
        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = kind,
            b = name,
            c = valueClass,
        )
    }
}
