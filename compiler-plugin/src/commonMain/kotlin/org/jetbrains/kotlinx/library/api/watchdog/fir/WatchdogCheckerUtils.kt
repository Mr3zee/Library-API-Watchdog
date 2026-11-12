package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getSingleMatchedExpectForActualOrNull
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassLikeSymbol
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.declarations.utils.isActual
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.declarations.utils.isExpect
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.FirSamResolver
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.resolve.transformers.publishedApiEffectiveVisibility
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirBackingFieldSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFileSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.functionTypeKind
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
    get() = when (classKind) {
        ClassKind.CLASS -> "class"
        ClassKind.INTERFACE -> "interface"
        ClassKind.OBJECT -> "object"
        ClassKind.ENUM_CLASS -> "enumClass"
        ClassKind.ANNOTATION_CLASS -> "annotationClass"
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
    val IntentionallyExposedInternalApiForBackwardsCompatibility: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyExposedInternalApiForBackwardsCompatibility"))
    val InternalAnnotationMarker: ClassId = ClassId(annotationsPackage, Name.identifier("InternalAnnotationMarker"))

    /**
     * The exemption annotations whose `reason` or `description` must explain the exemption.
     * The name of [IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility] and of
     * [IntentionallyExposedInternalApiForBackwardsCompatibility] is its reason, so they aren't in
     * the set.
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

/** A probe of an `actual` declaration runs with its `expect` class in the context, so the context doesn't count then. */
context(context: CheckerContext)
internal fun FirDeclaration.isExpectedDeclaration(): Boolean =
    (this as? FirMemberDeclaration)?.isExpect == true ||
            !ExemptionProbe.probingActual && context.containingDeclarations.any { symbol ->
                when (symbol) {
                    is FirCallableSymbol<*> -> symbol.isExpect
                    is FirClassLikeSymbol<*> -> symbol.isExpect
                    else -> false
                }
            }

internal object ExemptionProbe {
    private class Ignored(val annotations: List<FirAnnotation>, val classIds: Set<ClassId>, val actual: Boolean)

    private val ignored = ThreadLocal<Ignored?>()

    /** Whether the probed declaration is an `actual` or one of its members. */
    val probingActual: Boolean get() = ignored.get()?.actual == true

    /** Ignores [annotations] individually and [classIds] on every declaration during [block]. */
    fun <R> probing(
        annotations: List<FirAnnotation> = emptyList(),
        classIds: Set<ClassId> = emptySet(),
        actual: Boolean = false,
        block: () -> R,
    ): R {
        val previous = ignored.get()
        ignored.set(Ignored(annotations, classIds, actual))
        try {
            return block()
        } finally {
            // IDE pooled threads can hold references to FIR sessions through this entry.
            if (previous == null) ignored.remove() else ignored.set(previous)
        }
    }

    fun ignores(annotation: FirAnnotation, classId: ClassId): Boolean {
        val current = ignored.get() ?: return false
        return classId in current.classIds ||
                current.annotations.any { it === annotation || it.sameSourceAs(annotation) }
    }
}

/** FIR copies an annotation of a constructor parameter to its property, so the source identifies the annotation. */
internal fun FirAnnotation.sameSourceAs(other: FirAnnotation): Boolean {
    val source = source ?: return false
    val otherSource = other.source ?: return false
    return source.startOffset == otherSource.startOffset && source.endOffset == otherSource.endOffset
}

context(context: CheckerContext)
internal fun FirAnnotation.isEffective(classId: ClassId): Boolean =
    toAnnotationClassIdSafe(context.session) == classId && !ExemptionProbe.ignores(this, classId)

/** Every exemption lookup must use this function so probes can ignore annotations. */
context(context: CheckerContext)
internal fun FirAnnotationContainer.hasExemption(classId: ClassId): Boolean =
    annotations.any { it.isEffective(classId) }

context(context: CheckerContext)
internal fun FirBasedSymbol<*>.hasExemption(classId: ClassId): Boolean =
    resolvedAnnotationsWithClassIds.any { it.isEffective(classId) }

context(context: CheckerContext)
internal fun FirBasedSymbol<*>.hasAnnotationOnActualOrExpect(classId: ClassId): Boolean =
    hasExemption(classId) || getSingleMatchedExpectForActualOrNull()?.hasExemption(classId) == true

context(context: CheckerContext)
internal fun FirDeclaration.hasAnnotationOnActualOrExpect(classId: ClassId): Boolean =
    symbol.hasAnnotationOnActualOrExpect(classId)

context(context: CheckerContext)
internal fun FirValueParameterSymbol.hasExemptionOnParameterOrExpect(classId: ClassId): Boolean {
    if (hasExemption(classId)) return true
    val constructor = containingDeclarationSymbol as? FirConstructorSymbol ?: return false
    val expect = constructor.getSingleMatchedExpectForActualOrNull() as? FirConstructorSymbol ?: return false
    return expect.valueParameterSymbols.getOrNull(constructor.valueParameterSymbols.indexOf(this))?.hasExemption(classId) == true
}

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
    else -> if (isNamedFunction()) symbol.name else null
}

