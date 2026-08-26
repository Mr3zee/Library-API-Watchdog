# Inline functions with logic

`INLINE_FUNCTION_WITH_LOGIC` reports public inline functions and inline property accessors whose
body does more than delegate to a non-inline function.

|                  |                                                  |
|------------------|--------------------------------------------------|
| Diagnostic       | `INLINE_FUNCTION_WITH_LOGIC`                     |
| Default severity | Error                                            |
| Gradle property  | [`inlineFunctionWithLogic`](../configuration.md) |
| Exemption        | [`@IntentionallyInlinedLogic`](../exemptions.md) |

## What it reports

Any public `inline` function and any `inline` property accessor are reported unless their body is a thin wrapper: a single
statement - besides an optional contract - that only performs simple operations and delegates to a
non-inline call:

```kotlin
// !hide-focused
@file:JvmName("Numbers")

// !hide-focused
/** Classifies [value] by its sign. */
// !diag[/classifySign/] INLINE_FUNCTION_WITH_LOGIC ["inline function","classifySign"]
public inline fun classifySign(value: Int): Int = if (value < 0) -1 else 1
```

## Rationale

The compiler copies an inline function's body into every call site at compile time. Once a user
compiles against a library version, that copy - and any bug in it - is frozen in the user's
binary until the user recompiles against a fixed version. A regular function call would instead
pick up the fix at runtime by relinking against the new library binary. See the Kotlin library
authors' guide on
[`@PublishedApi` considerations](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#considerations-for-using-the-publishedapi-annotation).

[//]: # (TODO list permitted simple operations)

### Don't

```kotlin
// !hide-focused
/** Returns the number of functions declared by [T]. */
// !hide-focused
@JvmSynthetic
public inline fun <reified T : Any> resolveFunctionsCount(): Int {
    return T::class.memberFunctions.size
}
```

### Do

```kotlin
// !hide-focused
@file:JvmName("Reflection")

@PublishedApi
internal fun <T : Any> resolveFunctionsCountImpl(kClass: KClass<T>): Int {
    return kClass.memberFunctions.size
}

// !hide-focused
/** Returns the number of functions declared by [T]. */
// !hide-focused
@JvmSynthetic
public inline fun <reified T : Any> resolveFunctionsCount(): Int {
    return resolveFunctionsCountImpl(T::class)
}
```


## Notes

- A contract declared with `contract { ... }` doesn't count as a statement.
- Calling another inline function, or reading or writing through an inline accessor, is logic: the
  inliner drags that body into the user transitively even with no visible control flow.
- `@PublishedApi internal` inline functions and accessors are checked exactly like public ones
  because a public inline wrapper can call them and transitively inline their bodies into user
  binaries.

## Exemption

Apply `@IntentionallyInlinedLogic` when inlining the logic is intended, for example when a lambda
must run inline for non-local returns or a hot path must not pay for an extra call:

```kotlin
// !hide-focused
@file:JvmName("Values")

// !hide-focused
/** Chooses a sign for [value]. */
@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public inline fun choose(value: Int): Int = if (value < 0) -1 else 1
```

## Configuration

```kotlin
apiWatchdog {
    inlineFunctionWithLogic = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=INLINE_FUNCTION_WITH_LOGIC:warning
```

## See also

- [`@PublishedApi` considerations](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#considerations-for-using-the-publishedapi-annotation)
- [Exemptions and internal API](../exemptions.md)
