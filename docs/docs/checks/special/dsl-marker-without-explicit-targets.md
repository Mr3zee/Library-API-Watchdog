# DSL markers without explicit targets

`DSL_MARKER_WITHOUT_EXPLICIT_TARGETS` reports a `@DslMarker` annotation class that declares no
explicit annotation targets, either because `@Target` is absent or because it is empty.

|                  |                                                                                       |
|------------------|---------------------------------------------------------------------------------------|
| Diagnostic       | `DSL_MARKER_WITHOUT_EXPLICIT_TARGETS`                                                 |
| Default severity | Error                                                                                 |
| Gradle property  | [`dslMarkerWithoutExplicitTargets`](../../configuration.md)                           |
| Exemption        | [`@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility`](../../exemptions.md) |

## What it reports

Any annotation class annotated with `@DslMarker` that has no `@Target` of its own or declares
an empty `@Target()`:

```kotlin
// !hide-focused
/** Marks DSL receivers. */
@DslMarker
// !diag[/DefaultTargetsDsl/] DSL_MARKER_WITHOUT_EXPLICIT_TARGETS ["DefaultTargetsDsl"]
public annotation class DefaultTargetsDsl

// !hide-focused
/** Marks no declarations because its target set is empty. */
@DslMarker
@Target()
// !diag[/EmptyTargetsDsl/] DSL_MARKER_WITHOUT_EXPLICIT_TARGETS ["EmptyTargetsDsl"]
public annotation class EmptyTargetsDsl
```

## Rationale

[DSL marker scope control](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
only reacts to a marker found on a classifier declaration (`CLASS`, `ANNOTATION_CLASS`), a type
usage (`TYPE`), or a type alias (`TYPEALIAS`). The default target set includes `CLASS`, but omits
`TYPE` and `TYPEALIAS` while allowing parameters, properties, functions, and other positions where
the marker has no effect. An empty target set is worse: the marker can't be applied anywhere.
An explicit, non-empty target set makes the effective placements available without advertising
ineffective ones.


### Don't

```kotlin
// !hide-focused
/** Marks HTML DSL receivers. */
@DslMarker
// !diag[/HtmlDsl/] DSL_MARKER_WITHOUT_EXPLICIT_TARGETS ["HtmlDsl"]
public annotation class HtmlDsl
```

### Do

```kotlin
// !hide-focused
/** Marks HTML DSL receivers. */
@DslMarker
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPEALIAS,
)
public annotation class HtmlDsl

// A narrower, still-effective subset is fine too
// !hide-focused
/** Marks Ktor DSL receivers. */
@DslMarker
@Target(AnnotationTarget.CLASS)
public annotation class KtorDsl
```


## Notes

- `ANNOTATION_CLASS` is also effective because it is a classifier declaration, like `CLASS`.
- A marker with an explicit `@Target` that lists no-op targets is covered by the separate
  [`DSL_MARKER_NOOP_TARGET`](./dsl-marker-noop-target.md) check instead.
- A plain annotation class without `@DslMarker` is outside the scope of this check, regardless of
  its `@Target`.

## Exemption

<!-- diagnostic-exemption: DSL_MARKER_WITHOUT_EXPLICIT_TARGETS -->
If this API shape is intentional, apply
`@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility` to the DSL marker.

Use the exemption when the marker is already published and adding a `@Target` would reject existing
user code:

```kotlin
// !hide-focused
/** Marks HTML DSL receivers with default targets retained for compatibility. */
@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility(
  description = "Published without targets in 1.0.",
)
@DslMarker
public annotation class HtmlDsl
```

Wrong marker targets are never good API design, so this annotation bakes its only accepted
reason - backwards compatibility - into its name: it takes no `reason` parameter, just an
optional `description` for extra context. New DSL markers should declare effective
targets instead of reaching for this exemption.

## Configuration

```kotlin
apiWatchdog {
    dslMarkerWithoutExplicitTargets = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=DSL_MARKER_WITHOUT_EXPLICIT_TARGETS:warning
```

## See also

- [Scope control: @DslMarker](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
- [DSL markers with no-op targets](./dsl-marker-noop-target.md)
- [Exemptions and internal API](../../exemptions.md)
