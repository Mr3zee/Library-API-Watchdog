package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassLikeSymbol
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.resolve.transformers.publishedApiEffectiveVisibility
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.upperBoundIfFlexible
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.Name

internal object WatchdogClassIds {
    private val annotationsPackage = FqName("org.jetbrains.kotlinx.libs.api.watchdog")

    val SubclassOptInRequired: ClassId = ClassId(FqName("kotlin"), Name.identifier("SubclassOptInRequired"))
    val IntentionallyOpen: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyOpen"))
    val IntentionallyExhaustive: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyExhaustive"))
    val IntentionallyUndocumented: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyUndocumented"))
    val IntentionallyFunctionTypeAlias: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyFunctionTypeAlias"))
    val IntentionallyDataClass: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyDataClass"))
    val IntentionallyWithoutToString: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyWithoutToString"))
    val IntentionallyMutableCollection: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyMutableCollection"))
    val IntentionallyPairOrTriple: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyPairOrTriple"))
    val IntentionallyBooleanParameter: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyBooleanParameter"))
    val IntentionallyNullableBoolean: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyNullableBoolean"))
    val IntentionallyRequiredParameterAfterOptional: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyRequiredParameterAfterOptional"))
    val IntentionallyInconsistentParameterOrder: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyInconsistentParameterOrder"))
    val IntentionallyInlinedLogic: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyInlinedLogic"))
    val IntentionallyMangledJvmName: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyMangledJvmName"))
    val IntentionallyKotlinOnlyApi: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyKotlinOnlyApi"))
    val IntentionallyNonStaticCompanionApi: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyNonStaticCompanionApi"))
    val IntentionallyDefaultFacadeName: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyDefaultFacadeName"))
    val IntentionallyWithoutJvmOverloads: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyWithoutJvmOverloads"))
    val IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility"))
    val InternalAnnotationMarker: ClassId = ClassId(annotationsPackage, Name.identifier("InternalAnnotationMarker"))

    /**
     * The annotations that exempt a declaration from a check and must explain why they do.
     * [InternalAnnotationMarker] is deliberately not one of them: the marked annotation class
     * documents the internal API surface itself. Neither is
     * [IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility]: its only accepted reason is
     * baked into its name, so its description may stay empty.
     */
    val exemptionAnnotations: Set<ClassId> = setOf(
        IntentionallyOpen,
        IntentionallyExhaustive,
        IntentionallyUndocumented,
        IntentionallyFunctionTypeAlias,
        IntentionallyDataClass,
        IntentionallyWithoutToString,
        IntentionallyMutableCollection,
        IntentionallyPairOrTriple,
        IntentionallyBooleanParameter,
        IntentionallyNullableBoolean,
        IntentionallyRequiredParameterAfterOptional,
        IntentionallyInconsistentParameterOrder,
        IntentionallyInlinedLogic,
        IntentionallyMangledJvmName,
        IntentionallyKotlinOnlyApi,
        IntentionallyNonStaticCompanionApi,
        IntentionallyDefaultFacadeName,
        IntentionallyWithoutJvmOverloads,
    )
}

/** The class the checked declaration is a member of, or null for a top-level declaration. */
internal val CheckerContext.containingClassSymbol: FirClassSymbol<*>?
    get() = containingDeclarations.lastOrNull() as? FirClassSymbol<*>

/** The name a diagnostic reports for a callable: a constructor is named after its class. */
context(context: CheckerContext)
internal fun FirFunction.reportedName(): Name? = when (this) {
    is FirNamedFunction -> name
    is FirConstructor -> context.containingClassSymbol?.classId?.shortClassName
    else -> null
}

/**
 * Only declarations written in real sources and visible to library users (public or protected)
 * are worth watching: everything else can't be referenced from outside the library. Properties
 * created from constructor `val`/`var` parameters carry a fake source pointing at the parameter,
 * but they are still hand-written public API, so they count as real.
 *
 * `@PublishedApi` declarations are internal in sources but belong to the published binary API:
 * public inline functions expose them to users, so they are watched like public declarations.
 *
 * Declarations marked as internal API - annotated, directly or on an enclosing declaration, with
 * an annotation whose class carries `@InternalAnnotationMarker` - offer no compatibility contract
 * despite their visibility, so they are not watched either.
 */
