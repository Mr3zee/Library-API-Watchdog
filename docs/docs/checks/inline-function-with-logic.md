# Inline functions with logic

`INLINE_FUNCTION_WITH_LOGIC` reports public inline functions and inline property accessors whose
body does more than delegate to a non-inline function.

|                  |                                               |
|------------------|-----------------------------------------------|
| Diagnostic       | `INLINE_FUNCTION_WITH_LOGIC`                  |
| Default severity | Error                                         |
| Gradle property  | [`inlineFunctionWithLogic`](../configuration.md) |
| Exemption        | [`@IntentionallyInlinedLogic`](../exemptions.md) |

## What it reports

Any public `inline` function and any inline property accessor are flagged unless their body is a thin wrapper: a single
statement - besides an optional contract - that only performs simple operations and delegates to a
non-inline call:

```kotlin
// !diag[/choose/] INLINE_FUNCTION_WITH_LOGIC ["inline function","choose"]
public inline fun choose(value: Int): Int = if (value < 0) -1 else 1
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
// !diag[/choose/] INLINE_FUNCTION_WITH_LOGIC ["inline function","choose"]
public inline fun choose(value: Int): Int = if (value < 0) -1 else 1

// !diag[/cachedLength/] INLINE_FUNCTION_WITH_LOGIC ["inline function","cachedLength"]
public inline fun cachedLength(tag: String): Int {
    val cached = tag.length
    return cached
}
```

### Do

```kotlin
public inline fun choose(value: Int): Int = chooseImpl(value)

public inline fun cachedLength(tag: String): Int =
    cachedLengthImpl(tag)

@PublishedApi
internal fun chooseImpl(value: Int): Int = if (value < 0) -1 else 1

@PublishedApi
internal fun cachedLengthImpl(tag: String): Int = withCache {
    tag.length
}
```

### Don't {#dont-2}

```kotlin
@PublishedApi
internal val array1: Array<Int> = arrayOf()

@PublishedApi
internal val array2: Array<Int> = arrayOf()

// !diag[/calculateArraysSize/] INLINE_FUNCTION_WITH_LOGIC ["inline getter","calculateArraysSize"]
public inline val calculateArraysSize: Int
    get() {
        return array1.size + array2.size
    }
```

### Do {#do-2}

```kotlin
public val calculateArraysSize: Int
    get() = calculateArraysSizeImpl()
```

### Don't {#dont-3}

```kotlin
public inline fun <reified T> resolveFunctionsCount(): Int {
    return T::class.memberFunctions.size
}
```

### Do {#do-3}

```kotlin
@PublisedApi
public fun <T> resolveFunctionsCountImpl(kClass: KClass<T>): Int {
    return kClass.memberFunctions.size
}

public inline fun <reified T> resolveFunctionsCount(): Int {
    return resolveFunctionsCountImpl(T::class)
}
```

## Notes

- A contract declared with `contract { ... }` doesn't count as a statement.
- Calling another inline function, or reading or writing through an inline accessor, is logic: the
  inliner drags that body into the user transitively even with no visible control flow.
- `@PublishedApi internal` inline functions and accessors are checked exactly like public ones: a
  public inline wrapper can call them, which inlines their body into users just as transitively.

## Exemption

Apply `@IntentionallyInlinedLogic` when inlining the logic is intended, for example when a lambda
must run inline for non-local returns or a hot path must not pay for an extra call:

```kotlin
@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public inline fun clamped(value: Int): Int =
    if (value < 0) 0 else value
```

## Configuration

```kotlin
apiWatchdog {
    inlineFunctionWithLogic = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=INLINE_FUNCTION_WITH_LOGIC:warning
```

## See also

- [`@PublishedApi` considerations](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#considerations-for-using-the-publishedapi-annotation)
- [Exemptions and internal API](../exemptions.md)
