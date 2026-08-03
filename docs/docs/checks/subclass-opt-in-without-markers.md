# Subclass opt-in without markers

`SUBCLASS_OPT_IN_WITHOUT_MARKERS` reports `@SubclassOptInRequired` annotations that list no
marker classes.

|                  |                                                                                |
|------------------|--------------------------------------------------------------------------------|
| Diagnostic       | `SUBCLASS_OPT_IN_WITHOUT_MARKERS`                                              |
| Default severity | Error                                                                          |
| Gradle property  | [`subclassOptInWithoutMarkers`](../configuration.md)                              |
| Exemption        | none, replace with [`@IntentionallyOpen`](./open-api-without-subclass-opt-in.md) |

## What it reports

`markerClass` is a vararg parameter, so `@SubclassOptInRequired` compiles fine with zero
arguments. The annotation restricts nothing in this case: the class or
interface stays open to external subclassing exactly as if it were unannotated.

```kotlin
// !hide-focused
/** Base type for application extensions. */
// !diag[/@SubclassOptInRequired/] SUBCLASS_OPT_IN_WITHOUT_MARKERS
@SubclassOptInRequired
public abstract class ExtensionPoint
```

## Rationale

`@SubclassOptInRequired` exists so a library can add abstract members or otherwise change a
contract later, because every external subclasser had to explicitly opt in to that instability
first. An annotation with no marker doesn't protect against this. Any external class can
extend the type without acknowledging anything, so the library keeps the evolution risk it meant
to opt out of. See the
[opt-in requirements guide](https://kotlinlang.org/docs/opt-in-requirements.html#require-opt-in-to-extend-api)
for the intended pattern.


### Don't

```kotlin
// !hide-focused
/** Establishes communication with a remote service. */
// !diag[/@SubclassOptInRequired/] SUBCLASS_OPT_IN_WITHOUT_MARKERS
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

// !hide-focused
/** A plugin implemented under an opt-in contract. */
@SubclassOptInRequired(UnstableApi::class)
public interface Plugin
```


## Notes

- A class or interface that is not open to external subclassing in the first place (a final
  class, a class whose constructors are all internal or private, or a sealed interface) is
  outside the scope of this check regardless of what `@SubclassOptInRequired` lists.
- Multiple marker classes are allowed, and each further constrains who may subclass.

## Exemption

There is no `@Intentionally*` annotation for this diagnostic: an unmarkered
`@SubclassOptInRequired` never restricts anything, so keeping it as-is is never a valid design
choice. Fix it by listing at least one marker class in `@SubclassOptInRequired`.

To exempt this check for binary compatibility reasons, replace the `@SubclassOptInRequired` with
`@IntentionallyOpen`:

```kotlin
// !hide-focused
/** A connector kept unrestricted for compatibility. */
@IntentionallyOpen(
    reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY,
)
public abstract class Connector
```

[`updateBackwardsCompatibilityExempts`](../existing-libs.md) performs exactly this replacement: it
drops the markerless `@SubclassOptInRequired` and puts `@IntentionallyOpen` in its place.

## Configuration

```kotlin
apiWatchdog {
    subclassOptInWithoutMarkers = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=SUBCLASS_OPT_IN_WITHOUT_MARKERS:warning
```

## See also

- [Require opt-in to extend an API](https://kotlinlang.org/docs/opt-in-requirements.html#require-opt-in-to-extend-api)
- [Open API without subclass opt-in](./open-api-without-subclass-opt-in.md)
