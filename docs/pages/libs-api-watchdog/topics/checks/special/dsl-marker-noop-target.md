# DSL markers with no-op targets

`DSL_MARKER_NOOP_TARGET` reports an explicit `@Target` entry on a `@DslMarker` annotation that
names a target the marker has no effect on.

| | |
|---|---|
| Diagnostic | `DSL_MARKER_NOOP_TARGET` |
| Default severity | Error |
| Gradle property | [`dslMarkerNoopTarget`](configuration.md) |
| Exemption | [`@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility`](exemptions.md) |

## What it reports

Receiver scope control only reacts to a `@DslMarker` found on a classifier declaration (`CLASS`,
`ANNOTATION_CLASS`), a type usage (`TYPE`), or a type alias (`TYPEALIAS`). Any other target listed
in the marker's `@Target` lets a user apply the marker somewhere it silently restricts nothing,
giving a false sense of scope control. The check only looks at `@DslMarker`-annotated annotation
classes that declare an explicit `@Target`, and reports each no-op entry separately:

```kotlin
@DslMarker
// DSL_MARKER_NOOP_TARGET
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class MyDsl
```

## Rationale

`@DslMarker` exists to make [type-safe builder](https://kotlinlang.org/docs/type-safe-builders.html)
receivers unambiguous by hiding outer receivers from an inner scope. That mechanism only looks at
the marker's placement on a class, a type, or a type alias; any other allowed target is dead
weight in the API. It misleads callers into thinking annotating a function or a property also
scopes something, and it misleads the marker's author into thinking the surface is narrower than
it is. See the
[DSL marker design note](https://github.com/Kotlin/KEEP/blob/main/notes/0005-dsl-marker.md) and the
Kotlin docs on [scope control with `@DslMarker`](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker).

## Don't

```kotlin
// This is the shape that broke Ktor's @KtorDsl (KTOR-8901): FUNCTION doesn'thing.
@DslMarker
// DSL_MARKER_NOOP_TARGET
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
public annotation class KtorDsl
```

```kotlin
// Every no-op target is reported, not just the first one.
@DslMarker
// DSL_MARKER_NOOP_TARGET
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.CONSTRUCTOR,
)
public annotation class ManyNoopsDsl
```

## Do

```kotlin
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
public annotation class TidyDsl

@DslMarker
@Target(AnnotationTarget.CLASS)
public annotation class ClassOnlyDsl
```

## Notes

- `ANNOTATION_CLASS` is effective too: it is a classifier declaration like `CLASS`.
- The named array form, `@Target(allowedTargets = [...])`, is checked the same way as the
  positional form.
- Marker visibility is irrelevant: an `internal` or `private` marker is still applied across the
  library's - possibly public - DSL classes, so markers of any visibility are checked.
- An annotation class without `@DslMarker` is out of scope entirely, no matter what its `@Target`
  allows.
- A marker with no explicit `@Target` at all is a different diagnostic,
  [`DSL_MARKER_WITHOUT_EXPLICIT_TARGETS`](dsl-marker-without-explicit-targets.md), because the
  default target set has its own no-op entries plus forbids `TYPE`/`TYPEALIAS`.

## Exemption

For a marker that already shipped with a no-op target, narrowing `@Target` rejects user code
that applied the marker there - a breaking change. Acknowledge the legacy shape instead:

```kotlin
@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class LegacyNoopTargetDsl
```

Wrong marker targets are never good API design, so this exemption bakes its only accepted reason
into its name instead of taking an `ExemptionReason`: it needs no `reason` argument, and its
optional `description` may stay empty for a bare usage like the one above. The annotation targets
the marker's own annotation class declaration and, once applied, also suppresses
[`DSL_MARKER_WITHOUT_EXPLICIT_TARGETS`](dsl-marker-without-explicit-targets.md) on the same marker.
New markers should declare effective targets instead of reaching for this exemption.

## Configuration

```kotlin
apiWatchdog {
    dslMarkerNoopTarget = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=DSL_MARKER_NOOP_TARGET:warning
```

## See also

- [Scope control with @DslMarker](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
- [DSL marker design note](https://github.com/Kotlin/KEEP/blob/main/notes/0005-dsl-marker.md)
- [DSL markers without explicit targets](dsl-marker-without-explicit-targets.md)
- [Exemptions and internal API](exemptions.md)
