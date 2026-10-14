# Boolean parameters in public API

|                  |                                                      |
|------------------|------------------------------------------------------|
| Diagnostic       | `BOOLEAN_PARAMETER_PUBLIC_API`                       |
| Default severity | Error                                                |
| Gradle property  | [`booleanParameterPublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyBooleanParameter`](../exemptions.md) |

## What it reports

`Boolean` and `Boolean?` value and context parameters of functions in the public API.
For a `vararg` parameter, the check uses its element type. Type aliases don't exempt a parameter.

```kotlin
// !hide-focused
@file:JvmName("Work")

// !hide-focused
/** Processes pending data, optionally favoring speed over memory use. */
// !diag[/optimizeForSpeed/] BOOLEAN_PARAMETER_PUBLIC_API ["doWork","optimizeForSpeed"]
public fun doWork(optimizeForSpeed: Boolean) {}
```

## Rationale

A positional call such as `setLogging(true)` doesn't show what the Boolean controls.
Separate operations or a domain type make the call readable.

### Don't

```kotlin
// !hide-focused
@file:JvmName("Logging")

// !hide-focused
/** Controls whether diagnostic messages are recorded. */
// !diag[/enabled/] BOOLEAN_PARAMETER_PUBLIC_API ["setLogging","enabled"]
public fun setLogging(enabled: Boolean) {}
```

### Do

```kotlin
// !hide-focused
@file:JvmName("Logging")

// !hide-focused
/** Enables logging. */
public fun enableLogging() {}

// !hide-focused
/** Disables logging. */
public fun disableLogging() {}
```

## Notes

The check skips:

- Overrides.
- Constructors and factory functions named after their return type, such as `fun Widget(visible: Boolean): Widget`.
- Legacy context receivers.
- `@PublishedApi internal` functions.

[Nullable Booleans in public API](./nullable-boolean-public-api.md) also reports `Boolean?` parameters.

## Exemption

<!-- diagnostic-exemption: BOOLEAN_PARAMETER_PUBLIC_API -->
If this is intended, apply `@IntentionallyBooleanParameter` to the function or
Boolean parameter.

Use the exemption when the declaration is part of the API design or must stay for backwards compatibility.

```kotlin
// !hide-focused
@file:JvmName("Logging")

// !hide-focused
/** Controls whether diagnostic messages are recorded. */
@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public fun setIsVerbose(enabled: Boolean) {}
```

## Configuration

```kotlin
apiWatchdog {
    booleanParameterPublicApi = WatchdogSeverity.ERROR
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=BOOLEAN_PARAMETER_PUBLIC_API:error
```

## See also

- [Avoid using the Boolean type as an argument](https://kotlinlang.org/docs/api-guidelines-readability.html#avoid-using-the-boolean-type-as-an-argument)
- [Nullable Booleans in public API](./nullable-boolean-public-api.md)
- [Exemptions and internal API](../exemptions.md)
