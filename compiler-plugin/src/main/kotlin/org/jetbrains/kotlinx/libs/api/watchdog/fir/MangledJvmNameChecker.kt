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
 * Reports publicly visible callables that Java sources can't call because a
 * [value class](https://kotlinlang.org/docs/inline-classes.html#mangling) appears in their
 * signature. Value classes compile to their underlying type, so the JVM backend mangles the
 * name of every affected entry point with a hash suffix (`take-4ZD5Yi0`); the `-` makes the
 * name an illegal Java identifier. Kotlin users never notice, but for Java users the
 * declaration might as well not exist. A JVM name is mangled when the callable
 * - declares a value parameter, an extension receiver, or a context parameter of a value class
 *   type - nullable types and type parameters whose JVM erasure (the first upper bound) is a
 *   value class included, - or
 * - is a member of a class, interface, or object and returns a value class (top-level callables
 *   merely returning a value class keep their JVM name), while
 * - a constructor with a value class parameter is not mangled but replaced: the visible
 *   constructor becomes private and the public one grows a synthetic marker parameter, which
 *   Java rejects just the same.
 *
 * A value class inside a type argument (`List<UserId>`) is boxed and leaves the JVM name intact,
 * so it is not reported.
 *
 * The fix is an explicit Java-facing shape: `@JvmName` on the function (`@get:`/`@set:JvmName`
 * on property accessors) unmangles the name, and `@JvmExposeBoxed` generates Java-callable boxed
 * variants - the only option for constructors and overridable members, which `@JvmName` doesn't
 * accept. Authors acknowledge a deliberately Kotlin-only shape with `@IntentionallyMangledJvmName`
 * - on the declaration, on a constructor `val`/`var` parameter for the property made from it, or
 * on a class, where it covers every declaration inside.
 *
 * Deliberate exceptions:
 * - Non-JVM compilations: mangling is a JVM-ABI concern, so [WatchdogFirCheckers] only registers
 *   this checker when the platform is JVM.
 * - Members and constructors of the value class itself: they are Java-hostile by construction
 *   (`@JvmName` is not even applicable inside), so declaring the public value class is treated
 *   as the deliberate choice, and only its mentions in the rest of the API are watched.
 * - `suspend` functions: Java callers can't provide the continuation idiomatically anyway, so
 *   an unmangled name would not make the function Java-friendly.
 * - Overrides: their signature is fixed by the overridden declaration and is reported there.
 * - `@JvmSynthetic` declarations and accessors: they are hidden from Java on purpose.
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
        if (!isWatchedPublicApi()) {
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
