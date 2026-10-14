# Open API without subclass opt-in

|                  |                                                      |
|------------------|------------------------------------------------------|
| Diagnostic       | `OPEN_API_WITHOUT_SUBCLASS_OPT_IN`                   |
| Default severity | Error                                                |
| Gradle property  | [`openApiWithoutSubclassOptIn`](../configuration.md) |
| Exemption        | [`@IntentionallyOpen`](../exemptions.md)             |

## What it reports

An `open` or `abstract` class in the public API with a constructor that external subclasses can call,
or a non-sealed interface, without `@SubclassOptInRequired`.

```kotlin
// !hide-focused
/** Base type for components hosted by an application. */
// !diag[/Component/] OPEN_API_WITHOUT_SUBCLASS_OPT_IN ["$class","Component","$classFix"]
public open class Component
```

## Rationale

A new abstract member or a changed member signature can break external subclasses.
For an unstable subclass contract, use `@SubclassOptInRequired`.

### Don't

```kotlin
// !hide-focused
/** Extension point invoked during application startup. */
// !diag[/Plugin/] OPEN_API_WITHOUT_SUBCLASS_OPT_IN ["$interface","Plugin","$interfaceFix"]
public interface Plugin {
    // !hide-focused
    /** Initializes the plugin for the current application. */
    public fun run()
}
```

### Do

```kotlin

// !hide-focused
/** Marks APIs that require an opt-in. */
@RequiresOptIn
public annotation class InternalMyLibrarySubclassApi

// !hide-focused
/** A plugin implemented under an opt-in contract. */
@SubclassOptInRequired(InternalMyLibrarySubclassApi::class)
public interface Plugin {
    // !hide-focused
    /** Initializes the plugin for the current application. */
    public fun run()
}
```

## Notes

- Classes whose constructors are all `internal` or `private` aren't reported.
- `fun interface`s follow the same rules as other interfaces. `@SubclassOptInRequired` isn't
  applicable to a `fun interface`, so exempt it with `@IntentionallyOpen`.
- [Subclass opt-in without markers](./subclass-opt-in-without-markers.md) reports a markerless `@SubclassOptInRequired` instead.
- [Exhaustive public API](./exhaustive-public-api.md) reports sealed types.
- `@PublishedApi internal` types aren't reported.

## Exemption

<!-- diagnostic-exemption: OPEN_API_WITHOUT_SUBCLASS_OPT_IN -->
<!-- diagnostic-exemption-substitution: class or interface => {0} -->
If this is intended, apply `@IntentionallyOpen` to the class or interface.

Use the exemption when the declaration is part of the API design or must stay for backwards compatibility.

```kotlin
// !hide-focused
/** A UI widget open to external subclasses. */
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class Widget
```

## Configuration

```kotlin
apiWatchdog {
    openApiWithoutSubclassOptIn = WatchdogSeverity.ERROR
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=OPEN_API_WITHOUT_SUBCLASS_OPT_IN:error
```

## See also

- [Require opt-in to extend an API](./subclass-opt-in-without-markers.md)
- [Exhaustive public API](./exhaustive-public-api.md)
- [Kotlin API guidelines: prevent unwanted and invalid extensions](https://kotlinlang.org/docs/api-guidelines-predictability.html#prevent-unwanted-and-invalid-extensions)
- [`SubclassOptInRequired`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-subclass-opt-in-required/)
