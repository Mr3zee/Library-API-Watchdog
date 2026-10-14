# Default parameters without JvmOverloads

|                  |                                                                  |
|------------------|------------------------------------------------------------------|
| Diagnostic       | `DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS`                       |
| Default severity | Error                                                            |
| Applies to       | JVM compilations only                                            |
| Gradle property  | [`defaultParametersWithoutJvmOverloads`](../../configuration.md) |
| Exemption        | [`@IntentionallyWithoutJvmOverloads`](../../exemptions.md)       |

## What it reports

Functions and constructors in the public API with at least one default parameter value and no
`@JvmOverloads`.

```kotlin
// !hide-focused
@file:JvmName("Sockets")

// !hide-focused
/** Opens a socket to [host]. */
// !diag[/openSocket/] DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS ["$function","openSocket"]
public fun openSocket(
    host: String,
    port: Int = 80,
    timeout: Int = 30,
) { }
```

## Rationale

Without `@JvmOverloads`, Java callers must pass every argument. The generated overloads let them
use the defaults.

### Don't

```kotlin
// !hide-focused
@file:JvmName("Connections")

// !hide-focused
/** Connects to [host]. */
// !diag[/connect/] DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS ["$function","connect"]
public fun connect(
    host: String,
    port: Int = 80,
    timeout: Int = 30,
) { }
```

### Do

```kotlin
// !hide-focused
@file:JvmName("Connections")

// !hide-focused
/** Connects to [host]. */
@JvmOverloads
public fun connect(
    host: String,
    port: Int = 80,
    timeout: Int = 30,
) { }
```

## Notes

The check excludes:

- Abstract functions, interface members, and annotation class constructors.
- `suspend` functions and members of value classes.
- Overrides and `@JvmSynthetic` declarations.
- `@PublishedApi internal` declarations.

## Exemption

<!-- diagnostic-exemption: DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS -->
<!-- diagnostic-exemption-substitution: function or constructor => {0} -->
If this is intended, apply `@IntentionallyWithoutJvmOverloads` to the function or
constructor.

Use the exemption when the declaration is part of the API design or must stay for backwards compatibility.

```kotlin
// !hide-focused
@file:JvmName("Connections")

// !hide-focused
/** Options applied after the connection. */
public class ConnectionConfig

// !hide-focused
/** Connects to [host] and applies [builder]. */
@IntentionallyWithoutJvmOverloads(
    description = "Kotlin-only function. " +
            "Java callers are expected to use the builder instead.",
    reason = ExemptionReason.IGNORE_JAVA_INTEROP,
)
// !hide-focused
@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public fun connect(
    host: String,
    port: Int = 80,
    timeout: Int = 30,
    builder: ConnectionConfig.() -> Unit,
) { }
```

## Configuration

```kotlin
apiWatchdog {
    javaInterop {
        defaultParametersWithoutJvmOverloads = WatchdogSeverity.ERROR
    }
}
```

`javaInterop { enabled = false }` disables all [Java interop checks](./java-interop.md).

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS:error
```
