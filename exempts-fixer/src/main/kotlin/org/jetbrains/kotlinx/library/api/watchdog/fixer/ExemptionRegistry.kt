package org.jetbrains.kotlinx.library.api.watchdog.fixer

internal enum class TargetStrategy {
    /** Never an enum entry. */
    ENCLOSING_CLASS,
    ENCLOSING_ANNOTATION_CLASS,
    REPORTED_DECLARATION,
    DOCUMENTED_DECLARATION,

    /** The exemption replaces the reported annotation. */
    REPORTED_ANNOTATION,
    ENCLOSING_CALLABLE,
    ENCLOSING_FUNCTION_OR_CONSTRUCTOR,

    /** A `@file:` annotation. */
    CONTAINING_FILE,
}

internal sealed interface Fix {
    /** Fixes with one id on one target are applied once. */
    val id: String

    val targetStrategy: TargetStrategy
}

internal class ExemptionFix(
    val annotationShortName: String,
    override val targetStrategy: TargetStrategy,
    val hasReasonParameter: Boolean = true,
) : Fix {
    override val id: String get() = annotationShortName
}

internal object PlaceholderKdocFix : Fix {
    const val TEXT = "TODO: Provide kDocs for this declaration"

    override val id: String get() = "KDoc"
    override val targetStrategy: TargetStrategy get() = TargetStrategy.DOCUMENTED_DECLARATION
}

internal sealed interface FixResolution {
    class Fixable(val fix: Fix) : FixResolution

    class Unfixable(val reason: String) : FixResolution
}

internal object ExemptionRegistry {
    const val ANNOTATIONS_PACKAGE = "org.jetbrains.kotlinx.library.api.watchdog"
    const val REASON_CLASS = "ExemptionReason"
    const val REASON_ENTRY = "FOR_BACKWARDS_COMPATIBILITY"

