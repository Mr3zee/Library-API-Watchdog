# Top-level API without JvmName

`TOP_LEVEL_API_WITHOUT_JVM_NAME` reports a file whose public top-level functions or properties
compile into a file facade class without an explicit `@file:JvmName`.

| | |
|---|---|
| Diagnostic | `TOP_LEVEL_API_WITHOUT_JVM_NAME` |
| Default severity | Error |
| Applies to | JVM compilations only |
| Gradle property | [`topLevelApiWithoutJvmName`](configuration.md) |
| Exemption | [`@IntentionallyDefaultFacadeName`](exemptions.md) |

## What it reports

A file's public top-level functions and properties
[compile into a facade class](https://kotlinlang.org/docs/java-to-kotlin-interop.html#package-level-functions)
whose name is derived from the file name (`Network.kt` -> `NetworkKt`). Without `@file:JvmName`,
that derived name is what Java callers see and write at every call site:

```kotlin
package com.example

// TOP_LEVEL_API_WITHOUT_JVM_NAME
public fun connect(): Int = 0
```

The diagnostic fires once per file, anchored on the first public top-level function or property.

## Rationale

The derived facade name reads as an implementation detail at Java call sites (`NetworkKt.connect()`
instead of something Java-idiomatic), and it is tied to a fact Kotlin callers never see: the file
name. Renaming the file silently renames the facade and breaks Java sources and binaries compiled
against it. See Kotlin's
[Java-to-Kotlin interop guide](https://kotlinlang.org/docs/java-to-kotlin-interop.html#package-level-functions)
for how top-level declarations actually compile.

## Don't

```kotlin
// Network.kt
package com.example

// Facade class NetworkKt; renaming this file to NetworkClient.kt breaks every Java caller.
public fun connect(): Int = 0
public fun disconnect(): Int = 0
```

## Do

```kotlin
// Network.kt
@file:JvmName("Network")

package com.example

// Java callers write Network.connect(); the file can be renamed freely.
public fun connect(): Int = 0
public fun disconnect(): Int = 0
```

Notable cases that stay silent:

- Files exposing only classifiers - classes, objects, type aliases - produce no facade worth
  naming.
- Files whose every top-level callable is hidden from Java with `@JvmSynthetic` have no
  Java-visible facade member to anchor on.
- A file that already carries `@file:JvmName` has already pinned the name deliberately.

## Exemption

`@IntentionallyDefaultFacadeName` is a file-target annotation, applied once per file as
`@file:IntentionallyDefaultFacadeName(...)`, when keeping the derived facade name is intended:

```kotlin
@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)

package com.example

import org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyDefaultFacadeName

public fun legacyEntryPoint(): Int = 0
```

Since the diagnostic fires once per file, one file-level annotation covers the whole file.

## Configuration

```kotlin
apiWatchdog {
    javaInterop {
        topLevelApiWithoutJvmName = WatchdogSeverity.WARNING
    }
}
```

The property lives inside the `javaInterop { }` block; `javaInterop { enabled = false }` turns off
this check along with the rest of the [Java interop checks](java-interop.md) group.

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=TOP_LEVEL_API_WITHOUT_JVM_NAME:warning
```

## See also

- [Kotlin's Java-to-Kotlin interop guide: package-level functions](https://kotlinlang.org/docs/java-to-kotlin-interop.html#package-level-functions)
- [Java interop checks](java-interop.md)
- [Exemptions and internal API](exemptions.md)
