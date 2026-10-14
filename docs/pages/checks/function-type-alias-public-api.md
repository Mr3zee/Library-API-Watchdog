# Function type aliases in public API

|                  |                                                       |
|------------------|-------------------------------------------------------|
| Diagnostic       | `FUNCTION_TYPE_ALIAS_PUBLIC_API`                      |
| Default severity | Error                                                 |
| Gradle property  | [`functionTypeAliasPublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyFunctionTypeAlias`](../exemptions.md) |

## What it reports

A type alias in the public API that expands to a function type.

```kotlin
// !hide-focused
/** Handles progress updates emitted by background work. */
// !diag[/ProgressHandler/] FUNCTION_TYPE_ALIAS_PUBLIC_API ["ProgressHandler"]
public typealias ProgressHandler = (Int) -> Unit
```

## Rationale

Compiled callers use the expanded function type, so a signature change breaks compatibility.
A `fun interface` gives the callback a named type that can have additional members or hierarchy.

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

## Notes

- Function types nested in another type, such as `List<(Int) -> Unit>`, aren't reported.
- Reflection function types, such as `KFunction1` and `KSuspendFunction2`, aren't reported.
- Aliases inside a `@PublishedApi internal` class aren't reported.

## Exemption

<!-- diagnostic-exemption: FUNCTION_TYPE_ALIAS_PUBLIC_API -->
If this is intended, apply `@IntentionallyFunctionTypeAlias` to the type alias.

Use the exemption when the expanded function type is part of the API contract.

```kotlin
// !hide-focused
/** Receives completed work percentages through a function type. */
@IntentionallyFunctionTypeAlias(reason = ExemptionReason.API_DESIGN)
public typealias Callback = (Int) -> Unit
```

## Configuration

```kotlin
apiWatchdog {
    functionTypeAliasPublicApi = WatchdogSeverity.WARNING
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=FUNCTION_TYPE_ALIAS_PUBLIC_API:warning
```

## See also

- [Functional interfaces and type aliases](https://kotlinlang.org/docs/fun-interfaces.html#functional-interfaces-vs-type-aliases)
- [Exemptions and internal API](../exemptions.md)
- [Data classes in public API](./data-class-public-api.md)
