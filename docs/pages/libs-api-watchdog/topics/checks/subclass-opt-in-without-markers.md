# Subclass opt-in without markers

`SUBCLASS_OPT_IN_WITHOUT_MARKERS` reports `@SubclassOptInRequired` annotations that list no
marker classes, so they gate nothing.

| | |
|---|---|
| Diagnostic | `SUBCLASS_OPT_IN_WITHOUT_MARKERS` |
| Default severity | Error |
| Gradle property | [`subclassOptInWithoutMarkers`](configuration.md) |
| Exemption | none |

## What it reports

`markerClass` is a vararg parameter, so `@SubclassOptInRequired` compiles fine with zero
arguments, or with empty parentheses. Either way, the annotation restricts nothing: the class or
interface stays open to external subclassing exactly as if it were unannotated. The check only
looks at declarations that would otherwise need subclass opt-in at all - see
[Open API without subclass opt-in](open-api-without-subclass-opt-in.md) for that scope.

```kotlin
// SUBCLASS_OPT_IN_WITHOUT_MARKERS
@SubclassOptInRequired
public abstract class Connector
```

## Rationale

`@SubclassOptInRequired` exists so a library can add abstract members or otherwise change a
contract later, because every external subclasser had to explicitly opt in to that instability
first. An annotation with no marker classes gives none of that protection: any external class can
extend the type without acknowledging anything, so the library keeps the evolution risk it meant
to opt out of. See the
[opt-in requirements guide](https://kotlinlang.org/docs/opt-in-requirements.html#require-opt-in-to-extend-api)
for the intended pattern.

## Don't

```kotlin
@RequiresOptIn
public annotation class UnstableApi

// SUBCLASS_OPT_IN_WITHOUT_MARKERS
@SubclassOptInRequired
public abstract class Connector // any external class can still extend it
```

## Do

```kotlin
@RequiresOptIn
public annotation class UnstableApi

@SubclassOptInRequired(UnstableApi::class)
public abstract class Connector

@SubclassOptInRequired(UnstableApi::class)
public interface Plugin
```

Notes:

- A class or interface that is not open to external subclassing in the first place (a final
  class, a class whose constructors are all internal or private, or a sealed interface) is
  outside the scope of this check regardless of what `@SubclassOptInRequired` lists.
- Multiple marker classes are allowed, and each further constrains who may subclass.

## Exemption

There is no `@Intentionally*` annotation for this diagnostic: an unmarkered
`@SubclassOptInRequired` never restricts anything, so keeping it as-is is never a valid design
choice. Fix it by listing at least one marker class in `@SubclassOptInRequired`. To silence the
check project-wide instead (for example, temporarily during a migration), lower the severity with
the Gradle property below; there is no per-declaration escape hatch.

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
- [Open API without subclass opt-in](open-api-without-subclass-opt-in.md)
