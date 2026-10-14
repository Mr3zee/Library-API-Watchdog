# Inline functions with logic

|                  |                                                  |
|------------------|--------------------------------------------------|
| Diagnostic       | `INLINE_FUNCTION_WITH_LOGIC`                     |
| Default severity | Error                                            |
| Gradle property  | [`inlineFunctionWithLogic`](../configuration.md) |
| Exemption        | [`@IntentionallyInlinedLogic`](../exemptions.md) |

## What it reports

An inline function or property accessor in the public API or declared as `@PublishedApi internal`
whose body isn't a thin wrapper.
A thin wrapper has at most one statement after an optional `contract { ... }` call,
and the statement uses only the [accepted operations](#accepted-operations).
Parameter defaults must meet the same limits.

```kotlin
// !hide-focused
@file:JvmName("Payloads")

@PublishedApi
internal fun decodeValue(payload: String, type: KClass<*>): Any = payload

// !hide-focused(1:3)
/** Decodes [payload] as [T]. */
@Suppress("UNCHECKED_CAST")
@JvmSynthetic
// !diag[/decodePayload/] INLINE_FUNCTION_WITH_LOGIC ["$inlineFunction","decodePayload"]
public inline fun <reified T : Any> decodePayload(payload: String): T {
    require(payload.isNotBlank())
    return decodeValue(payload, T::class) as T
}
```

## Rationale

The compiler copies inline bodies into caller binaries.
A new library version doesn't replace those copies until callers recompile.
Move the logic to a non-inline `@PublishedApi internal` function and call it from the inline wrapper.
Compiled callers then run the implementation of the library version on their runtime classpath.

How the problem appears:

1. `analytics-sdk` compiles against `metrics-sdk` **1.0** and calls its inline function.
2. The published `analytics-sdk` binary holds a copy of the **1.0** inline body.
3. An application depends on the published `analytics-sdk` and resolves `metrics-sdk` **1.1**,
   which fixes the inline body.
4. `analytics-sdk` still runs its copy of the **1.0** body. A new `metrics-sdk` version can't fix it.

### Don't

```kotlin
// !hide-focused
@file:JvmName("Services")

@PublishedApi
internal fun serviceByType(type: KClass<*>): Any? = TODO()

// !hide-focused(1:3)
/** Returns the registered service of type [T]. */
@Suppress("UNCHECKED_CAST")
@JvmSynthetic
// !diag[/resolveService/] INLINE_FUNCTION_WITH_LOGIC ["$inlineFunction","resolveService"]
public inline fun <reified T : Any> resolveService(): T {
    val service = serviceByType(T::class)
    return checkNotNull(service) as T
}
```

### Do

```kotlin
// !hide-focused
@file:JvmName("Services")

@PublishedApi
internal fun serviceByType(type: KClass<*>): Any? = TODO()

// !hide-focused
@Suppress("UNCHECKED_CAST")
@PublishedApi
internal fun <T : Any> resolveServiceImpl(type: KClass<T>): T {
    val service = serviceByType(type)
    return checkNotNull(service) as T
}

// !hide-focused(1:2)
/** Returns the registered service of type [T]. */
@JvmSynthetic
public inline fun <reified T : Any> resolveService(): T = 
    resolveServiceImpl(T::class)
```

## Notes

A thin wrapper has one of these body forms:

- An expression body.
- A block with one statement, with or without `return`.
- An empty body.

Accepted operations can nest. Each receiver and argument must be an accepted operation too.

The check rejects these operations because they copy library behavior into caller binaries:

- Branches, loops, and `try` expressions.
- `throw`, string templates, local declarations, and object expressions.
- Null-safe calls, Elvis expressions, and `!!`.
- Equality expressions such as `a == b`, for every type.
- Operator and infix members of built-in types such as `Int`, `Boolean`, `String`, and arrays,
  in any syntax: `x + 1`, `x.plus(1)`, `!flag`, and `array[0]`.
- Calls, property accesses, and callable references to inline declarations outside the standard library.
  The compiler copies their bodies through the wrapper.

The check accepts standard library functions and properties, inline or not,
such as `println(message)`, `text.toInt()`, and `value.let { it }`.
Built-in operators and infix members stay rejected.

## Accepted operations

### Calls to non-inline functions and constructors

```kotlin
// !hide-focused
@file:JvmName("Metrics")

@PublishedApi
internal fun recordImpl(name: String, value: Long) {}

// !hide-focused
/** Records [value] under [name]. */
public inline fun record(name: String, value: Long) {
    recordImpl(name, value)
}
```

```kotlin
// !hide-focused
@file:JvmName("Metrics")

// !hide-focused
/** Creates a builder for a metric label starting with [prefix]. */
public inline fun labelBuilder(prefix: String): StringBuilder {
    return StringBuilder(prefix)
}
```

### Values and arguments

A wrapper can use these values:

- Parameter reads and `this` references.
- Literals, including negative numbers and `null`.
- Object, companion object, and enum entry references.
- Vararg spreads such as `*values`.

```kotlin
// !hide-focused
@file:JvmName("Metrics")

@PublishedApi
internal fun recordImpl(name: String, value: Long) {}

// !hide-focused
/** Records this measurement under [name]. */
public inline fun Long.recordAs(name: String) {
    recordImpl(name = name, value = this)
}
```

```kotlin
// !hide-focused
@file:JvmName("Metrics")

@PublishedApi
internal fun recordAllImpl(vararg values: Long) {}

// !hide-focused
/** Records every measurement in [values]. */
public inline fun recordAll(vararg values: Long) {
    recordAllImpl(*values)
}
```

### Property access through non-inline accessors

Outside the standard library, a property read must use a non-inline getter
and a property assignment must use a non-inline setter.

```kotlin
// !hide-focused
@file:JvmName("Metrics")

@PublishedApi
internal var limitValue: Int = 0

// !hide-focused
/** The maximum number of buffered measurements. */
public inline var limit: Int
    get() = limitValue
    set(value) {
        limitValue = value
    }
```

### Callable references

Outside the standard library, a callable reference must target a non-inline function
or a property with a non-inline getter.

```kotlin
// !hide-focused
@file:JvmName("Metrics")

@PublishedApi
internal fun flushImpl() {}

// !hide-focused
/** The action that flushes buffered measurements. */
public inline val flushAction: () -> Unit
    get() = ::flushImpl
```

### Class references, type checks, and casts

```kotlin
// !hide-focused
@file:JvmName("Decoding")

@PublishedApi
internal fun decodeImpl(payload: String, type: KClass<*>): Any = TODO()

// !hide-focused(1:3)
/** Decodes [payload] as [T]. */
@Suppress("UNCHECKED_CAST")
@JvmSynthetic
public inline fun <reified T : Any> decode(payload: String): T =
    decodeImpl(payload, T::class) as T
```

```kotlin
// !hide-focused
@file:JvmName("Decoding")

// !hide-focused(1:2)
/** Whether [value] is a [T]. */
@JvmSynthetic
public inline fun <reified T : Any> holds(value: Any?): Boolean = value is T
```

### Calls to the wrapper's own function parameter

The caller writes the lambda body, so it is caller logic.
The compiler copies it into the caller binary in any case.

```kotlin
// !hide-focused(1:6)
@file:JvmName("Execution")

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// !hide-focused(1:2)
/** Runs [block] in place exactly once. */
@JvmSynthetic
@OptIn(ExperimentalContracts::class)
public inline fun <R> once(block: () -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return block()
}
```

See [Lambda parameters and suspend callers](#lambda-parameters-and-suspend-callers) for more details.

### Lambda literals with a thin body

The compiler copies a lambda body into caller binaries too.
So each lambda or anonymous function body, nested or not, must meet the same limits as the wrapper.

```kotlin
// !hide-focused
@file:JvmName("Metrics")

@PublishedApi
internal fun scheduleFlushImpl(action: () -> Unit) {}

// !hide-focused(1:2)
/** Schedules [action] to run after the next flush. */
@JvmSynthetic
public inline fun afterFlush(crossinline action: () -> Unit) {
    scheduleFlushImpl { action() }
}
```

This lambda has two statements, so the check reports `addCallback`:

```kotlin
// !hide-focused(1:10)
@file:JvmName("Forms")

/** Application context */
public class Context { /* ... */ }

@PublishedApi
internal fun withApplicationContext(body: Context.() -> Unit) { /* ... */ }

@PublishedApi
internal fun addCallbackImpl(key: String, provider: () -> Unit) { /* ... */ }

// !hide-focused(1:2)
/** Add an application callback with an access to the Context */
@JvmSynthetic
// !diag[/addCallback/] INLINE_FUNCTION_WITH_LOGIC ["$inlineFunction","addCallback"]
public inline fun addCallback(
    key: String,
    crossinline callback: Context.() -> Unit,
) {
    addCallbackImpl(key) {
        println("Running callback: $key")
        withApplicationContext { callback() }
    }
}
```

Move the whole lambda body into one non-inline function, so the lambda has one plain call.

### User-defined operators and infix functions

Outside the standard library, the operator or infix function must be non-inline.

```kotlin
// !hide-focused
@file:JvmName("Vectors")

// !hide-focused(1:4)
/** A two-dimensional vector. */
@IntentionallyWithoutEqualsHashCodeOrToString(reason = ExemptionReason.API_DESIGN)
public class Vector {
    /** Adds [other] to this vector. */
    public operator fun plus(other: Vector): Vector = this
}

// !hide-focused
/** Returns the sum of [a] and [b]. */
public inline fun sum(a: Vector, b: Vector): Vector = a + b
```

## Lambda parameters and suspend callers

An in-place lambda runs in the caller's context.
So a suspend caller can call suspend functions in it even though the lambda type isn't `suspend`.
This is what makes `inline` useful for builder APIs:

```kotlin
// !hide-focused
@file:JvmName("QueryParameters")

// !hide-focused(1:2)
/** Immutable query parameters of a request. */
@Poko
public class Parameters(
    // !hide-focused
    /** Query values grouped by parameter name. */
    public val values: Map<String, List<String>>,
)

// !hide-focused(1:2)
/** Mutable collector of query parameters. */
@IntentionallyWithoutEqualsHashCodeOrToString(reason = ExemptionReason.API_DESIGN)
public class ParametersBuilder {
    private var map = mutableMapOf<String, MutableList<String>>()

    // !hide-focused
    /** Appends [value] under [name]. */
    public fun append(name: String, value: String) {
        map.computeIfAbsent(name) { mutableListOf() }.add(value)
    }

    // !hide-focused
    /** Builds the immutable [Parameters]. */
    public fun build(): Parameters { 
        return Parameters(map.mapValues { it.value.toList() }.toMap()) 
    }
}

// !hide-focused(1:2)
/** Builds a [Parameters] instance with the given [builder] function. */
@JvmSynthetic
public inline fun buildParameters(
    builder: ParametersBuilder.() -> Unit,
): Parameters = ParametersBuilder().apply(builder).build()
```

A suspend caller:

```kotlin
private suspend fun fetchToken(): String = "token"

private suspend fun authorizedQuery(): Parameters = buildParameters {
    append("token", fetchToken())
}
```

## Exemption

<!-- diagnostic-exemption: INLINE_FUNCTION_WITH_LOGIC -->
If this is intended, apply `@IntentionallyInlinedLogic` to the declaration.

Use the exemption when the declaration is part of the API design or must stay for backwards compatibility.

```kotlin
// !hide-focused
@file:JvmName("Tracing")

@PublishedApi
internal fun beginTrace(name: String): Any = name

@PublishedApi
internal fun endTrace(token: Any) {}

// !hide-focused(1:2)
/** Runs [block] while a trace is active. */
@JvmSynthetic
@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public inline fun <R> traced(name: String, block: () -> R): R {
    val token = beginTrace(name)
    try {
        return block()
    } finally {
        endTrace(token)
    }
}
```

## Configuration

```kotlin
apiWatchdog {
    inlineFunctionWithLogic = WatchdogSeverity.ERROR
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=INLINE_FUNCTION_WITH_LOGIC:error
```

## See also

- [`@PublishedApi` considerations](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#considerations-for-using-the-publishedapi-annotation)
- [Exemptions and internal API](../exemptions.md)
