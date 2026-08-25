# Boolean parameters in public API

`BOOLEAN_PARAMETER_PUBLIC_API` reports Boolean value parameters of public functions.

|                  |                                                      |
|------------------|------------------------------------------------------|
| Diagnostic       | `BOOLEAN_PARAMETER_PUBLIC_API`                       |
| Default severity | Error                                                |
| Gradle property  | [`booleanParameterPublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyBooleanParameter`](../exemptions.md) |

## What it reports

Every value parameter, and every context parameter, of a public or protected function whose
type is `Boolean`, including the declared element type of `vararg` parameter.

```kotlin
// !hide-focused
@file:JvmName("Work")

// !hide-focused
/** Processes pending data, optionally favoring speed over memory use. */
// !diag[/optimizeForSpeed/] BOOLEAN_PARAMETER_PUBLIC_API ["doWork","optimizeForSpeed"]
public fun doWork(optimizeForSpeed: Boolean) {}
```

## Rationale

At the call site, a positional `true`/`false` argument reads as noise: `resize(true)` says nothing
about what `true` does. Users can't be forced to use named arguments yet, so the meaning depends on
whoever reads the call site remembering the parameter name. See the
[Kotlin API guidelines on avoiding Boolean arguments](https://kotlinlang.org/docs/api-guidelines-readability.html#avoid-using-the-boolean-type-as-an-argument).


### Don't

```kotlin
// !hide-focused
@file:JvmName("Logging")

// !hide-focused
/** Controls whether diagnostic messages are recorded. */
// !diag[/enabled/] BOOLEAN_PARAMETER_PUBLIC_API ["setLogging","enabled"]
public fun setLogging(enabled: Boolean) {}
```

### Do

```kotlin
// !hide-focused
@file:JvmName("Logging")

// !hide-focused
/** Enables logging. */
public fun enableLogging() {}

// !hide-focused
/** Disables logging. */
public fun disableLogging() {}
```


## Notes

- A nullable `Boolean?` parameter is still a positional flag, just a three-state one, so it is
  reported the same way. It is also reported in [Nullable Booleans in public API](./nullable-boolean-public-api.md).
- A type alias to `Boolean` doesn't change what users pass and is still reported.
- A `Boolean` context parameter is reported too, and it hides the flag even better than a
  positional argument: the caller writes nothing at the call site, and the value is picked up
  from whatever `Boolean` happens to be in scope there.

  ```kotlin
  // !diag[/verbose/] BOOLEAN_PARAMETER_PUBLIC_API ["logLine","verbose"]
  context(verbose: Boolean)
  public fun logLine(message: String) {}
  ```

  Legacy context receivers are not reported.
- Overrides are not reported: their signature is fixed by the overridden declaration, which is
  reported instead.
- Constructors, and constructor functions - factory functions named after the type they create,
  such as `fun Widget(visible: Boolean): Widget` - are not reported.
- `Boolean` return types and `Boolean` properties are not arguments and are not reported.
- `@PublishedApi internal` functions are not reported because library users cannot call them in
  source.

## Exemption

Apply `@IntentionallyBooleanParameter` when the parameter's meaning is unmistakable from the
function name.

```kotlin
// !hide-focused
@file:JvmName("Logging")

// !hide-focused
/** Controls whether diagnostic messages are recorded. */
@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public fun setIsVerbose(enabled: Boolean) {}
```

## Configuration

```kotlin
apiWatchdog {
    booleanParameterPublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlin.library.api.watchdog:diagnosticSeverity=BOOLEAN_PARAMETER_PUBLIC_API:warning
```

## See also

- [Avoid using the Boolean type as an argument](https://kotlinlang.org/docs/api-guidelines-readability.html#avoid-using-the-boolean-type-as-an-argument)
- [Nullable Booleans in public API](./nullable-boolean-public-api.md)
- [Exemptions and internal API](../exemptions.md)
