# Required parameters after optional ones

|                  |                                                                    |
|------------------|--------------------------------------------------------------------|
| Diagnostic       | `REQUIRED_PARAMETER_AFTER_OPTIONAL`                                |
| Default severity | Error                                                              |
| Gradle property  | [`requiredParameterAfterOptional`](../configuration.md)            |
| Exemption        | [`@IntentionallyRequiredParameterAfterOptional`](../exemptions.md) |

## What it reports

Every required value parameter after a defaulted or `vararg` parameter in a public API function or constructor.
The last parameter is exempt if it accepts a trailing lambda.

```kotlin
// !hide-focused
@file:JvmName("Requests")

// !hide-focused
/** Sends a request to [host]. */
// !hide-focused
@JvmOverloads
// !diag[/host/] REQUIRED_PARAMETER_AFTER_OPTIONAL ["host","request","$function"]
public fun request(retries: Int = 3, host: String) { }
```

## Rationale

To omit an earlier default, callers must name the later required argument.
Put required parameters before optional ones.

### Don't

```kotlin
// !hide-focused
@file:JvmName("Connections")

// !hide-focused
/** Connects to [host]. */
// !hide-focused
@JvmOverloads
// !diag[/host/] REQUIRED_PARAMETER_AFTER_OPTIONAL ["host","connect","$function"]
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

## Notes

The check skips overrides and `@PublishedApi internal` functions and constructors.

The trailing-lambda exception applies to function types and `fun interface` types in the last position.
It doesn't apply to reflection types such as `KFunction` or to earlier parameters.

## Exemption

<!-- diagnostic-exemption: REQUIRED_PARAMETER_AFTER_OPTIONAL -->
<!-- diagnostic-exemption-substitution: function or constructor => {2} -->
If this is intended, apply `@IntentionallyRequiredParameterAfterOptional` to the
function or constructor.

Use the exemption when the declaration is part of the API design or must stay for backwards compatibility.

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
    requiredParameterAfterOptional = WatchdogSeverity.ERROR
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=REQUIRED_PARAMETER_AFTER_OPTIONAL:error
```

## See also

- [Preserve parameter order, naming, and usage](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage)
- [Inconsistent parameter order in overloads](./inconsistent-parameter-order-in-overloads.md)
- [Exemptions and internal API](../exemptions.md)
