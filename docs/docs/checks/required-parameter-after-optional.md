# Required parameters after optional ones

`REQUIRED_PARAMETER_AFTER_OPTIONAL` reports required parameters of public functions and
constructors declared after an optional (defaulted or `vararg`) parameter.

|                  |                                                                 |
|------------------|-----------------------------------------------------------------|
| Diagnostic       | `REQUIRED_PARAMETER_AFTER_OPTIONAL`                             |
| Default severity | Error                                                           |
| Gradle property  | [`requiredParameterAfterOptional`](../configuration.md)            |
| Exemption        | [`@IntentionallyRequiredParameterAfterOptional`](../exemptions.md) |

## What it reports

Every required parameter (one that doesn't have a default value) that comes after the first optional one - defaulted
or `vararg` - in the parameter list of a public function or constructor:

```kotlin
// REQUIRED_PARAMETER_AFTER_OPTIONAL
public fun connect(retries: Int = 3, host: String) { }
```

All required parameters behind the first optional one are reported, not just the first:

```kotlin
public fun configure(
    timeout: Long = 0L,
    // REQUIRED_PARAMETER_AFTER_OPTIONAL
    host: String,
    // REQUIRED_PARAMETER_AFTER_OPTIONAL
    port: Int,
) { }
```

## Rationale

A required parameter behind an optional one can't be passed positionally,
which pushes callers toward named arguments for a parameter that should have been more trivial to supply.
It also blocks the library from ever adding another optional
parameter in a natural position later. See the Kotlin library authors' guide on
[parameter order, naming, and usage](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage):
essential inputs first, optional inputs last.

### Don't

```kotlin
// REQUIRED_PARAMETER_AFTER_OPTIONAL
public fun connect(retries: Int = 3, host: String) { }
```

### Do

```kotlin
public fun connect(host: String, retries: Int = 3) { }
```

### Don't {#dont-2}

```kotlin
// REQUIRED_PARAMETER_AFTER_OPTIONAL
public class Server(port: Int = 80, host: String)
```

### Do {#do-2}

```kotlin
public class Server(host: String, port: Int = 80)
```

## Notes

- A `vararg` parameter counts as optional too: callers can omit it entirely, so a required
  parameter after it is still reported.
- A required function-type or `fun interface` parameter in the **last** position is exempt: keeping
  it last is what makes trailing-lambda call syntax available, and the standard library itself
  places such parameters after defaulted ones (`joinToString(separator = ..., transform)`). The
  same required function-type parameter is still reported when it is *not* last, since there is no
  trailing-lambda syntax to preserve there.
- Overrides are exempt: they can't declare default values, and their parameter order is fixed by
  the overridden declaration, which is reported where it is declared instead.

## Exemption

Apply `@IntentionallyRequiredParameterAfterOptional` to the function or constructor when the order
is a deliberate, stable part of the contract, for example an old parameter list kept for source
compatibility:

```kotlin
@IntentionallyRequiredParameterAfterOptional(
    reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY,
)
public fun legacyConnect(retries: Int = 3, host: String) { }
```

## Configuration

```kotlin
apiWatchdog {
    requiredParameterAfterOptional = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=REQUIRED_PARAMETER_AFTER_OPTIONAL:warning
```

## See also

- [Preserve parameter order, naming, and usage](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage)
- [Inconsistent parameter order in overloads](./inconsistent-parameter-order-in-overloads.md), a sibling check on parameter order across overloads
- [Exemptions and internal API](../exemptions.md)
