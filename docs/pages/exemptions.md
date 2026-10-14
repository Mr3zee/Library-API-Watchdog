# Exemptions and internal API

Four mechanisms exclude a declaration from a check while the check stays enabled for the project:

| Mechanism                                           | Excludes                                              | From                       |
|-----------------------------------------------------|-------------------------------------------------------|----------------------------|
| [Exemption annotation](#exemption-annotations)      | One declaration                                       | One check                  |
| [Dependency exemption](#dependency-exemptions)      | Every use of a dependency's types in the module       | One check                  |
| [Internal API annotation](#internal-api-annotations) | One declaration and the declarations nested in it     | Every public API check     |
| [Annotation ignore rule](#annotation-ignore-rules)  | Every declaration with a given annotation             | One configurable check     |

## Exemption annotations

An `@Intentionally*` annotation exempts one declaration from one check. Use it when the
declaration has a reason to break the rule of the check, and state the reason on the annotation.

The "Exemption" section of each check page names the exemption annotation
([example for data classes](./checks/data-class-public-api.md#exemption)).

Every `@Intentionally*` annotation except
[`@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility`](#the-dsl-marker-exemption) has two parameters:

| Parameter     | Type              | Default |
|---------------|-------------------|---------|
| `description` | `String`          | `""`    |
| `reason`      | `ExemptionReason` | `OTHER` |

`FOR_BACKWARDS_COMPATIBILITY` and `API_DESIGN` explain the exemption on their own. Every other
reason needs a `description`, or
[Exemptions without explanation](./checks/special/exemption-without-explanation.md) reports the
annotation.

An explained exemption:

```kotlin
// !hide-focused
/** Legacy RPC configuration. */
@IntentionallyDataClass(
    description = "Serialized as-is by the legacy RPC layer, " +
            "which reflects on componentN.",
    reason = ExemptionReason.INTEROP,
)
public data class LegacyConfig(
    // !hide-focused
    /** The network host that serves RPC requests. */
    public val host: String,
    // !hide-focused
    /** The TCP port exposed by [host]. */
    public val port: Int,
)
```

For a published API, the [`exemptExistingApi` task](./existing-libs.md) adds the exemptions.

### The DSL marker exemption

`@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility` exempts a DSL marker from
[DSL markers with no-op targets](./checks/special/dsl-marker-noop-target.md) and
[DSL markers without explicit targets](./checks/special/dsl-marker-without-explicit-targets.md).
It has no `reason` parameter, because backwards compatibility is the only valid reason for this
exemption. Its `description` is optional.

### All exemption annotations

| Annotation                                                     | Exempts                                                                                                                                                                                                             |
|----------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@IntentionallyOpen`                                           | [Open API without subclass opt-in](./checks/open-api-without-subclass-opt-in.md)                                                                                                                                    |
| `@IntentionallyExhaustive`                                     | [Exhaustive public API](./checks/exhaustive-public-api.md)                                                                                                                                                          |
| `@IntentionallyUndocumented`                                   | [Undocumented public API](./checks/undocumented-public-api.md)                                                                                                                                                      |
| `@IntentionallyFunctionTypeAlias`                              | [Function type aliases in public API](./checks/function-type-alias-public-api.md)                                                                                                                                   |
| `@IntentionallyDataClass`                                      | [Data classes in public API](./checks/data-class-public-api.md)                                                                                                                                                     |
| `@IntentionallyWithoutEqualsHashCodeOrToString`                | All three [stateful class checks](./checks/stateful-class-without-equals-hashcode-to-string.md)                                                                                                                     |
| `@IntentionallyWithoutEquals`                                  | [Stateful classes without equals, hashCode, and toString](./checks/stateful-class-without-equals-hashcode-to-string.md)                                                                                             |
| `@IntentionallyWithoutHashCode`                                | [Stateful classes without equals, hashCode, and toString](./checks/stateful-class-without-equals-hashcode-to-string.md)                                                                                             |
| `@IntentionallyWithoutToString`                                | [Stateful classes without equals, hashCode, and toString](./checks/stateful-class-without-equals-hashcode-to-string.md)                                                                                             |
| `@IntentionallyMutableCollection`                              | [Mutable collections in public API](./checks/mutable-collection-public-api.md)                                                                                                                                      |
| `@IntentionallyPairOrTriple`                                   | [Pair and Triple in public API](./checks/pair-or-triple-public-api.md)                                                                                                                                              |
| `@IntentionallyBooleanParameter`                               | [Boolean parameters in public API](./checks/boolean-parameter-public-api.md)                                                                                                                                        |
| `@IntentionallyNullableBoolean`                                | [Nullable Booleans in public API](./checks/nullable-boolean-public-api.md)                                                                                                                                          |
| `@IntentionallyRequiredParameterAfterOptional`                 | [Required parameters after optional ones](./checks/required-parameter-after-optional.md)                                                                                                                            |
| `@IntentionallyInconsistentParameterOrder`                     | [Inconsistent parameter order in overloads](./checks/inconsistent-parameter-order-in-overloads.md)                                                                                                                  |
| `@IntentionallyInlinedLogic`                                   | [Inline functions with logic](./checks/inline-function-with-logic.md)                                                                                                                                               |
| `@IntentionallyMangledJvmName`                                 | [Mangled JVM names in public API](./checks/java-interop/mangled-jvm-name-public-api.md)                                                                                                                             |
| `@IntentionallyKotlinOnlyApi`                                  | [Kotlin-only API without JvmSynthetic](./checks/java-interop/kotlin-only-api-without-jvm-synthetic.md)                                                                                                              |
| `@IntentionallyNonStaticCompanionApi`                          | [Companion function without JvmStatic](./checks/java-interop/companion-api-without-jvm-static.md) and [Companion property without static access](./checks/java-interop/companion-property-without-static-access.md) |
| `@IntentionallyDefaultFacadeName`                              | [Top-level API without JvmName](./checks/java-interop/top-level-api-without-jvm-name.md)                                                                                                                            |
| `@IntentionallyWithoutJvmOverloads`                            | [Default parameters without JvmOverloads](./checks/java-interop/default-parameters-without-jvm-overloads.md)                                                                                                        |
| `@IntentionallyNonTransitiveDependency`                        | [Public types from non-transitive dependencies](./checks/special/public-type-from-non-transitive-dependency.md)                                                                                                     |
| `@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility` | [DSL markers with no-op targets](./checks/special/dsl-marker-noop-target.md) and [DSL markers without explicit targets](./checks/special/dsl-marker-without-explicit-targets.md)                                    |

## Dependency exemptions

A dependency exemption exempts every use of a dependency's types in the module from
[Public types from non-transitive dependencies](./checks/special/public-type-from-non-transitive-dependency.md).
Use it instead of `@IntentionallyNonTransitiveDependency` on each declaration when the whole module
exposes the dependency's types. Declare the dependency on the `apiWatchdogExemptNonTransitive`
configuration:

```kotlin build.gradle.kts
dependencies {
    compileOnly("com.example:models:1.0")

    apiWatchdogExemptNonTransitive("com.example:models")
}
```

[Exempting a dependency](./checks/special/public-type-from-non-transitive-dependency.md#exempting-a-dependency)
on the check page describes the configuration.

## Internal API annotations

An internal API annotation, such as `@InternalMyLibraryApi`, excludes a declaration from every
public API check. Use it when a declaration is public only for technical reasons and the library
gives no compatibility guarantee for it.

Annotate the library's internal API annotation with `@InternalAnnotationMarker`:

```kotlin
// !hide-focused
/** Marks declarations that are public only for technical reasons. */
@InternalAnnotationMarker
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
public annotation class InternalMyLibraryApi

@InternalMyLibraryApi // Internal API, not watched
public class ReflectionHelper
```

The public API checks skip every declaration with the marked annotation and every declaration
nested in it. Four checks still run there, because they check every visibility:
- [Exemptions without explanation](./checks/special/exemption-without-explanation.md),
- [DSL markers with no-op targets](./checks/special/dsl-marker-noop-target.md),
- [DSL markers without explicit targets](./checks/special/dsl-marker-without-explicit-targets.md), and
- [DSL markers on no-op type positions](./checks/special/dsl-marker-noop-type-position.md).

The marker annotation class itself is public API and stays watched.

A public declaration without the annotation must not use an internal API type in its signature.
[Public types marked as internal API](./checks/special/public-type-with-internal-api.md) reports
the declaration as an error.

An annotation class from a dependency can't carry `@InternalAnnotationMarker`. List its fully
qualified name in `internalApiAnnotations` instead. The plugin then treats it like a marked
annotation:

```kotlin build.gradle.kts
apiWatchdog {
    internalApiAnnotations.add("com.example.dependency.InternalDependencyApi")
}
```

See [Without Gradle](./configuration.md#without-gradle) for more details about the compiler option.

## Annotation ignore rules

An annotation ignore rule skips one check on every declaration that carries a given annotation.
Use it when every annotated declaration already satisfies the check.

```kotlin build.gradle.kts
import org.jetbrains.kotlinx.library.api.watchdog.WatchdogChecks

apiWatchdog {
    ignore(
        WatchdogChecks.STATEFUL_CLASS_WITHOUT_EQUALS,
        whenAnnotatedWith = listOf("com.example.GeneratedValueMembers"),
    )
}
```

A rule works for configurable checks only, not for always-error checks.

## The annotations artifact

The `@Intentionally*` annotations, `@InternalAnnotationMarker`, and `ExemptionReason` are in
`org.jetbrains.kotlinx:kotlin-library-api-watchdog-plugin-annotations`. The Gradle plugin adds this
dependency to the project.
