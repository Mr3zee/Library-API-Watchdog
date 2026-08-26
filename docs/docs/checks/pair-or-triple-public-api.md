# Pair and Triple in public API

`PAIR_OR_TRIPLE_PUBLIC_API` reports the tuple types `Pair` and `Triple` in publicly visible
signatures.

|                  |                                                  |
|------------------|--------------------------------------------------|
| Diagnostic       | `PAIR_OR_TRIPLE_PUBLIC_API`                      |
| Default severity | Error                                            |
| Gradle property  | [`pairOrTriplePublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyPairOrTriple`](../exemptions.md) |

## What it reports

The check reports `Pair` and `Triple` in return types, property types, parameter types, and type
parameter bounds, including their type arguments (like `List<Pair<Int, String>>`):

```kotlin
// !hide-focused
@file:JvmName("Locations")

// !hide-focused
/** Returns an unnamed location. */
// !diag[/Pair<Int, Int>/] PAIR_OR_TRIPLE_PUBLIC_API ["function","locate","Pair"]
public fun locate(): Pair<Int, Int> = 0 to 0
```

## Rationale

`Pair` and `Triple` name their components `first`/`second`/`third`, so a call site reading
`point.first` or destructuring `val (a, b) = point` learns nothing about what the values mean.
Worse, the shape is fixed: it can't grow a fourth component or rename a component without
breaking every user in a source-incompatible way, while a purpose-built class can add an
optional property with a default value. See the
[Kotlin API guidelines on object-oriented design for data and state](https://kotlinlang.org/docs/api-guidelines-consistency.html#use-object-oriented-design-for-data-and-state).


### Don't

```kotlin
// !hide-focused
@file:JvmName("Geometry")

// !hide-focused
/** Returns unnamed dimensions. */
// !diag[/Triple<Int, Int, Int>/] PAIR_OR_TRIPLE_PUBLIC_API ["function","dimensions","Triple"]
public fun dimensions(): Triple<Int, Int, Int> = Triple(0, 0, 0)
```

### Do

```kotlin
// !hide-focused
@file:JvmName("Geometry")

// !hide-focused(1:7)
/**
 * Extents of a three-dimensional object.
 *
 * @property width horizontal extent in pixels.
 * @property height vertical extent in pixels.
 * @property depth front-to-back extent in pixels.
 */
// !hide-focused
@Poko
public class Dimensions(
    public val width: Int,
    public val height: Int,
    public val depth: Int,
)

// !hide-focused
/** Returns the dimensions. */
public fun dimensions(): Dimensions = Dimensions(0, 0, 0)
```

### Don't

```kotlin
// !hide-focused(1:5)
/**
 * Attaches content to an unnamed coordinate pair.
 *
 * @property position horizontal and vertical anchor coordinates.
 */
// !hide-focused
@Poko
// !diag[/Pair<Int, Int>/] PAIR_OR_TRIPLE_PUBLIC_API ["property","position","Pair"]
public class Anchor(public val position: Pair<Int, Int>)
```

### Do

```kotlin
// !hide-focused(1:6)
/**
 * A position in Cartesian coordinate space.
 *
 * @property x distance from the vertical axis.
 * @property y distance from the horizontal axis.
 */
// !hide-focused
@Poko
public class Point(
    public val x: Int,
    public val y: Int,
)

// !hide-focused(1:5)
/**
 * Attaches content to a point in Cartesian space.
 *
 * @property position point at which the content is anchored.
 */
// !hide-focused
@Poko
public class Anchor(public val position: Point)
```

### Don't

```kotlin
// !hide-focused
@file:JvmName("Geometry")

// !hide-focused
/** Returns unnamed edges. */
// !diag[/List<Pair<Int, Int>>/] PAIR_OR_TRIPLE_PUBLIC_API ["function","edges","Pair"]
public fun edges(): List<Pair<Int, Int>> = emptyList()
```

### Do

```kotlin
// !hide-focused
@file:JvmName("Geometry")

// !hide-focused(1:6)
/**
 * A position in Cartesian coordinate space.
 *
 * @property x distance from the vertical axis.
 * @property y distance from the horizontal axis.
 */
// !hide-focused(1:6)
@Poko
public class Point(
  public val x: Int,
  public val y: Int,
)

// !hide-focused
/** Returns edges. */
public fun edges(): List<Point> = emptyList()
```

## Notes

- A tuple type parameter bound (`<T : Pair<Int, Int>>`) is reported too: it constrains every
  instantiation to the tuple shape, exposing it just like a direct mention.
- Extension receivers are not reported: `fun Pair<Int, Int>.manhattanLength(): Int` serves a
  value the user already holds instead of handing out a new tuple.
- Overrides are not reported: their signature is fixed by the overridden declaration, which is
  reported instead.
- `@PublishedApi internal` declarations are not reported because their tuple types do not cross the
  supported source API boundary.

## Exemption

Apply `@IntentionallyPairOrTriple` on the whole declaration when the usage of a tuple type is intended:

```kotlin
// !hide-focused
@file:JvmName("Geometry")

// !hide-focused
/** Returns deliberately unnamed dimensions. */
@IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN)
public fun dimensions(): Triple<Int, Int, Int> = Triple(0, 0, 0)
```

## Configuration

```kotlin
apiWatchdog {
    pairOrTriplePublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=PAIR_OR_TRIPLE_PUBLIC_API:warning
```

## See also

- [Use object-oriented design for data and state](https://kotlinlang.org/docs/api-guidelines-consistency.html#use-object-oriented-design-for-data-and-state)
- [Data classes in public API](./data-class-public-api.md)
- [Exemptions and internal API](../exemptions.md)
