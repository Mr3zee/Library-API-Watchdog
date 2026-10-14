# Nullable Booleans in public API

|                  |                                                     |
|------------------|-----------------------------------------------------|
| Diagnostic       | `NULLABLE_BOOLEAN_PUBLIC_API`                       |
| Default severity | Error                                               |
| Gradle property  | [`nullableBooleanPublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyNullableBoolean`](../exemptions.md) |

## What it reports

`Boolean?` in these parts of the public API, including nested type arguments and expanded type aliases:

- Function return types and property types.
- Value and context parameter types, including constructor parameters.
- Type parameter bounds.

```kotlin
// !hide-focused
@file:JvmName("Probes")

// !hide-focused
/** Returns whether the service is ready, or null before the first health check. */
// !diag[/Boolean[?]/] NULLABLE_BOOLEAN_PUBLIC_API ["$function","probe"]
public fun probe(): Boolean? = null
```

## Rationale

`Boolean?` adds an unnamed third state. A domain type names each state so callers don't have to infer what `null` means.

### Don't

```kotlin
// !hide-focused
@file:JvmName("Connections")

// !hide-focused
/** Returns the connection state. */
// !diag[/Boolean[?]/] NULLABLE_BOOLEAN_PUBLIC_API ["$function","connectionState"]
public fun connectionState(): Boolean? = null
```

### Do

```kotlin
// !hide-focused
@file:JvmName("Connections")

// !hide-focused
/** Whether a network transport is available. */
// !hide-focused
@IntentionallyExhaustive(reason = ExemptionReason.API_DESIGN)
public enum class ConnectionState {
    // !hide-focused
    /** An active transport can carry requests. */
    CONNECTED,
    // !hide-focused

    // !hide-focused
    /** No transport is currently available. */
    DISCONNECTED,
    // !hide-focused

    // !hide-focused
    /** The transport has not reported its state yet. */
    UNKNOWN,
}

// !hide-focused
/** Returns the connection state. */
public fun connectionState(): ConnectionState = ConnectionState.UNKNOWN
```

## Notes

The check skips:

- Extension receivers on `Boolean?` type.
- Overrides.
- Java platform types.
- `@PublishedApi internal` declarations.

## Exemption

<!-- diagnostic-exemption: NULLABLE_BOOLEAN_PUBLIC_API -->
If this is intended, apply `@IntentionallyNullableBoolean` to the declaration,
parameter, or type usage.

Use the exemption when the declaration is part of the API design or must stay for backwards compatibility.

```kotlin
// !hide-focused
@file:JvmName("Connections")

// !hide-focused
/** Returns a three-state connection status. */
@IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN)
public fun connectionState(): Boolean? = null
```

## Configuration

```kotlin
apiWatchdog {
    nullableBooleanPublicApi = WatchdogSeverity.ERROR
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=NULLABLE_BOOLEAN_PUBLIC_API:error
```

## See also

- [Avoid using the Boolean type as an argument](https://kotlinlang.org/docs/api-guidelines-readability.html#avoid-using-the-boolean-type-as-an-argument)
- [Boolean parameters in public API](./boolean-parameter-public-api.md)
- [Exemptions and internal API](../exemptions.md)
