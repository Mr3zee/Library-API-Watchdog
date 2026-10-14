# Exemptions and internal API

Three mechanisms exclude a declaration from a check while the check stays enabled for the project:

- An `@Intentionally*` annotation exempts one declaration from one check. 
- An internal API annotation, such as `@InternalMyLibraryApi`, excludes a declaration from every
  public API check. Use it when a declaration is public only for technical reasons and the library
  gives no compatibility guarantee for it, whatever its visibility.
- An [annotation ignore rule](./configuration.md#annotation-ignore-rules) in the `apiWatchdog`
  extension skips one configurable check on every declaration with a given annotation. Use it when
  the annotation already guarantees the condition that the check tests. An example is a code
  generator that adds `equals` and `hashCode` to every annotated class.

## Exempting a single declaration

The "Exemption" section of each check page names the exemption annotation
([example for data classes](./checks/data-class-public-api.md#exemption)). The annotation silences
that check on the annotated declaration. 

Every `@Intentionally*` annotation has two parameters:

| Parameter     | Type              | Default |
|---------------|-------------------|---------|
| `description` | `String`          | `""`    |
| `reason`      | `ExemptionReason` | `OTHER` |

The `reason` decides whether the `description` is required:

- `FOR_BACKWARDS_COMPATIBILITY` and `API_DESIGN` explain the exemption on their own. The
  `description` is optional.
- `INTEROP`, `EXTERNAL_CONTRACT`, `IGNORE_JAVA_INTEROP`, and `OTHER` only categorize the exemption.
  The `description` must still explain the exemption.

[Exemptions without explanation](./checks/special/exemption-without-explanation.md) reports an
exemption that breaks this rule. The check is always an error, can't be disabled, and runs on
declarations of every visibility.

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

For a library whose API is already published, the [`exemptExistingApi` task](./existing-libs.md)
adds the exemption to every reported declaration with the `FOR_BACKWARDS_COMPATIBILITY` reason.

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

## Internal API annotations

Some declarations are public only because the library's own structure needs it, for example
internals that several modules share. Instead of an exemption on each of them, annotate the
library's internal API annotation with `@InternalAnnotationMarker`:

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
nested in it. Two kinds of checks still run there, because they check every visibility:
[Exemptions without explanation](./checks/special/exemption-without-explanation.md) and the DSL
marker checks.

The marker annotation class itself is public API and stays watched.

A supported public declaration must not expose an internal API type in its signature.
[Public types marked as internal API](./checks/special/public-type-with-internal-api.md) reports
such a signature. Either remove the type from the signature or mark the declaration as internal
API too.

An annotation class from a dependency can't carry `@InternalAnnotationMarker`. List its fully
qualified name in `internalApiAnnotations` instead. The plugin then treats it like a marked
annotation:

```kotlin
apiWatchdog {
    internalApiAnnotations.add("com.example.dependency.InternalDependencyApi")
}
```

## The annotations artifact

The `@Intentionally*` annotations, `@InternalAnnotationMarker`, and `ExemptionReason` are in
`org.jetbrains.kotlinx:kotlin-library-api-watchdog-plugin-annotations`. The Gradle plugin adds this
dependency to the project.
