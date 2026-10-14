# DSL markers without explicit targets

|                  |                                                                                       |
|------------------|---------------------------------------------------------------------------------------|
| Diagnostic       | `DSL_MARKER_WITHOUT_EXPLICIT_TARGETS`                                                 |
| Default severity | Error                                                                                 |
| Gradle property  | [`dslMarkerWithoutExplicitTargets`](../../configuration.md)                           |
| Exemption        | [`@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility`](../../exemptions.md) |

## What it reports

A `@DslMarker` annotation class without `@Target`, or with an empty `@Target()`:

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

The default targets omit `TYPE` and `TYPEALIAS` and let callers apply markers with no [DSL scope
effect](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker). An empty
target set prevents every usage.

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
```

## Notes

- The check applies to markers of any visibility.
- The check accepts any non-empty subset of `CLASS`, `ANNOTATION_CLASS`, `TYPE`, and `TYPEALIAS`.
- [DSL markers with no-op targets](./dsl-marker-noop-target.md) checks explicit ineffective targets.

## Exemption

<!-- diagnostic-exemption: DSL_MARKER_WITHOUT_EXPLICIT_TARGETS -->
If this is intended, apply
`@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility` to the DSL marker.

Use the exemption if an explicit target set on a published marker rejects existing user code:

```kotlin
// !hide-focused
/** Marks HTML DSL receivers with default targets retained for compatibility. */
@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility(
  description = "Published without targets in 1.0.",
)
@DslMarker
public annotation class HtmlDsl
```

The annotation has no `reason` parameter. Its `description` is optional.

## Configuration

```kotlin build.gradle.kts
apiWatchdog {
    dslMarkerWithoutExplicitTargets = WatchdogSeverity.ERROR
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=DSL_MARKER_WITHOUT_EXPLICIT_TARGETS:error
```

## See also

- [Scope control: @DslMarker](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
- [DSL markers with no-op targets](./dsl-marker-noop-target.md)
- [Exemptions and internal API](../../exemptions.md)
