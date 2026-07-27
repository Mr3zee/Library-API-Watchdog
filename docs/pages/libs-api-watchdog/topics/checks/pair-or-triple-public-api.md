# Pair and Triple in public API

`PAIR_OR_TRIPLE_PUBLIC_API` reports the tuple types `Pair` and `Triple` in publicly visible
signatures.

| | |
|---|---|
| Diagnostic | `PAIR_OR_TRIPLE_PUBLIC_API` |
| Default severity | Error |
| Gradle property | [`pairOrTriplePublicApi`](configuration.md) |
| Exemption | [`@IntentionallyPairOrTriple`](exemptions.md) |

## What it reports

The check flags `Pair` and `Triple` in return types, property types, parameter types, and type
parameter bounds, including their type arguments (`List<Pair<Int, String>>` exposes the tuple all
the same) and behind a type alias (an alias does not change what clients see). A minimal
triggering example:

```kotlin
// PAIR_OR_TRIPLE_PUBLIC_API
public fun locate(): Pair<Int, Int> = 0 to 0
```

## Rationale

`Pair` and `Triple` name their components `first`/`second`/`third`, so a call site reading
`point.first` or destructuring `val (a, b) = point` learns nothing about what the values mean.
Worse, the shape is fixed: it cannot grow a fourth component or rename a component without
breaking every client in a source-incompatible way, while a purpose-built class can add an
optional property with a default value. See the
[Kotlin API guidelines on object-oriented design for data and state](https://kotlinlang.org/docs/api-guidelines-consistency.html#use-object-oriented-design-for-data-and-state).

## Don't

```kotlin
// PAIR_OR_TRIPLE_PUBLIC_API
public fun dimensions(): Triple<Int, Int, Int> = Triple(0, 0, 0)

// PAIR_OR_TRIPLE_PUBLIC_API
public class Anchor(public val position: Pair<Int, Int>)
```

```kotlin
// The tuple hides behind a type alias and inside a type argument all the same.
public typealias Point = Pair<Int, Int>

// PAIR_OR_TRIPLE_PUBLIC_API
public fun aliased(): Point = 0 to 0

// PAIR_OR_TRIPLE_PUBLIC_API
public fun edges(): List<Pair<Int, Int>> = emptyList() 
```

## Do

```kotlin
public class Dimensions(public val width: Int, public val height: Int, public val depth: Int)

public fun dimensions(): Dimensions = Dimensions(0, 0, 0)

public class Anchor(public val position: Point)

public class Point(public val x: Int, public val y: Int)
```

Notable edge cases:

- A tuple type parameter bound (`<T : Pair<Int, Int>>`) is reported too: it constrains every
  instantiation to the tuple shape, exposing it just like a direct mention.
- A `vararg` parameter's array type is not itself a tuple, but a `Pair`/`Triple` element type
  still is.
- Extension receivers are exempt: `fun Pair<Int, Int>.manhattanLength(): Int` serves a value the
  client already holds instead of handing out a new tuple.
- Overrides are exempt: their signature is fixed by the overridden declaration and reported
  there.

## Exemption

Apply `@IntentionallyPairOrTriple` on the whole declaration, on a single parameter or type
parameter, or on a type usage, where it covers the annotated type and everything nested in it:

```kotlin
@IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN)
public fun rawLocation(): Pair<Int, Int> = 0 to 0

public fun draw(@IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN) at: Pair<Int, Int>): Unit = Unit

public fun corners(): List<@IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN) Pair<Int, Int>> =
    emptyList()
```

## Configuration

```kotlin
apiWatchdog {
    pairOrTriplePublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=PAIR_OR_TRIPLE_PUBLIC_API:warning
```

## See also

- [Use object-oriented design for data and state](https://kotlinlang.org/docs/api-guidelines-consistency.html#use-object-oriented-design-for-data-and-state)
- [Data classes in public API](data-class-public-api.md)
- [Exemptions and internal API](exemptions.md)
