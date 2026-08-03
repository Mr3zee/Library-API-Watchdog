# Exhaustive public API

`EXHAUSTIVE_PUBLIC_API` reports public enums and sealed hierarchies, which users can match
exhaustively with a `when` expression that has no `else` branch.

|                  |                                             |
|------------------|---------------------------------------------|
| Diagnostic       | `EXHAUSTIVE_PUBLIC_API`                     |
| Default severity | Error                                       |
| Gradle property  | [`exhaustivePublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyExhaustive`](../exemptions.md) |

## What it reports

The check flags every public or protected `enum class`, `sealed class`, and `sealed interface`
declaration.

```kotlin
/** Whether the service can accept requests. */
// !diag[/Status/] EXHAUSTIVE_PUBLIC_API ["enum class","Status","an entry"]
public enum class Status {
    /** The service is ready to accept requests. */
    ACTIVE,

    /** The service rejects requests until it is reactivated. */
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

### Don't

```kotlin
/** Severity assigned to a log record. */
// !diag[/LogLevel/] EXHAUSTIVE_PUBLIC_API ["enum class","LogLevel","an entry"]
public enum class LogLevel {
    /** Fine-grained information used to diagnose behavior. */
    DEBUG,

    /** Routine progress and state changes. */
    INFO,

    /** A failure that prevented an operation from completing. */
    ERROR,
}
```

```kotlin
/** A change in the service lifecycle. */
// !diag[/Event/] EXHAUSTIVE_PUBLIC_API ["interface","Event","a subtype"]
public sealed interface Event {
    /** Emitted after the service becomes ready. */
    public class Started : Event

    /** Emitted after the service finishes shutting down. */
    public class Stopped : Event
}
```

### Do

```kotlin
/** A logging level that can grow without breaking exhaustive matches. */
public class LogLevel {
    /** Named logging levels. */
    public companion object {
        /** Fine-grained information used to diagnose behavior. */
        @JvmField
        public val DEBUG: LogLevel = LogLevel()

        /** Routine progress and state changes. */
        @JvmField
        public val INFO: LogLevel = LogLevel()

        /** A failure that prevented an operation from completing. */
        @JvmField
        public val ERROR: LogLevel = LogLevel()
    }
}

/** A lifecycle event implemented under an opt-in contract. */
@SubclassOptInRequired(InternalMyLibrarySubclassApi::class)
public interface Event {
    /** Emitted after the service becomes ready. */
    public class Started : Event

    /** Emitted after the service finishes shutting down. */
    public class Stopped : Event
}
```

## Notes

A non-final member of a sealed hierarchy (an `abstract` or `sealed` subclass)
is itself unrestricted, subclassable API and is reported separately by
[Open API without subclass opt-in](./open-api-without-subclass-opt-in.md), on top of this check.

## Exemption

Apply `@IntentionallyExhaustive` on the enum or sealed class/interface, for example, when a declaration has a fixed
set of entries, or subtypes is a deliberate, stable part of the contract:

```kotlin
/** A cardinal direction. */
@IntentionallyExhaustive(reason = ExemptionReason.API_DESIGN)
public enum class Direction {
    /** Points toward increasing latitude. */
    NORTH,

    /** Points toward decreasing latitude. */
    SOUTH,

    /** Points toward increasing longitude. */
    EAST,

    /** Points toward decreasing longitude. */
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
- [Open API without subclass opt-in](./open-api-without-subclass-opt-in.md)
- [Exemptions and internal API](../exemptions.md)
