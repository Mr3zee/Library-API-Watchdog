# Function type aliases in public API

`FUNCTION_TYPE_ALIAS_PUBLIC_API` reports public type aliases that expand to a function type.

|                  |                                                       |
|------------------|-------------------------------------------------------|
| Diagnostic       | `FUNCTION_TYPE_ALIAS_PUBLIC_API`                      |
| Default severity | Error                                                 |
| Gradle property  | [`functionTypeAliasPublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyFunctionTypeAlias`](../exemptions.md) |

## What it reports

A type alias in the public API whose expanded type is a function type.

```kotlin
// !hide-focused
/** Handles progress updates emitted by background work. */
// !diag[/ProgressHandler/] FUNCTION_TYPE_ALIAS_PUBLIC_API ["ProgressHandler"]
public typealias ProgressHandler = (Int) -> Unit
```

## Rationale

A type alias is not a real type: it is erased at compile time, so a user compiled against
`Callback` really binds to `(Int) -> Unit`. The alias can never grow a second member, a default
implementation, or additional constraints. The only way to change the shape later is a breaking
change to the bare function type. A
[`fun interface`](https://kotlinlang.org/docs/fun-interfaces.html#functional-interfaces-vs-type-aliases)
keeps the same lambda call-site ergonomics (SAM conversion) behind a real type that can
be extended from or can new default members without breaking binary compatibility.


### Don't

```kotlin
// !hide-focused
/** Receives completed work percentages. */
// !diag[/Callback/] FUNCTION_TYPE_ALIAS_PUBLIC_API ["Callback"]
public typealias Callback = (Int) -> Unit
```

### Do

```kotlin
// !hide-focused
/** Receives completed work percentages. */
// !hide-focused
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public fun interface Callback {
    // !hide-focused
    /** Reports that [value] percent of the work is complete. */
    public fun onCall(value: Int)
}
```



### Don't {#dont-2}

```kotlin
// !hide-focused
/** Represents an operation that may suspend. */
// !diag[/SuspendAction/] FUNCTION_TYPE_ALIAS_PUBLIC_API ["SuspendAction"]
public typealias SuspendAction = suspend () -> Unit
```

### Do {#do-2}

```kotlin
// !hide-focused
/** Represents an operation that may suspend. */
// !hide-focused
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public fun interface SuspendAction {
    // !hide-focused
    /** Executes the operation. */
    public suspend fun invoke()
}
```


## Notes

- Nullable and receiver variants are caught the same way: `public typealias Some = ((String) -> Boolean)?` and
  `public typealias Some = StringBuilder.() -> Unit` are both function types under the alias.
- A function type nested inside another type, such as `List<(Int) -> Unit>`, doesn't trigger the
  check, only the type an alias directly expands to counts.
- Aliases for reflection function types (`KFunction1`, `KSuspendFunction2`, ...) are not
  reported: a `fun interface` can't replace them.
- `@PublishedApi` itself can't target a type alias, but an alias nested inside a
  `@PublishedApi internal` class is not reported: library users can't name it in source, and an
  alias contributes no binary declaration of its own.

## Exemption

<!-- diagnostic-exemption: FUNCTION_TYPE_ALIAS_PUBLIC_API -->
If this API shape is intentional, apply `@IntentionallyFunctionTypeAlias` to the type alias.

```kotlin
// !hide-focused
/** Receives completed work percentages through a deliberately bare function shape. */
@IntentionallyFunctionTypeAlias(reason = ExemptionReason.API_DESIGN)
public typealias Callback = (Int) -> Unit
```

## Configuration

```kotlin
apiWatchdog {
    functionTypeAliasPublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=FUNCTION_TYPE_ALIAS_PUBLIC_API:warning
```

## See also

- [Functional interfaces vs. type aliases](https://kotlinlang.org/docs/fun-interfaces.html#functional-interfaces-vs-type-aliases)
- [Exemptions and internal API](../exemptions.md)
- [Data classes in public API](./data-class-public-api.md)
