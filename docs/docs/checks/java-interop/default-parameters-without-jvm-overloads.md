# Default parameters without JvmOverloads

`DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS` reports public functions and constructors that declare
default parameter values without `@JvmOverloads`.

|                  |                                                                  |
|------------------|------------------------------------------------------------------|
| Diagnostic       | `DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS`                       |
| Default severity | Error                                                            |
| Applies to       | JVM compilations only                                            |
| Gradle property  | [`defaultParametersWithoutJvmOverloads`](../../configuration.md) |
| Exemption        | [`@IntentionallyWithoutJvmOverloads`](../../exemptions.md)       |

## What it reports

A public function or constructor that declares at least one default parameter value but carries
no `@JvmOverloads`.

```kotlin
// !hide-focused
@file:JvmName("Sockets")

// !hide-focused
/** Opens a socket to [host]. */
// !diag[/openSocket/] DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS ["function","openSocket"]
public fun openSocket(
    host: String,
    port: Int = 80,
    timeout: Int = 30,
) { }
```

## Rationale

Without `@JvmOverloads`, Java callers of a function with three defaulted parameters have to spell
out all three at every call site. See Kotlin's guide on
[overloads generation](https://kotlinlang.org/docs/java-to-kotlin-interop.html#overloads-generation)
for how `@JvmOverloads` compiles the reduced overloads Java needs.

### Don't

```kotlin
// !hide-focused
@file:JvmName("Connections")

// !hide-focused
/** Connects to [host]. */
// !diag[/connect/] DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS ["function","connect"]
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

### Don't {#dont-2}

```kotlin
// !hide-focused
/** A connection to [host]. */
// !diag[/Connection/] DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS ["constructor","Connection"]
public class Connection(
    host: String,
    port: Int = 80,
)
```

### Do {#do-2}

```kotlin
// !hide-focused
/** A connection to [host]. */
public class Connection @JvmOverloads constructor(
    host: String,
    port: Int = 80,
)
```


## Notes

- A defaulted parameter in the middle of the list still can't be skipped from Java even with
  `@JvmOverloads`. Keep optional parameters last (see [`REQUIRED_PARAMETER_AFTER_OPTIONAL`](../required-parameter-after-optional.md)) so the
  generated overloads actually cover the common call shapes.
- Abstract and interface members, and annotation class constructors, are not reported: `@JvmOverloads`
  doesn't apply to them.
- `suspend` functions and members of a value class are not reported: they are not Java-callable
  regardless of overloads.
- Overrides are not reported: they can't re-declare default values.
- `@JvmSynthetic` functions and constructors are hidden from Java on purpose and are not reported.
- Non-JVM compilations never register this check at all.
- `@PublishedApi internal` declarations are not reported because their public bytecode entries are
  binary implementation details rather than supported Java source API.

## Exemption

<!-- diagnostic-exemption: DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS -->
If this API shape is intentional, apply `@IntentionallyWithoutJvmOverloads` to the function or
constructor.

```kotlin
// !hide-focused
@file:JvmName("Connections")

// Supporting options type
// !hide-focused
/** Options applied after connecting. */
public class ConnectionConfig

// !hide-focused
/** Connects to [host] and applies [options]. */
@IntentionallyWithoutJvmOverloads(
    reason = ExemptionReason.IGNORE_JAVA_INTEROP,
    description = "Kotlin-only function. " +
            "Java callers are expected to use the builder instead.",
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
        defaultParametersWithoutJvmOverloads = WatchdogSeverity.WARNING
    }
}
```

The property lives inside the `javaInterop { }` block. `javaInterop { enabled = false }` turns off
this check along with the rest of the [Java interop checks](./java-interop.md) group.

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS:warning
```

## See also

- [Overloads generation](https://kotlinlang.org/docs/java-to-kotlin-interop.html#overloads-generation)
- [Java interop checks](./java-interop.md)
- [Required parameters after optional ones](../required-parameter-after-optional.md)
- [Exemptions and internal API](../../exemptions.md)
