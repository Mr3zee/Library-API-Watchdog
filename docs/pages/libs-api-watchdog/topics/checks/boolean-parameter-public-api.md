# Boolean parameters in public API

`BOOLEAN_PARAMETER_PUBLIC_API` reports Boolean value parameters of public functions.

| | |
|---|---|
| Diagnostic | `BOOLEAN_PARAMETER_PUBLIC_API` |
| Default severity | Error |
| Gradle property | [`booleanParameterPublicApi`](configuration.md) |
| Exemption | [`@IntentionallyBooleanParameter`](exemptions.md) |

## What it reports

Every regular value parameter of a public or protected (or `@PublishedApi` internal) function whose
type is `Boolean`, `Boolean?`, or a type alias to either, including the declared element type of a
`vararg` parameter.

```kotlin
// BOOLEAN_PARAMETER_PUBLIC_API
public fun doWork(optimizeForSpeed: Boolean): Unit {}
```

## Rationale

At the call site, a positional `true`/`false` argument reads as noise: `resize(true)` says nothing
about what turns on. Users can't be forced to use named arguments yet, so the meaning depends on
whoever reads the call site remembering the parameter name. See the
[Kotlin API guidelines on avoiding Boolean arguments](https://kotlinlang.org/docs/api-guidelines-readability.html#avoid-using-the-boolean-type-as-an-argument).

## Don't

```kotlin
// BOOLEAN_PARAMETER_PUBLIC_API
public fun setLogging(enabled: Boolean): Unit {}
```

## Do

```kotlin
public fun enableLogging(): Unit {}

public fun disableLogging(): Unit {}
```

## Don't {id="dont-2"}

```kotlin
// BOOLEAN_PARAMETER_PUBLIC_API
public fun configure(vararg flags: Boolean): Unit {}
```

## Do {id="do-2"}

```kotlin
public enum class Flag { FAST, VERBOSE }

public fun configure(vararg flags: Flag): Unit {}
```

## Notes

- A nullable `Boolean?` parameter is still a positional flag, just a three-state one, so it is
  reported the same way.
- A type alias to `Boolean` doesn't change what users pass and is still reported.
- Overrides are never reported: their signature is fixed by the overridden declaration, which is
  reported there instead.
- Constructors, and constructor functions - factory functions named after the type they create,
  such as `fun Widget(visible: Boolean): Widget` - are exempt: a construction site stores data in
  the named type rather than switching an operation mode.
- Context parameters are exempt: implicit values are never passed as positional arguments.
- Boolean return types and Boolean properties are not arguments and are not checked.

## Exemption

Apply `@IntentionallyBooleanParameter` when the parameter's meaning is unmistakable from the
function name, such as `setEnabled(enabled: Boolean)`. On the function it covers every parameter;
on a single parameter it covers just that one:

```kotlin
@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public fun setEnabled(enabled: Boolean): Unit {}
```

## Configuration

```kotlin
apiWatchdog {
    booleanParameterPublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=BOOLEAN_PARAMETER_PUBLIC_API:warning
```

## See also

- [Avoid using the Boolean type as an argument](https://kotlinlang.org/docs/api-guidelines-readability.html#avoid-using-the-boolean-type-as-an-argument)
- [Nullable Booleans in public API](nullable-boolean-public-api.md)
- [Exemptions and internal API](exemptions.md)
