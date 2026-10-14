# Mangled JVM names in public API

|                  |                                                       |
|------------------|-------------------------------------------------------|
| Diagnostic       | `MANGLED_JVM_NAME_PUBLIC_API`                         |
| Default severity | Error                                                 |
| Applies to       | JVM compilations only                                 |
| Gradle property  | [`mangledJvmNamePublicApi`](../../configuration.md)   |
| Exemption        | [`@IntentionallyMangledJvmName`](../../exemptions.md) |

## What it reports

Functions, properties, and constructors in the public API whose JVM name changes because the
signature uses a value class:

- In a value parameter, extension receiver, or context parameter.
- As a member's return type.
- As a mutable property's type, which affects its setter even at the top level.

```kotlin
// !hide-focused
@file:JvmName("Accounts")

// !hide-focused
/** Stable identifier assigned to a user. */
@JvmInline
public value class UserId(
    // !hide-focused
    /** Identifier as stored by the user service. */
    public val raw: String,
)

// !hide-focused
/** Finds the account identified by [id]. */
// !diag[/take/] MANGLED_JVM_NAME_PUBLIC_API ["$function","take","UserId","$functionFix"]
public fun take(id: UserId) { }
```

## Rationale

A mangled JVM method name such as `take-<hash>` is invalid in Java source.

For constructors with value-class parameters, the compiler generates a private constructor and
a synthetic constructor that Java source can't call.

Use `@JvmName` to give the function a valid name in Java source.

### Don't

```kotlin
// !hide-focused
@file:JvmName("Users")

// !hide-focused
/** Queues a refresh for the account identified by [id]. */
// !diag[/take/] MANGLED_JVM_NAME_PUBLIC_API ["$function","take","UserId","$functionFix"]
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

`@JvmExposeBoxed` generates boxed variants for Java callers alongside the mangled variants.
Constructors need this annotation because the compiler doesn't accept `@JvmName` on them.

### Don't {#dont-2}

```kotlin
// !hide-focused
/** Wallet associated with a user account. */
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
// !diag[/[(]/] MANGLED_JVM_NAME_PUBLIC_API ["$constructor","Wallet","UserId","$constructorFix"]
public class Wallet(
    // !hide-focused
    /** Identifier of the account that owns the wallet. */ 
    // !diag[/id/] MANGLED_JVM_NAME_PUBLIC_API ["$property","id","UserId","$propertyGetterFix"]
    public val id: UserId
)
```

### Do {#do-2}

```kotlin
// !hide-focused
/** Wallet associated with a user account. */
@OptIn(ExperimentalStdlibApi::class)
@JvmExposeBoxed
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
public class Wallet(
    // !hide-focused
    /** Identifier of the account that owns the wallet. */
    public val id: UserId,
)
```

### Don't {#dont-3}

```kotlin
// !hide-focused
/** Account operations of the user service. */
public class Ledger {
    // !hide-focused
    /** The identifier of the current account. */
    // !diag[/current/] MANGLED_JVM_NAME_PUBLIC_API ["$function","current","UserId","$functionFix"]
    public fun current(): UserId = UserId("x")
}
```

### Do {#do-3}

```kotlin
// !hide-focused
/** Account operations of the user service. */
public class Ledger {
    // !hide-focused
    /** The identifier of the current account. */
    @JvmName("current")
    public fun current(): UserId = UserId("x")
}
```

## Notes

- Rename property accessors with `@get:JvmName` and `@set:JvmName`.
  The check treats each accessor separately. A rename or `@JvmSynthetic` on one accessor doesn't exempt the other.
- Open and abstract members don't support `@JvmName` or boxed variants.
  If Java callers need access, remove the value class from the signature.
- Value classes in type arguments, such as `List<UserId>`, don't cause JVM name mangling.
- If a top-level function or getter uses a value class only as its return type, its JVM name doesn't change.
- The check skips members and constructors of the value class itself.
- The plugin checks `suspend` functions with [Kotlin-only API without JvmSynthetic](./kotlin-only-api-without-jvm-synthetic.md).
- The check skips overrides, `@JvmSynthetic` functions and accessors, and `@PublishedApi internal` declarations.

## Exemption

<!-- diagnostic-exemption: MANGLED_JVM_NAME_PUBLIC_API -->
If this is intended, apply `@IntentionallyMangledJvmName` to the declaration or an
enclosing class.

Use the exemption when the declaration is part of the API design or must stay for backwards compatibility.

```kotlin
// !hide-focused
@file:JvmName("Users")

// !hide-focused
/** Queues a refresh for the account identified by [id]. */
@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public fun take(id: UserId) { }
```

## Configuration

```kotlin build.gradle.kts
apiWatchdog {
    javaInterop {
        mangledJvmNamePublicApi = WatchdogSeverity.ERROR
    }
}
```

`javaInterop { enabled = false }` disables all [Java interop checks](./java-interop.md).

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=MANGLED_JVM_NAME_PUBLIC_API:error
```

## See also

- [Inline value classes](https://kotlinlang.org/docs/java-to-kotlin-interop.html#inline-value-classes)
- [Java interop checks](./java-interop.md)
- [Kotlin-only API without JvmSynthetic](./kotlin-only-api-without-jvm-synthetic.md)
- [Exemptions and internal API](../../exemptions.md)
