package org.jetbrains.kotlinx.libs.api.watchdog.fixer

/** How the fix target is located from the source element a diagnostic was reported on. */
internal enum class TargetStrategy {
    /** The nearest enclosing class, interface, or object; never an enum entry. */
    ENCLOSING_CLASS,

    /** The nearest enclosing annotation class. */
    ENCLOSING_ANNOTATION_CLASS,

    /** The reported declaration itself: class, type alias, function, property, or constructor. */
    REPORTED_DECLARATION,

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
 * `WatchdogDiagnostics` in `:compiler-plugin` and the annotations mirror `:plugin-annotations`;
 * the Gradle plugin's functional tests exercise every fixable entry end to end, which keeps the
 * three lists in sync.
 */
internal object ExemptionRegistry {
    const val ANNOTATIONS_PACKAGE = "org.jetbrains.kotlinx.libs.api.watchdog"
    const val REASON_CLASS = "ExemptionReason"
    const val REASON_ENTRY = "FOR_BACKWARDS_COMPATIBILITY"

    private val resolutions: Map<String, FixResolution> = mapOf(
        "OPEN_API_WITHOUT_SUBCLASS_OPT_IN" to fixable("IntentionallyOpen", TargetStrategy.ENCLOSING_CLASS),
        "SUBCLASS_OPT_IN_WITHOUT_MARKERS" to FixResolution.Unfixable(
            "no exemption annotation exists; pass at least one opt-in marker class to @SubclassOptInRequired"
        ),
        "EXHAUSTIVE_PUBLIC_API" to fixable("IntentionallyExhaustive", TargetStrategy.ENCLOSING_CLASS),
        "UNDOCUMENTED_PUBLIC_API" to fixable("IntentionallyUndocumented", TargetStrategy.REPORTED_DECLARATION),
        "FUNCTION_TYPE_ALIAS_PUBLIC_API" to fixable("IntentionallyFunctionTypeAlias", TargetStrategy.REPORTED_DECLARATION),
        "DATA_CLASS_PUBLIC_API" to fixable("IntentionallyDataClass", TargetStrategy.ENCLOSING_CLASS),
        "STATEFUL_CLASS_WITHOUT_TO_STRING" to fixable("IntentionallyWithoutToString", TargetStrategy.ENCLOSING_CLASS),
        "MUTABLE_COLLECTION_PUBLIC_API" to fixable("IntentionallyMutableCollection", TargetStrategy.ENCLOSING_CALLABLE),
        "PAIR_OR_TRIPLE_PUBLIC_API" to fixable("IntentionallyPairOrTriple", TargetStrategy.ENCLOSING_CALLABLE),
        "BOOLEAN_PARAMETER_PUBLIC_API" to fixable("IntentionallyBooleanParameter", TargetStrategy.ENCLOSING_CALLABLE),
        "NULLABLE_BOOLEAN_PUBLIC_API" to fixable("IntentionallyNullableBoolean", TargetStrategy.ENCLOSING_CALLABLE),
        "REQUIRED_PARAMETER_AFTER_OPTIONAL" to
                fixable("IntentionallyRequiredParameterAfterOptional", TargetStrategy.ENCLOSING_FUNCTION_OR_CONSTRUCTOR),
        "INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS" to
                fixable("IntentionallyInconsistentParameterOrder", TargetStrategy.ENCLOSING_FUNCTION_OR_CONSTRUCTOR),
        "INLINE_FUNCTION_WITH_LOGIC" to fixable("IntentionallyInlinedLogic", TargetStrategy.REPORTED_DECLARATION),
        "MANGLED_JVM_NAME_PUBLIC_API" to fixable("IntentionallyMangledJvmName", TargetStrategy.ENCLOSING_CALLABLE),
        "KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC" to fixable("IntentionallyKotlinOnlyApi", TargetStrategy.REPORTED_DECLARATION),
        "COMPANION_API_WITHOUT_JVM_STATIC" to fixable("IntentionallyNonStaticCompanionApi", TargetStrategy.REPORTED_DECLARATION),
        "COMPANION_CONSTANT_WITHOUT_JVM_FIELD" to fixable("IntentionallyNonStaticCompanionApi", TargetStrategy.REPORTED_DECLARATION),
        "TOP_LEVEL_API_WITHOUT_JVM_NAME" to fixable("IntentionallyDefaultFacadeName", TargetStrategy.CONTAINING_FILE),
        "DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS" to
                fixable("IntentionallyWithoutJvmOverloads", TargetStrategy.ENCLOSING_FUNCTION_OR_CONSTRUCTOR),
        "EXEMPTION_WITHOUT_EXPLANATION" to FixResolution.Unfixable(
            "the existing exemption needs a reason or description only its author can write"
        ),
        "DSL_MARKER_NOOP_TARGET" to fixable(
            "IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility",
            TargetStrategy.ENCLOSING_ANNOTATION_CLASS,
            hasReasonParameter = false,
        ),
        "DSL_MARKER_WITHOUT_EXPLICIT_TARGETS" to fixable(
            "IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility",
            TargetStrategy.ENCLOSING_ANNOTATION_CLASS,
            hasReasonParameter = false,
        ),
        "DSL_MARKER_NOOP_TYPE_POSITION" to FixResolution.Unfixable(
            "no exemption annotation exists; move the DSL marker to an effective position or remove it"
        ),
    )

    val knownDiagnostics: Set<String> get() = resolutions.keys

    fun resolutionFor(diagnostic: String): FixResolution =
        resolutions[diagnostic] ?: FixResolution.Unfixable(
            "unknown diagnostic; this fixer version does not know how to exempt it"
        )

    private fun fixable(
        annotationShortName: String,
        targetStrategy: TargetStrategy,
        hasReasonParameter: Boolean = true,
    ): FixResolution = FixResolution.Fixable(
        ExemptionFix(annotationShortName, targetStrategy, hasReasonParameter)
    )
}
