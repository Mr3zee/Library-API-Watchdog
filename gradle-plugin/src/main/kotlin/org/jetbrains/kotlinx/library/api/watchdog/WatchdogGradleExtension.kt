package org.jetbrains.kotlinx.library.api.watchdog

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty

/**
 * Configures the severity of each configurable watchdog diagnostic. Every configurable
 * diagnostic is reported as an error unless demoted to [WatchdogSeverity.WARNING] or disabled
 * with [WatchdogSeverity.NONE] here:
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
public open class WatchdogGradleExtension @Inject constructor(
    /**
     * The `apiWatchdogExemptNonTransitive` configuration: dependencies whose types
     * `PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY` accepts in public signatures although the
     * module doesn't publish them transitively. Use it when the environment that loads the
     * library supplies a dependency on its own, so every declaration mentioning its types would
     * otherwise need an `@IntentionallyNonTransitiveDependency` exemption: a Gradle plugin
     * compiled against `compileOnly` plugin APIs, an IDE plugin, or a library whose host
     * application ships a framework.
     *
     * Declare dependencies on it like on any other configuration, with coordinates, a version
     * catalog entry, a project, or `gradleApi()`. A version is optional, the one on the compile
     * classpath is used. Everything a listed dependency pulls onto the compile classpath is
     * exempt with it, so a Kotlin Multiplatform library is covered together with its platform
     * artifact. Exempt a whole configuration by extending from it:
     *
     * ```kotlin
     * dependencies {
     *     apiWatchdogExemptNonTransitive("org.jetbrains.kotlin:kotlin-gradle-plugin")
     *     apiWatchdogExemptNonTransitive(libs.host.api)
     *     apiWatchdogExemptNonTransitive(project(":host-api"))
     * }
     *
     * apiWatchdog {
     *     exemptNonTransitiveDependencies {
     *         extendsFrom(configurations.compileOnly.get())
     *     }
     * }
     * ```
     *
     * The configuration is a marker only: it adds nothing to the compile classpath, and the
     * dependency itself is still declared as `implementation` or `compileOnly`. Types from any
     * other non-transitive dependency are still reported. [publicTypesMustBeTransitiveDependencies]
     * remains the whole-check switch.
     */
    public val exemptNonTransitiveDependencies: NamedDomainObjectProvider<Configuration>,
    objectFactory: ObjectFactory,
) {
    private val annotationIgnoreRules: ListProperty<String> =
        objectFactory.listProperty(String::class.java).convention(emptyList())

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
     * Whether `PUBLIC_TYPE_WITH_INTERNAL_API` is checked. Enabled by default. A violation is
     * always an error when enabled. This Boolean property is the check's only off-switch.
     */
    public val publicTypeWithInternalApi: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(true)

    /**
     * Fully qualified names of annotation classes that mark internal API in addition to the
     * annotation classes carrying `@InternalAnnotationMarker`. A declaration annotated with one
     * of them, directly or through an enclosing declaration, is not watched, and
     * `PUBLIC_TYPE_WITH_INTERNAL_API` reports its type like any other internal-API type.
     *
     * Use it for annotations the library can't mark itself, typically the internal-API annotation
     * of a dependency:
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
     * Whether public signatures may only expose types from dependencies published transitively
     * to consumers. Enabled by default. Unlike design diagnostics, a violation is always an error
     * and has no warning severity. Setting this Gradle property to `false` is the check's only
     * off-switch. To keep the check and accept the types of selected dependencies, declare them on
     * [exemptNonTransitiveDependencies] instead.
     */
    public val publicTypesMustBeTransitiveDependencies: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(true)

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

    /**
     * Ignores [check] on a declaration carrying any annotation identified by
     * [whenAnnotatedWith]. The check is named by its diagnostic name, which [WatchdogChecks]
     * holds as constants, and the annotations by their fully qualified class names:
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
     * Call this method repeatedly to associate the annotations with several checks. Only
     * configurable checks support annotation ignore rules. The diagnostics documented as always
     * errors can't be ignored.
     */
    public fun ignore(check: String, whenAnnotatedWith: List<String>) {
        val knownChecks = diagnosticSeverities.keys
        require(check in knownChecks) {
            "Unknown API Watchdog check '$check'. Known configurable checks: ${knownChecks.joinToString()}"
        }
        require(whenAnnotatedWith.isNotEmpty()) {
            "At least one annotation name must be supplied for API Watchdog check '$check'"
        }
        whenAnnotatedWith.forEach { annotationName ->
            require(annotationName.isNotBlank() && ':' !in annotationName) {
                "The annotation name must be a non-blank fully qualified class name: '$annotationName'"
            }
            annotationIgnoreRules.add("$check:$annotationName")
        }
    }

    internal fun annotationIgnoreRules(): Provider<List<String>> = annotationIgnoreRules

    internal val diagnosticSeverities: Map<String, Provider<WatchdogSeverity>> = mapOf(
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
 * Exempts [dependency] from `PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`, so its types may appear in
 * the public API although it is not published transitively. Call it from the
 * declaration that adds it to a compilation. Inside the configuration block of a dependency,
 * `this` is the dependency being declared:
 *
 * ```kotlin
 * dependencies {
 *     compileOnly(libs.host.api) { exemptNonTransitiveDependency(this) }
 *     implementation(project(":host-api")) { exemptNonTransitiveDependency(this) }
 * }
 * ```
 *
 * Equivalent to declaring the same dependency on `apiWatchdogExemptNonTransitive` a second
 * time, see [WatchdogGradleExtension.exemptNonTransitiveDependencies]. The watchdog plugin must
 * be applied before the `dependencies` block runs.
 */
public fun DependencyHandler.exemptNonTransitiveDependency(dependency: Dependency) {
    try {
        add(WatchdogSupportPlugin.EXEMPT_NON_TRANSITIVE_CONFIGURATION_NAME, dependency)
    } catch (e: UnknownConfigurationException) {
        throw GradleException(
            """
            Apply the watchdog plugin before calling exemptNonTransitiveDependency.

              plugins {
                  id("${PluginInfo.PLUGIN_ID}")
              }
            """.trimIndent(),
            e,
        )
    }
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

    /** Severity of `COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS`: non-static companion property accessors. */
    public val companionPropertyWithoutStaticAccess: Property<WatchdogSeverity> = objectFactory.severityProperty()

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
