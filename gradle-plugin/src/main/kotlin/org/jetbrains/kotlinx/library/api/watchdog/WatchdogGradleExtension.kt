package org.jetbrains.kotlinx.library.api.watchdog

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty

/**
 * The `apiWatchdog` block: the [enabled] switch and the severity of each configurable diagnostic.
 * Default severity is [WatchdogSeverity.ERROR]. A property with another default states it.
 *
 * ```kotlin
 * apiWatchdog {
 *     undocumentedPublicApi = WatchdogSeverity.WARNING
 *     dataClassPublicApi = WatchdogSeverity.NONE
 * }
 * ```
 */
public open class WatchdogGradleExtension @Inject internal constructor(
    objectFactory: ObjectFactory,
) {
    private val annotationIgnoreRules: ListProperty<String> =
        objectFactory.listProperty(String::class.java).convention(emptyList())

    /** Whether the watchdog runs. Default is `true`. */
    public val enabled: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

    /** Severity of `OPEN_API_WITHOUT_SUBCLASS_OPT_IN`: unrestricted external subclassing. */
    public val openApiWithoutSubclassOptIn: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `SUBCLASS_OPT_IN_WITHOUT_MARKERS`: `@SubclassOptInRequired` with no markers. */
    public val subclassOptInWithoutMarkers: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `EXHAUSTIVE_PUBLIC_API`: exhaustively matchable enums and sealed hierarchies. */
    public val exhaustivePublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `UNDOCUMENTED_PUBLIC_API`: public declarations without KDoc. Default is [WatchdogSeverity.WARNING]. */
    public val undocumentedPublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty(WatchdogSeverity.WARNING)

    /** Severity of `FUNCTION_TYPE_ALIAS_PUBLIC_API`: type aliases that abbreviate function types. */
    public val functionTypeAliasPublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `DATA_CLASS_PUBLIC_API`: data classes in the public API. */
    public val dataClassPublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `STATEFUL_CLASS_WITHOUT_EQUALS`: stateful classes without an `equals` implementation. */
    public val statefulClassWithoutEquals: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `STATEFUL_CLASS_WITHOUT_HASH_CODE`: stateful classes without a `hashCode` implementation. */
    public val statefulClassWithoutHashCode: Property<WatchdogSeverity> = objectFactory.severityProperty()

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

    /** Severity of `INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS`: overloads with different positions for shared parameters. */
    public val inconsistentParameterOrderInOverloads: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `INLINE_FUNCTION_WITH_LOGIC`: inline functions and accessors doing more than delegating. */
    public val inlineFunctionWithLogic: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `DSL_MARKER_NOOP_TARGET`: DSL marker targets without scope-control effect. */
    public val dslMarkerNoopTarget: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `DSL_MARKER_WITHOUT_EXPLICIT_TARGETS`: DSL markers with the default target set. */
    public val dslMarkerWithoutExplicitTargets: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `DSL_MARKER_NOOP_TYPE_POSITION`: DSL markers on type positions without effect. */
    public val dslMarkerNoopTypePosition: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Whether `PUBLIC_TYPE_WITH_INTERNAL_API` runs. Default is `true`. A violation is always an error. */
    public val publicTypeWithInternalApi: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(true)

    /**
     * Fully qualified names of annotation classes that mark internal API, besides the classes
     * that carry `@InternalAnnotationMarker`. The watchdog doesn't check a declaration
     * under such an annotation, and `PUBLIC_TYPE_WITH_INTERNAL_API` reports its type. Use it for
     * the internal-API annotation of a dependency:
     *
     * ```kotlin
     * apiWatchdog {
     *     internalApiAnnotations.add("com.example.dependency.InternalApi")
     * }
     * ```
     */
    public val internalApiAnnotations: SetProperty<String> =
        objectFactory.setProperty(String::class.java).convention(emptySet())

    /**
     * Whether `PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY` runs. Default is `true`. A violation is
     * always an error. To accept the types of selected dependencies, declare them on the
     * `apiWatchdogExemptNonTransitive` configuration instead.
     */
    public val publicTypesMustBeTransitiveDependencies: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(true)

    /**
     * Whether the build warns when neither the Kotlin Gradle plugin's
     * [ABI validation](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html)
     * nor the [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator)
     * plugin is active. Default is `true`.
     */
    public val suggestAbiValidation: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

    /** The Java-interop diagnostics, see [WatchdogJavaInteropExtension]. */
    public val javaInterop: WatchdogJavaInteropExtension =
        objectFactory.newInstance(WatchdogJavaInteropExtension::class.java)

    /** Configures [javaInterop]. */
    public fun javaInterop(action: Action<WatchdogJavaInteropExtension>) {
        action.execute(javaInterop)
    }

    /**
     * The formats of the report that `generateApiWatchdogReport` and `exemptExistingApi` write,
     * see [WatchdogReports]. Both formats are on by default.
     */
    public val reports: WatchdogReports = objectFactory.newInstance(WatchdogReports::class.java)

    /** Configures [reports]. */
    public fun reports(action: Action<WatchdogReports>) {
        action.execute(reports)
    }

    /**
     * Ignores [check] on a declaration that carries one of the [whenAnnotatedWith] annotations.
     * [check] must have a severity property in this block or in [javaInterop]. An always-error
     * diagnostic has no severity property, so `ignore` rejects it. An annotation is its fully
     * qualified class name:
     *
     * ```kotlin
     * import org.jetbrains.kotlinx.library.api.watchdog.WatchdogChecks
     *
     * apiWatchdog {
     *     ignore(
     *         WatchdogChecks.STATEFUL_CLASS_WITHOUT_EQUALS,
     *         whenAnnotatedWith = listOf(
     *             "com.example.GeneratedValue",
     *             "com.example.GeneratedEntity",
     *         ),
     *     )
     * }
     * ```
     *
     * Call it again for another check.
     */
    public fun ignore(check: WatchdogChecks, whenAnnotatedWith: List<String>) {
        require(check in diagnosticSeverities) {
            "API Watchdog check '$check' is always an error and accepts no ignore rule"
        }
        require(whenAnnotatedWith.isNotEmpty()) {
            "API Watchdog check '$check' needs at least one annotation name"
        }
        whenAnnotatedWith.forEach { annotationName ->
            require(annotationName.isNotBlank() && ':' !in annotationName) {
                "The annotation name must be a non-blank fully qualified class name: '$annotationName'"
            }
            annotationIgnoreRules.add("${check.name}:$annotationName")
        }
    }

    internal fun annotationIgnoreRules(): Provider<List<String>> = annotationIgnoreRules

    internal val diagnosticSeverities: Map<WatchdogChecks, Provider<WatchdogSeverity>> = mapOf(
        WatchdogChecks.OPEN_API_WITHOUT_SUBCLASS_OPT_IN to openApiWithoutSubclassOptIn,
        WatchdogChecks.SUBCLASS_OPT_IN_WITHOUT_MARKERS to subclassOptInWithoutMarkers,
        WatchdogChecks.EXHAUSTIVE_PUBLIC_API to exhaustivePublicApi,
        WatchdogChecks.UNDOCUMENTED_PUBLIC_API to undocumentedPublicApi,
        WatchdogChecks.FUNCTION_TYPE_ALIAS_PUBLIC_API to functionTypeAliasPublicApi,
        WatchdogChecks.DATA_CLASS_PUBLIC_API to dataClassPublicApi,
        WatchdogChecks.STATEFUL_CLASS_WITHOUT_EQUALS to statefulClassWithoutEquals,
        WatchdogChecks.STATEFUL_CLASS_WITHOUT_HASH_CODE to statefulClassWithoutHashCode,
        WatchdogChecks.STATEFUL_CLASS_WITHOUT_TO_STRING to statefulClassWithoutToString,
        WatchdogChecks.MUTABLE_COLLECTION_PUBLIC_API to mutableCollectionPublicApi,
        WatchdogChecks.PAIR_OR_TRIPLE_PUBLIC_API to pairOrTriplePublicApi,
        WatchdogChecks.BOOLEAN_PARAMETER_PUBLIC_API to booleanParameterPublicApi,
        WatchdogChecks.NULLABLE_BOOLEAN_PUBLIC_API to nullableBooleanPublicApi,
        WatchdogChecks.REQUIRED_PARAMETER_AFTER_OPTIONAL to requiredParameterAfterOptional,
        WatchdogChecks.INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS to inconsistentParameterOrderInOverloads,
        WatchdogChecks.INLINE_FUNCTION_WITH_LOGIC to inlineFunctionWithLogic,
        WatchdogChecks.MANGLED_JVM_NAME_PUBLIC_API to javaInterop.effectiveSeverity { mangledJvmNamePublicApi },
        WatchdogChecks.KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC to javaInterop.effectiveSeverity { kotlinOnlyApiWithoutJvmSynthetic },
        WatchdogChecks.COMPANION_API_WITHOUT_JVM_STATIC to javaInterop.effectiveSeverity { companionApiWithoutJvmStatic },
        WatchdogChecks.COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS to javaInterop.effectiveSeverity { companionPropertyWithoutStaticAccess },
        WatchdogChecks.TOP_LEVEL_API_WITHOUT_JVM_NAME to javaInterop.effectiveSeverity { topLevelApiWithoutJvmName },
        WatchdogChecks.DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS to javaInterop.effectiveSeverity { defaultParametersWithoutJvmOverloads },
        WatchdogChecks.DSL_MARKER_NOOP_TARGET to dslMarkerNoopTarget,
        WatchdogChecks.DSL_MARKER_WITHOUT_EXPLICIT_TARGETS to dslMarkerWithoutExplicitTargets,
        WatchdogChecks.DSL_MARKER_NOOP_TYPE_POSITION to dslMarkerNoopTypePosition,
    )
}

