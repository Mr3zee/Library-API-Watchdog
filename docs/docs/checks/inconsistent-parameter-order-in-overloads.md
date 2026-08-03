# Inconsistent parameter order in overloads

`INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS` reports two overloads of the same public callable
whose shared parameter names appear in a different relative order.

|                  |                                                             |
|------------------|-------------------------------------------------------------|
| Diagnostic       | `INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS`                 |
| Default severity | Error                                                       |
| Gradle property  | [`inconsistentParameterOrderInOverloads`](../configuration.md) |
| Exemption        | [`@IntentionallyInconsistentParameterOrder`](../exemptions.md) |

## What it reports

For every pair of public overloads that share at least two parameter names, the check compares the
relative order of those shared names. No overload is treated as the canonical order: both members
of a disagreeing pair are reported, and reordering either one clears both.

```kotlin
// !hide-focused
@file:JvmName("Movement")

// !hide-focused
/** Moves to ([x], [y]). */
// !diag[/move/] INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS ["x","y","move"]
public fun move(x: Int, y: Int) { }

// !hide-focused
/** Moves to ([x], [y]) with [scale]. */
// !diag[/move/] INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS ["y","x","move"]
public fun move(y: Int, x: Int, scale: Double) { }
```

## Rationale

Callers transfer their intuition about one overload's parameter order to the next: once `x, y` is
established, a sibling overload that expects `y, x` invites a silently swapped call, especially
when the swapped parameters share a type and the mistake still compiles. See the Kotlin library
authors' guide on
[preserving parameter order, naming, and usage](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage).


### Don't

```kotlin
// !hide-focused
@file:JvmName("Drawing")

// !hide-focused
/** Draws at ([x], [y]). */
// !diag[/draw/] INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS ["x","y","draw"]
public fun draw(x: Int, y: Int) { }

// !hide-focused
/** Draws at ([x], [y]) with [scale]. */
// !diag[/draw/] INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS ["y","x","draw"]
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
// !diag[/[(]width: Int, height: Int[)]/] INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS ["width","height","Rect"]
public class Rect(width: Int, height: Int) {
    // !hide-focused
    /** Creates a rectangle and applies [scale] to both extents. */
    // !diag[/public constructor[(]height: Int, width: Int, scale: Double[)] : this[(]width, height[)]/] INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS ["height","width","Rect"]
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
    public constructor(
        width: Int,
        height: Int,
        scale: Double,
    ) : this(width, height)
}
```



### Don't {#dont-3}

```kotlin
// !hide-focused
@file:JvmName("Grids")

// Supporting member overload
// !hide-focused
/** Mutable grid addressed by a linear cell index. */
public class Grid {
    // !hide-focused
    /** Fills cells from [startIndex] through [endIndex]. */
    public fun fill(startIndex: Int, endIndex: Int) { }
}

// !hide-focused
/** Fills a range with [color]. */
// !diag[/fill/] INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS ["endIndex","startIndex","fill"]
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

// Supporting member overload
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

- `@PublishedApi internal` overloads are neither reported nor used as comparison references because
  library users cannot call them in source.
- Overloads that share fewer than two parameter names can't disagree on order and are never
  reported, which is why single-argument conversion overloads with the same parameter name but
  different types (`BigDecimal(value: Int)` next to `BigDecimal(value: String)`) stay silent.
- Only declarations users see side by side are compared: the members of one class body -
  inherited members included - the top-level functions of one package, or the constructors of one
  class among each other. A class member is never compared against a same-named top-level
  function, and declarations from dependencies are never compared.
- An extension is called like a member of the type it extends, so the members of its receiver
  class - inherited ones included - are its overloads too, wherever in the library the extension
  is declared. A receiver reached through a type alias, a nullable type, or a type parameter
  bound still leads back to the extended class; an unbounded type parameter is no class and has
  no members to compare against.
- For an inherited pair, only the subtype's own declaration is reported: the supertype can't see
  the subtype's overload, and it is the new declaration that strays from the established order.
  An extension next to the members of its receiver is reported the same way: only the extension,
  since the class can't see the extensions declared on it.
- Overrides are not reported - their order is fixed by the overridden declaration - but they still
  serve as an ordering reference for a new overload declared next to them.

## Exemption

Apply `@IntentionallyInconsistentParameterOrder` to the function or constructor when the differing
order is a deliberate, stable part of the contract, for example an old overload kept for source
compatibility. The annotation also removes the declaration as an ordering reference: it is skipped
both as a reporter and as a comparison target, so one acknowledged legacy overload doesn't force
its order onto otherwise-consistent newer ones.

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
-P plugin:org.jetbrains.kotlin.library.api.watchdog:diagnosticSeverity=INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS:warning
```

## See also

- [Preserve parameter order, naming, and usage](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage)
- [Required parameters after optional ones](./required-parameter-after-optional.md), a sibling check
  on parameter order within one declaration
- [Exemptions and internal API](../exemptions.md)
