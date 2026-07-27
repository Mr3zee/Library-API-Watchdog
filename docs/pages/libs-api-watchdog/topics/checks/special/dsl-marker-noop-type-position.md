# DSL markers on no-op type positions

`DSL_MARKER_NOOP_TYPE_POSITION` reports a `@DslMarker` annotation written directly on a type
position where it has no effect on scope control.

| | |
|---|---|
| Diagnostic | `DSL_MARKER_NOOP_TYPE_POSITION` |
| Default severity | Error |
| Gradle property | [`dslMarkerNoopTypePosition`](configuration.md) |
| Exemption | none |

## What it reports

Scope control from a `@DslMarker` only reacts to a marker found on the type of an implicit value:
a receiver type, a context parameter type, or a function type that itself has such an implicit
value (there the marker propagates to it). A marker written on a plain parameter type, a return
type, or a property or variable type marks a value that is only ever accessed by name, so it
restricts nothing:

```kotlin
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
public annotation class TreeDsl

public open class Tag

// DSL_MARKER_NOOP_TYPE_POSITION
public fun process(tag: @TreeDsl Tag): Unit = Unit
```

Unlike the API-surface checks, this one also fires on non-public and even internal declarations: an
inert marker misleads the library's own authors just as much as its users.

## Rationale

A marker in a no-op position gives none of the protection `@DslMarker` exists for: inside a nested
builder lambda, an outer builder's members stay implicitly callable, so code can silently call the
wrong scope's functions. That mistake only surfaces as confusing runtime behavior, not as a compile
error, so it is expensive to debug - and it defeats the whole point of writing scope control in the
first place. See the Kotlin guide on
[scope control for DSL markers](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker).

## Don't

```kotlin
// DSL_MARKER_NOOP_TYPE_POSITION
public fun process(tag: @TreeDsl Tag): Unit = Unit
```

## Do

```kotlin
// no scope control needed for a named value
public fun process(tag: Tag): Unit = Unit
```

## Don't {id="dont-2"}

```kotlin
// A function type without a receiver has no implicit value to propagate the marker to.
// 
// DSL_MARKER_NOOP_TYPE_POSITION
public fun configure(block: @TreeDsl () -> Unit): Unit = block()
```

## Do {id="do-2"}

```kotlin
// The marker on the function type now propagates to its receiver.
public fun configure(block: @TreeDsl Tag.() -> Unit): Unit = Tag().block()
```

Notable edge cases and deliberate exceptions:

- A context parameter's type is an implicit value just like a receiver, so a marker there is
  effective and not flagged.
- Markers on supertypes, type parameter bounds, and type alias expansions are effective carriers
  and stay exempt: `class Div : @TreeDsl Tag()`, `typealias MarkedTag = @TreeDsl Tag`.
- A marker nested inside a type argument is not analyzed at all (`List<@TreeDsl Tag>` triggers
  nothing), which is a known limitation rather than an endorsement.
- A `val`/`var` primary constructor parameter is reported once, not twice, even though it also
  produces a matching property under the hood.

## Exemption

There is no `@Intentionally*` annotation for this diagnostic: a marker on a no-op type position
never restricts anything, so keeping it there as-is is never a deliberate design choice. Fix it by
moving the marker to an effective position (a receiver, a context parameter, or a supertype) or by
removing it.

The one legitimate reason to keep a marker exactly where it is reported is deliberate flow-through:
a value whose type carries the marker can still become a scoped implicit receiver later through
type inference (`with(value) { ... }`), even though the position itself is inert. Suppress the
diagnostic on that declaration with `@Suppress("DSL_MARKER_NOOP_TYPE_POSITION")` if that flow-through
use is intended. To silence the check project-wide instead, lower its severity with the Gradle
property below; there is no other per-declaration escape hatch.

## Configuration

```kotlin
apiWatchdog {
    dslMarkerNoopTypePosition = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=DSL_MARKER_NOOP_TYPE_POSITION:warning
```

## See also

- [Scope control for DSL markers](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
- [DSL markers with no-op targets](dsl-marker-noop-target.md)
- [DSL markers without explicit targets](dsl-marker-without-explicit-targets.md)