/**
 * The `javaInterop` block of [WatchdogGradleExtension]: the Java-interop diagnostics. They
 * matter only for a library with Java consumers, so a Kotlin-only library turns them all off:
 *
 * ```kotlin
 * apiWatchdog {
 *     javaInterop {
 *         enabled = false
 *     }
 * }
 * ```
 */
public open class WatchdogJavaInteropExtension @Inject internal constructor(objectFactory: ObjectFactory) {
    /** Whether the Java-interop diagnostics run. Default is `true`. `false` wins over the severities. */
    public val enabled: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

    /** Severity of `MANGLED_JVM_NAME_PUBLIC_API`: value classes mangling public JVM signatures. */
    public val mangledJvmNamePublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC`: Kotlin-only shapes visible to Java. */
    public val kotlinOnlyApiWithoutJvmSynthetic: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `COMPANION_API_WITHOUT_JVM_STATIC`: companion functions Java reaches through the instance. */
    public val companionApiWithoutJvmStatic: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS`: non-static companion property accessors. */
    public val companionPropertyWithoutStaticAccess: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `TOP_LEVEL_API_WITHOUT_JVM_NAME`: file facades that show the file name to Java. */
    public val topLevelApiWithoutJvmName: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS`: defaults that don't exist for Java callers. */
    public val defaultParametersWithoutJvmOverloads: Property<WatchdogSeverity> = objectFactory.severityProperty()

    internal fun effectiveSeverity(
        severity: WatchdogJavaInteropExtension.() -> Property<WatchdogSeverity>,
    ): Provider<WatchdogSeverity> = enabled.zip(severity()) { on, configured ->
        if (on) configured else WatchdogSeverity.NONE
    }
}

private fun ObjectFactory.severityProperty(default: WatchdogSeverity = WatchdogSeverity.ERROR): Property<WatchdogSeverity> =
    property(WatchdogSeverity::class.java).convention(default)