/**
 * Whether the declaration is in the public or `@PublishedApi` binary API, isn't marked as
 * internal API, and passes [ConfiguredApiFilters]. A property from a constructor parameter has a
 * fake source but is hand-written API, so it counts as real.
 */
context(context: CheckerContext)
internal fun FirMemberDeclaration.isWatchedPublicApi(): Boolean {
    val sourceKind = source?.kind
    if (sourceKind != KtRealSourceElementKind && sourceKind != KtFakeSourceElementKind.PropertyFromParameter) {
        return false
    }

    if (visibility == Visibilities.Local || this is FirRegularClass && symbol.isLocal) {
        return false
    }

    if (!effectiveVisibility.publicApi && publishedApiEffectiveVisibility?.publicApi != true) {
        return false
    }

    return !isMarkedAsInternalApi() && passesApiFilters()
}

/**
 * When an included member includes an `expect` class, its `actual` class or `actual typealias` is
 * included too. The members of the class that a typealias expands to don't have the inclusion
 * annotations of the `expect` members.
 */
context(context: CheckerContext)
private fun FirMemberDeclaration.passesApiFilters(): Boolean {
    val filters = context.session.configuredApiFilters ?: return true
    val symbol = symbol
    val chain = enclosingDeclarationsWithFile() + symbol
    if (filters.excludes(chain)) return false
    if (!filters.hasInclusionRules) return true
    if (filters.includesAny(chain)) return true
    if (symbol is FirClassSymbol<*> && symbol.hasIncludedApiMember(filters)) return true
    val expectClass = (symbol as? FirClassLikeSymbol<*>)?.takeIf { it.isActual }?.getSingleMatchedExpectForActualOrNull()
    return expectClass is FirClassSymbol<*> && expectClass.hasIncludedApiMember(filters)
}

/**
 * A file checker doesn't have the file in the enclosing declarations of its context. A probe of an
 * `actual` declaration runs in the context of its `expect` declaration, so the probe reads them
 * from the symbol.
 */
context(context: CheckerContext)
private fun FirMemberDeclaration.enclosingDeclarationsWithFile(): List<FirBasedSymbol<*>> {
    if (ExemptionProbe.probingActual) return symbol.enclosingDeclarationsOfSymbol()
    val enclosing = context.containingDeclarations
    val file = context.containingFileSymbol ?: return enclosing
    return if (file in enclosing) enclosing else enclosing + file
}

/** The enclosing classes and the file of a source declaration. */
internal fun FirBasedSymbol<*>.enclosingDeclarationsOfSymbol(): List<FirBasedSymbol<*>> {
    val property = when (this) {
        is FirPropertyAccessorSymbol -> propertySymbol
        is FirBackingFieldSymbol -> propertySymbol
        else -> null
    }
    val chain = listOfNotNull<FirBasedSymbol<*>>(property) +
            generateSequence((property ?: this).containingClassSymbolCompat()) { it.containingClassSymbolCompat() }
    return chain + listOfNotNull((chain.lastOrNull() ?: this).sourceFileSymbol())
}

/** A nested class doesn't count. */
context(context: CheckerContext)
private fun FirClassSymbol<*>.hasIncludedApiMember(filters: ConfiguredApiFilters): Boolean {
    var included = false
    processAllDeclarations(context.session) { member ->
        if (!included && member is FirCallableSymbol<*> && member.isIncludedApiMember(filters)) included = true
    }
    return included
}

@OptIn(SymbolInternals::class)
context(context: CheckerContext)
private fun FirCallableSymbol<*>.isIncludedApiMember(filters: ConfiguredApiFilters): Boolean {
    val sourceKind = source?.kind
    if (sourceKind != KtRealSourceElementKind && sourceKind != KtFakeSourceElementKind.PropertyFromParameter) {
        return false
    }
    val publicApi = effectiveVisibility.publicApi ||
            (fir as? FirMemberDeclaration)?.publishedApiEffectiveVisibility?.publicApi == true
    return publicApi && filters.includes(this) && filters.excludingPattern(this) == null && !hasInternalApiMarker()
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
    symbol.hasInternalApiMarker() || enclosingDeclarationsWithFile().any { it.hasInternalApiMarker() }

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
    return apiAnnotations().firstNotNullOfOrNull { annotation ->
        val annotationSymbol = annotation.toAnnotationClassLikeSymbol(session)
            ?: return@firstNotNullOfOrNull null
        val classId = annotationSymbol.classId
        classId.takeIf {
            annotationSymbol.hasAnnotation(WatchdogClassIds.InternalAnnotationMarker, session) ||
                    configured?.contains(classId) == true
        }
    }
}

/**
 * The annotations that the internal API annotations and the API filters read. The validation tools
 * copy the annotations of the backing field to the accessors, so a `@field:` annotation counts for
 * the property.
 */
