# Mutable collections in public API

|                  |                                                       |
|------------------|-------------------------------------------------------|
| Diagnostic       | `MUTABLE_COLLECTION_PUBLIC_API`                       |
| Default severity | Error                                                 |
| Gradle property  | [`mutableCollectionPublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyMutableCollection`](../exemptions.md) |

## What it reports

Mutable types in public API return types, property types, value and context parameters, and type parameter bounds.
The check includes nested type arguments and expanded type aliases.

It reports:

- `MutableList`, `MutableSet`, and `MutableMap`.
- `MutableCollection`, `MutableIterable`, `MutableIterator`, and `MutableListIterator`.
- `MutableMap.MutableEntry`.
- Implementations of these interfaces, such as `ArrayList`.
- `Array` and primitive or unsigned array types.

```kotlin
// !hide-focused
@file:JvmName("Collections")

// !hide-focused
/** Returns a caller-owned list of pending job names. */
// !diag[/MutableList<String>/] MUTABLE_COLLECTION_PUBLIC_API ["$function","produce","MutableList","$returnTypeFix"]
public fun produce(): MutableList<String> = mutableListOf()
```

## Rationale

A mutable signature doesn't state who owns the collection or whose state a mutation changes.
Use read-only types unless mutation is part of the contract.

### Don't

```kotlin
// !hide-focused
/** Exposes the values scheduled for processing. */
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
public class Holder(
    // !hide-focused
    /** Live collection of scheduled values. */
    // !diag[/MutableList<Int>/] MUTABLE_COLLECTION_PUBLIC_API ["$property","items","MutableList","$returnTypeFix"]
    public val items: MutableList<Int>,
)
```

### Do

```kotlin
// !hide-focused
/** Gives read-only access to scheduled values. */
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
public class Holder(
    // !hide-focused
    /** Scheduled values. */
    public val items: List<Int>,
)
```

## Notes

The check skips:

- The array of a `vararg` parameter. The check still reports mutable element types.
- Extension receivers, including function-type receivers such as `MutableList<Int>.() -> Unit`.
- Class context parameters.
- Overrides.
- `@PublishedApi internal` declarations.

A read-only type can still refer to a mutable collection. Copy input collections when callers must not change stored values.

## Exemption

<!-- diagnostic-exemption: MUTABLE_COLLECTION_PUBLIC_API -->
If this is intended, apply `@IntentionallyMutableCollection` to the declaration,
parameter, or type usage.

Use the exemption when the declaration is part of the API design or must stay for backwards compatibility.

```kotlin
// !hide-focused
@file:JvmName("Collections")

// !hide-focused
/** Exposes a shared collection of scheduled values. */
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
public class Holder(
    // !hide-focused
    /** Live collection of scheduled values. */
    @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
    public val items: MutableList<Int>,
)
```

## Configuration

```kotlin build.gradle.kts
apiWatchdog {
    mutableCollectionPublicApi = WatchdogSeverity.ERROR
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=MUTABLE_COLLECTION_PUBLIC_API:error
```

## See also

- [Avoid exposing mutable state](https://kotlinlang.org/docs/api-guidelines-predictability.html#avoid-exposing-mutable-state)
- [Pair and Triple in public API](./pair-or-triple-public-api.md)
- [Exemptions and internal API](../exemptions.md)
