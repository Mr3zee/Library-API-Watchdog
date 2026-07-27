# Inline functions with logic

`INLINE_FUNCTION_WITH_LOGIC` reports public inline functions and inline property accessors whose
body does more than delegate to a non-inline function.

| | |
|---|---|
| Diagnostic | `INLINE_FUNCTION_WITH_LOGIC` |
| Default severity | Error |
| Gradle property | [`inlineFunctionWithLogic`](configuration.md) |
| Exemption | [`@IntentionallyInlinedLogic`](exemptions.md) |

## What it reports

Any public `inline` function, and any inline property accessor (inline because the accessor or
its property carries the modifier), is flagged unless its body is a thin wrapper: a single
statement - besides an optional contract - that only reads or writes values and delegates to a
non-inline call:

```kotlin
// INLINE_FUNCTION_WITH_LOGIC
public inline fun choose(value: Int): Int = if (value < 0) -1 else 1
```

## Rationale

The compiler copies an inline function's body into every call site at compile time. Once a client
compiles against a library version, that copy - and any bug in it - is frozen in the client's
binary until the client recompiles against a fixed version; a regular function call would instead
pick up the fix at runtime by relinking against the new library binary. See the Kotlin library
authors' guide on
[`@PublishedApi` considerations](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#considerations-for-using-the-publishedapi-annotation).

## Don't

```kotlin
// Branching here is copied into every client binary; a fix needs every client to recompile.
// 
// INLINE_FUNCTION_WITH_LOGIC
public inline fun choose(value: Int): Int = if (value < 0) -1 else 1

// A local variable and a separate return statement: more than one delegating expression.
//
// INLINE_FUNCTION_WITH_LOGIC
public inline fun cachedLength(tag: String): Int {
    val cached = tag.length
    return cached
}
```

## Do

```kotlin
public inline fun choose(value: Int): Int = chooseImpl(value)

public inline fun cachedLength(tag: String): Int = lengthImpl(tag)

@PublishedApi
internal fun chooseImpl(value: Int): Int = if (value < 0) -1 else 1

@PublishedApi
internal fun lengthImpl(tag: String): Int = tag.length
```

Thin wrappers stay allowed: they resolve only what the call site knows and hand the actual work to
a non-inline function - here, the wrapper's own lambda parameter, a `crossinline` parameter
forwarded through a lambda literal, and a plain read/write of a non-inline property (the thin
setter shape):

```kotlin
@PublishedApi
internal var storedLimit: Int = 0

@PublishedApi
internal fun transactImpl(work: () -> Unit): Unit = work()

public inline fun <R> once(block: () -> R): R = block()

public inline fun transact(crossinline work: () -> Unit): Unit = transactImpl { work() }

public inline var limit: Int
    get() = storedLimit
    set(value) {
        storedLimit = value
    }
```

Notable cases:

- A thin wrapper may also resolve a reified type argument and narrow the delegate's result with
  `as`/`as?`; a contract declared with `contract { ... }` does not count as a second statement.
- Calling another inline function, or reading or writing through an inline accessor, is logic: the
  inliner drags that body into the client transitively even with no visible control flow.
- `@PublishedApi internal` inline functions and accessors are checked exactly like public ones: a
  public inline wrapper can call them, which inlines their body into clients just as transitively.
- Plain `internal` and `private` inline declarations are not part of the public or published API.
- Only inline bodies freeze into clients: a non-inline function or accessor keeps its logic in the
  library binary and is never reported; on a mixed property, only the inline accessor is reported.

## Exemption

Apply `@IntentionallyInlinedLogic` when inlining the logic is intended, for example when a lambda
must run inline for non-local returns or a hot path must not pay for an extra call:

```kotlin
@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public inline fun clamped(value: Int): Int = if (value < 0) 0 else value
```

The annotation targets the function declaration, or the property declaration to cover both of its
accessors at once; there is no parameter- or type-level placement.

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
- [Exemptions and internal API](exemptions.md)
