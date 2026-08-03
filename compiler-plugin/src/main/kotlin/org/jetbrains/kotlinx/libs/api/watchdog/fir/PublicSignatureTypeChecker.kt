package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.isLegacyContextReceiver
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.upperBoundIfFlexible
import org.jetbrains.kotlin.name.Name

/**
 * Shared sweep for checks that find a type in public signatures. It covers callable return,
 * receiver, value and context parameter types; class supertypes and context parameters; type
 * parameter bounds; type aliases; and nested type arguments. The constructor options let checks
 * omit positions that do not expose the trait they police.
 *
 * Subclasses decide which declarations belong to their API surface, which classifier violates
 * their rule, and how a violation is reported. They may also customize alias traversal and
 * exemptions without reimplementing the signature sweep.
 */
internal abstract class PublicSignatureTypeChecker<Violation : Any>(
    private val checkExtensionReceivers: Boolean = true,
    private val checkClassSupertypes: Boolean = true,
    private val checkClassContextParameters: Boolean = true,
    private val checkTypeAliases: Boolean = true,
    private val skipOverrides: Boolean = false,
) : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    final override fun check(declaration: FirDeclaration) {
        when (declaration) {
            // Parameters are swept from their containing callable, where its public API gate and
            // any signature-wide exemption are evaluated once.
            is FirValueParameter -> return
            is FirProperty -> checkProperty(declaration)
            is FirNamedFunction -> checkFunction(declaration)
            is FirConstructor -> checkConstructor(declaration)
            is FirRegularClass -> checkClass(declaration)
            is FirTypeAlias -> if (checkTypeAliases) checkTypeAlias(declaration)
            else -> return
        }
    }

    /** Whether this declaration belongs to the API surface the check protects. */
    context(context: CheckerContext)
    protected open fun isCheckedDeclaration(declaration: FirMemberDeclaration): Boolean =
        declaration.isWatchedPublicApi()

    /** Whether an annotation exempts this declaration's whole checked signature. */
    context(context: CheckerContext)
    protected open fun isDeclarationExempt(declaration: FirDeclaration): Boolean = false

    /** Whether an annotation exempts a single parameter. */
    context(context: CheckerContext)
    protected open fun isParameterExempt(parameter: FirValueParameterSymbol): Boolean = false

    /** The violation represented by this classifier, or null when the classifier is accepted. */
    context(context: CheckerContext)
    protected abstract fun ConeKotlinType.violatingClassifier(): Violation?

    /**
     * The bound a flexible (Java platform) type is inspected through. Checks normally use the
     * upper bound, but may choose the lower bound when that better represents the declared trait.
     */
    protected open fun ConeKotlinType.declaredBound(): ConeKotlinType = upperBoundIfFlexible()

    /** Whether this annotated type and everything nested in it should be skipped. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    protected open fun ConeKotlinType.isTypeExempt(): Boolean = false

    /** An alternate type whose classifier must be inspected before this type's classifier. */
    context(context: CheckerContext)
    protected open fun ConeKotlinType.typeBeforeClassifier(): ConeKotlinType? = null

    /**
     * The type whose classifier and arguments are inspected. Expanding aliases is the normal
     * public-signature behavior; checks interested in an alias declaration itself may opt out.
     */
    context(context: CheckerContext)
    protected open fun ConeKotlinType.classifierType(): ConeKotlinType =
        if (this is ConeClassLikeType) fullyExpandedType() else this

    /** An alternate type whose classifier must be inspected after this type's classifier. */
    context(context: CheckerContext)
    protected open fun ConeKotlinType.typeAfterClassifier(): ConeKotlinType? = null

    /**
     * The violation a `vararg` parameter exposes. Most checks inspect its array type normally;
     * checks for which the compiler-generated array is not itself exposure may customize this.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    protected open fun findVarargViolation(parameterType: ConeKotlinType): Violation? =
        parameterType.findViolation()

    /** Reports a violation found in signature part [kind] named [name]. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    protected abstract fun report(
        source: KtSourceElement?,
        kind: String,
        name: Name,
        violation: Violation,
    )

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkProperty(declaration: FirProperty) {
        if (!shouldCheck(declaration)) return

        // For a val/var constructor parameter an exemption annotation lands on the parameter
        // (the default use-site target), not on the property generated from it.
        val constructorParameter = declaration.correspondingValueParameterFromPrimaryConstructor
        if (constructorParameter != null && isParameterExempt(constructorParameter)) {
            return
        }

        checkTypeParameters(declaration.typeParameters)
        if (checkExtensionReceivers) {
            declaration.receiverParameter?.typeRef?.let {
                checkType(it, "property receiver", declaration.name, declaration.source)
            }
        }
        declaration.contextParameters.forEach { checkParameter(it) }
        checkType(declaration.returnTypeRef, "property", declaration.name, declaration.source)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkFunction(declaration: FirNamedFunction) {
        if (!shouldCheck(declaration)) return

        checkTypeParameters(declaration.typeParameters)
        if (checkExtensionReceivers) {
            declaration.receiverParameter?.typeRef?.let {
                checkType(it, "function receiver", declaration.name, declaration.source)
            }
        }
        checkType(declaration.returnTypeRef, "function", declaration.name, declaration.source)
        declaration.contextParameters.forEach { checkParameter(it) }
        declaration.valueParameters.forEach { checkParameter(it) }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkConstructor(declaration: FirConstructor) {
        if (!shouldCheck(declaration)) return

        // A val/var parameter is also a property over the same source text. Let that property own
        // the report so one exposed type produces one diagnostic.
        var propertyParameters: MutableSet<FirValueParameterSymbol>? = null
        if (declaration.isPrimary) {
            context.containingClassSymbol?.processAllDeclarations(context.session) { member ->
                if (member is FirPropertySymbol) {
                    member.correspondingValueParameterFromPrimaryConstructor
                        ?.let { parameter ->
                            val parameters = propertyParameters ?: mutableSetOf<FirValueParameterSymbol>()
                                .also { propertyParameters = it }
                            parameters.add(parameter)
                        }
                }
            }
        }

        declaration.contextParameters.forEach { checkParameter(it) }
        for (parameter in declaration.valueParameters) {
            if (propertyParameters?.contains(parameter.symbol) != true) checkParameter(parameter)
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkClass(declaration: FirRegularClass) {
        if (!shouldCheck(declaration)) return

        checkTypeParameters(declaration.typeParameters)
        if (checkClassSupertypes) {
            declaration.superTypeRefs.forEach {
                checkType(it, "supertype of", declaration.name, declaration.source)
            }
        }
        if (checkClassContextParameters) {
            declaration.contextParameters.forEach { checkParameter(it) }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkTypeAlias(declaration: FirTypeAlias) {
        if (!shouldCheck(declaration)) return

        checkTypeParameters(declaration.typeParameters)
        checkType(declaration.expandedTypeRef, "type alias", declaration.name, declaration.source)
    }

    context(context: CheckerContext)
    private fun shouldCheck(declaration: FirMemberDeclaration): Boolean =
        isCheckedDeclaration(declaration) &&
                !(skipOverrides && declaration is FirProperty && declaration.isOverride) &&
                !(skipOverrides && declaration is FirNamedFunction && declaration.isOverride) &&
                !isDeclarationExempt(declaration)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkParameter(parameter: FirValueParameter) {
        if (parameter.isLegacyContextReceiver() || isParameterExempt(parameter.symbol)) return

        val parameterType = parameter.returnTypeRef.coneType
        val violation = if (parameter.isVararg) {
            findVarargViolation(parameterType)
        } else {
            parameterType.findViolation()
        }
        if (violation != null) {
            report(
                source = parameter.returnTypeRef.source ?: parameter.source,
                kind = "parameter",
                name = parameter.name,
                violation = violation,
            )
        }
    }

    /**
     * Reports violating bounds. Outer-class parameters reappear as [FirTypeParameterRef]s without
     * their own declaration and are skipped; they are reported on the declaring class.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkTypeParameters(typeParameters: List<FirTypeParameterRef>) {
        for (typeParameterRef in typeParameters) {
            val typeParameter = typeParameterRef as? FirTypeParameter ?: continue
            if (isDeclarationExempt(typeParameter)) continue

            for (bound in typeParameter.bounds) {
                checkType(bound, "type parameter", typeParameter.name, typeParameter.source)
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkType(
        typeRef: FirTypeRef,
        kind: String,
        name: Name,
        fallbackSource: KtSourceElement?,
    ) {
        val violation = typeRef.coneType.findViolation() ?: return
        report(typeRef.source ?: fallbackSource, kind, name, violation)
    }

    /** The first violation in this type or any nested type, guarded against recursive types. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    protected fun ConeKotlinType.findViolation(): Violation? =
        findViolation(visited = null)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun ConeKotlinType.findViolation(visited: MutableSet<ConeKotlinType>?): Violation? {
        if (isTypeExempt()) return null

        val type = declaredBound()
        var visitedTypes = visited
        if (visitedTypes != null && !visitedTypes.add(type)) return null

        val beforeClassifier = type.typeBeforeClassifier()
        if (beforeClassifier != null) {
            if (visitedTypes == null) visitedTypes = mutableSetOf(type)
            beforeClassifier.findViolation(visitedTypes)?.let { return it }
        }

        val classifierType = type.classifierType()
        if (classifierType != type && visitedTypes != null && !visitedTypes.add(classifierType)) return null
        classifierType.violatingClassifier()?.let { return it }

        val afterClassifier = classifierType.typeAfterClassifier()
        if (afterClassifier != null) {
            if (visitedTypes == null) {
                visitedTypes = mutableSetOf(type)
                if (classifierType != type) visitedTypes.add(classifierType)
            }
            afterClassifier.findViolation(visitedTypes)?.let { return it }
        }

        for (argument in classifierType.typeArguments) {
            val argumentType = argument.type ?: continue
            if (visitedTypes == null) {
                visitedTypes = mutableSetOf(type)
                if (classifierType != type) visitedTypes.add(classifierType)
            }
            argumentType.findViolation(visitedTypes)?.let { return it }
        }
        return null
    }
}
