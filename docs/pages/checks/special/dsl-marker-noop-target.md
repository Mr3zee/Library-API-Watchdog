# DSL markers with no-op targets

|                  |                                                                                       |
|------------------|---------------------------------------------------------------------------------------|
| Diagnostic       | `DSL_MARKER_NOOP_TARGET`                                                              |
| Default severity | Error                                                                                 |
| Gradle property  | [`dslMarkerNoopTarget`](../../configuration.md)                                       |
| Exemption        | [`@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility`](../../exemptions.md) |

## What it reports

Reports each explicit `@Target` entry on a `@DslMarker` annotation outside this set:

- `CLASS`.
- `ANNOTATION_CLASS`.
- `TYPE`.
- `TYPEALIAS`.

```kotlin
// !hide-focused
/** Marks DSL receivers. */
@DslMarker
// !diag[/AnnotationTarget[.]FUNCTION/] DSL_MARKER_NOOP_TARGET ["MyDsl","FUNCTION"]
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class MyDsl
```

## Rationale

[DSL scope control](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
ignores markers on functions and properties. Such targets let callers apply markers that don't
restrict outer receivers.

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

- The check applies to markers of any visibility.
- For missing or empty targets, [DSL markers without explicit
  targets](./dsl-marker-without-explicit-targets.md) applies.

## Exemption

<!-- diagnostic-exemption: DSL_MARKER_NOOP_TARGET -->
If this is intended, apply
`@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility` to the DSL marker.

Use the exemption if a narrower target set on a published marker rejects existing user code:

```kotlin
// !hide-focused
/** Marks Ktor DSL receivers with targets retained for compatibility. */
@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class KtorDsl
```

The annotation has no `reason` parameter. Its `description` is optional.

## Configuration

```kotlin
apiWatchdog {
    dslMarkerNoopTarget = WatchdogSeverity.ERROR
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=DSL_MARKER_NOOP_TARGET:error
```

## See also

- [Scope control with @DslMarker](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
- [DSL marker design note](https://github.com/Kotlin/KEEP/blob/main/notes/0005-dsl-marker.md)
- [DSL markers without explicit targets](./dsl-marker-without-explicit-targets.md)
- [Exemptions and internal API](../../exemptions.md)
