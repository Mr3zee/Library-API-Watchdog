# Exhaustive public API

`EXHAUSTIVE_PUBLIC_API` reports public enums and sealed hierarchies, which users can match
exhaustively with a `when` expression that has no `else` branch.

| | |
|---|---|
| Diagnostic | `EXHAUSTIVE_PUBLIC_API` |
| Default severity | Error |
| Gradle property | [`exhaustivePublicApi`](configuration.md) |
| Exemption | [`@IntentionallyExhaustive`](exemptions.md) |

## What it reports

The check flags every public or protected `enum class`, `sealed class`, and `sealed interface`
declaration (including `@PublishedApi` internal ones). A minimal triggering example:

```kotlin
// EXHAUSTIVE_PUBLIC_API
public enum class Status {
    ACTIVE,
    INACTIVE,
}
```

## Rationale

A user can write a `when` over an enum or a sealed hierarchy without an `else` branch and have
the compiler check exhaustiveness for them. That is convenient, but it also means the user code
depends on today's exact set of entries or subtypes. Adding a new enum entry or a new subtype
later makes every such `when` at every call site stop compiling: a source-incompatible change the
library author did not think of as breaking. See the
[Kotlin API guidelines on preventing unwanted extensions](https://kotlinlang.org/docs/api-guidelines-predictability.html#prevent-unwanted-and-invalid-extensions).

## Don't

```kotlin
// EXHAUSTIVE_PUBLIC_API
public enum class LogLevel {
    DEBUG,
    INFO,
    ERROR,
}
```

```kotlin
// EXHAUSTIVE_PUBLIC_API
public sealed interface Event {
    public class Started : Event

    public class Stopped : Event
}
```

## Do

```kotlin
@SubclassOptInRequired(InternalMyLibrarySubclassApi::class)
public interface Event {
    public companion object {
        public val Started = object : Event {}
        public val Stopted = object : Event {}
    }
}
```

## Notes

A non-final member of a sealed hierarchy (an `abstract` or `sealed` subclass)
is itself unrestricted, subclassable API and is reported separately by
[Open API without subclass opt-in](open-api-without-subclass-opt-in.md), on top of this check.

## Exemption

Apply `@IntentionallyExhaustive` on the enum or sealed class/interface declaration when the fixed
set of entries or subtypes is a deliberate, stable part of the contract:

```kotlin
@IntentionallyExhaustive(reason = ExemptionReason.API_DESIGN)
public enum class Direction {
    NORTH,
    SOUTH,
    EAST,
    WEST,
}
```

## Configuration

```kotlin
apiWatchdog {
    exhaustivePublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=EXHAUSTIVE_PUBLIC_API:warning
```

## See also

- [Prevent unwanted and invalid extensions](https://kotlinlang.org/docs/api-guidelines-predictability.html#prevent-unwanted-and-invalid-extensions)
- [Open API without subclass opt-in](open-api-without-subclass-opt-in.md)
- [Exemptions and internal API](exemptions.md)
