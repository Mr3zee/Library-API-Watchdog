# DSL markers with no-op targets

`DSL_MARKER_NOOP_TARGET` reports an explicit `@Target` entry on a `@DslMarker` annotation that
names a target the marker has no effect on.

|                  |                                                                                       |
|------------------|---------------------------------------------------------------------------------------|
| Diagnostic       | `DSL_MARKER_NOOP_TARGET`                                                              |
| Default severity | Error                                                                                 |
| Gradle property  | [`dslMarkerNoopTarget`](../../configuration.md)                                       |
| Exemption        | [`@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility`](../../exemptions.md) |

## What it reports

The check only looks at `@DslMarker`-annotated annotation classes that declare an explicit `@Target`
with noop targets.

```kotlin
// !hide-focused
/** Marks DSL receivers. */
@DslMarker
// !diag[/AnnotationTarget[.]FUNCTION/] DSL_MARKER_NOOP_TARGET ["MyDsl","FUNCTION"]
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class MyDsl
```

## Rationale

`@DslMarker` exists to make [type-safe builder](https://kotlinlang.org/docs/type-safe-builders.html)
receivers unambiguous by hiding outer receivers from an inner scope. That mechanism only looks at
the marker's placement on a class, a type, or a type alias.
It misleads callers into thinking annotating a function or a property also
scopes something, and it misleads the marker's author into thinking the surface is narrower than
it is. See the
[DSL marker design note](https://github.com/Kotlin/KEEP/blob/main/notes/0005-dsl-marker.md) and the
Kotlin docs on [scope control with `@DslMarker`](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker).


### Don't

```kotlin
// This is the shape that broke Ktor's @KtorDsl (KTOR-8901).
// !hide-focused
/** Marks Ktor DSL receivers. */
@DslMarker
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.TYPE,
    // !diag[/AnnotationTarget[.]FUNCTION/] DSL_MARKER_NOOP_TARGET ["KtorDsl","FUNCTION"]
    AnnotationTarget.FUNCTION,
)
public annotation class KtorDsl
```

### Do

```kotlin
// !hide-focused
/** Marks Ktor DSL receivers. */
@DslMarker
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPEALIAS,
)
public annotation class KtorDsl
```


## Notes

- `ANNOTATION_CLASS` is also effective because it is a classifier declaration, like `CLASS`.
- Marker visibility is irrelevant: an `internal` or `private` marker is still applied across the
  library's - possibly public - DSL classes, so markers of any visibility are checked.
- A plain annotation class without `@DslMarker` is outside the scope of this check, regardless of
  its `@Target`.
- A marker without an explicit `@Target` is covered by the separate, related
  [`DSL_MARKER_WITHOUT_EXPLICIT_TARGETS`](./dsl-marker-without-explicit-targets.md), because the
  default target set has its own no-op entries plus forbids `TYPE`/`TYPEALIAS`.

## Exemption

For a marker that already shipped with a no-op target, narrowing `@Target` rejects user code
that applied the marker there - a breaking change. Acknowledge the legacy shape instead:

```kotlin
// !hide-focused
/** Marks Ktor DSL receivers with targets retained for compatibility. */
@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class KtorDsl
```

Wrong marker targets are never good API design, so this annotation bakes its only accepted
reason - backwards compatibility - into its name: it takes no `reason` parameter, just an
optional `description` for extra context. New DSL markers should declare effective
targets instead of reaching for this exemption.

## Configuration

```kotlin
apiWatchdog {
    dslMarkerNoopTarget = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=DSL_MARKER_NOOP_TARGET:warning
```

## See also

- [Scope control with @DslMarker](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
- [DSL marker design note](https://github.com/Kotlin/KEEP/blob/main/notes/0005-dsl-marker.md)
- [DSL markers without explicit targets](./dsl-marker-without-explicit-targets.md)
- [Exemptions and internal API](../../exemptions.md)
