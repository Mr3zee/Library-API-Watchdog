# Default parameters without JvmOverloads

`DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS` reports public functions and constructors that declare
default parameter values without `@JvmOverloads`.

|                  |                                                            |
|------------------|------------------------------------------------------------|
| Diagnostic       | `DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS`                 |
| Default severity | Error                                                      |
| Applies to       | JVM compilations only                                      |
| Gradle property  | [`defaultParametersWithoutJvmOverloads`](../../configuration.md) |
| Exemption        | [`@IntentionallyWithoutJvmOverloads`](../../exemptions.md)       |

## What it reports

A public function or constructor that declares at least one default parameter value but carries
no `@JvmOverloads`.

```kotlin
// DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS
public fun connect(
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

The recommendation is honest about its limits, though: `@JvmOverloads` only generates
right-truncated overloads, so a defaulted parameter in the middle of the list still can't be
skipped from Java, and it only improves Java call sites - it doesn't make adding a parameter
later binary compatible for Kotlin callers either.

### Don't

```kotlin
public fun connect(
    host: String,
    port: Int = 80,
    timeout: Int = 30,
) { }
```

### Do

```kotlin
@JvmOverloads
public fun connect(
    host: String,
    port: Int = 80,
    timeout: Int = 30,
) { }
```

### Don't {#dont-2}

```kotlin
public class Connection(
    host: String,
    port: Int = 80,
)
```

### Do {#do-2}

```kotlin
public class Connection @JvmOverloads constructor(
    host: String,
    port: Int = 80,
)
```

## Notes

- A defaulted parameter in the middle of the list still can't be skipped from Java even with
  `@JvmOverloads`. Keep optional parameters last (see [`REQUIRED_PARAMETER_AFTER_OPTIONAL`](../required-parameter-after-optional.md)) so the
  generated overloads actually cover the common call shapes.
- Abstract and interface members, and annotation class constructors, are exempt: `@JvmOverloads`
  doesn't apply to them.
- `suspend` functions and members of a value class are exempt: they are not Java-callable
  regardless of overloads.
- Overrides are exempt: they can't re-declare default values.
- `@JvmSynthetic` functions and constructors are hidden from Java on purpose and are not flagged.
- Non-JVM compilations never register this check at all.

## Exemption

Apply `@IntentionallyWithoutJvmOverloads` to the function or constructor when serving Java callers
the full signature only is intended, for example when the defaulted parameters make no sense
without Kotlin's named arguments:

```kotlin
@IntentionallyWithoutJvmOverloads(
    reason = ExemptionReason.IGNORE_JAVA_INTEROP,
    description = "Kotlin-only function. " +
            "Java callers are expected to use the builder instead.",
)
public fun connectDsl(
    host: String,
    port: Int = 80,
    timeout: Int = 30,
    builder: Connection.() -> Unit,
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
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS:warning
```

## See also

- [Overloads generation](https://kotlinlang.org/docs/java-to-kotlin-interop.html#overloads-generation)
- [Java interop checks](./java-interop.md)
- [Required parameters after optional ones](../required-parameter-after-optional.md), which keeps
  defaulted parameters last so the generated overloads are useful
- [Exemptions and internal API](../../exemptions.md)
