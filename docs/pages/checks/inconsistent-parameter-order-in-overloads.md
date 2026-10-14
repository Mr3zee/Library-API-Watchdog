# Inconsistent parameter order in overloads

|                  |                                                                |
|------------------|----------------------------------------------------------------|
| Diagnostic       | `INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS`                    |
| Default severity | Error                                                          |
| Gradle property  | [`inconsistentParameterOrderInOverloads`](../configuration.md) |
| Exemption        | [`@IntentionallyInconsistentParameterOrder`](../exemptions.md) |

## What it reports

Public API overloads that share at least two parameter names but put them at different positions.
The check reports swaps and extra parameters before or between shared parameters. Extra parameters at the end aren't reported.

Align shared parameter positions across the overload group.

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

Callers can reuse one overload's argument order in another and pass values to the wrong parameters.
If the parameter types match, the mistake still compiles.

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

The comparisons have these limits:

- Members of one class, including inherited members, share a group. Only the subtype's declaration reports an inherited mismatch.
- Top-level functions in one package share a group. Constructors of one class share a group.
- An extension also compares against its receiver's members. Only the extension reports that mismatch.
- Ordinary top-level functions don't compare against class members. Declarations from dependencies aren't checked.
- Overrides don't get diagnostics but are used in comparisons.
- `@PublishedApi internal` overloads aren't used in comparisons.

## Exemption

<!-- diagnostic-exemption: INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS -->
<!-- diagnostic-exemption-substitution: function or constructor => {3} -->
If this is intended, apply `@IntentionallyInconsistentParameterOrder` to the function
or constructor.

Use the exemption when the declaration is part of the API design or must stay for backwards compatibility.
The annotation removes that overload from all comparisons.

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

```kotlin build.gradle.kts
apiWatchdog {
    inconsistentParameterOrderInOverloads = WatchdogSeverity.ERROR
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS:error
```

## See also

- [Preserve parameter order, naming, and usage](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage)
- [Required parameters after optional ones](./required-parameter-after-optional.md)
- [Exemptions and internal API](../exemptions.md)
