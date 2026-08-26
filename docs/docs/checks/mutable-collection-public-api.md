# Mutable collections in public API

`MUTABLE_COLLECTION_PUBLIC_API` reports mutable collection and array types in public signatures.

|                  |                                                       |
|------------------|-------------------------------------------------------|
| Diagnostic       | `MUTABLE_COLLECTION_PUBLIC_API`                       |
| Default severity | Error                                                 |
| Gradle property  | [`mutableCollectionPublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyMutableCollection`](../exemptions.md) |

## What it reports

Flags public APIs that expose a mutable collection type (any of the `kotlin.collections` mutable interfaces):
 - `MutableList`
 - `MutableSet` 
 - `MutableMap`
 - `MutableCollection`
 - `MutableIterable`
 - `MutableIterator`,
 - `MutableListIterator`
 - `MutableMap.MutableEntry` 
 - A classifier implementing one of them (`ArrayList`, a hand-written `MutableList` subtype, etc.)
 - An array (`Array`, `IntArray`, etc.) 

```kotlin
// !hide-focused
@file:JvmName("Collections")

// !hide-focused
/** Returns a caller-owned list of pending job names. */
// !diag[/MutableList<String>/] MUTABLE_COLLECTION_PUBLIC_API ["function","produce","MutableList"]
public fun produce(): MutableList<String> = mutableListOf()

// !hide-focused
/** Returns batches that callers may edit in place. */
// !diag[/List<MutableList<Int>>/] MUTABLE_COLLECTION_PUBLIC_API ["function","nested","MutableList"]
public fun nested(): List<MutableList<Int>> = emptyList()
```

## Rationale

A mutable return type or property lets users mutate a collection they don't own. A mutable
parameter lets the library mutate an argument the user still holds. Either way, once a mutable
collection crosses the API boundary it is unclear which mutations are safe, and the library can
no longer swap its internal representation for a different collection type without risking a
behavioral change for users that relied on mutating the exposed instance. See the Kotlin guide on
[avoiding exposing mutable state](https://kotlinlang.org/docs/api-guidelines-predictability.html#avoid-exposing-mutable-state).


### Don't

```kotlin
// !hide-focused(1:5)
/**
 * Exposes the values scheduled for processing.
 *
 * @property items live collection of scheduled values.
 */
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
// !diag[/MutableList<Int>/] MUTABLE_COLLECTION_PUBLIC_API ["property","items","MutableList"]
public class Holder(public val items: MutableList<Int>)
```

### Do

```kotlin
// !hide-focused(1:5)
/**
 * Captures the values scheduled at construction time.
 *
 * @property items immutable snapshot of the scheduled values.
 */
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
public class Holder(public val items: List<Int>)
```

### Don't {#dont-2}

```kotlin
// !hide-focused
@file:JvmName("Collections")

// !hide-focused
/** Removes all scheduled item IDs from [items]. */
// !diag[/MutableSet<Int>/] MUTABLE_COLLECTION_PUBLIC_API ["parameter","items","MutableSet"]
public fun consume(items: MutableSet<Int>) { 
    items.add(1)
}
```

### Do {#do-2}

```kotlin
// !hide-focused
@file:JvmName("Collections")

// !hide-focused
/** Reads scheduled item IDs from [items]. */
public fun consume(items: Set<Int>) {
    items.toMutableSet()
    // proceed with mutations
}
```


## Notes

- `vararg` parameters are not reported - the compiler already passes a defensive copy of
  the array. But a mutable element type is still reported (`vararg groups: MutableList<Int>`).
- Extension receivers are not reported because the caller already has the collection. Builder
  lambda receivers are reported because the library gives a mutable collection to caller code:

  ```kotlin
  // Extension receiver: not reported.
  public fun MutableList<Int>.snapshot(): List<Int> = toList()

  // Builder lambda receiver: reported.
  // !diag[/MutableList<Int>\.\(\) -> Unit/] MUTABLE_COLLECTION_PUBLIC_API ["parameter","block","MutableList"]
  public fun build(block: MutableList<Int>.() -> Unit): List<Int> =
      mutableListOf<Int>().apply(block).toList()
  ```
- Overrides are not reported: their signature is fixed by the overridden declaration, which is
  reported instead.
- `@PublishedApi internal` declarations are not reported because their types do not cross the
  supported source API boundary.

## Exemption

<!-- diagnostic-exemption: MUTABLE_COLLECTION_PUBLIC_API -->
If this API shape is intentional, apply `@IntentionallyMutableCollection` to the reported
declaration, parameter, or type usage.

Use the exemption when sharing the mutable collection is a deliberate part of the API contract.

```kotlin
// !hide-focused
@file:JvmName("Collections")

// !hide-focused(1:5)
/**
 * Exposes the values scheduled for processing as a deliberately shared collection.
 *
 * @property items live collection of scheduled values.
 */
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
public class Holder(
    @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
    public val items: MutableList<Int>,
)

// !hide-focused
/** Removes all scheduled item IDs from [items]. */
public fun consume(
    @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
    items: MutableSet<Int>,
) {
    items.clear()
}
```

## Configuration

```kotlin
apiWatchdog {
    mutableCollectionPublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=MUTABLE_COLLECTION_PUBLIC_API:warning
```

## See also

- [Avoid exposing mutable state](https://kotlinlang.org/docs/api-guidelines-predictability.html#avoid-exposing-mutable-state)
- [Pair and Triple in public API](./pair-or-triple-public-api.md)
- [Exemptions and internal API](../exemptions.md)
