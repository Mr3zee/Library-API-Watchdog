package org.jetbrains.kotlinx.libs.api.watchdog

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Configures the severity of each watchdog diagnostic. Every diagnostic is reported as an error
 * unless demoted to [WatchdogSeverity.WARNING] or disabled with [WatchdogSeverity.NONE] here:
 *
 * ```kotlin
 * apiWatchdog {
 *     undocumentedPublicApi = WatchdogSeverity.WARNING
 *     dataClassPublicApi = WatchdogSeverity.NONE
 * }
 * ```
 *
 * The Java-interop diagnostics live in the [javaInterop] group. They only pay off for libraries
 * with Java consumers, so the group has one off-switch - a Kotlin-only library disables all of
 * them at once, no matter what the individual severities say:
 *
 * ```kotlin
 * apiWatchdog {
 *     javaInterop {
 *         enabled = false
 *     }
 * }
 * ```
 */
public open class WatchdogGradleExtension(objectFactory: ObjectFactory) {
    /** Severity of `OPEN_API_WITHOUT_SUBCLASS_OPT_IN`: unrestricted external subclassing. */
    public val openApiWithoutSubclassOptIn: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `SUBCLASS_OPT_IN_WITHOUT_MARKERS`: `@SubclassOptInRequired` with no markers. */
    public val subclassOptInWithoutMarkers: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `EXHAUSTIVE_PUBLIC_API`: exhaustively matchable enums and sealed hierarchies. */
    public val exhaustivePublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `UNDOCUMENTED_PUBLIC_API`: public declarations without KDoc. */
    public val undocumentedPublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `FUNCTION_TYPE_ALIAS_PUBLIC_API`: type aliases that abbreviate function types. */
    public val functionTypeAliasPublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `DATA_CLASS_PUBLIC_API`: data classes in the public API. */
    public val dataClassPublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `STATEFUL_CLASS_WITHOUT_TO_STRING`: stateful classes without a `toString` implementation. */
    public val statefulClassWithoutToString: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `MUTABLE_COLLECTION_PUBLIC_API`: mutable collections and arrays in public signatures. */
    public val mutableCollectionPublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `PAIR_OR_TRIPLE_PUBLIC_API`: `Pair` and `Triple` in public signatures. */
    public val pairOrTriplePublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `BOOLEAN_PARAMETER_PUBLIC_API`: Boolean parameters of public functions. */
    public val booleanParameterPublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `NULLABLE_BOOLEAN_PUBLIC_API`: nullable Booleans in public signatures. */
    public val nullableBooleanPublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `REQUIRED_PARAMETER_AFTER_OPTIONAL`: required parameters declared after optional ones. */
    public val requiredParameterAfterOptional: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS`: overloads disagreeing on shared parameter order. */
    public val inconsistentParameterOrderInOverloads: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `INLINE_FUNCTION_WITH_LOGIC`: inline functions and accessors doing more than delegating. */
    public val inlineFunctionWithLogic: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `DSL_MARKER_NOOP_TARGET`: DSL marker targets without scope-control effect. */
    public val dslMarkerNoopTarget: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `DSL_MARKER_WITHOUT_EXPLICIT_TARGETS`: DSL markers with the default target set. */
    public val dslMarkerWithoutExplicitTargets: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `DSL_MARKER_NOOP_TYPE_POSITION`: DSL markers on type positions without effect. */
    public val dslMarkerNoopTypePosition: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /**
     * Whether to suggest applying the [Tapmoc](https://gradleup.com/tapmoc/) Gradle plugin
     * (`com.gradleup.tapmoc`) when it is missing. Tapmoc pins the Java and Kotlin compatibility
     * levels a library is built against - the complement of watching the API shape - so the
     * plugin recommends it with a build warning. `true` by default; set to `false` to silence
     * the suggestion.
     */
    public val suggestTapmoc: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

    /**
     * Whether to suggest enabling binary compatibility validation when neither the Kotlin Gradle
     * plugin's built-in
     * [ABI validation](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html)
     * nor the standalone
     * [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator)
     * plugin is active. The watchdog reviews the shape of new API declarations, while binary
     * compatibility validation guards the API that already shipped, so the plugin recommends it
     * with a build warning. `true` by default, set to `false` to silence the suggestion.
     */
    public val suggestAbiValidation: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

    /** The Java-interop diagnostic group: its off-switch and the individual severities. */
    public val javaInterop: WatchdogJavaInteropExtension =
        objectFactory.newInstance(WatchdogJavaInteropExtension::class.java)

    /** Configures the [javaInterop] diagnostic group. */
    public fun javaInterop(action: Action<WatchdogJavaInteropExtension>) {
        action.execute(javaInterop)
    }

    internal fun diagnosticSeverities(): Map<String, Provider<WatchdogSeverity>> = mapOf(
        "OPEN_API_WITHOUT_SUBCLASS_OPT_IN" to openApiWithoutSubclassOptIn,
        "SUBCLASS_OPT_IN_WITHOUT_MARKERS" to subclassOptInWithoutMarkers,
        "EXHAUSTIVE_PUBLIC_API" to exhaustivePublicApi,
        "UNDOCUMENTED_PUBLIC_API" to undocumentedPublicApi,
        "FUNCTION_TYPE_ALIAS_PUBLIC_API" to functionTypeAliasPublicApi,
        "DATA_CLASS_PUBLIC_API" to dataClassPublicApi,
        "STATEFUL_CLASS_WITHOUT_TO_STRING" to statefulClassWithoutToString,
        "MUTABLE_COLLECTION_PUBLIC_API" to mutableCollectionPublicApi,
        "PAIR_OR_TRIPLE_PUBLIC_API" to pairOrTriplePublicApi,
        "BOOLEAN_PARAMETER_PUBLIC_API" to booleanParameterPublicApi,
        "NULLABLE_BOOLEAN_PUBLIC_API" to nullableBooleanPublicApi,
        "REQUIRED_PARAMETER_AFTER_OPTIONAL" to requiredParameterAfterOptional,
        "INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS" to inconsistentParameterOrderInOverloads,
        "INLINE_FUNCTION_WITH_LOGIC" to inlineFunctionWithLogic,
        "MANGLED_JVM_NAME_PUBLIC_API" to javaInterop.effectiveSeverity { mangledJvmNamePublicApi },
        "KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC" to javaInterop.effectiveSeverity { kotlinOnlyApiWithoutJvmSynthetic },
        "COMPANION_API_WITHOUT_JVM_STATIC" to javaInterop.effectiveSeverity { companionApiWithoutJvmStatic },
        "COMPANION_CONSTANT_WITHOUT_JVM_FIELD" to javaInterop.effectiveSeverity { companionConstantWithoutJvmField },
        "TOP_LEVEL_API_WITHOUT_JVM_NAME" to javaInterop.effectiveSeverity { topLevelApiWithoutJvmName },
        "DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS" to javaInterop.effectiveSeverity { defaultParametersWithoutJvmOverloads },
        "DSL_MARKER_NOOP_TARGET" to dslMarkerNoopTarget,
        "DSL_MARKER_WITHOUT_EXPLICIT_TARGETS" to dslMarkerWithoutExplicitTargets,
        "DSL_MARKER_NOOP_TYPE_POSITION" to dslMarkerNoopTypePosition,
    )
}

/**
 * The Java-interop diagnostic group of [WatchdogGradleExtension]. The [enabled] off-switch
 * disables every diagnostic of the group at once, regardless of the individual severities.
 */
public open class WatchdogJavaInteropExtension @Inject constructor(objectFactory: ObjectFactory) {
    /**
     * Whether the Java-interop diagnostics run at all. `true` by default. A library with a
     * Kotlin-only audience sets it to `false` instead of disabling the six diagnostics one by
     * one - the switch wins over the individual severities.
     */
    public val enabled: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

    /** Severity of `MANGLED_JVM_NAME_PUBLIC_API`: value classes mangling public JVM signatures. */
    public val mangledJvmNamePublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC`: Kotlin-only shapes visible to Java. */
    public val kotlinOnlyApiWithoutJvmSynthetic: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `COMPANION_API_WITHOUT_JVM_STATIC`: companion functions Java reaches through the instance. */
    public val companionApiWithoutJvmStatic: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `COMPANION_CONSTANT_WITHOUT_JVM_FIELD`: companion constants Java reads through the instance. */
    public val companionConstantWithoutJvmField: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `TOP_LEVEL_API_WITHOUT_JVM_NAME`: file facades leaking the file name to Java. */
    public val topLevelApiWithoutJvmName: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS`: defaults that don't exist for Java callers. */
    public val defaultParametersWithoutJvmOverloads: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** The severity the compiler plugin sees: the diagnostic's own, or NONE when switched off. */
    internal fun effectiveSeverity(
        severity: WatchdogJavaInteropExtension.() -> Property<WatchdogSeverity>,
    ): Provider<WatchdogSeverity> = enabled.zip(severity()) { on, configured ->
        if (on) configured else WatchdogSeverity.NONE
    }
}

private fun ObjectFactory.severityProperty(): Property<WatchdogSeverity> =
    property(WatchdogSeverity::class.java).convention(WatchdogSeverity.ERROR)
