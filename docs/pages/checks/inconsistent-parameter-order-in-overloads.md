# Inconsistent parameter order in overloads

`INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS` reports two overloads of the same public callable
whose same-named parameters occupy different positions.

|                  |                                                                |
|------------------|----------------------------------------------------------------|
| Diagnostic       | `INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS`                    |
| Default severity | Error                                                          |
| Gradle property  | [`inconsistentParameterOrderInOverloads`](../configuration.md) |
| Exemption        | [`@IntentionallyInconsistentParameterOrder`](../exemptions.md) |

## What it reports

For each pair of overloads in the public API that share at least two parameter names, the check
compares the positions of those names. It reports swaps and extra parameters inserted before
or between shared parameters. Extra parameters at the end are allowed.

The diagnostic shows every overload that shares at least two parameter names with this declaration.
Its table has a column for each overload and a row for each shared parameter whose position differs.
If an overload lacks a parameter in a row, its cell contains `absent`.
Positions start at 1. "This overload" is the declaration with the diagnostic.

No overload defines the required order. Align the shared parameter positions across the group
to clear the reports.

```kotlin
// !hide-focused
@file:JvmName("Movement")

// !hide-focused
/** Moves to ([x], [y]). */
// !diag[/move/] INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS ["$twoParameterOverload(move,x: Int,y: Int)","$otherThreeParameterOverload(move,x: Int,scale: Double,y: Int)","$parameterPosition(y,2nd,3rd)","$function"]
public fun move(x: Int, y: Int) { }

// !hide-focused
/** Moves to ([x], [y]) with [scale]. */
// !diag[/move/] INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS ["$threeParameterOverload(move,x: Int,scale: Double,y: Int)","$otherTwoParameterOverload(move,x: Int,y: Int)","$parameterPosition(y,3rd,2nd)","$function"]
public fun move(x: Int, scale: Double, y: Int) { }
```

## Rationale

Callers often reuse the parameter order of one overload in another. An overload that expects
`y, x` instead of `x, y` can bind positional arguments to the wrong parameters. If their types
match, the mistake still compiles.

An extra parameter between shared parameters also changes their positions. For `move(x, y)`
and `move(x, scale, y)`, move `scale` after `y` to give both overloads the same shared positions.
The Kotlin library authors' guide recommends
[consistent parameter order, naming, and usage](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage).


### Don't

```kotlin
// !hide-focused
@file:JvmName("Drawing")

// !hide-focused
/** Draws at ([x], [y]). */
// !diag[/draw/] INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS ["$twoParameterOverload(draw,x: Int,y: Int)","$otherThreeParameterOverload(draw,y: Int,x: Int,scale: Double)","$twoParameterPositions(x,1st,2nd,y,2nd,1st)","$function"]
public fun draw(x: Int, y: Int) { }

// !hide-focused
/** Draws at ([x], [y]) with [scale]. */
// !diag[/draw/] INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS ["$threeParameterOverload(draw,y: Int,x: Int,scale: Double)","$otherTwoParameterOverload(draw,x: Int,y: Int)","$twoParameterPositions(y,1st,2nd,x,2nd,1st)","$function"]
public fun draw(y: Int, x: Int, scale: Double) { }
```

### Do

```kotlin
// !hide-focused
@file:JvmName("Drawing")

// !hide-focused
/** Draws at ([x], [y]). */
public fun draw(x: Int, y: Int) { }

// !hide-focused
/** Draws at ([x], [y]) with [scale]. */
public fun draw(x: Int, y: Int, scale: Double) { }
```



### Don't {#dont-2}

