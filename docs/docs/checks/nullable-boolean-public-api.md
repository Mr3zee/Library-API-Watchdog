# Nullable Booleans in public API

`NULLABLE_BOOLEAN_PUBLIC_API` reports `Boolean?` in publicly visible signatures.

|                  |                                                     |
|------------------|-----------------------------------------------------|
| Diagnostic       | `NULLABLE_BOOLEAN_PUBLIC_API`                       |
| Default severity | Error                                               |
| Gradle property  | [`nullableBooleanPublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyNullableBoolean`](../exemptions.md) |

## What it reports

Flags return types, property types, value parameter types (constructors included), context
parameter types, and type parameter bounds in the public API that mention `Boolean?`.

```kotlin
// !hide-focused
@file:JvmName("Probes")

// !hide-focused
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
// !hide-focused
@file:JvmName("Connections")

// true, false, or... what does null mean here?
//
// !hide-focused
/** Returns the connection state. */
// !diag[/Boolean[?]/] NULLABLE_BOOLEAN_PUBLIC_API ["function","connectionState"]
public fun connectionState(): Boolean? = null
```

### Do

```kotlin
// !hide-focused
@file:JvmName("Connections")

// !hide-focused
/** Whether a network transport is available. */
// !hide-focused
@IntentionallyExhaustive(reason = ExemptionReason.API_DESIGN)
public enum class ConnectionState {
    // !hide-focused
    /** An active transport can carry requests. */
    CONNECTED,
    // !hide-focused

    // !hide-focused
    /** No transport is currently available. */
    DISCONNECTED,
    // !hide-focused

    // !hide-focused
    /** The transport has not reported its state yet. */
    UNKNOWN,
}

// !hide-focused
/** Returns the connection state. */
public fun connectionState(): ConnectionState = ConnectionState.UNKNOWN
```



### Don't {#dont-2}

```kotlin
// !hide-focused(1:5)
/**
 * Stores the selection state of a control.
 *
 * @property checked whether the control is selected, or null before it is initialized.
 */
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
// !diag[/Boolean[?]/] NULLABLE_BOOLEAN_PUBLIC_API ["property","checked"]
public class Holder(public val checked: Boolean?)
```

### Do {#do-2}

```kotlin
// !hide-focused
/** Selection state of a control. */
// !hide-focused
@IntentionallyExhaustive(reason = ExemptionReason.API_DESIGN)
public enum class CheckState {
    // !hide-focused
    /** The control is selected. */
    CHECKED,
    // !hide-focused

    // !hide-focused
    /** The control is not selected. */
    UNCHECKED,
    // !hide-focused

    // !hide-focused
    /** The control has not been initialized. */
    UNKNOWN,
}

// !hide-focused(1:5)
/**
 * Stores the selection state of a control.
 *
 * @property checked current selection state.
 */
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
public class Holder(public val checked: CheckState)
```


## Notes

- Unlike [`BOOLEAN_PARAMETER_PUBLIC_API`](./boolean-parameter-public-api.md), constructors are checked too.
- Type aliases are expanded and reported, so they don't hide a nullable Boolean.
- A `Boolean?` type parameter bound constrains every substitution to the same three-state shape.
- Extension receivers are not reported: an extension on `Boolean?`, typically a remedial helper
  like `fun Boolean?.orFalse()`, serves values the user already holds.
- Overrides are not reported: their signature is fixed by the overridden declaration, which is
  reported instead.
- Java platform types are not reported: their nullability is not declared in Kotlin sources.
- `@PublishedApi internal` declarations are not reported because their types do not cross the
  supported source API boundary.

## Exemption

<!-- diagnostic-exemption: NULLABLE_BOOLEAN_PUBLIC_API -->
If this API shape is intentional, apply `@IntentionallyNullableBoolean` to the declaration,
parameter, or type usage.

Use the exemption when the nullable Boolean is a deliberate part of the API contract.

```kotlin
// !hide-focused
@file:JvmName("Connections")

// !hide-focused
/** Returns a deliberately three-state connection status. */
@IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN)
public fun connectionState(): Boolean? = null
```

## Configuration

```kotlin
apiWatchdog {
    nullableBooleanPublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=NULLABLE_BOOLEAN_PUBLIC_API:warning
```

## See also

- [Avoid using the Boolean type as an argument](https://kotlinlang.org/docs/api-guidelines-readability.html#avoid-using-the-boolean-type-as-an-argument)
- [Boolean parameters in public API](./boolean-parameter-public-api.md)
- [Exemptions and internal API](../exemptions.md)
