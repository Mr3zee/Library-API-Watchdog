package org.jetbrains.kotlinx.library.api.watchdog.fir

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
import org.jetbrains.kotlin.fir.types.customAnnotations
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.upperBoundIfFlexible
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * Finds a type in public signatures: return, receiver, parameter, and context parameter types,
 * class supertypes, type parameter bounds, type alias expansions, and nested type arguments.
 * The [exemption] annotation applies to a declaration, a parameter, or a type usage with the
 * types nested in it.
 */
internal abstract class PublicSignatureTypeChecker<Violation : Any>(
    private val checkExtensionReceivers: Boolean = true,
    private val checkClassSupertypes: Boolean = true,
    private val checkTypeAliases: Boolean = true,
    private val skipOverrides: Boolean = false,
    private val exemption: ClassId? = null,
) : FirBasicDeclarationChecker(MppCheckerKind.Common), ExemptingChecker {
    override val exemptions: Set<ClassId> = setOfNotNull(exemption)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    final override fun check(declaration: FirDeclaration) {
        // The expansion of an actual typealias can expose a type that the expect doesn't mention.
        if (declaration.isActualizedDeclaration() && declaration !is FirTypeAlias) return

        when (declaration) {
            // The enclosing callable checks its parameters.
            is FirValueParameter -> return
            is FirProperty -> checkProperty(declaration)
            is FirConstructor -> checkConstructor(declaration)
            is FirNamedFunction -> checkFunction(declaration)
            is FirRegularClass -> checkClass(declaration)
            is FirTypeAlias -> if (checkTypeAliases) checkTypeAlias(declaration)
            else -> return
        }
    }

    context(context: CheckerContext)
    protected open fun isCheckedDeclaration(declaration: FirMemberDeclaration): Boolean =
        declaration.isWatchedPublicApi()

    context(context: CheckerContext)
    protected open fun isDeclarationExempt(declaration: FirDeclaration): Boolean =
        exemption != null && declaration.hasExemption(exemption)

    context(context: CheckerContext)
    protected open fun isParameterExempt(parameter: FirValueParameterSymbol): Boolean =
        exemption != null && parameter.hasExemption(exemption)

    context(context: CheckerContext)
    protected abstract fun ConeKotlinType.violatingClassifier(): Violation?

    /** The bound of a flexible type that the check inspects. */
    protected open fun ConeKotlinType.declaredBound(): ConeKotlinType = upperBoundIfFlexible()

    context(context: CheckerContext)
    protected open fun ConeKotlinType.isTypeExempt(): Boolean =
        exemption != null && customAnnotations.any { it.isEffective(exemption) }

    context(context: CheckerContext)
    protected open fun ConeKotlinType.typeBeforeClassifier(): ConeKotlinType? = null

    context(context: CheckerContext)
    protected open fun ConeKotlinType.classifierType(): ConeKotlinType =
        if (this is ConeClassLikeType) fullyExpandedType() else this

    context(context: CheckerContext)
    protected open fun ConeKotlinType.typeAfterClassifier(): ConeKotlinType? = null

    context(context: CheckerContext)
    protected open fun ConeKotlinType.shouldCheckTypeArgument(index: Int): Boolean = true

    context(context: CheckerContext, reporter: DiagnosticReporter)
    protected open fun findVarargViolation(parameterType: ConeKotlinType): Violation? =
        parameterType.findViolation()

    /** [kind] is the kind word of `diagnostics.json` for the signature part named [name]. */
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

        // An exemption on a val/var constructor parameter is on the parameter, not on the property.
        val constructorParameter = declaration.correspondingValueParameterFromPrimaryConstructor
        if (constructorParameter != null && isParameterExempt(constructorParameter)) {
            return
        }

        checkTypeParameters(declaration.typeParameters)
        if (checkExtensionReceivers) {
            declaration.receiverParameter?.typeRef?.let {
                checkType(it, "propertyReceiver", declaration.name, declaration.source)
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
                checkType(it, "functionReceiver", declaration.name, declaration.source)
            }
        }
        checkType(declaration.returnTypeRef, "function", declaration.name, declaration.source)
        declaration.contextParameters.forEach { checkParameter(it) }
        declaration.valueParameters.forEach { checkParameter(it) }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkConstructor(declaration: FirConstructor) {
        if (!shouldCheck(declaration)) return

        // The property of a val/var parameter reports the parameter type.
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
                checkType(it, "supertypeOf", declaration.name, declaration.source)
            }
        }
        declaration.contextParameters.forEach { checkParameter(it) }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkTypeAlias(declaration: FirTypeAlias) {
        if (!shouldCheck(declaration)) return

        checkTypeParameters(declaration.typeParameters)
        checkType(declaration.expandedTypeRef, "typeAlias", declaration.name, declaration.source)
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
                name = parameter.sourceName,
                violation = violation,
            )
        }
    }

    /** A type parameter of an outer class is a [FirTypeParameterRef] without a declaration. The outer class reports it. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkTypeParameters(typeParameters: List<FirTypeParameterRef>) {
        for (typeParameterRef in typeParameters) {
            val typeParameter = typeParameterRef as? FirTypeParameter ?: continue
            if (isDeclarationExempt(typeParameter)) continue

            for (bound in typeParameter.bounds) {
                checkType(bound, "typeParameter", typeParameter.name, typeParameter.source)
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

        for ((index, argument) in classifierType.typeArguments.withIndex()) {
            if (!classifierType.shouldCheckTypeArgument(index)) continue
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
