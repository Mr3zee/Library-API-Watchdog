# Kotlin-only API without JvmSynthetic

|                  |                                                              |
|------------------|--------------------------------------------------------------|
| Diagnostic       | `KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC`                      |
| Default severity | Error                                                        |
| Applies to       | JVM compilations only                                        |
| Gradle property  | [`kotlinOnlyApiWithoutJvmSynthetic`](../../configuration.md) |
| Exemption        | [`@IntentionallyKotlinOnlyApi`](../../exemptions.md)         |

## What it reports

Functions in the public API with one of these shapes:

- A `suspend` function.
- An `inline` function with a `reified` type parameter.
- A value or context parameter with a suspend, receiver, or `Unit`-returning function type.
  This includes `vararg` element types.

```kotlin
// !hide-focused
@file:JvmName("Loading")

// !hide-focused
/** Loads the value identified by [key]. */
// !diag[/load/] KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC ["load","$suspend"]
public suspend fun load(key: String) { }
```

## Rationale

Java sees an extra `Continuation` parameter on suspend functions. Calls to reified functions fail
at runtime. Kotlin function types also expose Java-specific constraints:

- Suspend callbacks can't be Java lambdas.
- Receiver callbacks take the receiver as an explicit argument.
- `Unit` callbacks must return `Unit.INSTANCE`.

`@JvmSynthetic` hides these functions from Java source. For Java callers, add a blocking or
`CompletableFuture` alternative for suspend functions, or a `fun interface` for callbacks.

### Don't

```kotlin
// !hide-focused
@file:JvmName("KotlinOnly")

// !hide-focused
/** Refreshes [key]. */
// !diag[/refresh/] KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC ["refresh","$suspend"]
public suspend fun refresh(key: String) { }
```

### Do

```kotlin
// !hide-focused
@file:JvmName("KotlinOnly")

// !hide-focused
/** Refreshes [key]. */
@JvmSynthetic
public suspend fun refresh(key: String) { }
```

### Don't {#dont-2}

```kotlin
// !hide-focused
@file:JvmName("KotlinOnly")

// !hide-focused
/** Creates an instance of [T]. */
// !diag[/instantiate/] KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC ["instantiate","$reified"]
public inline fun <reified T> instantiate(): T? = null
```

### Do {#do-2}

```kotlin
// !hide-focused
@file:JvmName("KotlinOnly")

// !hide-focused
/** Creates an instance of [T]. */
@JvmSynthetic
public inline fun <reified T> instantiate(): T? = null
```


### Don't {#dont-3}

```kotlin
// !hide-focused
@file:JvmName("KotlinOnly")

// !hide-focused
/** Invokes [action] for each value. */
// !diag[/onEach/] KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC ["onEach","$unitFunctionType(action)"]
public fun onEach(action: (Int) -> Unit) { }
```

### Do {#do-3}

```kotlin
// !hide-focused
@file:JvmName("KotlinOnly")

// !hide-focused
/** Consumes values produced by an iteration. */
// !hide-focused
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public fun interface Action {
    // !hide-focused
    /** Processes [value] emitted by the iteration. */
    public fun doAction(value: Int)
}

// !hide-focused
/** Invokes [action] for each value. */
public fun onEach(action: Action) { }
```



## Notes

- The check skips abstract functions, interface members, overrides, constructors, and members of value classes.
- The check skips `@JvmSynthetic` and `@PublishedApi internal` functions.
- Non-suspend functions with a mangled signature belong to
  [Mangled JVM names in public API](./mangled-jvm-name-public-api.md).
  Suspend functions stay with this check even when the compiler mangles their signature.

## Exemption

<!-- diagnostic-exemption: KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -->
If this is intended, apply `@IntentionallyKotlinOnlyApi` to the function or an
enclosing class.

Use the exemption when Java sources must see the Kotlin-only function.

```kotlin
// !hide-focused
@file:JvmName("KotlinOnly")

// !hide-focused
/** Calls [action] for each value. */
@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public fun onEach(action: (Int) -> Unit) { }
```

## Configuration

```kotlin
apiWatchdog {
    javaInterop {
        kotlinOnlyApiWithoutJvmSynthetic = WatchdogSeverity.WARNING
    }
}
```

`javaInterop { enabled = false }` disables all [Java interop checks](./java-interop.md).

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC:warning
```

## See also

- [Java-to-Kotlin interop guide](https://kotlinlang.org/docs/java-to-kotlin-interop.html)
- [Java interop checks](./java-interop.md)
- [Mangled JVM names in public API](./mangled-jvm-name-public-api.md)
- [Exemptions and internal API](../../exemptions.md)
