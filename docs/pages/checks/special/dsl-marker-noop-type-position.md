# DSL markers on no-op type positions

|                  |                                                       |
|------------------|-------------------------------------------------------|
| Diagnostic       | `DSL_MARKER_NOOP_TYPE_POSITION`                       |
| Default severity | Error                                                 |
| Gradle property  | [`dslMarkerNoopTypePosition`](../../configuration.md) |
| Exemption        | none                                                  |

## What it reports

Reports a DSL marker directly on these types:

- A regular parameter type.
- A function return type.
- A property type.
- A local variable type.

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
// !diag[/@BranchDsl/] DSL_MARKER_NOOP_TYPE_POSITION ["BranchDsl","$parameterType"]
public fun append(branch: @BranchDsl Branch) { }
```

## Rationale

[DSL scope control](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
restricts implicit receivers. A marker on a named value doesn't prevent calls to an outer receiver.

### Don't

```kotlin
// !hide-focused
/** Applies [block] while constructing a tree tag. */
// !hide-focused
@JvmSynthetic
// !diag[/@TreeDsl/] DSL_MARKER_NOOP_TYPE_POSITION ["TreeDsl","$returnType"]
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

## Notes

The check applies to declarations of any visibility. It skips:

- Function types with a receiver or context parameters.
- Receiver and context parameter types, supertypes, type parameter bounds, and type alias
  expansions.
- Nested type arguments, such as `List<@TreeDsl Tag>`.

## Configuration

```kotlin
apiWatchdog {
    dslMarkerNoopTypePosition = WatchdogSeverity.ERROR
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=DSL_MARKER_NOOP_TYPE_POSITION:error
```

## See also

- [Scope control for DSL markers](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
- [DSL markers with no-op targets](./dsl-marker-noop-target.md)
- [DSL markers without explicit targets](./dsl-marker-without-explicit-targets.md)
