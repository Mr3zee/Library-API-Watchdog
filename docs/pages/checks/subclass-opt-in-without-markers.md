# Subclass opt-in without markers

|                  |                                                                                  |
|------------------|----------------------------------------------------------------------------------|
| Diagnostic       | `SUBCLASS_OPT_IN_WITHOUT_MARKERS`                                                |
| Default severity | Error                                                                            |
| Gradle property  | [`subclassOptInWithoutMarkers`](../configuration.md)                             |
| Exemption        | none, replace with [`@IntentionallyOpen`](./open-api-without-subclass-opt-in.md) |

## What it reports

A markerless `@SubclassOptInRequired` on a class or interface in the public API
that external classes can subclass.

```kotlin
// !hide-focused
/** Base type for application extensions. */
// !diag[/@SubclassOptInRequired/] SUBCLASS_OPT_IN_WITHOUT_MARKERS ["ExtensionPoint"]
@SubclassOptInRequired
public abstract class ExtensionPoint
```

## Rationale

Without a marker class, `@SubclassOptInRequired` doesn't restrict subclassing.
External subclasses can depend on the API without an opt-in to future changes.

### Don't

```kotlin
// !hide-focused
/** Establishes communication with a remote service. */
// !diag[/@SubclassOptInRequired/] SUBCLASS_OPT_IN_WITHOUT_MARKERS ["Connector"]
@SubclassOptInRequired
public abstract class Connector
```

### Do

```kotlin
// !hide-focused
/** Marks unstable API. */
@RequiresOptIn
public annotation class UnstableApi

// !hide-focused
/** A connector implemented under an opt-in contract. */
@SubclassOptInRequired(UnstableApi::class)
public abstract class Connector
```

## Notes

- Final classes, classes whose constructors are all `internal` or `private`, and sealed types aren't reported.
- The annotation can list more than one marker class.
- `@PublishedApi internal` types aren't reported.

## Exemption

This check has no exemption annotation.
To keep unrestricted subclassing for compatibility, replace `@SubclassOptInRequired` with `@IntentionallyOpen`:

```kotlin
// !hide-focused
/** A connector kept unrestricted for compatibility. */
@IntentionallyOpen(
    reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY,
)
public abstract class Connector
```

The [`exemptExistingApi`](../existing-libs.md) task makes this replacement.

## Configuration

```kotlin
apiWatchdog {
    subclassOptInWithoutMarkers = WatchdogSeverity.WARNING
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=SUBCLASS_OPT_IN_WITHOUT_MARKERS:warning
```

## See also

- [Require opt-in to extend an API](https://kotlinlang.org/docs/opt-in-requirements.html#require-opt-in-to-extend-api)
- [Open API without subclass opt-in](./open-api-without-subclass-opt-in.md)
