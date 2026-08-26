# Required parameters after optional ones

`REQUIRED_PARAMETER_AFTER_OPTIONAL` reports required parameters of public functions and
constructors declared after an optional (defaulted or `vararg`) parameter.

|                  |                                                                    |
|------------------|--------------------------------------------------------------------|
| Diagnostic       | `REQUIRED_PARAMETER_AFTER_OPTIONAL`                                |
| Default severity | Error                                                              |
| Gradle property  | [`requiredParameterAfterOptional`](../configuration.md)            |
| Exemption        | [`@IntentionallyRequiredParameterAfterOptional`](../exemptions.md) |

## What it reports

Every required parameter (one that doesn't have a default value) that comes after the first
optional one - defaulted or `vararg` - in a function or constructor in the public API:

```kotlin
// !hide-focused
@file:JvmName("Requests")

// !hide-focused
/** Sends a request to [host]. */
// !hide-focused
@JvmOverloads
// !diag[/host/] REQUIRED_PARAMETER_AFTER_OPTIONAL ["host","request"]
public fun request(retries: Int = 3, host: String) { }
```

All required parameters behind the first optional one are reported, not just the first:

```kotlin
// !hide-focused
@file:JvmName("Configuration")

// !hide-focused
/** Configures a connection. */
// !hide-focused
@JvmOverloads
public fun configure(
    timeout: Long = 0L,
    // !diag[/host/] REQUIRED_PARAMETER_AFTER_OPTIONAL ["host","configure"]
    host: String,
    // !diag[/port/] REQUIRED_PARAMETER_AFTER_OPTIONAL ["port","configure"]
    port: Int,
) { }
```

## Rationale

A required parameter behind an optional one can't be passed positionally,
which pushes callers toward named arguments for a parameter that should have been more trivial to supply.
See the Kotlin library authors' guide on
[parameter order, naming, and usage](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage).

### Don't

```kotlin
// !hide-focused
@file:JvmName("Connections")

// !hide-focused
/** Connects to [host]. */
// !hide-focused
@JvmOverloads
// !diag[/host/] REQUIRED_PARAMETER_AFTER_OPTIONAL ["host","connect"]
public fun connect(retries: Int = 3, host: String) { }
```

### Do

```kotlin
// !hide-focused
@file:JvmName("Connections")

// !hide-focused
/** Connects to [host]. */
// !hide-focused
@JvmOverloads
public fun connect(host: String, retries: Int = 3) { }
```



### Don't {#dont-2}

```kotlin
// !hide-focused
/** A server at [host]. */
public class Server
    // !hide-focused
    @JvmOverloads
    // !diag[/host/] REQUIRED_PARAMETER_AFTER_OPTIONAL ["host","Server"]
    constructor(port: Int = 80, host: String)
```

### Do {#do-2}

```kotlin
// !hide-focused
/** A server at [host]. */
public class Server
    // !hide-focused
    @JvmOverloads
    constructor(host: String, port: Int = 80)
```


## Notes

- A `vararg` parameter counts as optional too: callers can omit it entirely, so a required
  parameter after it is still reported.
- A required function-type or `fun interface` parameter in the **last** position is not reported:
  keeping it last is what makes trailing-lambda call syntax available. The
  same required function-type parameter is still reported when it is *not* last, since there is no
  trailing-lambda syntax to preserve there.
- Overrides are not reported: they can't declare default values, and their parameter order is
  fixed by the overridden declaration, which is reported instead.
- `@PublishedApi internal` functions and constructors are not reported because library users can't
  call them in source.

## Exemption

<!-- diagnostic-exemption: REQUIRED_PARAMETER_AFTER_OPTIONAL -->
If this API shape is intentional, apply `@IntentionallyRequiredParameterAfterOptional` to the
function or constructor.

Use the exemption when the order is a deliberate, stable part of the contract, for example an old
parameter list kept for source compatibility:

```kotlin
// !hide-focused
@file:JvmName("Connections")

// !hide-focused
/** Connects through the legacy parameter order. */
@IntentionallyRequiredParameterAfterOptional(
    reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY,
)
// !hide-focused(1:3)
@IntentionallyWithoutJvmOverloads(
    reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY,
)
public fun connect(retries: Int = 3, host: String) { }
```

## Configuration

```kotlin
apiWatchdog {
    requiredParameterAfterOptional = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=REQUIRED_PARAMETER_AFTER_OPTIONAL:warning
```

## See also

- [Preserve parameter order, naming, and usage](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage)
- [Inconsistent parameter order in overloads](./inconsistent-parameter-order-in-overloads.md)
- [Exemptions and internal API](../exemptions.md)
