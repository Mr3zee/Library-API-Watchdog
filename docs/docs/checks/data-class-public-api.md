# Data classes in public API

`DATA_CLASS_PUBLIC_API` reports public data classes, whose generated members expose their
constructor property list as part of the compiled API.

|                  |                                            |
|------------------|--------------------------------------------|
| Diagnostic       | `DATA_CLASS_PUBLIC_API`                    |
| Default severity | Error                                      |
| Gradle property  | [`dataClassPublicApi`](configuration.md)   |
| Exemption        | [`@IntentionallyDataClass`](exemptions.md) |

## What it reports

Any `data class` reachable from the public API - top-level, nested inside another public class 
- is flagged, regardless of nesting depth:

```kotlin
// DATA_CLASS_PUBLIC_API
public data class Coordinates(public val x: Int, public val y: Int)
```

## Rationale

A `data class` generates `copy` and one `componentN` function
per constructor property, all shaped by the exact property list and its order. Adding, removing,
or reordering a property later changes the signatures of these functions,
which breaks source and binary compatibility for callers who use the functions,
destructuring declarations, or positional construction. See the Kotlin library authors' guide on
[avoiding data classes in your API](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#avoid-using-data-classes-in-your-api).

### Don't

```kotlin
// DATA_CLASS_PUBLIC_API
public data class Coordinates(public val x: Int, public val y: Int)
```

### Do

```kotlin
@Poko
public class Coordinates(
    public val x: Int,
    public val y: Int,
)
```

## Notes

- `data object`s are exempt: with no constructor properties, none of `copy`, `componentN`, or a
  per-instance constructor are generated.

## Exemption

Apply `@IntentionallyDataClass` to the class declaration when the property list is a deliberate,
stable part of the contract:

```kotlin
@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public data class MarkedData(public val x: Int)
```

## Configuration

```kotlin
apiWatchdog {
    dataClassPublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=DATA_CLASS_PUBLIC_API:warning
```

## See also

- [Avoid using data classes in your API](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#avoid-using-data-classes-in-your-api)
- [](stateful-class-without-equals-hashcode-to-string.md)
- [](exemptions.md)
