package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getSingleMatchedExpectForActualOrNull
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassLikeSymbol
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.declarations.utils.isActual
import org.jetbrains.kotlin.fir.declarations.utils.isExpect
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.resolve.transformers.publishedApiEffectiveVisibility
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
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
import org.jetbrains.kotlin.name.SpecialNames

/** The kind word of `diagnostics.json` for this class, or null when no message names the kind. */
internal val FirRegularClass.kindParameterValue: String?
    // An exhaustive `when` over the enum compiles to an `ordinal()` switch, which AnimalSniffer
    // rejects against the compiler API baseline.
    get() = when {
        classKind == ClassKind.CLASS -> "class"
        classKind == ClassKind.INTERFACE -> "interface"
        classKind == ClassKind.OBJECT -> "object"
        classKind == ClassKind.ENUM_CLASS -> "enumClass"
        classKind == ClassKind.ANNOTATION_CLASS -> "annotationClass"
        else -> null
    }

/** The name as written in the source: `_` for an unnamed context parameter. */
internal val FirValueParameter.sourceName: Name
    get() = if (name == SpecialNames.UNDERSCORE_FOR_UNUSED_VAR) UNDERSCORE else name

private val UNDERSCORE = Name.identifier("_")

internal object WatchdogClassIds {
    private val annotationsPackage = FqName("org.jetbrains.kotlinx.library.api.watchdog")

    val SubclassOptInRequired: ClassId = ClassId(FqName("kotlin"), Name.identifier("SubclassOptInRequired"))
    val IntentionallyOpen: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyOpen"))
    val IntentionallyExhaustive: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyExhaustive"))
    val IntentionallyUndocumented: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyUndocumented"))
    val IntentionallyFunctionTypeAlias: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyFunctionTypeAlias"))
    val IntentionallyDataClass: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyDataClass"))
    val IntentionallyWithoutEquals: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyWithoutEquals"))
    val IntentionallyWithoutEqualsHashCodeOrToString: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyWithoutEqualsHashCodeOrToString"))
    val IntentionallyWithoutHashCode: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyWithoutHashCode"))
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
    val IntentionallyNonTransitiveDependency: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyNonTransitiveDependency"))
    val IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility"))
    val InternalAnnotationMarker: ClassId = ClassId(annotationsPackage, Name.identifier("InternalAnnotationMarker"))

    /**
     * The exemption annotations whose `reason` or `description` must explain the exemption.
     * The name of [IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility] is its reason, so
     * it isn't in the set.
     */
    val exemptionAnnotations: Set<ClassId> = setOf(
        IntentionallyOpen,
        IntentionallyExhaustive,
        IntentionallyUndocumented,
        IntentionallyFunctionTypeAlias,
        IntentionallyDataClass,
        IntentionallyWithoutEquals,
        IntentionallyWithoutEqualsHashCodeOrToString,
        IntentionallyWithoutHashCode,
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
        IntentionallyNonTransitiveDependency,
    )
}

internal val CheckerContext.containingClassSymbol: FirClassSymbol<*>?
    get() = containingDeclarations.lastOrNull() as? FirClassSymbol<*>

/** A value parameter takes the status of its closest enclosing callable. */
context(context: CheckerContext)
internal fun FirDeclaration.isActualizedDeclaration(): Boolean =
    (this as? FirMemberDeclaration)?.isActual == true ||
            this is FirValueParameter &&
            context.containingDeclarations.lastOrNull { it is FirCallableSymbol<*> }
                ?.let { (it as FirCallableSymbol<*>).isActual } == true

context(context: CheckerContext)
internal fun FirDeclaration.isExpectedDeclaration(): Boolean =
    (this as? FirMemberDeclaration)?.isExpect == true ||
            context.containingDeclarations.any { symbol ->
                when (symbol) {
                    is FirCallableSymbol<*> -> symbol.isExpect
                    is FirClassLikeSymbol<*> -> symbol.isExpect
                    else -> false
                }
            }

context(context: CheckerContext)
internal fun FirBasedSymbol<*>.hasAnnotationOnActualOrExpect(classId: ClassId): Boolean =
    hasAnnotation(classId, context.session) ||
            getSingleMatchedExpectForActualOrNull()?.hasAnnotation(classId, context.session) == true

context(context: CheckerContext)
internal fun FirDeclaration.hasAnnotationOnActualOrExpect(classId: ClassId): Boolean =
    symbol.hasAnnotationOnActualOrExpect(classId)

