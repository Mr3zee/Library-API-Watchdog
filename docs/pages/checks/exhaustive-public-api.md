# Exhaustive public API

|                  |                                                |
|------------------|------------------------------------------------|
| Diagnostic       | `EXHAUSTIVE_PUBLIC_API`                        |
| Default severity | Error                                          |
| Gradle property  | [`exhaustivePublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyExhaustive`](../exemptions.md) |

## What it reports

An `enum class`, `sealed class`, or `sealed interface` in the public API.

```kotlin
// !hide-focused
/** Whether the service can accept requests. */
// !diag[/Status/] EXHAUSTIVE_PUBLIC_API ["$enumClass","Status","$entry","$enumFix"]
public enum class Status {
    // !hide-focused
    /** The service is ready to accept requests. */
    ACTIVE,
    // !hide-focused

    // !hide-focused
    /** The service rejects requests until it is reactivated. */
    INACTIVE,
}
```

## Rationale

Users can match an enum or sealed hierarchy without an `else` branch.
A new entry or subtype then makes those expressions fail to compile.

### Don't

```kotlin
// !hide-focused
/** Severity assigned to a log record. */
// !diag[/LogLevel/] EXHAUSTIVE_PUBLIC_API ["$enumClass","LogLevel","$entry","$enumFix"]
public enum class LogLevel {
    // !hide-focused
    /** Fine-grained information used to diagnose behavior. */
    DEBUG,
    // !hide-focused

    // !hide-focused
    /** Routine progress and state changes. */
    INFO,
    // !hide-focused

    // !hide-focused
    /** A failure that prevented an operation from completing. */
    ERROR,
}
```

### Do

```kotlin
// !hide-focused
/** A logging level that can grow without breaking exhaustive matches. */
public class LogLevel {
    // !hide-focused
    /** Named logging levels. */
    public companion object {
        // !hide-focused
        /** Fine-grained information used to diagnose behavior. */
        // !hide-focused
        @JvmField
        public val DEBUG: LogLevel = LogLevel()

        // !hide-focused
        /** Routine progress and state changes. */
        // !hide-focused
        @JvmField
        public val INFO: LogLevel = LogLevel()

        // !hide-focused
        /** A failure that prevented an operation from completing. */
        // !hide-focused
        @JvmField
        public val ERROR: LogLevel = LogLevel()
    }
}
```

### Don't {#dont-2}

```kotlin
// !hide-focused
/** A change in the service lifecycle. */
// !diag[/Event/] EXHAUSTIVE_PUBLIC_API ["$interface","Event","$subtype","$sealedFix"]
public sealed interface Event {
    // !hide-focused
    /** Emitted after the service becomes ready. */
    public class Started : Event

    // !hide-focused
    /** Emitted after the service finishes shutting down. */
    public class Stopped : Event
}
```

### Do {#do-2}

```kotlin 
// !hide-focused
/** A lifecycle event implemented under an opt-in contract. */
@SubclassOptInRequired(InternalMyLibrarySubclassApi::class)
public interface Event {
    // !hide-focused
    /** Emitted after the service becomes ready. */
    public class Started : Event

    // !hide-focused
    /** Emitted after the service finishes shutting down. */
    public class Stopped : Event
}
```

## Notes

- Open and abstract subclasses of a sealed hierarchy can also need subclass opt-in.
  See [Open API without subclass opt-in](./open-api-without-subclass-opt-in.md) for more details.
- `@PublishedApi internal` types aren't reported.

## Exemption

<!-- diagnostic-exemption: EXHAUSTIVE_PUBLIC_API -->
<!-- diagnostic-exemption-substitution: enum or sealed hierarchy => {0} -->
If this is intended, apply `@IntentionallyExhaustive` to the enum or sealed hierarchy.

Use the exemption when the set of entries or subtypes is a stable part of the API contract.

```kotlin
// !hide-focused
/** A fixed set of logging levels. */
@IntentionallyExhaustive(reason = ExemptionReason.API_DESIGN)
public enum class LogLevel {
    // !hide-focused
    /** Fine-grained information used to diagnose behavior. */
    DEBUG,
    // !hide-focused

    // !hide-focused
    /** Routine progress and state changes. */
    INFO,
    // !hide-focused

    // !hide-focused
    /** A failure that prevented an operation from completing. */
    ERROR,
}
```

## Configuration

```kotlin
apiWatchdog {
    exhaustivePublicApi = WatchdogSeverity.WARNING
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=EXHAUSTIVE_PUBLIC_API:warning
```

## See also

- [Prevent unwanted and invalid extensions](https://kotlinlang.org/docs/api-guidelines-predictability.html#prevent-unwanted-and-invalid-extensions)
- [Open API without subclass opt-in](./open-api-without-subclass-opt-in.md)
- [Exemptions and internal API](../exemptions.md)
