# Inconsistent parameter order in overloads

`INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS` reports two overloads of the same public callable
whose shared parameter names appear in a different relative order.

| | |
|---|---|
| Diagnostic | `INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS` |
| Default severity | Error |
| Gradle property | [`inconsistentParameterOrderInOverloads`](configuration.md) |
| Exemption | [`@IntentionallyInconsistentParameterOrder`](exemptions.md) |

## What it reports

For every pair of public overloads that share at least two parameter names, the check compares the
relative order of those shared names. No overload is treated as the canonical order: both members
of a disagreeing pair are reported, and reordering either one clears both.

```kotlin
// INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS
public fun draw(x: Int, y: Int): Unit = Unit

// INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS
public fun draw(y: Int, x: Int, scale: Double): Unit = Unit
```

## Rationale

Callers transfer their intuition about one overload's parameter order to the next: once `x, y` is
established, a sibling overload that expects `y, x` invites a silently swapped call, especially
when the swapped parameters share a type and the mistake still compiles. See the Kotlin library
authors' guide on
[preserving parameter order, naming, and usage](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage).

## Don't

```kotlin
// INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS
public fun draw(x: Int, y: Int): Unit = Unit

// INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS
public fun draw(y: Int, x: Int, scale: Double): Unit = Unit
```

## Do

```kotlin
public fun draw(x: Int, y: Int): Unit = Unit

public fun draw(x: Int, y: Int, scale: Double): Unit = Unit
```

## Don't {id="dont-2"}

```kotlin
// INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS
public class Rect(width: Int, height: Int) {
    // INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS
    public constructor(height: Int, width: Int, scale: Double) : this(width, height)
}
```

## Do {id="do-2"}

```kotlin
public class Rect(width: Int, height: Int) {
    public constructor(width: Int, height: Int, scale: Double) : this(width, height)
}
```

Notable edge cases and deliberate exceptions:

- Overloads that share fewer than two parameter names cannot disagree on order and are never
  reported, which is why single-argument conversion overloads with the same parameter name but
  different types (`BigDecimal(value: Int)` next to `BigDecimal(value: String)`) stay silent.
- Only declarations users see side by side are compared: the members of one class body -
  inherited members included - the top-level functions of one package, or the constructors of one
  class among each other. A class member is never compared against a same-named top-level
  function, and declarations from dependencies are never compared.
- For an inherited pair, only the subtype's own declaration is reported: the supertype cannot see
  the subtype's overload, and it is the new declaration that strays from the established order.
- Overrides never report - their order is fixed by the overridden declaration - but they still
  serve as an ordering reference for a new overload declared next to them.

## Exemption

Apply `@IntentionallyInconsistentParameterOrder` to the function or constructor when the differing
order is a deliberate, stable part of the contract, for example an old overload kept for source
compatibility. The annotation also removes the declaration as an ordering reference: it is skipped
both as a reporter and as a comparison target, so one acknowledged legacy overload doesn't force
its order onto otherwise-consistent newer ones.

```kotlin
public fun render(x: Int, y: Int): Unit = Unit

@IntentionallyInconsistentParameterOrder(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
public fun render(y: Int, x: Int, alpha: Long): Unit = Unit
```

The annotation targets the function or constructor declaration only; there is no parameter- or
type-level placement.

## Configuration

```kotlin
apiWatchdog {
    inconsistentParameterOrderInOverloads = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS:warning
```

## See also

- [Preserve parameter order, naming, and usage](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage)
- [Required parameters after optional ones](required-parameter-after-optional.md), a sibling check
  on parameter order within one declaration
- [Exemptions and internal API](exemptions.md)
