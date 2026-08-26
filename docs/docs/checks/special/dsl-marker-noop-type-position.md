# DSL markers on no-op type positions

`DSL_MARKER_NOOP_TYPE_POSITION` reports a `@DslMarker` annotation written directly on a named
value's type where it has no effect on scope control.

|                  |                                                       |
|------------------|-------------------------------------------------------|
| Diagnostic       | `DSL_MARKER_NOOP_TYPE_POSITION`                       |
| Default severity | Error                                                 |
| Gradle property  | [`dslMarkerNoopTypePosition`](../../configuration.md) |
| Exemption        | none                                                  |

## What it reports

A `@DslMarker` directly on a regular parameter, function return, property, or local variable type
marks a value that is accessed by name at that declaration, so it restricts nothing there.

```kotlin
// !hide-focused
@file:JvmName("Branches")

// !hide-focused
/** Marks branch DSL receivers. */
@DslMarker
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPEALIAS,
)
public annotation class BranchDsl

// !hide-focused
/** Branch accepted by the branch-building DSL. */
public class Branch

// !hide-focused
/** Appends [branch] to the current tree. */
// !diag[/@BranchDsl/] DSL_MARKER_NOOP_TYPE_POSITION ["BranchDsl","parameter type"]
public fun append(branch: @BranchDsl Branch) { }
```

Unlike the API-surface checks, this one also fires on non-public and even internal declarations: an
inert marker misleads the library's own authors just as much as its users.

## Rationale

A marker in a no-op position gives none of the protection `@DslMarker` exists for: inside a nested
builder lambda, an outer builder's members stay implicitly callable, so code can silently call the
wrong scope's functions. See the Kotlin guide on [scope control for DSL markers](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker).


### Don't

```kotlin
// !hide-focused
/** Applies [block] while constructing a tree tag. */
// !hide-focused
@JvmSynthetic
// !diag[/@TreeDsl/] DSL_MARKER_NOOP_TYPE_POSITION ["TreeDsl","return type"]
public fun configure(block: Tag.() -> Unit): @TreeDsl String = ""
```

### Do

```kotlin
// !hide-focused
/** Node whose receiver participates in tree DSL scope control. */
@TreeDsl
public class Tag

// !hide-focused
/** Applies [block] while constructing a tree tag. */
// !hide-focused
@JvmSynthetic
public fun configure(block: Tag.() -> Unit): String = ""
```



### Don't {#dont-2}

```kotlin
// !hide-focused
@file:JvmName("Trees")

// !hide-focused
/** Adds [tag] to the current tree. */
// !diag[/@TreeDsl/] DSL_MARKER_NOOP_TYPE_POSITION ["TreeDsl","parameter type"]
public fun process(tag: @TreeDsl Tag) { }
```

### Do {#do-2}

```kotlin
// !hide-focused
@file:JvmName("Trees")

// no scope control needed for a named value
// !hide-focused
/** Adds [tag] to the current tree. */
public fun process(tag: Tag) { }
```

## Notes

- A context parameter is an implicit value, so a marker on its type is effective and not
  reported: when a nearer implicit value carries the same marker, the parameter is hidden
  from context resolution.
- Markers on supertypes, type parameter bounds, and type alias expansions are effective carriers
  and are not reported: `class Div : @TreeDsl Tag()`, `typealias MarkedTag = @TreeDsl Tag`.
- Markers nested inside type arguments are deliberately accepted. Generic substitution can expose
  the annotated type later: `tags.first()` from a `List<@TreeDsl Tag>` has the annotated type, and
  the marker becomes effective if that value is then used as an implicit receiver (inside the `with` function, for example).

## Exemption

There is no `@Intentionally*` annotation for this diagnostic: a marker on a no-op type position
should normally be moved to an effective position (a receiver, a context parameter, or a supertype)
or removed.

## Configuration

```kotlin
apiWatchdog {
    dslMarkerNoopTypePosition = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=DSL_MARKER_NOOP_TYPE_POSITION:warning
```

## See also

- [Scope control for DSL markers](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
- [DSL markers with no-op targets](./dsl-marker-noop-target.md)
- [DSL markers without explicit targets](./dsl-marker-without-explicit-targets.md)
