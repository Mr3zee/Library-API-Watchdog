package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.customAnnotations
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.upperBoundIfFlexible
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * Base class for the checkers that hunt a type down in publicly visible signatures - return
 * types, property types, value parameter types, and type parameter bounds (`<T : X>` constrains
 * every instantiation to the same shape as a direct mention of `X`), including their type
 * arguments (`List<X>` exposes `X` all the same). Subclasses supply the classifier judgement
 * ([violatingClassifier]), the annotation authors acknowledge a deliberate use with
 * ([exemption]), and the diagnostic to report ([report]).
 *
 * Shared sweep rules:
 * - Value parameters are swept from their containing callable, where the public API gate and the
 *   signature-wide exemption are evaluated once per callable.
 * - Overrides are skipped: their signature is fixed by the overridden declaration and is
 *   reported there.
 * - Extension receivers are skipped: an extension on the hunted type provides functionality for
 *   values the user already holds instead of exposing new ones.
 * - A `val`/`var` primary constructor parameter is watched through the property it creates, so
 *   the shared parameter/property text is reported once - even when only the property is public.
 * - Type aliases are expanded before the classifier judgement: an alias does not change what
 *   users see. Flexible (Java platform) types are inspected through [declaredBound] only.
 * - The exemption annotation is honored on the whole declaration, on a single (type) parameter,
 *   or on a type usage, where it covers the annotated type and everything nested in it. Type-use
 *   exemptions never reach [ExemptionExplanationChecker], so the sweep enforces the explanation
 *   requirement for them itself.
 */
