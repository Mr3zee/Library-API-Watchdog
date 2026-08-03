package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
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
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.abbreviatedType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.upperBoundIfFlexible
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * Reports internal-API types exposed in supported public source signatures. A declaration marked
 * as internal API is already outside the supported surface and is skipped by
 * [isWatchedPublicApi]. Declarations that only `@PublishedApi` promotes are skipped too: they are
 * binary implementation details rather than types users can name as source API.
 *
 * The sweep matches [NonTransitiveDependencyChecker]: callable return, receiver, value and
 * context parameter types; class supertypes and context parameters; type parameter bounds; type
 * aliases; and every nested type argument. Both type aliases and their expanded types are checked.
 */
internal class InternalApiTypeExposureChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirDeclaration) {
        when (declaration) {
            // Parameters are swept from their callable so its visibility is evaluated once.
            is FirValueParameter -> return
            is FirProperty -> checkProperty(declaration)
            is FirNamedFunction -> checkFunction(declaration)
            is FirConstructor -> checkConstructor(declaration)
            is FirRegularClass -> checkClass(declaration)
            is FirTypeAlias -> checkTypeAlias(declaration)
            else -> return
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkProperty(declaration: FirProperty) {
        if (!declaration.isSupportedPublicSourceApi()) return

        checkTypeParameters(declaration.typeParameters)
        declaration.receiverParameter?.typeRef?.let {
            checkType(it, "property receiver", declaration.name, declaration.source)
        }
        declaration.contextParameters.forEach { checkParameter(it) }
        checkType(declaration.returnTypeRef, "property", declaration.name, declaration.source)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkFunction(declaration: FirNamedFunction) {
        if (!declaration.isSupportedPublicSourceApi()) return

        checkTypeParameters(declaration.typeParameters)
        declaration.receiverParameter?.typeRef?.let {
            checkType(it, "function receiver", declaration.name, declaration.source)
        }
        checkType(declaration.returnTypeRef, "function", declaration.name, declaration.source)
        (declaration.contextParameters + declaration.valueParameters).forEach { checkParameter(it) }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkConstructor(declaration: FirConstructor) {
        if (!declaration.isSupportedPublicSourceApi()) return

        // A val/var parameter is also a property over the same source text. Let that property own
        // the report so one internal type produces one diagnostic.
        val propertyParameters = mutableSetOf<FirValueParameterSymbol>()
        if (declaration.isPrimary) {
            context.containingClassSymbol?.processAllDeclarations(context.session) { member ->
                if (member is FirPropertySymbol) {
                    member.correspondingValueParameterFromPrimaryConstructor?.let(propertyParameters::add)
                }
            }
        }

        for (parameter in declaration.contextParameters + declaration.valueParameters) {
            if (parameter.symbol !in propertyParameters) checkParameter(parameter)
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkClass(declaration: FirRegularClass) {
        if (!declaration.isSupportedPublicSourceApi()) return

        checkTypeParameters(declaration.typeParameters)
        declaration.superTypeRefs.forEach {
            checkType(it, "supertype of", declaration.name, declaration.source)
        }
        declaration.contextParameters.forEach { checkParameter(it) }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkTypeAlias(declaration: FirTypeAlias) {
        if (!declaration.isSupportedPublicSourceApi()) return

        checkTypeParameters(declaration.typeParameters)
        checkType(declaration.expandedTypeRef, "type alias", declaration.name, declaration.source)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkParameter(parameter: FirValueParameter) {
        if (parameter.isLegacyContextReceiver()) return
        checkType(parameter.returnTypeRef, "parameter", parameter.name, parameter.source)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkTypeParameters(typeParameters: List<FirTypeParameterRef>) {
        for (typeParameter in typeParameters.filterIsInstance<FirTypeParameter>()) {
            for (bound in typeParameter.bounds) {
                checkType(bound, "type parameter", typeParameter.name, typeParameter.source)
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkType(typeRef: FirTypeRef, kind: String, name: Name, fallbackSource: KtSourceElement?) {
        val violation = typeRef.coneType.findInternalApiType() ?: return
        val factory = severities[WatchdogDiagnostics.PUBLIC_TYPE_WITH_INTERNAL_API] ?: return
        reporter.reportOn(
            source = typeRef.source ?: fallbackSource,
            factory = factory,
            a = kind,
            b = name,
            c = violation.asSingleFqName().asString(),
        )
    }

    context(context: CheckerContext)
    private fun ConeKotlinType.findInternalApiType(): ClassId? =
        findInternalApiType(mutableSetOf())

    context(context: CheckerContext)
    private fun ConeKotlinType.findInternalApiType(visited: MutableSet<ConeKotlinType>): ClassId? {
        val type = upperBoundIfFlexible()
        if (!visited.add(type)) return null

        type.abbreviatedType?.findInternalApiType(visited)?.let { return it }
        if (type is ConeClassLikeType) {
            val symbol = type.lookupTag.toSymbol(context.session)
            symbol?.internalApiOwner()?.let { return it.classId }

            // An unmarked alias can still expand to an internal-API type. Inspecting the alias
            // first also preserves an internal marker applied to the alias declaration itself.
            if (symbol is FirTypeAliasSymbol) {
                type.fullyExpandedType().findInternalApiType(visited)?.let { return it }
            }
        }
        return type.typeArguments.firstNotNullOfOrNull { it.type?.findInternalApiType(visited) }
    }

    context(context: CheckerContext)
    private fun FirClassLikeSymbol<*>.internalApiOwner(): FirClassLikeSymbol<*>? {
        var current: FirClassLikeSymbol<*>? = this
        while (current != null) {
            if (current.hasInternalApiMarker()) return current
            current = current.getContainingClassSymbol()
        }
        return null
    }

    context(context: CheckerContext)
    private fun FirMemberDeclaration.isSupportedPublicSourceApi(): Boolean =
        isWatchedPublicApi() && !isPublishedApiOnly()
}
