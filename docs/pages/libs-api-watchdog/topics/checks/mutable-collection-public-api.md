# Mutable collections in public API

`MUTABLE_COLLECTION_PUBLIC_API` reports mutable collection and array types in public signatures.

| | |
|---|---|
| Diagnostic | `MUTABLE_COLLECTION_PUBLIC_API` |
| Default severity | Error |
| Gradle property | [`mutableCollectionPublicApi`](configuration.md) |
| Exemption | [`@IntentionallyMutableCollection`](exemptions.md) |

## What it reports

Flags return types, property types, value parameter types, and type parameter bounds that mention
a mutable collection type: any of the `kotlin.collections` mutable interfaces (`MutableList`,
`MutableSet`, `MutableMap`, `MutableCollection`, `MutableIterable`, `MutableIterator`,
`MutableListIterator`, `MutableMap.MutableEntry`), a classifier implementing one of them
(`ArrayList`, a hand-written `MutableList` subtype, ...), or an array. Type arguments are checked
too, so a mutable type nested in an otherwise read-only container still counts:

```kotlin
// MUTABLE_COLLECTION_PUBLIC_API
public fun produce(): MutableList<String> = mutableListOf()

// MUTABLE_COLLECTION_PUBLIC_API
public fun nested(): List<MutableList<Int>> = emptyList()
```

## Rationale

A mutable return type or property lets users mutate a collection they do not own; a mutable
parameter lets the library mutate an argument the user still holds. Either way, once a mutable
collection crosses the API boundary it is unclear whose mutations are safe, and the library can
no longer swap its internal representation for a different collection type without risking a
behavioral change for users that relied on mutating the exposed instance. See the Kotlin guide on
[avoiding exposing mutable state](https://kotlinlang.org/docs/api-guidelines-predictability.html#avoid-exposing-mutable-state).

## Don't

```kotlin
// MUTABLE_COLLECTION_PUBLIC_API
public class Holder(public val items: MutableList<Int>)
```

## Do

```kotlin
public class Holder(items: List<Int>) {
    public val items: List<Int> = items.toList()
}
```

## Don't {id="dont-2"}

```kotlin
// MUTABLE_COLLECTION_PUBLIC_API
public fun consume(items: MutableSet<Int>): Unit = items.clear()
```

## Do {id="do-2"}

```kotlin
public fun consume(items: Set<Int>): Unit = Unit // copy internally before mutating, if needed
```

Notable edge cases and deliberate exceptions:

- `vararg` parameters are not flagged themselves - the compiler already passes a defensive copy of
  the array - but a mutable element type still is (`vararg groups: MutableList<Int>`).
- Extension receivers are not flagged: an extension on a mutable collection serves values the
  user already holds, unlike a builder lambda receiver, which is flagged.
- Overrides are not flagged: their signature is fixed by the overridden declaration and reported
  there instead.
- Java platform types are not flagged: their mutability is not declared in Kotlin sources, so only
  the read-only upper bound is inspected.
- A type alias resolves to its expansion, and a mutable bound on a type parameter
  (`<T : MutableList<Int>>`) is flagged the same as a direct mention of the bound.

## Exemption

Use `@IntentionallyMutableCollection` when sharing the mutable collection is a deliberate part of
the API contract. It applies on the whole declaration (function, property, or constructor - covering
the whole signature), on a single parameter or type parameter, or on a type usage, where it covers
the annotated type and everything nested in it:

```kotlin
@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
public fun sharedRegistry(): MutableList<String> = mutableListOf()

public fun fill(
    @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) target: MutableList<Int>,
): Unit = target.add(1)

public fun snapshots(): List<@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<Int>> =
    emptyList()
```

For a `val`/`var` primary constructor parameter, put the annotation on the parameter - the
default use-site target - not on the property it generates; it still covers the property.

## Configuration

```kotlin
apiWatchdog {
    mutableCollectionPublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=MUTABLE_COLLECTION_PUBLIC_API:warning
```

## See also

- [Avoid exposing mutable state](https://kotlinlang.org/docs/api-guidelines-predictability.html#avoid-exposing-mutable-state)
- [Pair and Triple in public API](pair-or-triple-public-api.md), a sibling check for tuple types
  found by the same signature sweep.
- [Exemptions and internal API](exemptions.md)
