package org.jetbrains.kotlinx.library.api.watchdog.fixer

/** How the fix target is located from the source element a diagnostic was reported on. */
internal enum class TargetStrategy {
    /** The nearest enclosing class, interface, or object. Never a enum entry. */
    ENCLOSING_CLASS,

    /** The nearest enclosing annotation class. */
    ENCLOSING_ANNOTATION_CLASS,

    /** The reported declaration itself: class, type alias, function, property, or constructor. */
    REPORTED_DECLARATION,

    /**
     * The annotation entry the diagnostic was reported on. The reported annotation is the problem
     * itself, so the exemption replaces it instead of joining it.
     */
    REPORTED_ANNOTATION,

    /** The callable whose signature contains the reported type, parameter, or bound. */
    ENCLOSING_CALLABLE,

    /** The nearest enclosing function or constructor. */
    ENCLOSING_FUNCTION_OR_CONSTRUCTOR,

    /** The containing file, as a `@file:` annotation. */
    CONTAINING_FILE,
}

/** The exemption annotation that acknowledges one watchdog diagnostic, and where it goes. */
internal class ExemptionFix(
    val annotationShortName: String,
    val targetStrategy: TargetStrategy,
    /** `@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility` bakes its reason in. */
    val hasReasonParameter: Boolean = true,
)

/** How one watchdog diagnostic can be resolved by the fixer. */
internal sealed interface FixResolution {
    class Fixable(val fix: ExemptionFix) : FixResolution

    class Unfixable(val reason: String) : FixResolution
}

/**
 * Maps every watchdog diagnostic to the `@Intentionally*` exemption annotation that acknowledges
 * it, or to the reason why no annotation can be added automatically. The diagnostic names mirror
 * `WatchdogDiagnostics` in `:kotlin-library-api-watchdog-compiler-plugin` and the annotations
 * mirror `:kotlin-library-api-watchdog-plugin-annotations`.
 * The Gradle plugin's functional tests exercise every fixable entry end to end, which keeps the
 * three lists in sync.
 */
internal object ExemptionRegistry {
    const val ANNOTATIONS_PACKAGE = "org.jetbrains.kotlinx.library.api.watchdog"
    const val REASON_CLASS = "ExemptionReason"
    const val REASON_ENTRY = "FOR_BACKWARDS_COMPATIBILITY"

