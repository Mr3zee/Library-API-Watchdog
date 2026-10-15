package org.jetbrains.kotlinx.library.api.watchdog

/**
 * Why an exemption annotation is applied.
 *
 * [FOR_BACKWARDS_COMPATIBILITY], [API_DESIGN], and [FOR_DEPRECATION] explain an exemption on their
 * own. Every other reason needs a `description` that names the constraint, or the compiler reports
 * `EXEMPTION_WITHOUT_EXPLANATION`.
 *
 * See [Exemptions and internal API](https://mr3zee.github.io/Library-API-Watchdog/exemptions) for more details.
 */
public enum class ExemptionReason {
    /** The shape stays for compatibility with existing users. */
    FOR_BACKWARDS_COMPATIBILITY,

    /** The shape is part of the API design. */
    API_DESIGN,

    /** The declaration is deprecated or soon to be deprecated, so no evolution is planned for it. */
    FOR_DEPRECATION,

    /**
     * Interoperability with another language, platform, or framework needs the shape. Name it in
     * the `description`.
     */
    INTEROP,

    /**
     * The shape mirrors a specification, a protocol, or a closed real-world domain. Name it in the
     * `description`.
     */
    EXTERNAL_CONTRACT,

    /**
     * The declaration ignores Java callers. Explain why in the `description`. A library without Java
     * callers can disable the Java interop checks in its build configuration instead.
     */
    IGNORE_JAVA_INTEROP,

    /** None of the other reasons fits. The default. Explain the exemption in the `description`. */
    OTHER,
}

