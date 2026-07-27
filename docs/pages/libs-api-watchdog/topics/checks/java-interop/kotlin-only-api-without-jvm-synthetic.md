# Kotlin-only API without JvmSynthetic

`KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC` reports public functions whose shape only Kotlin callers
can use idiomatically, while the function still lands in the API surface Java sources see.

| | |
|---|---|
| Diagnostic | `KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC` |
| Default severity | Error |
| Applies to | JVM compilations only |
| Gradle property | [`kotlinOnlyApiWithoutJvmSynthetic`](configuration.md) |
| Exemption | [`@IntentionallyKotlinOnlyApi`](exemptions.md) |

## What it reports

Three shapes trigger it: a `suspend` function (Java sees a trailing `Continuation` parameter it
cannot provide idiomatically), an `inline` function with a `reified` type parameter (calling the
compiled method from Java fails at runtime), and a function taking a Kotlin-specific function
type - a suspend function type, a function type with receiver, or a `Unit`-returning function
type:

```kotlin
// KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC
public suspend fun fetch(key: String): String = key
```

## Rationale

A Kotlin-only shape still compiles a method Java sources can see and try to call, even though
Java cannot use it the way Kotlin callers do, or cannot use it at all. Leaving it visible without
comment misleads Java-facing API browsing and, for a `reified` type parameter, produces a call
that compiles in Java but fails at runtime. See Kotlin's
[Java-to-Kotlin interop guide](https://kotlinlang.org/docs/java-to-kotlin-interop.html) for how
these shapes actually compile.

## Don't

```kotlin
// Java sees a trailing Continuation parameter it cannot provide idiomatically.
// 
// KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC
public suspend fun fetch(key: String): String = key

// Only inlining Kotlin call sites can substitute T; calling the compiled method from Java
// fails at runtime.
// 
// KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC
public inline fun <reified T> instantiate(): T? = null

// A Java lambda has to return the Unit.INSTANCE token explicitly.
// 
// KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC
public fun onEach(action: (Int) -> Unit) {}
```

## Do

```kotlin
@JvmSynthetic
public suspend fun fetch(key: String): String = key

public fun interface Listener {
    public fun onChange(value: Int)
}

public fun listen(listener: Listener) {}
```

`@JvmSynthetic` hides the Kotlin-only member from Java entirely; a `fun interface` parameter gives
Java a lambda-friendly type instead of a Kotlin function type. A suspend function can instead ship
alongside a blocking or `CompletableFuture`-returning bridge for Java callers.

Notable cases:

- Abstract and interface members are exempt: `@JvmSynthetic` cannot hide a member that
  implementations must provide, so there is no non-breaking fix to suggest.
- Overrides are exempt; the shape is reported on the base declaration instead.
- Constructors are exempt: `@JvmSynthetic` doesn't apply to them.
- A signature mangled by a value class is reported by `MANGLED_JVM_NAME_PUBLIC_API` instead.
- A member declared inside a value class is exempt from both checks: the public value class itself
  is the deliberate choice.
- A declaration already carrying `@JvmSynthetic` is skipped: the Kotlin-only shape is already
  hidden from Java on purpose.

## Exemption

Apply `@IntentionallyKotlinOnlyApi` to the function, or to an enclosing class to cover every
function inside, when leaving the Kotlin-only shape visible to Java is intended:

```kotlin
@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public suspend fun refresh(key: String): String = key
```

## Configuration

```kotlin
apiWatchdog {
    javaInterop {
        kotlinOnlyApiWithoutJvmSynthetic = WatchdogSeverity.WARNING
    }
}
```

The property lives inside the `javaInterop { }` block; `javaInterop { enabled = false }` turns off
this check along with the rest of the [Java interop checks](java-interop.md) group.

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC:warning
```

## See also

- [Java-to-Kotlin interop guide](https://kotlinlang.org/docs/java-to-kotlin-interop.html)
- [Java interop checks](java-interop.md)
- [Mangled JVM names in public API](mangled-jvm-name-public-api.md)
- [Exemptions and internal API](exemptions.md)