internal abstract class ExposedTypeChecker(
    private val exemption: ClassId,
) : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    /**
     * The name reported when this classifier is the hunted type, or null. Only the classifier
     * itself is judged here: the sweep has already expanded aliases and taken [declaredBound],
     * and recurses into type arguments separately.
     */
    context(context: CheckerContext)
    protected abstract fun ConeKotlinType.violatingClassifier(): Name?

    /**
     * Reports the [violation] found in the signature part [kind] named [name]; the subclass
     * resolves its diagnostic's configured severity here.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    protected abstract fun report(source: KtSourceElement?, kind: String, name: Name, violation: Name)

    /**
     * The bound a flexible (Java platform) type is inspected through. A platform type does not
     * declare the watched trait in Kotlin sources, so each check picks the bound where the trait
     * is absent - [NullableBooleanChecker] the non-nullable lower bound, everything else the
     * read-only upper bound - keeping platform types unreported.
     */
    protected open fun ConeKotlinType.declaredBound(): ConeKotlinType = upperBoundIfFlexible()

    /**
     * The violation a `vararg` parameter's declared type exposes. By default the type is
     * traversed like any other parameter type: for most checks the array carrying the arguments
     * never matches the hunted classifier, and an offending element type is found as its type
     * argument. [MutableCollectionChecker] overrides this - its judgement matches the array
     * itself, which the compiler already passes as a defensive copy.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    protected open fun findVarargViolation(parameterType: ConeKotlinType): Name? =
        parameterType.findViolation()

    context(context: CheckerContext, reporter: DiagnosticReporter)
    final override fun check(declaration: FirDeclaration) {
        when (declaration) {
            // Parameters are swept from their containing callable, where the public API gate and
            // the signature-wide exemption are evaluated once per callable.
            is FirValueParameter -> return
            is FirProperty -> checkProperty(declaration)
            is FirNamedFunction -> checkFunction(declaration)
            is FirConstructor -> checkConstructor(declaration)
            is FirRegularClass -> checkClass(declaration)
            else -> return
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkProperty(declaration: FirProperty) {
        if (!declaration.isWatchedPublicApi() || declaration.isOverride) {
            return
        }

        // For a val/var constructor parameter the exemption annotation lands on the parameter
        // (the default use-site target), not on the property generated from it.
        if (declaration.isExempt() ||
            declaration.correspondingValueParameterFromPrimaryConstructor?.isExempt() == true
        ) {
            return
        }

        // Extension properties can declare their own type parameters.
        checkTypeParameters(declaration.typeParameters)
        checkSignatureType(declaration.returnTypeRef, "property", declaration.name, declaration)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkFunction(declaration: FirNamedFunction) {
        if (!declaration.isWatchedPublicApi() || declaration.isOverride || declaration.isExempt()) {
            return
        }

        checkTypeParameters(declaration.typeParameters)
        checkSignatureType(declaration.returnTypeRef, "function", declaration.name, declaration)
        declaration.valueParameters.forEach { checkParameter(it) }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkConstructor(declaration: FirConstructor) {
        if (!declaration.isWatchedPublicApi() || declaration.isExempt()) {
            return
        }

        // val/var parameters produce a property covering the same source text; the property
        // check owns those, keeping the report single.
        val propertyParameters = mutableSetOf<FirValueParameterSymbol>()
        if (declaration.isPrimary) {
            context.containingClassSymbol?.processAllDeclarations(context.session) { member ->
                if (member is FirPropertySymbol) {
                    member.correspondingValueParameterFromPrimaryConstructor
                        ?.let(propertyParameters::add)
                }
            }
        }

        for (parameter in declaration.valueParameters) {
            if (parameter.symbol !in propertyParameters) {
                checkParameter(parameter)
            }
        }
    }

    /** Classes carry no checked signature of their own, but their type parameters have bounds. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkClass(declaration: FirRegularClass) {
        if (declaration.isWatchedPublicApi()) {
            checkTypeParameters(declaration.typeParameters)
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkParameter(parameter: FirValueParameter) {
        if (parameter.isExempt()) {
            return
        }

        val type = parameter.returnTypeRef.coneType
        val violation = if (parameter.isVararg) findVarargViolation(type) else type.findViolation()
        if (violation != null) {
            report(parameter.returnTypeRef.source ?: parameter.source, "parameter", parameter.name, violation)
        }
    }

    /**
     * Reports violating bounds: a type parameter bounded by the hunted type constrains every
     * instantiation to it, so it exposes the same shape as a direct mention of the bound.
     * Outer-class parameters reappear as [FirTypeParameterRef]s without their own declaration
     * and are skipped - they are reported on the declaring class.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkTypeParameters(typeParameters: List<FirTypeParameterRef>) {
        for (typeParameter in typeParameters.filterIsInstance<FirTypeParameter>()) {
            if (typeParameter.isExempt()) {
                continue
            }

            for (bound in typeParameter.bounds) {
                checkSignatureType(bound, "type parameter", typeParameter.name, typeParameter)
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkSignatureType(typeRef: FirTypeRef, kind: String, name: Name, declaration: FirDeclaration) {
        val violation = typeRef.coneType.findViolation() ?: return
        report(typeRef.source ?: declaration.source, kind, name, violation)
    }

    context(context: CheckerContext)
    private fun FirDeclaration.isExempt(): Boolean =
        hasAnnotation(exemption, context.session)

    context(context: CheckerContext)
    private fun FirValueParameterSymbol.isExempt(): Boolean =
        hasAnnotation(exemption, context.session)

    /**
     * The name of the first hunted type the (fully expanded) type mentions, or null.
     *
     * A type-use [exemption] exempts the annotated type and everything nested in it.
     * [ExemptionExplanationChecker] never sees type-use annotations, so the explanation
     * requirement is enforced here, where the exemption is honored.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    protected fun ConeKotlinType.findViolation(): Name? {
        val session = context.session
        val typeUseExemption = customAnnotations.firstOrNull {
            it.toAnnotationClassIdSafe(session) == exemption
        }
        if (typeUseExemption != null) {
            typeUseExemption.unexplainedExemptionReason()?.let { reason ->
                reporter.reportOn(
                    source = typeUseExemption.source,
                    factory = WatchdogDiagnostics.EXEMPTION_WITHOUT_EXPLANATION,
                    a = exemption.shortClassName,
                    b = reason,
                )
            }
            return null
        }

        val type = declaredBound().let {
            if (it is ConeClassLikeType) it.fullyExpandedType() else it
        }

        type.violatingClassifier()?.let { return it }
        return type.typeArguments.firstNotNullOfOrNull { it.type?.findViolation() }
    }
}
