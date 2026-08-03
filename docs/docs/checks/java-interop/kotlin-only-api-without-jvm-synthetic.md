# Kotlin-only API without JvmSynthetic

`KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC` reports public functions whose shape only Kotlin callers
can use idiomatically, while the function still lands in the API surface Java sources see.

|                  |                                                        |
|------------------|--------------------------------------------------------|
| Diagnostic       | `KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC`                |
| Default severity | Error                                                  |
| Applies to       | JVM compilations only                                  |
| Gradle property  | [`kotlinOnlyApiWithoutJvmSynthetic`](../../configuration.md) |
| Exemption        | [`@IntentionallyKotlinOnlyApi`](../../exemptions.md)         |

## What it reports

Three shapes trigger it:
- A `suspend` function (Java sees a trailing `Continuation` parameter it can't provide idiomatically)
- An `inline` function with a `reified` type parameter (calling the compiled method from Java fails at runtime)
- A function taking a Kotlin-specific function type - a suspend function type, a
  function type with receiver, or a `Unit`-returning function type

```kotlin
// !hide-focused
@file:JvmName("Loading")

// !hide-focused
/** Loads the value identified by [key]. */
// !diag[/load/] KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC ["load","$suspend"]
public suspend fun load(key: String) { }
```

## Rationale

A Kotlin-only shape still compiles a method Java sources can see and try to call, even though
Java can't use it the way Kotlin callers do, or can't use it at all. Leaving it visible without
comment misleads Java-facing API browsing and, for a `reified` type parameter, produces a call
that compiles in Java but fails at runtime. See Kotlin's
[Java-to-Kotlin interop guide](https://kotlinlang.org/docs/java-to-kotlin-interop.html) for how
these shapes actually compile.


### Don't

```kotlin
// !hide-focused
@file:JvmName("KotlinOnly")

// Java sees a trailing Continuation parameter
// it can't provide idiomatically.
//
// !hide-focused
/** Refreshes [key]. */
// !diag[/refresh/] KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC ["refresh","$suspend"]
public suspend fun refresh(key: String) { }

// Only inlining Kotlin call sites can substitute T.
// Calling the compiled method from Java fails at runtime.
//
// !hide-focused
/** Creates an instance of [T]. */
// !diag[/instantiate/] KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC ["instantiate","$reified"]
public inline fun <reified T> instantiate(): T? = null

// A Java lambda has to return the Unit.INSTANCE token explicitly.
//
// !hide-focused
/** Invokes [action] for each value. */
// !diag[/onEach/] KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC ["onEach","$unitFunctionType(action)"]
public fun onEach(action: (Int) -> Unit) { }
```

### Do

```kotlin
// !hide-focused
@file:JvmName("KotlinOnly")

// !hide-focused
/** Refreshes [key]. */
@JvmSynthetic
public suspend fun refresh(key: String) { }

// !hide-focused
/** Creates an instance of [T]. */
@JvmSynthetic
public inline fun <reified T> instantiate(): T? = null

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

- `@JvmSynthetic` hides the Kotlin-only member from Java entirely.
  (A `suspend` function can instead ship alongside a blocking or `CompletableFuture`-returning bridge for Java callers.)
- A `fun interface` parameter gives Java a lambda-friendly type instead of a Kotlin function type.


## Notes

- Abstract and interface members are not reported: `@JvmSynthetic` can't hide a member that
  implementations must provide.
- Overrides are not reported: their shape is fixed by the overridden declaration, which is
  reported instead.
- Constructors are not reported: `@JvmSynthetic` doesn't apply to them.
- A signature mangled by a value class is reported by [`MANGLED_JVM_NAME_PUBLIC_API`](./mangled-jvm-name-public-api.md) instead.
- `@JvmSynthetic` declarations are hidden from Java on purpose and are not reported.
- Non-JVM compilations never register this check at all.

## Exemption

Apply `@IntentionallyKotlinOnlyApi` to the function, or to an enclosing class to cover every
function inside, when leaving the Kotlin-only shape visible to Java is intended:

```kotlin
// !hide-focused
@file:JvmName("KotlinOnly")

// !hide-focused
/** Refreshes [key] through a deliberately Kotlin-only API. */
@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public suspend fun refresh(key: String) {}

// !hide-focused
/** Creates an instance of [T] through a deliberately Kotlin-only API. */
@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public inline fun <reified T> instantiate(): T? = null

// !hide-focused
/** Invokes [action] through a deliberately Kotlin-only API. */
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

The property lives inside the `javaInterop { }` block. `javaInterop { enabled = false }` turns off
this check along with the rest of the [Java interop checks](./java-interop.md) group.

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlin.library.api.watchdog:diagnosticSeverity=KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC:warning
```

## See also

- [Java-to-Kotlin interop guide](https://kotlinlang.org/docs/java-to-kotlin-interop.html)
- [Java interop checks](./java-interop.md)
- [Mangled JVM names in public API](./mangled-jvm-name-public-api.md)
- [Exemptions and internal API](../../exemptions.md)
