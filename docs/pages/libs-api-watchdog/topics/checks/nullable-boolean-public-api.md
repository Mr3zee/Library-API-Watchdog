# Nullable Booleans in public API

`NULLABLE_BOOLEAN_PUBLIC_API` reports `Boolean?` in publicly visible signatures.

|                  |                                                  |
|------------------|--------------------------------------------------|
| Diagnostic       | `NULLABLE_BOOLEAN_PUBLIC_API`                    |
| Default severity | Error                                            |
| Gradle property  | [`nullableBooleanPublicApi`](configuration.md)   |
| Exemption        | [`@IntentionallyNullableBoolean`](exemptions.md) |

## What it reports

Flags return types, property types, value parameter types (constructors included), context
parameter types, and type parameter bounds that mention `Boolean?`.

```kotlin
// NULLABLE_BOOLEAN_PUBLIC_API
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
// true, false, or... what does null mean here?
//
// NULLABLE_BOOLEAN_PUBLIC_API
public fun connectionState(): Boolean? = null
```

### Do

```kotlin
public enum class ConnectionState { CONNECTED, DISCONNECTED, UNKNOWN }

public fun connectionState(): ConnectionState = ConnectionState.UNKNOWN
```

### Don't {id="dont-2"}

```kotlin
// NULLABLE_BOOLEAN_PUBLIC_API
public class Holder(public val checked: Boolean?)
```

### Do {id="do-2"}

```kotlin
public enum class CheckState { CHECKED, UNCHECKED, UNKNOWN }

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
- [](boolean-parameter-public-api.md), a sibling check that skips
  constructors and only looks at parameters, not return types or properties.
- [](exemptions.md)