context(context: CheckerContext)
internal fun FirMemberDeclaration.isWatchedPublicApi(): Boolean {
    val sourceKind = source?.kind
    if (sourceKind != KtRealSourceElementKind && sourceKind != KtFakeSourceElementKind.PropertyFromParameter) {
        return false
    }

    if (visibility == Visibilities.Local || this is FirRegularClass && isLocal) {
        return false
    }

    if (!effectiveVisibility.publicApi && publishedApiEffectiveVisibility?.publicApi != true) {
        return false
    }

    return !isMarkedAsInternalApi()
}

/**
 * The marker on an enclosing declaration covers the whole subtree: an internal API class can't
 * be used by users, so nothing declared inside it is usable public API.
 */
context(context: CheckerContext)
private fun FirMemberDeclaration.isMarkedAsInternalApi(): Boolean =
    symbol.hasInternalApiMarker() || context.containingDeclarations.any { it.hasInternalApiMarker() }

context(context: CheckerContext)
private fun FirBasedSymbol<*>.hasInternalApiMarker(): Boolean =
    resolvedAnnotationsWithClassIds.any { annotation ->
        annotation.toAnnotationClassLikeSymbol(context.session)
            ?.hasAnnotation(WatchdogClassIds.InternalAnnotationMarker, context.session) == true
    }

/** `value class` sets the `isValue` status flag; `isInline` covers the legacy `inline class`. */
internal fun FirClassSymbol<*>.isValueClass(): Boolean =
    resolvedStatus.let { it.isValue || it.isInline }

/**
 * The name of the value class that mangles a JVM signature mentioning this type, or null.
 * Only the classifier itself counts - a value class inside a type argument is boxed and
 * leaves the name intact. Type aliases are expanded, and a type parameter erases to its
 * first upper bound, so `<T : UserId> f(t: T)` mangles like a direct mention. The visited
 * set guards against cyclic bounds in erroneous code.
 */
context(context: CheckerContext)
internal fun ConeKotlinType.mangledValueClass(): Name? {
    var type: ConeKotlinType = upperBoundIfFlexible()
    val visitedTypeParameters = mutableSetOf<FirTypeParameterSymbol>()
    while (type is ConeTypeParameterType) {
        val typeParameter = type.lookupTag.typeParameterSymbol
        if (!visitedTypeParameters.add(typeParameter)) {
            return null
        }
        type = typeParameter.resolvedBounds.firstOrNull()?.coneType?.upperBoundIfFlexible() ?: return null
    }

    val classLike = (type as? ConeClassLikeType)?.fullyExpandedType() ?: return null
    val symbol = classLike.toClassSymbol() ?: return null
    return symbol.classId.shortClassName.takeIf { symbol.isValueClass() }
}

/**
 * Return types only mangle members: a dispatch receiver is what distinguishes `give()` - a
 * top-level function keeping its JVM name - from `member-aWk8GsU()`.
 */
context(context: CheckerContext)
internal fun FirCallableDeclaration.returnValueClassIfMember(): Name? =
    if (dispatchReceiverType != null) returnTypeRef.coneType.mangledValueClass() else null

/**
 * The value class that mangles this function's compiled JVM name - found among the value
 * parameters, the extension receiver, the context parameters, or, for a member, the return
 * type - or null when the JVM name is kept.
 */
context(context: CheckerContext)
internal fun FirNamedFunction.mangledValueClassInSignature(): Name? =
    receiverParameter?.typeRef?.coneType?.mangledValueClass()
        ?: contextParameters.firstNotNullOfOrNull { it.returnTypeRef.coneType.mangledValueClass() }
        ?: valueParameters.firstNotNullOfOrNull { it.returnTypeRef.coneType.mangledValueClass() }
        ?: returnValueClassIfMember()

/**
 * Whether Java sources can't see this declaration because it is marked `@JvmSynthetic`. A
 * property is hidden when its every Java entry point is: the getter - and the setter of a
 * `var` - via `@get:`/`@set:JvmSynthetic`, while a `const` or `@JvmField` property stays
 * visible as a field regardless of accessor annotations.
 */
