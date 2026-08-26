package org.jetbrains.kotlinx.library.api.watchdog

/**
 * Explains why a watchdog exemption annotation is applied.
 *
 * Every exemption annotation in this package carries a `reason` and a free-form `description`.
 * The compiler plugin requires the explanation to be meaningful: the description
 * may be left empty only when the reason explains the exemption on its own
 * ([FOR_BACKWARDS_COMPATIBILITY], [API_DESIGN]). The other reasons only categorize the exemption
 * and keep the description shorter - the specific constraint still has to be spelled out there.
 *
 * See [Exemptions and internal API](https://mr3zee.github.io/Library-API-Watchdog/exemptions) for the full explanation of how reasons and descriptions are validated.
 */
public enum class ExemptionReason {
    /** The exempted shape is kept to stay compatible with existing users. */
    FOR_BACKWARDS_COMPATIBILITY,

    /** The exempted shape is a deliberate part of the API design. */
    API_DESIGN,

    /**
     * The exempted shape is dictated by interoperability with another language, platform, or
     * framework. Which interop constraint applies is not obvious from the entry alone, so the
     * `description` must still name it.
     */
    INTEROP,

    /**
     * The exempted shape mirrors an externally defined contract - a specification, a protocol,
     * or a closed real-world domain. Which contract is mirrored is not obvious from the entry
     * alone, so the `description` must still name it.
     */
    EXTERNAL_CONTRACT,

    /**
     * The exempted declaration deliberately ignores Java interoperability. This reason marks the
     * handful of spots where Java ergonomics are knowingly sacrificed - a library that doesn't
     * support Java callers at all disables the Java-interop diagnostics wholesale in its build
     * configuration instead. Why this particular declaration gets to ignore Java callers is not
     * obvious from the entry alone, so the `description` must still explain it.
     */
    IGNORE_JAVA_INTEROP,

    /**
     * None of the other entries fits. This is the default, and it explains nothing by itself,
     * so the exemption annotation must spell the motivation out in its `description`.
     */
    OTHER,
}

