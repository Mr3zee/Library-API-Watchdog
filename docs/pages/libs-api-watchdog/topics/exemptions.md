# Exemptions and internal API

%product% gives library authors two ways to opt out of its checks. A per-declaration
`@Intentionally*` annotation acknowledges that one specific hard-to-evolve shape is a deliberate
choice. A library-wide `@InternalAnnotationMarker` removes a whole subtree of declarations from
consideration because it carries no compatibility contract at all, regardless of its visibility.
Reach for the first when the shape itself is the deliberate part of the API; reach for the second
when a declaration is public only for technical reasons and was never meant to be supported API in
the first place.

## Exempting a single declaration

Each check that has an exemption annotation names it in its "When to exempt" section. Applying the
annotation silences that one check on that one declaration (or, for annotations placed on a type
parameter or type usage, on that one type). But an exemption is not a bare escape hatch: it has to
explain itself.

Every `@Intentionally*` annotation takes a `reason: ExemptionReason` (default `OTHER`) and a
`description: String` (default `""`), with one exception:
`@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility` bakes its only accepted reason into
its name and takes just an optional `description`. For the rest, whether the description may stay
empty depends on the reason:

- `FOR_BACKWARDS_COMPATIBILITY` and `API_DESIGN` explain the exemption on their own; the
  description is optional.
- `INTEROP`, `EXTERNAL_CONTRACT`, `IGNORE_JAVA_INTEROP`, and `OTHER` only categorize the exemption
  - which interop constraint, which external contract, or why this declaration in particular gets
  to ignore Java callers is not obvious from the entry alone - so a non-empty `description` is
  required.

A bare `@IntentionallyOpen` (reason left at `OTHER`, description left empty) or one that spells the
same thing out explicitly - `@IntentionallyOpen(reason = ExemptionReason.OTHER)` - explains
nothing and is rejected by the
[Exemptions without explanation](exemption-without-explanation.md) check. That check is always an
error and cannot be configured or disabled; it fires on every exemption annotation usage, even on
non-public declarations, because leaving any exemption unexplained defeats the point of exemptions.

A well-explained exemption:

```kotlin
@IntentionallyDataClass(
    reason = ExemptionReason.INTEROP,
    description = "Serialized as-is by the legacy RPC layer, which reflects on componentN.",
)
public data class LegacyConfig(
    public val host: String,
    public val port: Int,
)
```

When adopting the watchdog on a library whose API has already shipped, the exemptions for the
existing surface do not have to be written by hand: the Gradle plugin's
[`updateBackwardsCompatibilityExempts` task](gradle-plugin.md#the-updatebackwardscompatibilityexempts-task)
inserts them - with the self-explanatory `FOR_BACKWARDS_COMPATIBILITY` reason - for every
diagnostic the current sources trigger.

## All exemption annotations

| Annotation | Exempts |
|---|---|
| `@IntentionallyOpen` | [Open API without subclass opt-in](open-api-without-subclass-opt-in.md) |
| `@IntentionallyExhaustive` | [Exhaustive public API](exhaustive-public-api.md) |
| `@IntentionallyUndocumented` | [Undocumented public API](undocumented-public-api.md) |
| `@IntentionallyFunctionTypeAlias` | [Function type aliases in public API](function-type-alias-public-api.md) |
| `@IntentionallyDataClass` | [Data classes in public API](data-class-public-api.md) |
| `@IntentionallyWithoutToString` | [Stateful classes without toString](stateful-class-without-to-string.md) |
| `@IntentionallyMutableCollection` | [Mutable collections in public API](mutable-collection-public-api.md) |
| `@IntentionallyPairOrTriple` | [Pair and Triple in public API](pair-or-triple-public-api.md) |
| `@IntentionallyBooleanParameter` | [Boolean parameters in public API](boolean-parameter-public-api.md) |
| `@IntentionallyNullableBoolean` | [Nullable Booleans in public API](nullable-boolean-public-api.md) |
| `@IntentionallyRequiredParameterAfterOptional` | [Required parameters after optional ones](required-parameter-after-optional.md) |
| `@IntentionallyInconsistentParameterOrder` | [Inconsistent parameter order in overloads](inconsistent-parameter-order-in-overloads.md) |
| `@IntentionallyInlinedLogic` | [Inline functions with logic](inline-function-with-logic.md) |
| `@IntentionallyMangledJvmName` | [Mangled JVM names in public API](mangled-jvm-name-public-api.md) |
| `@IntentionallyKotlinOnlyApi` | [Kotlin-only API without JvmSynthetic](kotlin-only-api-without-jvm-synthetic.md) |
| `@IntentionallyNonStaticCompanionApi` | [Companion API without JvmStatic](companion-api-without-jvm-static.md) and [Companion constants without JvmField](companion-constant-without-jvm-field.md) |
| `@IntentionallyDefaultFacadeName` | [Top-level API without JvmName](top-level-api-without-jvm-name.md) |
| `@IntentionallyWithoutJvmOverloads` | [Default parameters without JvmOverloads](default-parameters-without-jvm-overloads.md) |
| `@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility` | [DSL markers with no-op targets](dsl-marker-noop-target.md) and [DSL markers without explicit targets](dsl-marker-without-explicit-targets.md) |

## Marking a whole API surface as internal

Some declarations are public only because the language requires it, not because they are supported
API - reflection helpers behind an opt-in marker, generated glue code, and the like. Rather than
exempting every one of them individually, annotate the library's own internal-API marker annotation
with `@InternalAnnotationMarker`:

```kotlin
@InternalAnnotationMarker
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
public annotation class InternalMyLibraryApi

@InternalMyLibraryApi // Not watched: internal API despite the public visibility.
public class ReflectionHelper
```

Every declaration carrying the marked annotation - directly, or through a type alias of it - is no
longer watched by any check, and neither is anything nested inside it: members, nested classes,
everything. The marker annotation class itself is ordinary public API and stays watched like any
other declaration, so it still needs a KDoc comment and the rest.

`@InternalAnnotationMarker` takes no `reason` or `description` argument at all - it has no
parameters. The marked annotation class documents the internal API surface itself, so it is exempt
from the explanation requirement described above.

Note that `@PublishedApi` declarations are not affected by this distinction between source
visibility and API surface in the opposite direction: they are `internal` in source, but a public
inline function can expose them to clients, so they are watched exactly like public declarations.

## Where the annotations come from

Every `@Intentionally*` annotation, `@InternalAnnotationMarker`, and `ExemptionReason` live in the
`org.jetbrains.kotlinx.libs.api.watchdog` package of the `plugin-annotations` library. Applying the
Gradle plugin adds this library as a dependency automatically - no manual dependency declaration is
needed, just the import.
