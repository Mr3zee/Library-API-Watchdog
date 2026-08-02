# Top-level API without JvmName

`TOP_LEVEL_API_WITHOUT_JVM_NAME` reports a file whose public top-level functions or properties
compile into a file facade class without an explicit `@file:JvmName`.

|                  |                                                    |
|------------------|----------------------------------------------------|
| Diagnostic       | `TOP_LEVEL_API_WITHOUT_JVM_NAME`                   |
| Default severity | Error                                              |
| Applies to       | JVM compilations only                              |
| Gradle property  | [`topLevelApiWithoutJvmName`](../../configuration.md)    |
| Exemption        | [`@IntentionallyDefaultFacadeName`](../../exemptions.md) |

## What it reports

Kotlin files with top-level properties or functions that can be called from Java sources.

The diagnostic fires once per file, anchored on the first public top-level function or property.

```kotlin Network.kt
package com.example

/** Connects to the network. */
// !diag[/connect/] TOP_LEVEL_API_WITHOUT_JVM_NAME ["NetworkKt"]
public fun connect(): Int = 0
```

## Rationale

The derived facade name reads as an implementation detail at Java call sites (`NetworkKt.connect()`
instead of something Java-idiomatic), and it is tied to a fact Kotlin callers never see: the file
name. Renaming the file silently renames the facade and breaks Java sources and binaries compiled
against it. See Kotlin's
[Java-to-Kotlin interop guide](https://kotlinlang.org/docs/java-to-kotlin-interop.html#package-level-functions)
for how top-level declarations actually compile.

### Don't

```kotlin Network.kt
package com.example

// Facade class NetworkKt
// renaming this file to NetworkClient.kt breaks every Java caller.
/** Connects to the network. */
// !diag[/connect/] TOP_LEVEL_API_WITHOUT_JVM_NAME ["NetworkKt"]
public fun connect(): Int = 0

/** Disconnects from the network. */
public fun disconnect(): Int = 0
```

### Do

```kotlin Network.kt
@file:JvmName("Network")

package com.example

// Java callers write Network.connect(),
// the file can be renamed freely.
/** Connects to the network. */
public fun connect(): Int = 0

/** Disconnects from the network. */
public fun disconnect(): Int = 0
```

## Notes

- Files exposing only classifiers - classes, objects, type aliases - produce no facade worth
  naming.
- Files where every top-level callable is hidden from Java with `@JvmSynthetic` are not flagged.
- Non-JVM compilations never register this check at all.

## Exemption

`@IntentionallyDefaultFacadeName` is a file-target annotation, applied once per file as
`@file:IntentionallyDefaultFacadeName(...)`, when keeping the derived facade name is intended:

```kotlin
@file:IntentionallyDefaultFacadeName(
    reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY,
)

package com.example

import org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyDefaultFacadeName

/** A legacy entry point tied to the default facade name. */
public fun legacyEntryPoint(): Int = 0
```

## Configuration

```kotlin
apiWatchdog {
    javaInterop {
        topLevelApiWithoutJvmName = WatchdogSeverity.WARNING
    }
}
```

The property lives inside the `javaInterop { }` block. `javaInterop { enabled = false }` turns off
this check along with the rest of the [Java interop checks](./java-interop.md) group.

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=TOP_LEVEL_API_WITHOUT_JVM_NAME:warning
```

## See also

- [Kotlin's Java-to-Kotlin interop guide: package-level functions](https://kotlinlang.org/docs/java-to-kotlin-interop.html#package-level-functions)
- [Java interop checks](./java-interop.md)
- [Exemptions and internal API](../../exemptions.md)
