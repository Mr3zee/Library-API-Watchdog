# Mangled JVM names in public API

`MANGLED_JVM_NAME_PUBLIC_API` reports public functions, properties, and constructors that Java
sources can't call because a value class in their signature makes the JVM backend mangle the
compiled name.

|                  |                                                       |
|------------------|-------------------------------------------------------|
| Diagnostic       | `MANGLED_JVM_NAME_PUBLIC_API`                         |
| Default severity | Error                                                 |
| Applies to       | JVM compilations only                                 |
| Gradle property  | [`mangledJvmNamePublicApi`](../../configuration.md)   |
| Exemption        | [`@IntentionallyMangledJvmName`](../../exemptions.md) |

## What it reports

A value class among the value parameters, the extension receiver, or the context parameters of a
function or property - nullable types and type parameters bounded by a value class included.

```kotlin
// !hide-focused
@file:JvmName("Accounts")

// !hide-focused(1:5)
/**
 * Stable identifier assigned to a user.
 *
 * @property raw identifier as stored by the user service.
 */
@JvmInline
public value class UserId(public val raw: String)

// !hide-focused
/** Finds the account identified by [id]. */
// !diag[/take/] MANGLED_JVM_NAME_PUBLIC_API ["function","take","UserId"]
public fun take(id: UserId) { }
```

## Rationale

Value classes compile to their underlying type, so the backend needs a hashed suffix to keep the
compiled method distinct from an overload taking the underlying type directly - `take(id: UserId)`
compiles to `take-4ZD5Yi0(...)`. A constructor gets no such suffix: the visible one becomes
private and a synthetic overload with a marker parameter takes its place. Kotlin callers resolve
by the source signature and never notice, but for Java the declaration is unreachable. See the
Kotlin guide on
[inline value classes and mangling](https://kotlinlang.org/docs/java-to-kotlin-interop.html#inline-value-classes).


### Don't

```kotlin
// !hide-focused
@file:JvmName("Users")

// Compiles to take-4ZD5Yi0(...): an illegal Java identifier.
// !hide-focused
/** Queues a refresh for the account identified by [id]. */
// !diag[/take/] MANGLED_JVM_NAME_PUBLIC_API ["function","take","UserId"]
public fun take(id: UserId) { }
```

### Do

```kotlin
// !hide-focused
@file:JvmName("Users")

// !hide-focused
/** Queues a refresh for the account identified by [id]. */
@JvmName("take")
public fun take(id: UserId) { }
```



### Don't {#dont-2}

```kotlin
// The public constructor is replaced by
// a private one and a synthetic marker-parameter overload.
// !hide-focused(1:5)
/**
 * Wallet associated with a user account.
 *
 * @property id identifier of the account that owns the wallet.
 */
// !hide-focused
@Poko
// !diag[/[(]public val id: UserId[)]/] MANGLED_JVM_NAME_PUBLIC_API ["constructor","Wallet","UserId"]
// !diag[/id/] MANGLED_JVM_NAME_PUBLIC_API ["property","id","UserId"]
public class Wallet(public val id: UserId)
```

### Do {#do-2}

```kotlin
// !hide-focused(1:5)
/**
 * Wallet associated with a user account.
 *
 * @property id identifier of the account that owns the wallet.
 */
@OptIn(ExperimentalStdlibApi::class)
@JvmExposeBoxed
// !hide-focused
@Poko
public class Wallet(public val id: UserId)
```

`@JvmExposeBoxed` generates Java-callable boxed variants alongside the mangled ones. It is the
only fix for constructors and overridable members, since `@JvmName` doesn't accept them.


## Notes

- A value class inside a type argument (`List<UserId>`) is boxed and keeps the JVM name. Only the
  classifier itself mangles, not a type it is nested in.
- A top-level callable that only *returns* a value class keeps its JVM name. 
- A `var` property's setter mangles independently of the getter - renaming or hiding one accessor
  leaves the other checked on its own.
- Members and constructors of the value class itself are not reported: declaring the public value class
  is the deliberate choice.
- `suspend` functions are reported by [`KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC`](./kotlin-only-api-without-jvm-synthetic.md) instead.
- Overrides are not reported: their signature is fixed by the overridden declaration, which is
  reported instead.
- `@PublishedApi internal` declarations are not reported: their public bytecode entry is a binary
  implementation detail, not supported Java source API.
- `@JvmSynthetic` declarations are hidden from Java on purpose and are not reported.
- Non-JVM compilations never register this check at all.

## Exemption

Apply `@IntentionallyMangledJvmName` when Java callers are not supported for this declaration:

```kotlin
// !hide-focused
@file:JvmName("Users")

// !hide-focused
/** Queues a refresh for [id] through a deliberately Kotlin-only API. */
@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public fun take(id: UserId) { }
```

## Configuration

```kotlin
apiWatchdog {
    javaInterop {
        mangledJvmNamePublicApi = WatchdogSeverity.WARNING
    }
}
```

The property lives inside the `javaInterop { }` block. `javaInterop { enabled = false }` turns off
this check along with the rest of the [Java interop checks](./java-interop.md) group.

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlin.library.api.watchdog:diagnosticSeverity=MANGLED_JVM_NAME_PUBLIC_API:warning
```

## See also

- [Inline value classes and mangling](https://kotlinlang.org/docs/java-to-kotlin-interop.html#inline-value-classes)
- [Java interop checks](./java-interop.md)
- [Kotlin-only API without JvmSynthetic](./kotlin-only-api-without-jvm-synthetic.md)
- [Exemptions and internal API](../../exemptions.md)