/**
 * Exempts the annotated class or interface from
 * [Open API without subclass opt-in](https://mr3zee.github.io/Library-API-Watchdog/checks/open-api-without-subclass-opt-in).
 *
 * Apply it when unrestricted subclassing outside the library is part of the API contract.
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyOpen(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated enum or sealed hierarchy from
 * [Exhaustive public API](https://mr3zee.github.io/Library-API-Watchdog/checks/exhaustive-public-api).
 *
 * Apply it when the set of entries or subtypes is a stable part of the API contract.
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyExhaustive(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated declaration from
 * [Undocumented public API](https://mr3zee.github.io/Library-API-Watchdog/checks/undocumented-public-api).
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyUndocumented(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated type alias from
 * [Function type aliases in public API](https://mr3zee.github.io/Library-API-Watchdog/checks/function-type-alias-public-api).
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(AnnotationTarget.TYPEALIAS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyFunctionTypeAlias(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated data class from
 * [Data classes in public API](https://mr3zee.github.io/Library-API-Watchdog/checks/data-class-public-api).
 *
 * Apply it when the generated declarations are a stable part of the API contract.
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyDataClass(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated class from `STATEFUL_CLASS_WITHOUT_EQUALS`, one of the checks of
 * [Stateful classes without equals, hashCode, and toString](https://mr3zee.github.io/Library-API-Watchdog/checks/stateful-class-without-equals-hashcode-to-string).
 *
 * Apply it when identity equality is intended.
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyWithoutEquals(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated class from `STATEFUL_CLASS_WITHOUT_HASH_CODE`, one of the checks of
 * [Stateful classes without equals, hashCode, and toString](https://mr3zee.github.io/Library-API-Watchdog/checks/stateful-class-without-equals-hashcode-to-string).
 *
 * Apply it when identity hashing is intended.
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyWithoutHashCode(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated class from `STATEFUL_CLASS_WITHOUT_TO_STRING`, one of the checks of
 * [Stateful classes without equals, hashCode, and toString](https://mr3zee.github.io/Library-API-Watchdog/checks/stateful-class-without-equals-hashcode-to-string).
 *
 * Apply it when the default `toString` output is intended.
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyWithoutToString(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated class from all three checks of
 * [Stateful classes without equals, hashCode, and toString](https://mr3zee.github.io/Library-API-Watchdog/checks/stateful-class-without-equals-hashcode-to-string).
 *
 * Use [IntentionallyWithoutEquals], [IntentionallyWithoutHashCode], or
 * [IntentionallyWithoutToString] when only one inherited implementation is intended.
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyWithoutEqualsHashCodeOrToString(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated declaration, parameter, or type usage from
 * [Mutable collections in public API](https://mr3zee.github.io/Library-API-Watchdog/checks/mutable-collection-public-api).
 *
 * Apply it when the shared mutable collection is part of the API contract.
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE_PARAMETER,
    AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyMutableCollection(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated declaration, parameter, or type usage from
 * [Pair and Triple in public API](https://mr3zee.github.io/Library-API-Watchdog/checks/pair-or-triple-public-api).
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE_PARAMETER,
    AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyPairOrTriple(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated function or parameter from
 * [Boolean parameters in public API](https://mr3zee.github.io/Library-API-Watchdog/checks/boolean-parameter-public-api).
 *
 * Apply it when the function name makes the parameter unmistakable, as in
 * `setEnabled(enabled: Boolean)`.
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyBooleanParameter(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated declaration, parameter, or type usage from
 * [Nullable Booleans in public API](https://mr3zee.github.io/Library-API-Watchdog/checks/nullable-boolean-public-api).
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE_PARAMETER,
    AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyNullableBoolean(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated function or constructor from
 * [Required parameters after optional ones](https://mr3zee.github.io/Library-API-Watchdog/checks/required-parameter-after-optional).
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyRequiredParameterAfterOptional(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated function or constructor from
 * [Inconsistent parameter order in overloads](https://mr3zee.github.io/Library-API-Watchdog/checks/inconsistent-parameter-order-in-overloads).
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyInconsistentParameterOrder(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated inline function, or the inline accessors of the annotated property, from
 * [Inline functions with logic](https://mr3zee.github.io/Library-API-Watchdog/checks/inline-function-with-logic).
 *
 * Apply it when a lambda must run inline for non-local returns, or when a hot path must avoid a
 * call.
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyInlinedLogic(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated declaration from
 * [Mangled JVM names in public API](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/mangled-jvm-name-public-api).
 * On a class, it covers every nested declaration.
 *
 * Apply it when the declaration is Kotlin-only. Otherwise, give it a Java-callable shape with
 * `@JvmName` or `@JvmExposeBoxed`.
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyMangledJvmName(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated function from
 * [Kotlin-only API without JvmSynthetic](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/kotlin-only-api-without-jvm-synthetic).
 * On a class, it covers every function in the class.
 *
 * Apply it when Java sources must see the Kotlin-only function. Otherwise, hide it with
 * `@JvmSynthetic`, or add a Java-friendly alternative.
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyKotlinOnlyApi(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated companion object member from
 * [Companion function without JvmStatic](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/companion-api-without-jvm-static)
 * and
 * [Companion property without static access](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/companion-property-without-static-access).
 * On a companion object or a class, it covers every member.
 *
 * Apply it when Java callers must use the companion instance. Otherwise, add static access
 * with `@JvmStatic`, `@JvmField`, or `const val`, or hide the member from Java with `@JvmSynthetic`.
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyNonStaticCompanionApi(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated file from
 * [Top-level API without JvmName](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/top-level-api-without-jvm-name).
 *
 * Apply it as `@file:IntentionallyDefaultFacadeName(...)`.
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyDefaultFacadeName(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated function or constructor from
 * [Default parameters without JvmOverloads](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/default-parameters-without-jvm-overloads).
 *
 * Apply it when Java callers must pass every argument.
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyWithoutJvmOverloads(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated declaration, parameter, or type usage from
 * [Public types from non-transitive dependencies](https://mr3zee.github.io/Library-API-Watchdog/checks/special/public-type-from-non-transitive-dependency).
 * On a class, it covers every nested declaration.
 *
 * Apply it when the environment that loads the library has the dependency on its classpath. A
 * Gradle plugin is the common case: it compiles against `gradleApi()` and runs inside Gradle.
 * Name the environment in [description].
 *
 * To exempt every type of the dependency, declare it on the `apiWatchdogExemptNonTransitive`
 * configuration instead
 * ([Exempting a dependency](https://mr3zee.github.io/Library-API-Watchdog/checks/special/public-type-from-non-transitive-dependency#exempting-a-dependency)):
 *
 * ```kotlin
 * dependencies {
 *     implementation("com.example:models:1.0.0")
 *     apiWatchdogExemptNonTransitive("com.example:models")
 * }
 * ```
 *
 * @param description Why the exemption is applied. Can be empty when [reason] is
 *   [ExemptionReason.FOR_BACKWARDS_COMPATIBILITY] or [ExemptionReason.API_DESIGN].
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE_PARAMETER,
    AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyNonTransitiveDependency(
    val description: String = "",
    val reason: ExemptionReason = ExemptionReason.OTHER,
)

/**
 * Exempts the annotated DSL marker from
 * [DSL markers with no-op targets](https://mr3zee.github.io/Library-API-Watchdog/checks/special/dsl-marker-noop-target)
 * and
 * [DSL markers without explicit targets](https://mr3zee.github.io/Library-API-Watchdog/checks/special/dsl-marker-without-explicit-targets).
 *
 * Apply it to a published marker, because a change of its targets breaks user code. A new marker
 * must declare effective targets. Backwards compatibility is the only accepted reason, so the
 * annotation has no [ExemptionReason].
 *
 * @param description Optional context for the exemption.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility(
    val description: String = "",
)

/**
 * Makes the annotated annotation class an internal API marker. Declarations annotated with the
 * marked annotation, and everything nested in them, are exempt from the public API checks.
 * `EXEMPTION_WITHOUT_EXPLANATION` and the DSL marker checks still apply to them. The marked
 * annotation class itself is still checked.
 *
 * Apply it to an annotation that marks declarations which are public for technical reasons but
 * not supported API (see [Shared internals](https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0451-shared-internals.md)).
 *
 * See [Exemptions and internal API](https://mr3zee.github.io/Library-API-Watchdog/exemptions) for more details.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class InternalAnnotationMarker
