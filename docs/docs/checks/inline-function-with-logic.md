# Inline functions with logic

`INLINE_FUNCTION_WITH_LOGIC` reports public inline functions and inline property accessors whose
body does more than a thin delegation.

|                  |                                                  |
|------------------|--------------------------------------------------|
| Diagnostic       | `INLINE_FUNCTION_WITH_LOGIC`                     |
| Default severity | Error                                            |
| Gradle property  | [`inlineFunctionWithLogic`](../configuration.md) |
| Exemption        | [`@IntentionallyInlinedLogic`](../exemptions.md) |

## What it reports

Public and `@PublishedApi internal` inline functions and property accessors are reported unless
their body is a thin wrapper. A thin wrapper contains at most one statement, besides an optional
contract, and uses only the [permitted operations](#permitted-thin-wrapper-operations).

```kotlin
// !hide-focused
@file:JvmName("Payloads")

@PublishedApi
internal fun decodeValue(payload: String, type: KClass<*>): Any = payload

// !hide-focused(1:3)
/** Decodes [payload] as [T]. */
@Suppress("UNCHECKED_CAST")
@JvmSynthetic
// !diag[/decodePayload/] INLINE_FUNCTION_WITH_LOGIC ["inline function","decodePayload"]
public inline fun <reified T : Any> decodePayload(payload: String): T {
    require(payload.isNotBlank())
    return decodeValue(payload, T::class) as T
}
```

## Rationale

The compiler copies an inline function's body into every call site at compile time. Once a user
compiles against a library version, that copy and any bug in it are in the user's binary
until that binary is recompiled. A regular function call instead runs the implementation from the
library version present at runtime. See the Kotlin library authors' guide on
[`@PublishedApi` considerations](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#considerations-for-using-the-publishedapi-annotation).

The problem is easy to illustrate through a transitive dependency:

1. `analytics-sdk` is compiled against version 1.0 of your library and calls an inline function.
2. The inline body from 1.0 is copied into the published `analytics-sdk` binary.
3. An application later resolves version 1.1 of your library, which contains an important fix,
   without getting a recompiled `analytics-sdk`.
4. The SDK still executes its private copy of the 1.0 logic. Updating your library's version can't
   fix it.

Keep the actual logic in a non-inline `@PublishedApi internal`. The tiny public inline wrapper
still provides reified types or in-place lambdas, but precompiled callers contain only a normal
call to this delegate. When version 1.1 replaces the library, that call reaches the fixed delegate.

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
// !diag[/resolveService/] INLINE_FUNCTION_WITH_LOGIC ["inline function","resolveService"]
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

Do not mark `resolveServiceImpl` as `inline`. If both functions are inline, the compiler follows
the call and copies the delegate body too. The diagnostic reports both the inline delegate and the
wrapper that calls it.

## Permitted thin-wrapper operations

Control flow, operators, string templates, local declarations, object expressions, null-safe calls,
Elvis expressions, `!!`, multiple statements, and calls to inline declarations are not permitted.
They put library-authored behavior into the caller binary.

Instead, thin wrappers are permitted. A thin wrapper has at most one statement: 
an expression body, a single explicit `return`, or an empty body. 
An optional `contract` call before the statement doesn't count. 
The statement can combine any of the following operations, nested freely into one expression, and nothing else.

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

@PublishedApi
internal fun snapshotImpl(): Map<String, Long> = emptyMap()

// !hide-focused
/** Returns a snapshot of everything recorded so far. */
public inline fun snapshot(): Map<String, Long> {
    return snapshotImpl()
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

### Pass values as arguments

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

### Reified class references and casts 

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

### Direct invocation of the wrapper's own function parameter

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

See [Lambda parameters and suspend callers](#lambda-parameters-and-suspend-callers) for more details on this case.

### A lambda literal with a thin body 

A lambda literal here is `{ action() }` - the `action` parameter passed to `scheduleFlushImpl`:

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

Kotlin copies `{ action() }` into every caller. This is safe because the lambda only calls the user's `action`. 
The library's behavior stays in `scheduleFlushImpl`, where a library update can fix it.

The same rule applies to lambdas inside other lambdas:

```kotlin
// !hide-focused
@file:JvmName("Forms")

// !hide-focused
/** Application context */
public class Context { /* ... */ }

@PublishedApi
internal fun withApplicationContext(body: Context.() -> Unit) { /* ... */ }

@PublishedApi
internal fun addCallbackImpl(key: String, provider: () -> Unit) { /* ... */ }

// !hide-focused(1:2)
/** Add an application callback with an access to the Context */
@JvmSynthetic
public inline fun addCallback(
    key: String, 
    crossinline callback: Context.() -> Unit,
) {
    addCallbackImpl(key) { 
        withApplicationContext { 
            callback() 
        } 
    }
}
```

Kotlin copies both lambda bodies into every caller. Keep them simple and put library behavior in
regular functions such as `withApplicationContext` and `addCallbackImpl`.

This, for example, won't pass the check:

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
// !diag[/addCallback/] INLINE_FUNCTION_WITH_LOGIC ["inline function","addCallback"]
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

The lambda passed to `addCallbackImpl` contains two statements. Kotlin copies both into every
caller, so the check reports `addCallback`. Put the logging in a regular function instead.

## Lambda parameters and suspend callers

Directly invoking the wrapper's own lambda parameter, 
like `once` [here](#direct-invocation-of-the-wrappers-own-function-parameter), is permitted. 
The lambda body is written by the caller and is compiled into the caller binary in any case, so it is caller
logic rather than library logic.

However, in-place invocation is also why the `inline` modifier is useful for builder APIs. An inlined lambda
runs in the caller's context, so a caller inside a suspend function can call suspend functions in
the lambda even though the lambda type is not `suspend`.

Take this example of a builder API:

```kotlin
// !hide-focused
@file:JvmName("QueryParameters")

// !hide-focused
/** Immutable query parameters of a request. */
public class Parameters(val values: Map<String, List<String>>)

// !hide-focused
/** Mutable collector of query parameters. */
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
// !diag[/buildParameters/] INLINE_FUNCTION_WITH_LOGIC ["inline function","buildParameters"]
public inline fun buildParameters(
    builder: ParametersBuilder.() -> Unit,
): Parameters = ParametersBuilder().apply(builder).build()
```

And then a user code that calls it:

```kotlin
private suspend fun fetchToken(): String = "token"

private suspend fun authorizedQuery(): Parameters = buildParameters {
    append("token", fetchToken())
}
```

There is no thin rewrite of `buildParameters` that still runs `builder` directly in the caller.
Passing `builder` to a regular helper function requires `noinline`. Calling it from another lambda
requires `crossinline`. In both cases, `authorizedQuery` can no longer call `fetchToken` from
`builder`. Such a builder is a deliberate trade-off:

- Keep it inline and apply [`@IntentionallyInlinedLogic`](#exemption) when callers rely on suspend
  calls or non-local returns inside the lambda. Keep the exempted body minimal: every operation in
  it ends up in caller binaries.
- Drop the `inline` modifier when in-place execution is not worth it. A regular
  function keeps its logic in the library binary and is not checked at all, but callers can no
  longer call suspend functions inside the lambda.

## Notes

- A contract declared with `contract { ... }` doesn't count as a statement.
- `@PublishedApi internal` inline functions and accessors are checked exactly like public ones
  because a public inline wrapper can call them and transitively inline their bodies into user
  binaries.

## Exemption

<!-- diagnostic-exemption: INLINE_FUNCTION_WITH_LOGIC -->
If this API shape is intentional, apply `@IntentionallyInlinedLogic` to the declaration.

Use the exemption when a lambda must run in place for non-local returns or suspend calls, such as
the builder above, or when a hot path must not pay for an extra call:

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