/**
 * Acknowledges that the annotated class or interface is deliberately open for unrestricted
 * subclassing outside the library.
 *
 * Once external code subclasses a type, the library can no longer add abstract members, change
 * existing members' signatures, or tighten invariants without breaking those subclasses. The
 * compiler plugin warns about publicly visible open or abstract
 * classes and interfaces not protected with [kotlin.SubclassOptInRequired]. 
 * 
 * Apply this annotation when unrestricted subclassing is an intended part of the API contract.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/open-api-without-subclass-opt-in) for the full rationale and examples.
 *
 * @param reason why the class is deliberately open.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyOpen(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated enum or sealed hierarchy is deliberately exhaustive.
 *
 * Users can `when`-match an enum or a sealed hierarchy without an `else` branch, so their code
 * depends on today's exact set of entries or subtypes: adding one later stops every such `when`
 * from compiling. The compiler plugin warns about publicly visible enums and sealed hierarchies.
 * 
 * Apply this annotation when the set of entries or subtypes is an intended, stable part of the API contract.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/exhaustive-public-api) for the full rationale and examples.
 *
 * @param reason why the hierarchy is deliberately exhaustive.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyExhaustive(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated declaration is deliberately left without KDoc.
 *
 * A KDoc is the contract users can rely on. Without one, they can only guess intent from the
 * implementation, and any later change - even a bug fix - risks breaking a usage nobody wrote
 * down as supported. The compiler plugin warns about publicly visible declarations that have no KDoc.
 * 
 * Apply this annotation when leaving the declaration undocumented is intended.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/undocumented-public-api) for the full rationale and examples.
 *
 * @param reason why the declaration is deliberately undocumented.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
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
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated type alias deliberately exposes a bare function type.
 *
 * A type alias is erased at compile time, so users bind to the bare function type, and the
 * alias can never grow members or constraints the way a `fun interface` with the same lambda
 * ergonomics can. The compiler plugin warns about publicly visible type aliases that abbreviate function types.
 * 
 * Apply this annotation when exposing the function type is intended.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/function-type-alias-public-api) for the full rationale and examples.
 *
 * @param reason why the alias deliberately exposes a function type.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.TYPEALIAS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyFunctionTypeAlias(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated data class is deliberately part of the public API.
 *
 * The `data` modifier generates positional `copy` and `componentN` functions and
 * `equals`/`hashCode`/`toString` over the exact ordered primary-constructor property list, so
 * adding, removing, or reordering a property later breaks users, and working around that by hand
 * negates the convenience the modifier was chosen for. The compiler plugin warns about publicly visible data classes.
 * 
 * Apply this annotation when the property list is an intended, stable part of the API contract.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/data-class-public-api) for the full rationale and examples.
 *
 * @param reason why the class is deliberately a data class.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyDataClass(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated stateful class deliberately uses identity equality.
 *
 * State usually gives instances their meaning, and identity equality treats two instances holding
 * the same state as different values. The compiler plugin warns
 * about publicly visible stateful classes - classes with at least one property backed by a
 * field - that neither declare nor inherit `equals`. 
 * 
 * Apply this annotation when identity equality is intended.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/stateful-class-without-equals-hashcode-to-string) for the full rationale and examples.
 *
 * @param reason why the class deliberately has no `equals` implementation.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyWithoutEquals(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated stateful class deliberately uses identity hashing.
 *
 * Identity hashing carries identity equality into hash-based collections: sets and map keys
 * organize instances by identity instead of their meaningful state. The compiler plugin
 * warns about publicly visible stateful classes - classes with at least
 * one property backed by a field - that neither declare nor inherit `hashCode`. 
 * 
 * Apply this annotation when identity hashing is intended.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/stateful-class-without-equals-hashcode-to-string) for the full rationale and examples.
 *
 * @param reason why the class deliberately has no `hashCode` implementation.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyWithoutHashCode(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated class deliberately provides no `toString` implementation.
 *
 * An instance that only prints as the opaque default `Connection@1a2b3c4d` reveals nothing in a
 * log line, exception message, or debugger view. The compiler plugin
 * warns about publicly visible stateful classes - classes with at least one property
 * backed by a field - that neither declare nor inherit `toString`. 
 * 
 * Apply this annotation when the opaque rendering is intended.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/stateful-class-without-equals-hashcode-to-string) for the full rationale and examples.
 *
 * @param reason why the class deliberately has no `toString`.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyWithoutToString(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated stateful class deliberately uses identity equality and hashing
 * and the opaque default rendering inherited from `kotlin.Any`.
 *
 * The compiler plugin warns about publicly visible stateful classes - classes
 * with at least one property backed by a field - that neither declare nor inherit meaningful
 * `equals`, `hashCode`, and `toString` implementations. 
 * 
 * Apply this annotation to acknowledge all three inherited implementations at once. Use [IntentionallyWithoutEquals],
 * [IntentionallyWithoutHashCode], or [IntentionallyWithoutToString] when only one behavior is
 * intentional.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/stateful-class-without-equals-hashcode-to-string) for the full rationale and examples.
 *
 * @param reason why the class deliberately inherits all three implementations from `kotlin.Any`.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyWithoutEqualsHashCodeOrToString(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated declaration deliberately exposes a mutable collection type in
 * the public API.
 *
 * Once a mutable collection crosses the API boundary, it is unclear which mutations are safe.
 * Users can mutate a collection the library owns, and the library can mutate an argument the user
 * still holds. The compiler plugin warns about public signatures that mention
 * mutable collection types (`MutableList`, `MutableMap`, ..., their implementations, and arrays,
 * which are mutable collections too). 
 * 
 * Apply this annotation when sharing the mutable collection is an intended part of the API contract.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/mutable-collection-public-api) for the full rationale and examples.
 *
 * @param reason why the declaration deliberately exposes a mutable collection.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
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
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated declaration deliberately exposes the tuple type `Pair` or
 * `Triple` in the public API.
 *
 * Tuple components carry no domain meaning - `first`/`second`/`third` and positional
 * destructuring reveal nothing about the values - and the fixed shape can't grow another
 * component without switching to a different type and breaking users. The compiler plugin
 * warns about public signatures that mention `Pair` or `Triple`.
 * 
 * Apply this annotation when exposing the tuple is an intended part of the API contract.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/pair-or-triple-public-api) for the full rationale and examples.
 *
 * @param reason why the declaration deliberately exposes a tuple type.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
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
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated function or parameter deliberately takes a Boolean argument.
 *
 * At the call site a positional `true`/`false` says nothing about its meaning, and users can't be
 * forced to name the argument they pass. The compiler plugin warns
 * about Boolean value parameters in publicly visible functions,
 * except constructors and factory functions named after the type they create.
 * 
 * Apply this annotation when the Boolean parameter is intended (for example, when its meaning is
 * unmistakable from the function name alone, as in `setEnabled(enabled: Boolean)`).
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/boolean-parameter-public-api) for the full rationale and examples.
 *
 * @param reason why the Boolean parameter is intended.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyBooleanParameter(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated declaration deliberately exposes a nullable Boolean in the
 * public API.
 *
 * `Boolean?` models three states but names only two of them, so every use site has to remember
 * what `null` stands for, and three-state logic hides in two-branch `if`s. The
 * compiler plugin warns about public signatures that mention `Boolean?`.
 * 
 * Apply this annotation when the nullable Boolean is an intended part of the API contract. 
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/nullable-boolean-public-api) for the full rationale and examples.
 *
 * @param reason why the declaration deliberately exposes a nullable Boolean.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
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
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated function or constructor deliberately declares a required
 * parameter after optional ones.
 *
 * A required parameter behind an optional (defaulted or `vararg`) one can't be passed positionally
 * without re-stating the defaults in front of it, which pushes callers toward named arguments for
 * an input that should be trivial to supply. The compiler plugin
 * warns about publicly visible functions and constructors with such parameter orders. 
 * 
 * Apply this annotation when the order is intended.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/required-parameter-after-optional) for the full rationale and examples.
 *
 * @param reason why the parameter order is intended.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyRequiredParameterAfterOptional(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated function or constructor deliberately orders its parameters
 * differently from its other overloads.
 *
 * Users transfer their intuition about one overload's parameter order to the next, so an overload
 * that reverses same-named parameters invites a silently swapped call, especially when the
 * swapped parameters share a type and the mistake still compiles. The compiler plugin
 * warns about publicly visible overloads declaring the same parameter names in a different relative order.
 * 
 * Apply this annotation when the differing order is intended.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/inconsistent-parameter-order-in-overloads) for the full rationale and examples.
 *
 * @param reason why the differing parameter order is intended.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyInconsistentParameterOrder(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated inline function or the annotated property's inline accessors
 * deliberately carry logic in their body.
 *
 * The compiler copies an inline body into every call site, so logic placed there - and its bugs -
 * stays in each user's binary until that binary is recompiled, while a regular call runs the
 * library version present at runtime. The compiler plugin warns
 * about publicly visible inline functions and inline property accessors whose body does more than
 * delegate to a non-inline function.
 * 
 * Apply this annotation when inlining the logic is intended (for example, when a lambda
 * must run inline for non-local returns, or when a hot path must not pay for an extra call).
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/inline-function-with-logic) for the full rationale and examples.
 *
 * @param reason why the logic is deliberately inlined.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyInlinedLogic(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated declaration deliberately compiles to a JVM shape that Java
 * sources can't call.
 *
 * A value class in a signature makes the compiler mangle the compiled JVM name with a hash suffix
 * (and hide such constructors behind a synthetic one), so Kotlin callers never notice, but Java
 * sources can't call the declaration. The compiler plugin warns,
 * in JVM compilations, about publicly visible functions, properties, and constructors with a
 * value class in their signature. 
 * 
 * Apply this annotation when the declaration is deliberately
 * Kotlin-only, or give the compiled code a Java-callable shape with `@JvmName`
 * (`@get:`/`@set:JvmName` on property accessors) or `@JvmExposeBoxed` instead.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/mangled-jvm-name-public-api) for the full rationale and examples.
 *
 * @param reason why the Java-inaccessible shape is intended.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
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
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated function - or every function inside the annotated class - is
 * deliberately Kotlin-only API left visible to Java sources.
 *
 * A Kotlin-only shape - a `suspend` function, an `inline` function with a `reified` type
 * parameter, or a function taking a Kotlin-specific function type - still compiles a method Java
 * sources see and may try to call, unidiomatically or, for `reified`, failing at runtime. The
 * compiler plugin warns, in JVM compilations, about such publicly visible functions.
 * 
 * Apply this annotation when leaving the Kotlin-only shape visible to Java is
 * intended, or hide it with `@JvmSynthetic` or provide a Java-friendly alternative instead. 
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/kotlin-only-api-without-jvm-synthetic) for the full rationale and examples.
 *
 * @param reason why the Kotlin-only shape deliberately stays visible to Java.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyKotlinOnlyApi(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated companion object member - or every member inside the
 * annotated companion object or class - is deliberately reachable from Java only through the
 * companion instance.
 *
 * Java has no companion-object syntax: companion members compile to the nested `Companion`
 * class, so Java callers must use forms such as `Registry.Companion.getCurrentEndpoint()`, which
 * expose a Kotlin implementation detail in the Java API. The compiler plugin
 * warns, in JVM compilations, about publicly visible companion functions without
 * `@JvmStatic` and companion properties whose Java-visible accessors remain on the nested
 * `Companion` class. 
 * 
 * Apply this annotation when the companion-instance access path is intended,
 * or add static access on the outer class (`@JvmStatic`, `@JvmField`, `const val`) or hide the
 * member from Java with `@JvmSynthetic` instead.
 *
 * See the check documentation for the full rationale and examples:
 * [companion functions](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/companion-api-without-jvm-static),
 * [companion properties](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/companion-property-without-static-access).
 *
 * @param reason why the companion-instance access path is intended.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyNonStaticCompanionApi(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated file deliberately keeps the file facade class name derived
 * from the file name.
 *
 * Public top-level functions and properties compile into a facade class whose derived name
 * (`foo.kt` becomes `FooKt`) reads as an implementation detail at Java call sites and is tied to
 * a fact Kotlin callers never see: renaming the file silently renames the facade and breaks Java
 * callers. The compiler plugin warns, in JVM compilations, about such files without an explicit `@file:JvmName`.
 * 
 * Apply this annotation - as `@file:IntentionallyDefaultFacadeName(...)` - when keeping the derived name is intended.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/top-level-api-without-jvm-name) for the full rationale and examples.
 *
 * @param reason why the derived facade name is intended.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyDefaultFacadeName(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated function or constructor deliberately keeps its default
 * parameter values invisible to Java callers.
 *
 * Only the full signature of a function with default parameter values is compiled, so for Java
 * callers the defaults don't exist, and every argument must be spelled out at every call site.
 * The compiler plugin warns, in JVM compilations, about publicly
 * visible functions and constructors declaring defaults without `@JvmOverloads`, which would
 * additionally compile the overloads that omit trailing defaulted parameters. 
 * 
 * Apply this annotation when serving Java callers the full signature only is intended.
 *
 * See the [documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/default-parameters-without-jvm-overloads) for the full rationale and examples.
 *
 * @param reason why the defaults deliberately stay invisible to Java callers.
 * @param description free-form explanation of the exemption. May be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyWithoutJvmOverloads(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated DSL marker deliberately keeps wrong target set - no-op
 * targets in its `@Target`, or no explicit `@Target` at all - because fixing it would break
 * existing users.
 *
 * DSL marker scope control only reacts to markers on classifier declarations, type usages, and
 * type aliases, so other targets restrict nothing and only give a false sense of receiver scope
 * control. The compiler plugin warns about such target sets, but for an
 * already-published marker the fix is breaking: removing a target rejects user code that applies
 * the marker there, and declaring an explicit `@Target` forbids the previously allowed default
 * targets. 
 * 
 * Apply this annotation to suppress the warnings for such legacy markers.
 *
 * Wrong marker targets are never good API design, so unlike the other exemptions, this one bakes
 * its only accepted reason - backwards compatibility - into its name and carries no
 * [ExemptionReason]. New DSL markers must declare effective targets instead.
 *
 * See the check documentation for the full rationale and examples:
 * [no-op targets](https://mr3zee.github.io/Library-API-Watchdog/checks/special/dsl-marker-noop-target),
 * [missing explicit targets](https://mr3zee.github.io/Library-API-Watchdog/checks/special/dsl-marker-without-explicit-targets).
 *
 * @param description optional free-form context for the exemption.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility(
    val description: String = "",
)

/**
 * Turns the annotated annotation class into an internal API marker: declarations annotated with
 * the marked annotation, and everything nested in them, are exempt from all public API checks.
 *
 * Libraries sometimes expose declarations that are public for technical reasons but are not part
 * of the supported API surface and flag them with a dedicated annotation (usually one that also
 * requires opt-in). Such declarations carry no compatibility contract, so the compiler plugin
 * shouldn't demand documentation or evolution safeguards for them. The marker
 * annotation class itself remains part of the public API surface and is still watched.
 *
 * See [Exemptions and internal API](https://mr3zee.github.io/Library-API-Watchdog/exemptions) for the full explanation and examples.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class InternalAnnotationMarker