    private val resolutions: Map<String, FixResolution> = mapOf(
        WatchdogChecks.OPEN_API_WITHOUT_SUBCLASS_OPT_IN to fixable("IntentionallyOpen", TargetStrategy.ENCLOSING_CLASS),
        WatchdogChecks.SUBCLASS_OPT_IN_WITHOUT_MARKERS to fixable("IntentionallyOpen", TargetStrategy.REPORTED_ANNOTATION),
        WatchdogChecks.EXHAUSTIVE_PUBLIC_API to fixable("IntentionallyExhaustive", TargetStrategy.ENCLOSING_CLASS),
        WatchdogChecks.FUNCTION_TYPE_ALIAS_PUBLIC_API to fixable("IntentionallyFunctionTypeAlias", TargetStrategy.REPORTED_DECLARATION),
        WatchdogChecks.DATA_CLASS_PUBLIC_API to fixable("IntentionallyDataClass", TargetStrategy.ENCLOSING_CLASS),
        WatchdogChecks.STATEFUL_CLASS_WITHOUT_EQUALS to fixable("IntentionallyWithoutEquals", TargetStrategy.ENCLOSING_CLASS),
        WatchdogChecks.STATEFUL_CLASS_WITHOUT_HASH_CODE to fixable("IntentionallyWithoutHashCode", TargetStrategy.ENCLOSING_CLASS),
        WatchdogChecks.STATEFUL_CLASS_WITHOUT_TO_STRING to fixable("IntentionallyWithoutToString", TargetStrategy.ENCLOSING_CLASS),
        WatchdogChecks.MUTABLE_COLLECTION_PUBLIC_API to fixable("IntentionallyMutableCollection", TargetStrategy.ENCLOSING_CALLABLE),
        WatchdogChecks.PAIR_OR_TRIPLE_PUBLIC_API to fixable("IntentionallyPairOrTriple", TargetStrategy.ENCLOSING_CALLABLE),
        WatchdogChecks.BOOLEAN_PARAMETER_PUBLIC_API to fixable("IntentionallyBooleanParameter", TargetStrategy.ENCLOSING_CALLABLE),
        WatchdogChecks.NULLABLE_BOOLEAN_PUBLIC_API to fixable("IntentionallyNullableBoolean", TargetStrategy.ENCLOSING_CALLABLE),
        WatchdogChecks.REQUIRED_PARAMETER_AFTER_OPTIONAL to fixable("IntentionallyRequiredParameterAfterOptional", TargetStrategy.ENCLOSING_FUNCTION_OR_CONSTRUCTOR),
        WatchdogChecks.INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS to fixable("IntentionallyInconsistentParameterOrder", TargetStrategy.ENCLOSING_FUNCTION_OR_CONSTRUCTOR),
        WatchdogChecks.INLINE_FUNCTION_WITH_LOGIC to fixable("IntentionallyInlinedLogic", TargetStrategy.REPORTED_DECLARATION),
        WatchdogChecks.MANGLED_JVM_NAME_PUBLIC_API to fixable("IntentionallyMangledJvmName", TargetStrategy.ENCLOSING_CALLABLE),
        WatchdogChecks.KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC to fixable("IntentionallyKotlinOnlyApi", TargetStrategy.REPORTED_DECLARATION),
        WatchdogChecks.COMPANION_API_WITHOUT_JVM_STATIC to fixable("IntentionallyNonStaticCompanionApi", TargetStrategy.REPORTED_DECLARATION),
        WatchdogChecks.COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS to fixable("IntentionallyNonStaticCompanionApi", TargetStrategy.REPORTED_DECLARATION),
        WatchdogChecks.TOP_LEVEL_API_WITHOUT_JVM_NAME to fixable("IntentionallyDefaultFacadeName", TargetStrategy.CONTAINING_FILE),
        WatchdogChecks.DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS to fixable("IntentionallyWithoutJvmOverloads", TargetStrategy.ENCLOSING_FUNCTION_OR_CONSTRUCTOR),

        WatchdogChecks.DSL_MARKER_NOOP_TARGET to fixable("IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility", TargetStrategy.ENCLOSING_ANNOTATION_CLASS, hasReasonParameter = false),
        WatchdogChecks.DSL_MARKER_WITHOUT_EXPLICIT_TARGETS to fixable("IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility", TargetStrategy.ENCLOSING_ANNOTATION_CLASS, hasReasonParameter = false),

        // Documentation isn't a compatibility concern, so a placeholder KDoc replaces the exemption.
        WatchdogChecks.UNDOCUMENTED_PUBLIC_API to FixResolution.Fixable(PlaceholderKdocFix),
        WatchdogChecks.EXEMPTION_WITHOUT_EXPLANATION to FixResolution.Unfixable("The existing exemption needs a reason or description only its author can write"),
        WatchdogChecks.DSL_MARKER_NOOP_TYPE_POSITION to FixResolution.Unfixable("No exemption annotation exists. Move the DSL marker to an effective position or remove it"),
        WatchdogChecks.PUBLIC_TYPE_WITH_INTERNAL_API to FixResolution.Unfixable("Remove the internal type from the public signature or mark the exposing declaration as internal API"),
    )

    /** A declaration that collects every part of a group gets the combined annotation instead. */
    val combinedExemptions: Map<String, Set<String>> = mapOf(
        "IntentionallyWithoutEqualsHashCodeOrToString" to setOf(
            "IntentionallyWithoutEquals",
            "IntentionallyWithoutHashCode",
            "IntentionallyWithoutToString",
        ),
    )

    val knownDiagnostics: Set<String> get() = resolutions.keys

    /** Each annotation the fixer can add, and whether it takes a `reason` argument. */
    val exemptionAnnotations: Map<String, Boolean> = resolutions.values
        .filterIsInstance<FixResolution.Fixable>()
        .map { it.fix }
        .filterIsInstance<ExemptionFix>()
        .associate { it.annotationShortName to it.hasReasonParameter } +
            combinedExemptions.keys.associateWith { true }

    fun annotationFor(diagnostic: String): String? =
        ((resolutions[diagnostic] as? FixResolution.Fixable)?.fix as? ExemptionFix)?.annotationShortName

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