    private val resolutions: Map<String, FixResolution> = mapOf(
        "OPEN_API_WITHOUT_SUBCLASS_OPT_IN" to fixable("IntentionallyOpen", TargetStrategy.ENCLOSING_CLASS),
        // A markerless @SubclassOptInRequired gates nothing, so the exemption takes its place:
        // the class stays open to everyone, now stated outright.
        "SUBCLASS_OPT_IN_WITHOUT_MARKERS" to fixable("IntentionallyOpen", TargetStrategy.REPORTED_ANNOTATION),
        "EXHAUSTIVE_PUBLIC_API" to fixable("IntentionallyExhaustive", TargetStrategy.ENCLOSING_CLASS),
        "FUNCTION_TYPE_ALIAS_PUBLIC_API" to fixable("IntentionallyFunctionTypeAlias", TargetStrategy.REPORTED_DECLARATION),
        "DATA_CLASS_PUBLIC_API" to fixable("IntentionallyDataClass", TargetStrategy.ENCLOSING_CLASS),
        "STATEFUL_CLASS_WITHOUT_EQUALS" to fixable("IntentionallyWithoutEquals", TargetStrategy.ENCLOSING_CLASS),
        "STATEFUL_CLASS_WITHOUT_HASH_CODE" to fixable("IntentionallyWithoutHashCode", TargetStrategy.ENCLOSING_CLASS),
        "STATEFUL_CLASS_WITHOUT_TO_STRING" to fixable("IntentionallyWithoutToString", TargetStrategy.ENCLOSING_CLASS),
        "MUTABLE_COLLECTION_PUBLIC_API" to fixable("IntentionallyMutableCollection", TargetStrategy.ENCLOSING_CALLABLE),
        "PAIR_OR_TRIPLE_PUBLIC_API" to fixable("IntentionallyPairOrTriple", TargetStrategy.ENCLOSING_CALLABLE),
        "BOOLEAN_PARAMETER_PUBLIC_API" to fixable("IntentionallyBooleanParameter", TargetStrategy.ENCLOSING_CALLABLE),
        "NULLABLE_BOOLEAN_PUBLIC_API" to fixable("IntentionallyNullableBoolean", TargetStrategy.ENCLOSING_CALLABLE),
        "REQUIRED_PARAMETER_AFTER_OPTIONAL" to fixable("IntentionallyRequiredParameterAfterOptional", TargetStrategy.ENCLOSING_FUNCTION_OR_CONSTRUCTOR),
        "INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS" to fixable("IntentionallyInconsistentParameterOrder", TargetStrategy.ENCLOSING_FUNCTION_OR_CONSTRUCTOR),
        "INLINE_FUNCTION_WITH_LOGIC" to fixable("IntentionallyInlinedLogic", TargetStrategy.REPORTED_DECLARATION),
        "MANGLED_JVM_NAME_PUBLIC_API" to fixable("IntentionallyMangledJvmName", TargetStrategy.ENCLOSING_CALLABLE),
        "KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC" to fixable("IntentionallyKotlinOnlyApi", TargetStrategy.REPORTED_DECLARATION),
        "COMPANION_API_WITHOUT_JVM_STATIC" to fixable("IntentionallyNonStaticCompanionApi", TargetStrategy.REPORTED_DECLARATION),
        "COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS" to fixable("IntentionallyNonStaticCompanionApi", TargetStrategy.REPORTED_DECLARATION),
        "TOP_LEVEL_API_WITHOUT_JVM_NAME" to fixable("IntentionallyDefaultFacadeName", TargetStrategy.CONTAINING_FILE),
        "DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS" to fixable("IntentionallyWithoutJvmOverloads", TargetStrategy.ENCLOSING_FUNCTION_OR_CONSTRUCTOR),

        "DSL_MARKER_NOOP_TARGET" to fixable("IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility", TargetStrategy.ENCLOSING_ANNOTATION_CLASS, hasReasonParameter = false),
        "DSL_MARKER_WITHOUT_EXPLICIT_TARGETS" to fixable("IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility", TargetStrategy.ENCLOSING_ANNOTATION_CLASS, hasReasonParameter = false),

        "UNDOCUMENTED_PUBLIC_API" to FixResolution.Unfixable("Adding KDocs is not backwards incompatible. Please, add them."),
        "EXEMPTION_WITHOUT_EXPLANATION" to FixResolution.Unfixable("The existing exemption needs a reason or description only its author can write"),
        "DSL_MARKER_NOOP_TYPE_POSITION" to FixResolution.Unfixable("No exemption annotation exists. Move the DSL marker to an effective position or remove it"),
        "PUBLIC_TYPE_WITH_INTERNAL_API" to FixResolution.Unfixable("Remove the internal type from the public signature or mark the exposing declaration as internal API"),
    )

    val knownDiagnostics: Set<String> get() = resolutions.keys

    /** Every exemption annotation the fixer can add, and whether its reason is an argument. */
    val exemptionAnnotations: Map<String, Boolean> = resolutions.values
        .filterIsInstance<FixResolution.Fixable>()
        .associate { it.fix.annotationShortName to it.fix.hasReasonParameter }

    /** The annotation a diagnostic would receive, or null when it can't be acknowledged automatically. */
    fun annotationFor(diagnostic: String): String? =
        (resolutions[diagnostic] as? FixResolution.Fixable)?.fix?.annotationShortName

    fun resolutionFor(diagnostic: String): FixResolution = resolutions[diagnostic] ?: FixResolution.Unfixable(
        "Unknown diagnostic. This fixer version doesn't know how to exempt it"
    )

    private fun fixable(
        annotationShortName: String,
        targetStrategy: TargetStrategy,
        hasReasonParameter: Boolean = true,
    ): FixResolution = FixResolution.Fixable(
        ExemptionFix(annotationShortName, targetStrategy, hasReasonParameter)
    )
}
