# DSL markers without explicit targets

`DSL_MARKER_WITHOUT_EXPLICIT_TARGETS` reports a `@DslMarker` annotation class that declares no
explicit `@Target`.

|                  |                                                                                 |
|------------------|---------------------------------------------------------------------------------|
| Diagnostic       | `DSL_MARKER_WITHOUT_EXPLICIT_TARGETS`                                           |
| Default severity | Error                                                                           |
| Gradle property  | [`dslMarkerWithoutExplicitTargets`](../../configuration.md)                           |
| Exemption        | [`@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility`](../../exemptions.md) |

## What it reports

Any annotation class annotated with `@DslMarker` that has no `@Target` of its own:

```kotlin
// !hide-focused
/** Marks DSL receivers. */
@DslMarker
// !diag[/DefaultTargetsDsl/] DSL_MARKER_WITHOUT_EXPLICIT_TARGETS ["DefaultTargetsDsl"]
public annotation class DefaultTargetsDsl
```

## Rationale

[DSL marker scope control](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
only reacts to a marker found on a classifier declaration (`CLASS`, `ANNOTATION_CLASS`), a type
usage (`TYPE`), or a type alias (`TYPEALIAS`). The default target set - what a class gets when it
declares no `@Target` at all - covers neither `TYPE` nor `TYPEALIAS`, so a marker left without an
explicit `@Target` can never be applied where it would restrict an implicit receiver. Users are
free to put it on parameters, properties, or return types instead, where it silently
restricts nothing and gives a false sense of scope control.


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
- A marker with an explicit `@Target` that lists no-op targets is covered by the separate, related
  [DSL markers with no-op targets](./dsl-marker-noop-target.md) check.
- A plain annotation class without `@DslMarker` is outside the scope of this check, regardless of
  its `@Target`.

## Exemption

For an already-published marker, adding a `@Target` at all is a breaking change: it rejects
user code that currently applies the marker to a now-disallowed target. Acknowledge the legacy
shape with `@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility` instead of fixing it:

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
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=DSL_MARKER_WITHOUT_EXPLICIT_TARGETS:warning
```

## See also

- [Scope control: @DslMarker](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
- [DSL markers with no-op targets](./dsl-marker-noop-target.md), the sibling check for markers that
  declare an explicit `@Target` but still list ineffective ones
- [Exemptions and internal API](../../exemptions.md)