/** Whether an enclosing class carries [classId], on the `actual` or the matched `expect`. */
context(context: CheckerContext)
internal fun enclosingClassHasAnnotation(classId: ClassId): Boolean =
    context.containingDeclarations.any {
        it is FirClassSymbol<*> && it.hasAnnotationOnActualOrExpect(classId)
    }

/** [hasAnnotationOnActualOrExpect] of the declaration or [enclosingClassHasAnnotation]. */
context(context: CheckerContext)
internal fun FirDeclaration.hasAnnotationOnSelfOrEnclosingClass(classId: ClassId): Boolean =
    hasAnnotationOnActualOrExpect(classId) || enclosingClassHasAnnotation(classId)

context(context: CheckerContext)
internal fun FirFunction.reportedName(): Name? = when (this) {
    is FirConstructor -> context.containingClassSymbol?.classId?.shortClassName
    is FirNamedFunction -> name
    else -> null
}

/**
 * Whether the declaration is in the public or `@PublishedApi` binary API and isn't marked as
 * internal API. A property from a constructor parameter has a fake source but is hand-written
 * API, so it counts as real.
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
 * Whether users can reference the declaration in Kotlin source. Use it in a check about how users
 * write code against the declaration. A check about the binary shape uses [isWatchedPublicApi].
 */
context(context: CheckerContext)
internal fun FirMemberDeclaration.isWatchedPublicSourceApi(): Boolean =
    isWatchedPublicApi() && !isPublishedApiOnly()

context(context: CheckerContext)
private fun FirMemberDeclaration.isMarkedAsInternalApi(): Boolean =
    symbol.hasInternalApiMarker() || context.containingDeclarations.any { it.hasInternalApiMarker() }

context(context: CheckerContext)
internal fun FirBasedSymbol<*>.hasInternalApiMarker(): Boolean = internalApiAnnotation() != null

/**
 * The annotation on this declaration whose class carries [WatchdogClassIds.InternalAnnotationMarker]
 * or that [ConfiguredInternalApiAnnotations] lists, or null.
 */
context(context: CheckerContext)
internal fun FirBasedSymbol<*>.internalApiAnnotation(): ClassId? {
    val session = context.session
    val configured = session.configuredInternalApiAnnotations
    return resolvedAnnotationsWithClassIds.firstNotNullOfOrNull { annotation ->
        val annotationSymbol = annotation.toAnnotationClassLikeSymbol(session)
            ?: return@firstNotNullOfOrNull null
        val classId = annotationSymbol.classId
        classId.takeIf {
            annotationSymbol.hasAnnotation(WatchdogClassIds.InternalAnnotationMarker, session) ||
                    configured?.contains(classId) == true
        }
    }
}

/** Valid only for a declaration that passed [isWatchedPublicApi]. */
internal fun FirMemberDeclaration.isPublishedApiOnly(): Boolean = !effectiveVisibility.publicApi

/** `isInline` covers the legacy `inline class`. */
internal fun FirClassSymbol<*>.isValueClass(): Boolean =
    resolvedStatus.let { it.isValue || it.isInline }

/**
 * The class of the expanded type, or null. A type parameter erases to its first upper bound.
 * A cyclic bound in erroneous code gives null.
 */
context(context: CheckerContext)
internal fun ConeKotlinType.erasedClassSymbol(): FirClassSymbol<*>? {
    var type: ConeKotlinType = upperBoundIfFlexible()
    var visitedTypeParameters: MutableSet<FirTypeParameterSymbol>? = null
    while (type is ConeTypeParameterType) {
        val typeParameter = type.typeParameterSymbol
        val visited = visitedTypeParameters ?: mutableSetOf<FirTypeParameterSymbol>()
            .also { visitedTypeParameters = it }
        if (!visited.add(typeParameter)) {
            return null
        }
        type = typeParameter.resolvedBounds.firstOrNull()?.coneType?.upperBoundIfFlexible() ?: return null
    }

    return (type as? ConeClassLikeType)?.fullyExpandedType()?.toClassSymbol()
}

/**
 * The name of the value class that mangles a JVM signature with this type, or null. The JVM
 * boxes a value class in a type argument, so it doesn't count.
 */
context(context: CheckerContext)
internal fun ConeKotlinType.mangledValueClass(): Name? {
    val symbol = erasedClassSymbol() ?: return null
    return symbol.classId.shortClassName.takeIf { symbol.isValueClass() }
}

/** A return type mangles the JVM name of a member only. */
context(context: CheckerContext)
internal fun FirCallableDeclaration.returnValueClassIfMember(): Name? =
    if (dispatchReceiverType != null) returnTypeRef.coneType.mangledValueClass() else null

