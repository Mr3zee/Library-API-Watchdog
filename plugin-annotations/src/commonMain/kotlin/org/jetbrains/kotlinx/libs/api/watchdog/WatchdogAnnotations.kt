package org.jetbrains.kotlinx.libs.api.watchdog

/**
 * Explains why a watchdog exemption annotation is applied.
 *
 * Every exemption annotation in this package carries a `reason` and a free-form `description`.
 * The library-api-watchdog compiler plugin requires the explanation to be meaningful: the description
 * may be left empty only when the reason explains the exemption on its own
 * ([FOR_BACKWARDS_COMPATIBILITY], [API_DESIGN]). The other reasons only categorize the exemption
 * and keep the description shorter - the specific constraint still has to be spelled out there.
 *
 * See [Exemptions and internal API](https://mr3zee.github.io/Library-API-Watchdog/exemptions.html) for how reasons and descriptions are validated.
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
 * The library-api-watchdog compiler plugin warns about publicly visible open/abstract classes and
 * interfaces that are not protected with [kotlin.SubclassOptInRequired], because every external
 * subclass
 * [constrains how the library can evolve](https://kotlinlang.org/docs/api-guidelines-predictability.html#prevent-unwanted-and-invalid-extensions).
 * Apply this annotation to suppress the warning when unrestricted subclassing is an intended
 * part of the API contract.
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/open-api-without-subclass-opt-in.html) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns about publicly visible enums and sealed hierarchies,
 * because users can
 * [match on them exhaustively](https://kotlinlang.org/docs/api-guidelines-predictability.html#prevent-unwanted-and-invalid-extensions)
 * (`when` without an `else` branch), which turns adding an entry or a subtype into a breaking
 * change. Apply this annotation to suppress the warning when the set of entries/subtypes is an
 * intended, stable part of the API contract.
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/exhaustive-public-api.html) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns about publicly visible declarations that have no KDoc -
 * classifiers, type aliases, functions, properties, constructors, and enum entries - because
 * [undocumented API forces users to guess the usage contract](https://kotlinlang.org/docs/api-guidelines-informative-documentation.html#thoroughly-document-your-api).
 * Apply this annotation to suppress the warning when leaving the declaration undocumented is
 * intended (for example, when it is self-explanatory or documented elsewhere).
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/undocumented-public-api.html) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns about publicly visible type aliases that abbreviate
 * function types, because the alias is erased from the compiled API: users bind to the bare
 * function shape, and the type can't grow members or constraints later without breaking them,
 * [unlike a `fun interface`](https://kotlinlang.org/docs/fun-interfaces.html#functional-interfaces-vs-type-aliases).
 * Apply this annotation to suppress the warning when exposing the function type is intended
 * (for example, for lambdas that only travel through inline functions).
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/function-type-alias-public-api.html) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns about
 * [publicly visible data classes](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#avoid-using-data-classes-in-your-api),
 * because the generated `copy` and `componentN` functions and the constructor bake the exact
 * property list into the compiled API: adding, removing, or reordering a property later breaks
 * users. Apply this annotation to suppress the warning when the property list is an intended,
 * stable part of the API contract.
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/data-class-public-api.html) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns about publicly visible stateful classes - classes
 * with at least one property backed by a field - that neither declare nor inherit `equals`,
 * because two instances holding the same meaningful state otherwise compare as different values.
 * Apply this annotation to suppress the warning when identity equality is intended.
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/stateful-class-without-equals-hashcode-to-string) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns about publicly visible stateful classes - classes
 * with at least one property backed by a field - that neither declare nor inherit `hashCode`,
 * because hash-based collections otherwise organize instances by identity instead of meaningful
 * state. Apply this annotation to suppress the warning when identity hashing is intended.
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/stateful-class-without-equals-hashcode-to-string) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns about publicly visible stateful classes - classes with
 * at least one property backed by a field - that neither declare nor inherit
 * [a `toString` implementation](https://kotlinlang.org/docs/api-guidelines-debuggability.html#provide-a-tostring-method-for-stateful-types),
 * because their instances render as the opaque default class-name-with-hash-code,
 * which makes logs and debugger output meaningless. Apply this annotation to suppress the warning
 * when the opaque rendering is intended (for example, when the state is sensitive and must not
 * leak into logs).
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/stateful-class-without-equals-hashcode-to-string) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns about publicly visible stateful classes - classes
 * with at least one property backed by a field - that neither declare nor inherit meaningful
 * `equals`, `hashCode`, and `toString` implementations. Apply this annotation to suppress all
 * three warnings when identity semantics and opaque rendering are intended. Use
 * [IntentionallyWithoutEquals], [IntentionallyWithoutHashCode], or
 * [IntentionallyWithoutToString] when only one behavior is intentional.
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/checks/stateful-class-without-equals-hashcode-to-string) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns about public signatures - return types, property
 * types, and parameter types, including their type arguments - that mention mutable collection
 * types (`MutableList`, `MutableMap`, ..., their implementations, and arrays, which are mutable
 * collections too), as well as mutable bounds on type parameters.
 * [Sharing a mutable collection](https://kotlinlang.org/docs/api-guidelines-predictability.html#avoid-exposing-mutable-state)
 * across the API boundary makes it unclear whether user-side and library-side mutations affect
 * each other. Apply this annotation to suppress the warning when sharing the mutable collection
 * is an intended part of the API contract. On a function, a property, or a constructor it covers the
 * whole signature. On a single parameter or type parameter it covers just that parameter. On a
 * type usage (`List<@IntentionallyMutableCollection MutableList<Int>>`) it covers the annotated
 * type and everything nested in it.
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/mutable-collection-public-api.html) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns about public signatures - return types, property
 * types, and parameter types, including their type arguments (`List<Pair<Int, String>>` exposes
 * the tuple all the same) - that mention `Pair` or `Triple`, as well as tuple bounds on type
 * parameters. Tuple components carry no domain meaning: at the use site `first`/`second`/`third`
 * and positional destructuring reveal nothing about the values, and the fixed shape can't
 * evolve - adding a value means switching to a different type, breaking users. Prefer a
 * [small class with descriptively named properties](https://kotlinlang.org/docs/api-guidelines-consistency.html#use-object-oriented-design-for-data-and-state).
 * Apply this annotation to suppress the warning when exposing the tuple is an intended part of
 * the API contract. On a function, a property, or a constructor it covers the whole signature.
 * On a single parameter or type parameter it covers just that parameter. On a type usage
 * (`List<@IntentionallyPairOrTriple Pair<Int, String>>`) it covers the annotated type and
 * everything nested in it.
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/pair-or-triple-public-api.html) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns about
 * [Boolean value parameters](https://kotlinlang.org/docs/api-guidelines-readability.html#avoid-using-the-boolean-type-as-an-argument)
 * - including nullable, `vararg`, and context ones - in publicly visible functions, because at
 * the call site a bare `true`/`false` reveals nothing about its meaning, and users can't be
 * forced to name the argument they pass. Prefer separate, descriptively named functions for
 * each mode, or an enum class naming the modes. Constructors and constructor functions -
 * factory functions named after the type they create - are not checked: a construction site
 * stores data in the named type rather than switching an operation mode. Apply this annotation
 * to suppress the warning when the Boolean parameter is intended (for example, when the
 * parameter is unmistakable from the function name alone, as in `setEnabled(enabled: Boolean)`).
 * On a function it covers every parameter. On a single parameter it covers just that parameter.
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/boolean-parameter-public-api.html) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns about public signatures - return types, property
 * types, and parameter types, including their type arguments - that mention `Boolean?`, as well
 * as `Boolean?` bounds on type parameters. A nullable Boolean models three states but names only
 * two of them, so every use site has to know what `null` stands for, and three-state logic hides
 * in two-branch `if`s. Prefer an
 * [enum class naming all three states](https://kotlinlang.org/docs/api-guidelines-readability.html#avoid-using-the-boolean-type-as-an-argument).
 * Apply this annotation to suppress the warning when the nullable Boolean is an intended part
 * of the API contract. On a
 * function, a property, or a constructor it covers the whole signature. On a single parameter or
 * type parameter it covers just that parameter. On a type usage
 * (`List<@IntentionallyNullableBoolean Boolean?>`) it covers the annotated type and everything
 * nested in it.
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/nullable-boolean-public-api.html) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns about publicly visible functions and constructors that
 * declare a required parameter - one without a default value - after an optional (defaulted or
 * `vararg`) parameter, because
 * [parameters should go from the general to the specific](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage):
 * essential inputs first, optional inputs last. A required parameter behind optional ones can't
 * be passed positionally without re-stating the defaults in front of it. A required function-type
 * (or `fun interface`) parameter in the last position is not reported: it keeps trailing-lambda
 * call syntax available. Apply this annotation to suppress the warning when the order is intended
 * (for example, when appending a parameter anywhere else would break existing users).
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/required-parameter-after-optional.html) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns about publicly visible overloads that declare the same
 * parameter names in a different relative order, because
 * [users transfer their expectations between overloads](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage):
 * an inconsistent order of same-named parameters invites silently swapped arguments. Apply this
 * annotation to suppress the warning when the differing order is intended. The annotated
 * declaration is also no longer used as an ordering reference for other overloads.
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/inconsistent-parameter-order-in-overloads.html) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns about publicly visible inline functions and inline
 * property accessors whose body does more than delegate to a non-inline function, because the
 * compiler
 * [copies an inline body into every user binary](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#considerations-for-using-the-publishedapi-annotation):
 * logic placed there - and its bugs - stays frozen in users compiled against an old library
 * version until they recompile. Keep
 * public inline functions thin wrappers that resolve what only the call site knows (a reified
 * type argument, an inlined lambda) and hand the actual work to a non-inline function, marked
 * `@PublishedApi internal` when it should stay out of the public API. Apply this annotation to
 * suppress the warning when inlining the logic is intended (for example, when a lambda must run
 * inline for non-local returns, or when a hot path must not pay for an extra call). On a
 * property it covers both accessors.
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/inline-function-with-logic.html) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns, in JVM compilations, about publicly visible functions,
 * properties, and constructors that have a
 * [value class](https://kotlinlang.org/docs/inline-classes.html#mangling) in their signature - as
 * a parameter or receiver type, or as the return type of a class member. The compiler mangles the
 * JVM name of such entry points with a hash suffix (and hides such constructors behind a synthetic
 * one), so Kotlin users are unaffected but
 * [Java users can't call them](https://kotlinlang.org/docs/java-to-kotlin-interop.html#inline-value-classes).
 * Prefer giving the
 * compiled code a Java-callable shape with `@JvmName` (`@get:`/`@set:JvmName` on property
 * accessors) or `@JvmExposeBoxed`, and apply this annotation to suppress the warning when the
 * declaration is deliberately Kotlin-only. On a class it covers every declaration inside. On a
 * primary constructor `val`/`var` parameter it covers the property created from it.
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/mangled-jvm-name-public-api.html) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns, in JVM compilations, about publicly visible functions
 * whose shape
 * [only Kotlin callers can use idiomatically](https://kotlinlang.org/docs/java-to-kotlin-interop.html):
 * `suspend` functions (Java sees a
 * trailing `Continuation` parameter it can't provide idiomatically), `inline` functions with a
 * `reified` type parameter (calling the compiled method from Java fails at runtime), and
 * functions taking a Kotlin-specific function type - a suspend function type, a function type
 * with receiver, or a `Unit`-returning function type. Prefer hiding such members from Java with
 * `@JvmSynthetic`, or provide a Java-friendly alternative alongside (a blocking or
 * `CompletableFuture`-returning bridge, a `fun interface` parameter), and apply this annotation
 * to suppress the warning when leaving the Kotlin-only shape visible to Java is intended. On a
 * class it covers every function declared inside.
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/kotlin-only-api-without-jvm-synthetic.html) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns, in JVM compilations, about publicly visible companion
 * object functions without
 * [`@JvmStatic`](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-methods) and
 * constant-shaped companion `val`s without
 * [`@JvmField`](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-fields): both
 * compile to members of the nested `Companion` class, so Java callers have to go through
 * `Outer.Companion`. Prefer exposing such members on the outer class itself (`@JvmStatic`,
 * `@JvmField`, `const val`) or hiding them from Java (`@JvmSynthetic`), and apply this
 * annotation to suppress the warnings when the companion-instance access path is intended. On a
 * class - the companion object itself or its outer class - it covers every member inside.
 *
 * See the check documentation for rationale and examples:
 * [companion functions](https://mr3zee.github.io/Library-API-Watchdog/companion-api-without-jvm-static.html),
 * [companion constants](https://mr3zee.github.io/Library-API-Watchdog/companion-constant-without-jvm-field.html).
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
 * The library-api-watchdog compiler plugin warns, in JVM compilations, about files whose public
 * top-level functions or properties
 * [compile into a facade class](https://kotlinlang.org/docs/java-to-kotlin-interop.html#package-level-functions)
 * without an explicit `@file:JvmName`: the derived name (`foo.kt` → `FooKt`) leaks the file name
 * into the Java API
 * surface, and renaming the file - invisible to Kotlin callers - renames the facade and breaks
 * Java users. Prefer choosing and pinning the facade name with `@file:JvmName`, and apply
 * this annotation - as `@file:IntentionallyDefaultFacadeName(...)` - to suppress the warning
 * when keeping the derived name is intended.
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/top-level-api-without-jvm-name.html) for rationale and examples.
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
 * The library-api-watchdog compiler plugin warns, in JVM compilations, about publicly visible functions
 * and constructors that declare default parameter values without `@JvmOverloads`: only the full
 * signature is compiled, so for Java callers the defaults don't exist and every argument must
 * be spelled out. Prefer
 * [`@JvmOverloads`](https://kotlinlang.org/docs/java-to-kotlin-interop.html#overloads-generation),
 * which additionally compiles the overloads that omit defaulted parameters from the right, and
 * apply this annotation to suppress the warning when
 * serving Java callers the full signature only is intended (for example, when the defaulted
 * parameters make no sense without Kotlin's named arguments).
 *
 * See the [check documentation](https://mr3zee.github.io/Library-API-Watchdog/default-parameters-without-jvm-overloads.html) for rationale and examples.
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
 * Acknowledges that the annotated DSL marker deliberately keeps a wrong target set - no-op
 * targets in its `@Target`, or no explicit `@Target` at all - because fixing it would break
 * existing users.
 *
 * The library-api-watchdog compiler plugin warns about
 * [DSL marker](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
 * targets on which the marker has no effect. For an already-published marker the fix is
 * breaking: removing a target rejects user
 * code that applies the marker there, and declaring an explicit `@Target` forbids the previously
 * allowed default targets. Apply this annotation to suppress the warnings for such legacy
 * markers.
 *
 * Wrong marker targets are never good API design, so unlike the other exemptions this one bakes
 * its only accepted reason - backwards compatibility - into its name and carries no
 * [ExemptionReason]. New DSL markers must declare effective targets instead.
 *
 * See the check documentation for rationale and examples:
 * [no-op targets](https://mr3zee.github.io/Library-API-Watchdog/dsl-marker-noop-target.html),
 * [missing explicit targets](https://mr3zee.github.io/Library-API-Watchdog/dsl-marker-without-explicit-targets.html).
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
 * of the supported API surface, and flag them with a dedicated annotation (usually one that also
 * requires opt-in). Such declarations carry no compatibility contract, so the library-api-watchdog
 * compiler plugin shouldn't demand documentation or evolution safeguards for them:
 *
 * ```
 * @InternalAnnotationMarker
 * @RequiresOptIn(level = RequiresOptIn.Level.ERROR)
 * public annotation class InternalMyLibraryApi
 *
 * @InternalMyLibraryApi // Not watched: internal API despite the public visibility.
 * public class ReflectionHelper
 * ```
 *
 * The marker annotation class itself remains part of the public API surface and is still watched.
 *
 * See [Exemptions and internal API](https://mr3zee.github.io/Library-API-Watchdog/exemptions.html) for details.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class InternalAnnotationMarker