```kotlin
// !hide-focused
/** Rectangle defined by its horizontal and vertical extents. */
// !diag[/Rect/] INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS ["$twoParameterOverload(Rect,width: Int,height: Int)","$otherThreeParameterOverload(Rect,height: Int,width: Int,scale: Double)","$twoParameterPositions(width,1st,2nd,height,2nd,1st)","$constructor"]
public class Rect(width: Int, height: Int) {
    // !hide-focused
    /** Creates a rectangle and applies [scale] to both extents. */
    // !diag[/constructor/] INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS ["$threeParameterOverload(Rect,height: Int,width: Int,scale: Double)","$otherTwoParameterOverload(Rect,width: Int,height: Int)","$twoParameterPositions(height,1st,2nd,width,2nd,1st)","$constructor"]
    public constructor(height: Int, width: Int, scale: Double) : this(width, height)
}
```

### Do {#do-2}

```kotlin
// !hide-focused
/** Rectangle defined by its horizontal and vertical extents. */
public class Rect(width: Int, height: Int) {
    // !hide-focused
    /** Creates a rectangle and applies [scale] to both extents. */
    public constructor(width: Int, height: Int, scale: Double) : this(width, height)
}
```

### Don't {#dont-3}

```kotlin
// !hide-focused
@file:JvmName("Grids")

// !hide-focused
/** Mutable grid addressed by a linear cell index. */
public class Grid {
    // !hide-focused
    /** Fills cells from [startIndex] through [endIndex]. */
    public fun fill(startIndex: Int, endIndex: Int) { }
}

// !hide-focused
/** Fills a range with [color]. */
// !diag[/fill/] INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS ["$threeParameterOverload(fill,endIndex: Int,startIndex: Int,color: Long)","$otherTwoParameterOverload(fill,startIndex: Int,endIndex: Int)","$twoParameterPositions(endIndex,1st,2nd,startIndex,2nd,1st)","$function"]
public fun Grid.fill(
    endIndex: Int,
    startIndex: Int,
    color: Long,
) { }
```

### Do {#do-3}

```kotlin
// !hide-focused
@file:JvmName("Grids")

// !hide-focused
/** Mutable grid addressed by a linear cell index. */
public class Grid {
    // !hide-focused
    /** Fills cells from [startIndex] through [endIndex]. */
    public fun fill(startIndex: Int, endIndex: Int) { }
}

// !hide-focused
/** Fills a range with [color]. */
public fun Grid.fill(
    startIndex: Int,
    endIndex: Int,
    color: Long,
) { }
```


## Notes

- Overloads that share fewer than two parameter names are never reported. Single-argument
  conversion overloads such as `BigDecimal(value: Int)` and `BigDecimal(value: String)` stay silent.
- The check compares same-named members of one class, including inherited members. It compares
  top-level functions in one package and constructors of one class.
- A class member isn't compared against a same-named top-level function. Declarations from
  dependencies aren't compared.
- For an inherited pair, only the subtype's declaration gets a diagnostic. The supertype can't
  see the subtype's overload. For an extension and a receiver member, only the extension gets
  a diagnostic.
- Overrides never get a diagnostic because inherited API fixes their parameter order. Other
  overloads still use an override as a comparison reference.
- `@PublishedApi internal` overloads are neither reported nor used as comparison references because
  library users can't call them in source.

## Exemption

<!-- diagnostic-exemption: INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS -->
<!-- diagnostic-exemption-substitution: function or constructor => {3} -->
If this is intended, apply `@IntentionallyInconsistentParameterOrder` to the function
or constructor.

Use it for an overload whose parameter positions must differ, such as an old overload needed
for source compatibility. The annotation removes the overload from comparison with other
declarations, so newer overloads can use consistent positions.

```kotlin
// !hide-focused
@file:JvmName("Drawing")

// !hide-focused
/** Draws at ([x], [y]). */
public fun draw(x: Int, y: Int) { }

// !hide-focused
/** Draws at ([x], [y]) with [scale]. */
@IntentionallyInconsistentParameterOrder(
    reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY,
)
public fun draw(y: Int, x: Int, scale: Double) { }
```

## Configuration

```kotlin
apiWatchdog {
    inconsistentParameterOrderInOverloads = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS:warning
```

## See also

- [Preserve parameter order, naming, and usage](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage)
- [Required parameters after optional ones](./required-parameter-after-optional.md)
- [Exemptions and internal API](../exemptions.md)
