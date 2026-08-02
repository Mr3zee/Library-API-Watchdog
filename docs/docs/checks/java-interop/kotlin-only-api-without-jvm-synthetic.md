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
// KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC
public suspend fun refresh(key: String) { }
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
// Java sees a trailing Continuation parameter
// it can't provide idiomatically.
//
// KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC
public suspend fun refresh(key: String) { }

// Only inlining Kotlin call sites can substitute T.
// Calling the compiled method from Java fails at runtime.
//
// KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC
public inline fun <reified T> instantiate(): T? = null

// A Java lambda has to return the Unit.INSTANCE token explicitly.
//
// KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC
public fun onEach(action: (Int) -> Unit) { }
```

### Do

```kotlin
@JvmSynthetic
public suspend fun refresh(key: String) { }

public fun interface Action {
    public fun doAction(value: Int)
}

public fun onEach(action: Action) { }
```

- `@JvmSynthetic` hides the Kotlin-only member from Java entirely.
  (A `suspend` function can instead ship alongside a blocking or `CompletableFuture`-returning bridge for Java callers.)
- A `fun interface` parameter gives Java a lambda-friendly type instead of a Kotlin function type.

## Notes

- Abstract and interface members are exempt: `@JvmSynthetic` can't hide a member that
  implementations must provide.
- Overrides are exempt, the shape is reported on the base declaration instead.
- Constructors are exempt: `@JvmSynthetic` doesn't apply to them.
- A signature mangled by a value class is reported by [`MANGLED_JVM_NAME_PUBLIC_API`](./mangled-jvm-name-public-api.md) instead.
- `@JvmSynthetic` declarations are hidden from Java on purpose and are not flagged.
- Non-JVM compilations never register this check at all.

## Exemption

Apply `@IntentionallyKotlinOnlyApi` to the function, or to an enclosing class to cover every
function inside, when leaving the Kotlin-only shape visible to Java is intended:

```kotlin
@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public suspend fun refresh(key: String) {}
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
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC:warning
```

## See also

- [Java-to-Kotlin interop guide](https://kotlinlang.org/docs/java-to-kotlin-interop.html)
- [Java interop checks](./java-interop.md)
- [Mangled JVM names in public API](./mangled-jvm-name-public-api.md)
- [Exemptions and internal API](../../exemptions.md)
