# Top-level API without JvmName

|                  |                                                          |
|------------------|----------------------------------------------------------|
| Diagnostic       | `TOP_LEVEL_API_WITHOUT_JVM_NAME`                         |
| Default severity | Error                                                    |
| Applies to       | JVM compilations only                                    |
| Gradle property  | [`topLevelApiWithoutJvmName`](../../configuration.md)    |
| Exemption        | [`@IntentionallyDefaultFacadeName`](../../exemptions.md) |

## What it reports

Files without `@file:JvmName` that have a Java-visible top-level function or property in the
public API or declared as `@PublishedApi internal`. The check reports one diagnostic for each file.

```kotlin Health.kt
// !hide-focused
/** Checks whether the network is reachable. */
// !diag[/ping/] TOP_LEVEL_API_WITHOUT_JVM_NAME ["HealthKt","$functions"]
public fun ping(): Int = 0
```

## Rationale

Without `@file:JvmName`, the facade name depends on the file name. A file rename breaks
Java source calls and existing binaries that reference the facade.

### Don't

```kotlin Network.kt
// !hide-focused
/** Connects to the network. */
// !diag[/connect/] TOP_LEVEL_API_WITHOUT_JVM_NAME ["NetworkKt","$functions"]
public fun connect(): Int = 0
```

### Do

```kotlin Network.kt
@file:JvmName("Network")

// !hide-focused
/** Connects to the network. */
public fun connect(): Int = 0
```

## Notes

- The check skips files with only classes, objects, or type aliases.
- The check skips files without a Java-visible top-level callable.
- The check includes `@PublishedApi internal` callables because public inline bodies can copy facade
  references into user binaries.

## Exemption

<!-- diagnostic-exemption: TOP_LEVEL_API_WITHOUT_JVM_NAME -->
If this is intended, apply `@file:IntentionallyDefaultFacadeName` to the file.

Use the exemption when the declaration is part of the API design or must stay for backwards compatibility.

```kotlin
@file:IntentionallyDefaultFacadeName(
    reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY,
)

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

// !hide-focused
/** Connects to the network. */
public fun connect(): Int = 0
```

## Configuration

```kotlin build.gradle.kts
apiWatchdog {
    javaInterop {
        topLevelApiWithoutJvmName = WatchdogSeverity.ERROR
    }
}
```

`javaInterop { enabled = false }` disables all [Java interop checks](./java-interop.md).

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=TOP_LEVEL_API_WITHOUT_JVM_NAME:error
```

## See also

- [Package-level functions](https://kotlinlang.org/docs/java-to-kotlin-interop.html#package-level-functions)
- [Java interop checks](./java-interop.md)
- [Exemptions and internal API](../../exemptions.md)
