# Pair and Triple in public API

|                  |                                                  |
|------------------|--------------------------------------------------|
| Diagnostic       | `PAIR_OR_TRIPLE_PUBLIC_API`                      |
| Default severity | Error                                            |
| Gradle property  | [`pairOrTriplePublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyPairOrTriple`](../exemptions.md) |

## What it reports

`Pair` or `Triple` in these parts of the public API, including nested type arguments and expanded type aliases:

- Function return types and property types.
- Value and context parameter types, including constructor parameters.
- Type parameter bounds.

```kotlin
// !hide-focused
@file:JvmName("Locations")

// !hide-focused
/** Returns an unnamed location. */
// !diag[/Pair<Int, Int>/] PAIR_OR_TRIPLE_PUBLIC_API ["$function","locate","Pair","$pairComponents"]
public fun locate(): Pair<Int, Int> = 0 to 0
```

## Rationale

`first`, `second`, and `third` don't name the components' meanings.
A domain class names each component and can add properties while its return type stays the same.

### Don't

```kotlin
// !hide-focused
@file:JvmName("Geometry")

// !hide-focused
/** Returns unnamed dimensions. */
// !diag[/Triple<Int, Int, Int>/] PAIR_OR_TRIPLE_PUBLIC_API ["$function","dimensions","Triple","$tripleComponents"]
public fun dimensions(): Triple<Int, Int, Int> = Triple(0, 0, 0)
```

### Do

```kotlin
// !hide-focused
@file:JvmName("Geometry")

// !hide-focused
/** Extents of a three-dimensional object. */
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
public class Dimensions(
    // !hide-focused
    /** Horizontal extent in pixels. */
    public val width: Int,
    // !hide-focused
    /** Vertical extent in pixels. */
    public val height: Int,
    // !hide-focused
    /** Front-to-back extent in pixels. */
    public val depth: Int,
)

// !hide-focused
/** Returns the dimensions. */
public fun dimensions(): Dimensions = Dimensions(0, 0, 0)
```

## Notes

The check skips:

- Extension receivers on these types.
- Overrides.
- `@PublishedApi internal` declarations.

## Exemption

<!-- diagnostic-exemption: PAIR_OR_TRIPLE_PUBLIC_API -->
If this is intended, apply `@IntentionallyPairOrTriple` to the declaration,
parameter, or type usage.

Use the exemption when the declaration is part of the API design or must stay for backwards compatibility.

```kotlin
// !hide-focused
@file:JvmName("Geometry")

// !hide-focused
/** Returns dimensions as a tuple. */
@IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN)
public fun dimensions(): Triple<Int, Int, Int> = Triple(0, 0, 0)
```

## Configuration

```kotlin
apiWatchdog {
    pairOrTriplePublicApi = WatchdogSeverity.ERROR
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=PAIR_OR_TRIPLE_PUBLIC_API:error
```

## See also

- [Use object-oriented design for data and state](https://kotlinlang.org/docs/api-guidelines-consistency.html#use-object-oriented-design-for-data-and-state)
- [Data classes in public API](./data-class-public-api.md)
- [Exemptions and internal API](../exemptions.md)
