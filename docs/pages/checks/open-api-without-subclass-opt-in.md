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
- Users implement a `fun interface` with a lambda, so external implementations are its purpose.
  `@SubclassOptInRequired` isn't applicable to a `fun interface`, and the compiler rejects it.
  The check reports the `fun interface` anyway. The open contract must be an explicit decision
  of the author, so acknowledge the external implementations with `@IntentionallyOpen`.

  ```kotlin
  // !hide-focused
  /** Receives one event. */
  // !diag[/Listener/] OPEN_API_WITHOUT_SUBCLASS_OPT_IN ["$interface","Listener","$funInterfaceFix"]
  public fun interface Listener {
      // !hide-focused
      /** Handles the event. */
      public fun onEvent()
  }

  // !hide-focused
  /** Receives one event. */
  @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
  public fun interface AcknowledgedListener {
      // !hide-focused
      /** Handles the event. */
      public fun onEvent()
  }
  ```
- In a multiplatform project, users on a platform can subclass the `actual` declaration. The check
  reports an `actual` declaration that is open when its `expect` declaration isn't:
  - An `open` `actual` class for a final `expect` class.
  - A public or protected constructor that only the `actual` class declares.
  - An `actual typealias` to an open class or interface of another module. `@SubclassOptInRequired`
    doesn't apply to a type alias, so declare an `actual` class or interface instead.
  - An `actual typealias` to an open Java class or interface of the module. Apply
    `@SubclassOptInRequired` to the Java declaration.

  `@IntentionallyOpen` on an `expect` declaration also exempts its `actual` declarations.
- [Subclass opt-in without markers](./subclass-opt-in-without-markers.md) reports a markerless `@SubclassOptInRequired` instead.
- [Exhaustive public API](./exhaustive-public-api.md) reports sealed types.
- `@PublishedApi internal` types aren't reported.

## Exemption

<!-- diagnostic-exemption: OPEN_API_WITHOUT_SUBCLASS_OPT_IN -->
<!-- diagnostic-exemption-substitution: class, interface, or type alias => {0} -->
If this is intended, apply `@IntentionallyOpen` to the class, interface, or type alias.

Use the exemption when the declaration is part of the API design or must stay for backwards compatibility.

```kotlin
// !hide-focused
/** A UI widget open to external subclasses. */
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class Widget
```

## Configuration

```kotlin build.gradle.kts
apiWatchdog {
    openApiWithoutSubclassOptIn = WatchdogSeverity.ERROR
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=OPEN_API_WITHOUT_SUBCLASS_OPT_IN:error
```

## See also

- [Subclass opt-in without markers](./subclass-opt-in-without-markers.md)
- [Exhaustive public API](./exhaustive-public-api.md)
- [Prevent unwanted and invalid extensions](https://kotlinlang.org/docs/api-guidelines-predictability.html#prevent-unwanted-and-invalid-extensions)
- [`SubclassOptInRequired`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-subclass-opt-in-required/)
- [Exemptions and internal API](../exemptions.md)