context(context: CheckerContext)
internal fun FirCallableDeclaration.isHiddenFromJavaWithJvmSynthetic(): Boolean {
    val session = context.session
    if (this !is FirProperty) {
        return hasAnnotation(JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID, session)
    }

    if (isConst || hasJvmFieldAnnotation()) {
        return false
    }
    return isAccessorHiddenWithJvmSynthetic(getter, AnnotationUseSiteTarget.PROPERTY_GETTER) &&
            (!isVar || isAccessorHiddenWithJvmSynthetic(setter, AnnotationUseSiteTarget.PROPERTY_SETTER))
}

/** The annotation is moved to the backing field during resolution, so both carriers count. */
context(context: CheckerContext)
internal fun FirProperty.hasJvmFieldAnnotation(): Boolean =
    hasAnnotation(JvmStandardClassIds.Annotations.JvmField, context.session) ||
            backingField?.hasAnnotation(JvmStandardClassIds.Annotations.JvmField, context.session) == true ||
            annotations.any {
                it.useSiteTarget == AnnotationUseSiteTarget.FIELD &&
                        it.toAnnotationClassIdSafe(context.session) == JvmStandardClassIds.Annotations.JvmField
            }

/**
 * `@JvmSynthetic` sits on an explicit accessor directly; the `@get:`/`@set:` use-site form
 * stays on the property with the accessor as its use-site target.
 */
context(context: CheckerContext)
private fun FirProperty.isAccessorHiddenWithJvmSynthetic(
    accessor: FirPropertyAccessor?,
    useSiteTarget: AnnotationUseSiteTarget,
): Boolean {
    val session = context.session
    return accessor != null && accessor.hasAnnotation(JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID, session) ||
            annotations.any {
                it.useSiteTarget == useSiteTarget &&
                        it.toAnnotationClassIdSafe(session) == JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID
            }
}

/**
 * Symbol-based public API gate for overload siblings, which checkers only reach as symbols. The
 * declaration under check shares the containers its own [isWatchedPublicApi] gate already vets -
 * or, for an inherited sibling, subsumes them: a supertype visible in a public class's scope is
 * itself reachable by users. So only the sibling's own state matters here. Library symbols
 * carry no real source, so dependencies never pass this gate.
 */
context(context: CheckerContext)
internal fun FirCallableSymbol<*>.isWatchedPublicApiSibling(): Boolean =
    source?.kind == KtRealSourceElementKind &&
            (effectiveVisibility.publicApi || publishedApiEffectiveVisibility?.publicApi == true) &&
            !hasInternalApiMarker()

private val reasonParameter = Name.identifier("reason")
private val descriptionParameter = Name.identifier("description")
private val otherReason = Name.identifier("OTHER")

/**
 * The reasons that explain an exemption on their own. Every other entry - including ones a
 * future annotations version may add - only categorizes the exemption and requires a
 * non-empty description next to it.
 */
private val selfSufficientReasons = setOf(
    Name.identifier("FOR_BACKWARDS_COMPATIBILITY"),
    Name.identifier("API_DESIGN"),
)

/**
 * The reason that fails to explain this exemption annotation on its own ([otherReason] when the
 * argument is absent), or null when the exemption is explained - by a self-sufficient reason or
 * by a non-blank description. Shared between [ExemptionExplanationChecker], which validates
 * exemptions on declarations, and the checkers that honor exemptions in positions declaration
 * checkers can't see (type-use annotations).
 */
internal fun FirAnnotation.unexplainedExemptionReason(): Name? {
    val reasonArgument = findArgumentByName(reasonParameter, returnFirstWhenNotFound = false)
    val reason = if (reasonArgument == null) {
        otherReason
    } else {
        // An argument that resolves to no enum entry is already a compilation error, so it
        // is not reported again.
        reasonArgument.extractEnumValueArgumentInfo()?.enumEntryName ?: return null
    }
    if (reason in selfSufficientReasons) {
        return null
    }

    if (getStringArgument(descriptionParameter)?.isNotBlank() == true) {
        return null
    }

    return reason
}
