# Open API without subclass opt-in

`OPEN_API_WITHOUT_SUBCLASS_OPT_IN` reports public open or abstract classes and interfaces that
can be subclassed outside the library without any restriction.

|                  |                                                   |
|------------------|---------------------------------------------------|
| Diagnostic       | `OPEN_API_WITHOUT_SUBCLASS_OPT_IN`                |
| Default severity | Error                                             |
| Gradle property  | [`openApiWithoutSubclassOptIn`](../configuration.md) |
| Exemption        | [`@IntentionallyOpen`](../exemptions.md)             |

## What it reports

A public `open`/`abstract` class with at least one public or protected constructor, or a public
non-sealed interface. User-side subclasses constrain how
the declaration can evolve later.

```kotlin
// OPEN_API_WITHOUT_SUBCLASS_OPT_IN
public open class Widget
```

## Rationale

Once external code subclasses a type, the library can no longer freely add abstract members,
change existing members' signatures, or tighten invariants without breaking those subclasses.
Unrestricted open API is one of the classic ways a
[public declaration becomes hard to evolve](https://kotlinlang.org/docs/api-guidelines-predictability.html#prevent-unwanted-and-invalid-extensions).

### Don't

```kotlin
// OPEN_API_WITHOUT_SUBCLASS_OPT_IN
public open class Widget

// OPEN_API_WITHOUT_SUBCLASS_OPT_IN
public interface Plugin {
    public fun run()
}
```

### Do

```kotlin
public open class Widget internal constructor()

@RequiresOptIn
public annotation class InternalMyLibrarySubclassApi

@SubclassOptInRequired(InternalMyLibrarySubclassApi::class)
public interface Plugin {
    public fun run()
}
```

`@SubclassOptInRequired` marks the api as internal to opt-in for, preventing unexpected breaking changes.

## Notes

- A `@SubclassOptInRequired` annotation with no marker classes gates nothing. It is reported by
  the separate [`SUBCLASS_OPT_IN_WITHOUT_MARKERS`](./subclass-opt-in-without-markers.md) check
  instead of this one.
- A class whose constructors are all `internal` or `private` can't be subclassed outside the
  library, so it is never reported, even if it is `open` or `abstract`.
- `fun interface`s are checked like any other interface.
- Sealed interfaces are exempt here, they are covered by
  [`EXHAUSTIVE_PUBLIC_API`](./exhaustive-public-api.md) instead.

## Exemption

When unrestricted subclassing is an intended, stable part of the contract, acknowledge it
instead of adding an opt-in marker:

```kotlin
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class Widget
```

`@IntentionallyOpen` targets the class declaration only.

## Configuration

```kotlin
apiWatchdog {
    openApiWithoutSubclassOptIn = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=OPEN_API_WITHOUT_SUBCLASS_OPT_IN:warning
```

## See also

- [Subclass opt-in without markers](./subclass-opt-in-without-markers.md)
- [Exhaustive public API](./exhaustive-public-api.md)
- [Kotlin API guidelines: prevent unwanted and invalid extensions](https://kotlinlang.org/docs/api-guidelines-predictability.html#prevent-unwanted-and-invalid-extensions)
- [`SubclassOptInRequired`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-subclass-opt-in-required/)
