# Exemptions and internal API

`library-api-watchdog` gives library authors two ways to opt out of its checks without disabling them in the project:

- A per-declaration `@Intentionally*` annotation acknowledges that one specific hard-to-evolve shape is a deliberate
  choice.
- An annotation like `@InternalMyLibraryApi` removes declarations from
  consideration because they carry no compatibility guarantees at all, regardless of their visibility,
  making them internal by the library contract.

Use the first one when the shape itself is the deliberate part of the API. Use the second one
when a declaration is public only for technical reasons and is not meant to be supported API.

A third option covers one check at a time: an
[annotation ignore rule](./configuration.md#annotation-ignore-rules) in the `apiWatchdog`
extension skips a configurable check on every declaration carrying a given annotation. Use it
when an annotation already guarantees what the check is looking for, such as a code generator
that provides `equals` and `hashCode` for every annotated class.

## Exempting a single declaration

Each check has an exemption annotation. It is described in the corresponding "Exemption" section of the check's page
([Example for data classes](./checks/data-class-public-api.md#exemption)). Applying the
annotation silences that one check on that one declaration (or, for annotations placed on a type
parameter or type usage, on that one type).

**But an exemption is not a bare escape hatch. It has to be explained.**

Every `@Intentionally*` annotation takes a `reason: ExemptionReason` (default `OTHER`) and a
`description: String` (empty string by default). Exemptions reasons are divided into two groups:

- `FOR_BACKWARDS_COMPATIBILITY` and `API_DESIGN` explain the exemption on their own,
  so the description is optional.
- `INTEROP`, `EXTERNAL_CONTRACT`, `IGNORE_JAVA_INTEROP`, and `OTHER` only categorize the exemption -
  which interop constraint, which external contract, or why this declaration in particular gets
  to ignore Java callers is not obvious from the entry alone - so a non-empty `description` is
  required.

A bare `@IntentionallyDataClass` without a description, for example, explains nothing and is rejected by the
[Exemptions without explanation](./checks/special/exemption-without-explanation.md) check. That check is always an
error and can't be configured or disabled. It fires on every exemption annotation usage, even on
non-public declarations, because leaving any exemption unexplained defeats the point of exemptions.

A well-explained exemption:

```kotlin
// !hide-focused
/** Legacy RPC configuration. */
@IntentionallyDataClass(
    description = "Serialized as-is by the legacy RPC layer, " +
            "which reflects on componentN.",
    reason = ExemptionReason.INTEROP,
)
public data class LegacyConfig(
    /** The network host that serves RPC requests. */
    public val host: String,
    /** The TCP port exposed by [host]. */
    public val port: Int,
)
```

When adopting the watchdog on a library whose API has already shipped, the exemptions for the
existing surface don't have to be written by hand: the Gradle plugin's
[`exemptExistingApi` task](./existing-libs.md)
inserts them - with the self-explanatory `FOR_BACKWARDS_COMPATIBILITY` reason - for every
diagnostic the current sources trigger.

### One Exception

There is one exception: `@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility` bakes its only accepted reason into
its name and takes just an optional `description`. This exemption targets [`DSL_MARKER_NOOP_TARGET`](./checks/special/dsl-marker-noop-target.md)
and [`DSL_MARKER_WITHOUT_EXPLICIT_TARGETS`](./checks/special/dsl-marker-without-explicit-targets.md), and backwards compatibility is the
only valid reason for a exemption.

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

Some declarations are public only because the current library's internal structure requires it, 
not because they are supported API (for example, shared internals). Rather than
exempting every one of them individually, annotate the library's own internal-API marker annotation
with `@InternalAnnotationMarker`:

```kotlin
// !hide-focused
/** Marks declarations that are public only for technical reasons. */
@InternalAnnotationMarker
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
public annotation class InternalMyLibraryApi

@InternalMyLibraryApi // Not watched, library's internal API
public class ReflectionHelper
```

Every declaration carrying the marked annotation is no
longer watched by the public API checks, and neither is anything nested inside it. Only the
occurrence-based checks still apply there: exemption annotations must be explained, and DSL
markers are validated at every visibility.

A supported public declaration must not expose one of those internal types in its signature. The
[`PUBLIC_TYPE_WITH_INTERNAL_API`](./checks/special/public-type-with-internal-api.md) check reports
that mismatch. Either keep the type behind the implementation boundary or mark the exposing
declaration as internal API too.

The marker annotation class itself is ordinary public API and stays watched like any
other declaration, so it still needs a KDoc comment and the rest.

An internal-API annotation that comes from a dependency can't be annotated with
`@InternalAnnotationMarker`. List it in the `apiWatchdog` extension by its fully qualified class
name instead, and it is treated exactly like a marked annotation:

```kotlin
apiWatchdog {
    internalApiAnnotations.add("com.example.dependency.InternalDependencyApi")
}
```

See [Internal API annotations from dependencies](./configuration.md#internal-api-annotations-from-dependencies)
for details and the matching compiler option.

## Configuration

Every `@Intentionally*` annotation, `@InternalAnnotationMarker`, and `ExemptionReason` live in the
`org.jetbrains.kotlinx:kotlin-library-api-watchdog-plugin-annotations` artifact.
Applying the Gradle plugin adds this library as a dependency automatically - no manual dependency declaration is needed.
