# DSL markers without explicit targets

`DSL_MARKER_WITHOUT_EXPLICIT_TARGETS` reports a `@DslMarker` annotation class that declares no
explicit `@Target`.

| | |
|---|---|
| Diagnostic | `DSL_MARKER_WITHOUT_EXPLICIT_TARGETS` |
| Default severity | Error |
| Gradle property | [`dslMarkerWithoutExplicitTargets`](configuration.md) |
| Exemption | [`@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility`](exemptions.md) |

## What it reports

Any annotation class annotated with `@DslMarker` that has no `@Target` of its own:

```kotlin
// DSL_MARKER_WITHOUT_EXPLICIT_TARGETS
@DslMarker
public annotation class DefaultTargetsDsl
```

Without an explicit `@Target`, the annotation falls back to the default target set, which allows
nine targets on which a DSL marker has no effect (functions, properties, and the like) while
forbidding the two targets that are actually effective: `TYPE` and `TYPEALIAS`. The marker's own
visibility doesn't matter - even an internal or private marker is applied across the library's
possibly public DSL classes, so it is checked all the same.

## Rationale

[DSL marker scope control](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
only reacts to a marker found on a classifier declaration (`CLASS`, `ANNOTATION_CLASS`), a type
usage (`TYPE`), or a type alias (`TYPEALIAS`). The default target set - what a class gets when it
declares no `@Target` at all - covers neither `TYPE` nor `TYPEALIAS`, so a marker left without an
explicit `@Target` can never be applied where it would restrict an implicit receiver. Users are
free to sprinkle it on parameters, properties, or return types instead, where it silently
restricts nothing and gives a false sense of scope control.

## Don't

```kotlin
// DSL_MARKER_WITHOUT_EXPLICIT_TARGETS
@DslMarker
public annotation class HtmlDsl
```

## Do

```kotlin
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
public annotation class HtmlDsl
```

A narrower, still-effective subset is fine too:

```kotlin
@DslMarker
@Target(AnnotationTarget.CLASS)
public annotation class HtmlDsl
```

Notable edge cases:

- `ANNOTATION_CLASS` counts as effective too, since it is a classifier declaration:
  `@Target(AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)` needs no exemption.
- An explicit `@Target` that still lists no-op targets is a separate, related check; see
  [DSL markers with no-op targets](dsl-marker-noop-target.md).
- A plain annotation class without `@DslMarker` is never checked here.

## Exemption

For an already-published marker, adding a `@Target` at all is a breaking change: it rejects
user code that currently applies the marker to a now-disallowed target. Acknowledge the legacy
shape with `@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility` instead of fixing it:

```kotlin
@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility(description = "Published without targets in 1.0.")
@DslMarker
public annotation class LegacyDsl
```

Wrong marker targets are never good API design, so this annotation bakes its only accepted
reason - backwards compatibility - into its name: it takes no `reason` parameter, just an
optional `description` for extra context, and a bare `@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility`
needs no description either. It targets the annotation class declaration only, and also
suppresses `DSL_MARKER_NOOP_TARGET` on the same marker. New DSL markers should declare effective
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
- [DSL markers with no-op targets](dsl-marker-noop-target.md), the sibling check for markers that
  declare an explicit `@Target` but still list ineffective ones
- [Exemptions and internal API](exemptions.md)