internal fun FirBasedSymbol<*>.apiAnnotations(): List<FirAnnotation> =
    if (this is FirPropertySymbol) {
        resolvedAnnotationsWithClassIds + backingFieldSymbol?.resolvedAnnotationsWithClassIds.orEmpty()
    } else {
        resolvedAnnotationsWithClassIds
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

    return (type as? ConeClassLikeType)?.fullyExpandedType()?.toClassSymbolCompat(context.session)
}

/**
 * True when a call can pass a trailing lambda to this parameter: a function type, a `fun interface`, or a
 * Java SAM interface. No lambda literal satisfies a `KFunction` type. A call can't pass a trailing lambda to
 * a `vararg` parameter.
 */
context(context: CheckerContext)
internal fun FirValueParameterSymbol.acceptsTrailingLambda(): Boolean {
    if (isVararg) {
        return false
    }

    val type = resolvedReturnType.let {
        if (it is ConeClassLikeType) it.fullyExpandedType() else it
    }

    val functionTypeKind = type.functionTypeKind(context.session)
    if (functionTypeKind != null) {
        return !functionTypeKind.isReflectType
    }

    return FirSamResolver(context.session, context.scopeSession).isSamType(type)
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
internal fun FirFunction.mangledValueClassInSignature(): Name? =
    receiverParameter?.typeRef?.coneType?.mangledValueClass()
        ?: contextParameters.firstNotNullOfOrNull { it.returnTypeRef.coneType.mangledValueClass() }
        ?: valueParameters.firstNotNullOfOrNull { it.returnTypeRef.coneType.mangledValueClass() }
        ?: returnValueClassIfMember()

/**
 * Whether `@JvmExposeBoxed` on the declaration or on an enclosing class generates a boxed variant
 * for Java. It doesn't for an open or abstract member.
 */
context(context: CheckerContext)
internal fun FirCallableDeclaration.hasEffectiveJvmExposeBoxed(): Boolean =
    modality != Modality.OPEN && modality != Modality.ABSTRACT &&
            hasAnnotationOnSelfOrEnclosingClass(JvmStandardClassIds.JVM_EXPOSE_BOXED_ANNOTATION_CLASS_ID)

/**
 * Whether Java sees a mangled name: a value class in the signature, and neither `@JvmName` nor an
 * effective `@JvmExposeBoxed` gives the function a Java-facing name.
 */
context(context: CheckerContext)
internal fun FirFunction.hasMangledJvmName(): Boolean =
    mangledValueClassInSignature() != null &&
            !hasAnnotationOnActualOrExpect(JvmStandardClassIds.Annotations.JvmName) &&
            !hasEffectiveJvmExposeBoxed()

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
 * [isWatchedPublicSourceApi] for an overload sibling. The sibling can be a member of another
 * class or a function of another file, so its own enclosing classes and file give the internal
 * API marker and the filter verdict. A library symbol has no real source, so a dependency never
 * passes.
 */
context(context: CheckerContext)
internal fun FirCallableSymbol<*>.isWatchedPublicSourceApiSibling(): Boolean {
    if (source?.kind != KtRealSourceElementKind || !effectiveVisibility.publicApi) return false
    val chain = listOf(this) + enclosingDeclarationsOfSymbol()
    return chain.none { it.hasInternalApiMarker() } &&
            context.session.configuredApiFilters?.watches(chain) != false
}

/** The file of a top-level source declaration, or null. */
private fun FirBasedSymbol<*>.sourceFileSymbol(): FirFileSymbol? {
    val provider = moduleData.session.firProvider
    return when (this) {
        is FirClassLikeSymbol<*> -> provider.getFirClassifierContainerFileIfAny(this)
        is FirCallableSymbol<*> -> provider.getFirCallableContainerFile(this)
        else -> null
    }?.symbol
}

private val reasonParameter = Name.identifier("reason")
private val descriptionParameter = Name.identifier("description")
private val otherReason = Name.identifier("OTHER")

/** The reasons that explain an exemption without a description. */
private val selfExplanatoryReasons = setOf(
    Name.identifier("FOR_BACKWARDS_COMPATIBILITY"),
    Name.identifier("API_DESIGN"),
    Name.identifier("FOR_DEPRECATION"),
)

/**
 * The reason that doesn't explain this exemption, [otherReason] when the argument is absent, or
 * null when a reason or a description explains the exemption.
 */
internal fun FirAnnotation.unexplainedExemptionReason(session: FirSession): Name? {
    val reasonArgument = findArgumentByName(reasonParameter, returnFirstWhenNotFound = false)
    val reason = if (reasonArgument == null) {
        otherReason
    } else {
        // An argument that doesn't resolve to an enum entry is already a compilation error.
        reasonArgument.extractEnumValueArgumentInfo()?.enumEntryName ?: return null
    }
    if (reason in selfExplanatoryReasons) {
        return null
    }

    if (getStringArgumentCompat(descriptionParameter, session)?.isNotBlank() == true) {
        return null
    }

    return reason
}
