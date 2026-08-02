# Nullable Booleans in public API

`NULLABLE_BOOLEAN_PUBLIC_API` reports `Boolean?` in publicly visible signatures.

|                  |                                                  |
|------------------|--------------------------------------------------|
| Diagnostic       | `NULLABLE_BOOLEAN_PUBLIC_API`                    |
| Default severity | Error                                            |
| Gradle property  | [`nullableBooleanPublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyNullableBoolean`](../exemptions.md) |

## What it reports

Flags return types, property types, value parameter types (constructors included), context
parameter types, and type parameter bounds that mention `Boolean?`.

```kotlin
@file:JvmName("Probes")

/** Returns whether the service is ready, or null before the first health check. */
// !diag[/Boolean[?]/] NULLABLE_BOOLEAN_PUBLIC_API ["function","probe"]
public fun probe(): Boolean? = null
```

## Rationale

`Boolean?` models three states but names only two of them: `true`, `false`, and an unnamed third
state that every caller has to remember stands for something, whether that is "unknown", "not yet
decided", or "not applicable". Because the third state has no name, callers reach for a
two-branch `if` where the real logic needs three branches, and the meaning of `null` can only be
learned from documentation. See the Kotlin library authors' guide on
[avoiding the Boolean type as an argument](https://kotlinlang.org/docs/api-guidelines-readability.html#avoid-using-the-boolean-type-as-an-argument).

### Don't

```kotlin
@file:JvmName("Connections")

// true, false, or... what does null mean here?
//
/** Returns the connection state. */
// !diag[/Boolean[?]/] NULLABLE_BOOLEAN_PUBLIC_API ["function","connectionState"]
public fun connectionState(): Boolean? = null
```

### Do

```kotlin
@file:JvmName("Connections")

// !collapse(1:2) collapsed details
/** Whether a network transport is available. */
@IntentionallyExhaustive(reason = ExemptionReason.API_DESIGN)
public enum class ConnectionState {
    /** An active transport can carry requests. */
    CONNECTED,

    /** No transport is currently available. */
    DISCONNECTED,

    /** The transport has not reported its state yet. */
    UNKNOWN,
}

/** Returns the connection state. */
public fun connectionState(): ConnectionState = ConnectionState.UNKNOWN
```

### Don't {#dont-2}

```kotlin
// !collapse(1:6) collapsed details
/**
 * Stores the selection state of a control.
 *
 * @property checked whether the control is selected, or null before it is initialized.
 */
@Poko
// !diag[/Boolean[?]/] NULLABLE_BOOLEAN_PUBLIC_API ["property","checked"]
public class Holder(public val checked: Boolean?)
```

### Do {#do-2}

```kotlin
// !collapse(1:2) collapsed details
/** Selection state of a control. */
@IntentionallyExhaustive(reason = ExemptionReason.API_DESIGN)
public enum class CheckState {
    /** The control is selected. */
    CHECKED,

    /** The control is not selected. */
    UNCHECKED,

    /** The control has not been initialized. */
    UNKNOWN,
}

// !collapse(1:6) collapsed details
/**
 * Stores the selection state of a control.
 *
 * @property checked current selection state.
 */
@Poko
public class Holder(public val checked: CheckState)
```

## Notes

- Unlike `BOOLEAN_PARAMETER_PUBLIC_API`, constructors are checked too.
- A type alias resolves to its expansion, and a `Boolean?` bound on a type parameter
  (`<T : Boolean?>`) is flagged the same as a direct mention.
- Extension receivers are not flagged: an extension on `Boolean?`, typically a remedial helper
  like `fun Boolean?.orFalse()`, serves values the user already holds.
- Overrides are not flagged: their signature is fixed by the overridden declaration and reported
  there instead.
- Java platform types are not flagged: their nullability is not declared in Kotlin sources.

## Exemption

Use `@IntentionallyNullableBoolean` when the nullable Boolean is a deliberate part of the API
contract.

```kotlin
@file:JvmName("Probes")

/** Performs a deliberately three-state legacy probe. */
@IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN)
public fun legacyProbe(): Boolean? = null
```

## Configuration

```kotlin
apiWatchdog {
    nullableBooleanPublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=NULLABLE_BOOLEAN_PUBLIC_API:warning
```

## See also

- [Avoid using the Boolean type as an argument](https://kotlinlang.org/docs/api-guidelines-readability.html#avoid-using-the-boolean-type-as-an-argument)
- [Boolean parameters in public API](./boolean-parameter-public-api.md), a sibling check that skips
  constructors and only looks at parameters, not return types or properties.
- [Exemptions and internal API](../exemptions.md)