context(context: CheckerContext)
internal fun FirNamedFunction.mangledValueClassInSignature(): Name? =
    receiverParameter?.typeRef?.coneType?.mangledValueClass()
        ?: contextParameters.firstNotNullOfOrNull { it.returnTypeRef.coneType.mangledValueClass() }
        ?: valueParameters.firstNotNullOfOrNull { it.returnTypeRef.coneType.mangledValueClass() }
        ?: returnValueClassIfMember()

/** `@JvmSynthetic` hides a property when it hides every Java entry point of it. */
context(context: CheckerContext)
internal fun FirCallableDeclaration.isHiddenFromJavaWithJvmSynthetic(): Boolean {
    val session = context.session
    if (this !is FirProperty) {
        return hasAnnotation(JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID, session)
    }

    val jvmSynthetic = JvmStandardClassIds.JVM_SYNTHETIC_ANNOTATION_CLASS_ID
    if (isConst || hasJvmFieldAnnotation()) {
        // The backing field is the only Java entry point.
        return hasFieldAnnotation(jvmSynthetic)
    }
    return hasAccessorAnnotation(getter, AnnotationUseSiteTarget.PROPERTY_GETTER, jvmSynthetic) &&
            (!isVar || hasAccessorAnnotation(setter, AnnotationUseSiteTarget.PROPERTY_SETTER, jvmSynthetic))
}

/** Resolution moves the annotation to the backing field, so both carriers count. */
context(context: CheckerContext)
internal fun FirProperty.hasJvmFieldAnnotation(): Boolean =
    hasAnnotation(JvmStandardClassIds.Annotations.JvmField, context.session) ||
            hasFieldAnnotation(JvmStandardClassIds.Annotations.JvmField)

/** Whether the annotation is on the backing field or on the property with the `field:` target. */
context(context: CheckerContext)
internal fun FirProperty.hasFieldAnnotation(annotationClassId: ClassId): Boolean =
    backingField?.hasAnnotation(annotationClassId, context.session) == true ||
            hasAnnotationWithUseSiteTarget(AnnotationUseSiteTarget.FIELD, annotationClassId)

/**
 * Whether the annotation is on [accessor] or on the property with [useSiteTarget]. Resolution
 * moves an accessor annotation onto the accessor or keeps it on the property with the target.
 */
context(context: CheckerContext)
internal fun FirProperty.hasAccessorAnnotation(
    accessor: FirPropertyAccessor?,
    useSiteTarget: AnnotationUseSiteTarget,
    annotationClassId: ClassId,
): Boolean = accessor?.hasAnnotation(annotationClassId, context.session) == true ||
        hasAnnotationWithUseSiteTarget(useSiteTarget, annotationClassId)

context(context: CheckerContext)
private fun FirProperty.hasAnnotationWithUseSiteTarget(
    useSiteTarget: AnnotationUseSiteTarget,
    annotationClassId: ClassId,
): Boolean = annotations.any {
    it.useSiteTarget == useSiteTarget && it.toAnnotationClassIdSafe(context.session) == annotationClassId
}

/**
 * [isWatchedPublicSourceApi] for an overload sibling. The check of the declaration under check
 * already vets the containers, so only the state of the sibling counts. A library symbol has no
 * real source, so a dependency never passes.
 */
context(context: CheckerContext)
internal fun FirCallableSymbol<*>.isWatchedPublicSourceApiSibling(): Boolean =
    source?.kind == KtRealSourceElementKind &&
            effectiveVisibility.publicApi &&
            !hasInternalApiMarker()

private val reasonParameter = Name.identifier("reason")
private val descriptionParameter = Name.identifier("description")
private val otherReason = Name.identifier("OTHER")

/** The reasons that explain an exemption without a description. */
private val backwardsCompatibilityReason = Name.identifier("FOR_BACKWARDS_COMPATIBILITY")
private val apiDesignReason = Name.identifier("API_DESIGN")

/**
 * The reason that doesn't explain this exemption, [otherReason] when the argument is absent, or
 * null when a reason or a description explains the exemption.
 */
context(context: SessionHolder)
internal fun FirAnnotation.unexplainedExemptionReason(): Name? {
    val reasonArgument = findArgumentByName(reasonParameter, returnFirstWhenNotFound = false)
    val reason = if (reasonArgument == null) {
        otherReason
    } else {
        // An argument that resolves to no enum entry is already a compilation error.
        reasonArgument.extractEnumValueArgumentInfo()?.enumEntryName ?: return null
    }
    if (reason == backwardsCompatibilityReason || reason == apiDesignReason) {
        return null
    }

    if (getStringArgumentCompat(descriptionParameter)?.isNotBlank() == true) {
        return null
    }

    return reason
}
