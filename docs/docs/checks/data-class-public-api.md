# Data classes in public API

`DATA_CLASS_PUBLIC_API` reports public data classes, whose generated members expose their
constructor property list as part of the compiled API.

|                  |                                               |
|------------------|-----------------------------------------------|
| Diagnostic       | `DATA_CLASS_PUBLIC_API`                       |
| Default severity | Error                                         |
| Gradle property  | [`dataClassPublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyDataClass`](../exemptions.md) |

## What it reports

Any `data class` reachable from the public API - top-level, nested inside another public class
- is reported, regardless of nesting depth:

```kotlin
// !hide-focused(1:6)
/**
 * A position in Cartesian coordinate space.
 *
 * @property x distance from the vertical axis.
 * @property y distance from the horizontal axis.
 */
// !diag[/Point/] DATA_CLASS_PUBLIC_API ["Point"]
public data class Point(public val x: Int, public val y: Int)
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
// !hide-focused(1:6)
/**
 * A position in Cartesian coordinate space.
 *
 * @property x distance from the vertical axis.
 * @property y distance from the horizontal axis.
 */
// !diag[/Coordinates/] DATA_CLASS_PUBLIC_API ["Coordinates"]
public data class Coordinates(public val x: Int, public val y: Int)
```

### Do

```kotlin
// !hide-focused(1:6)
/**
 * A position in Cartesian coordinate space.
 *
 * @property x distance from the vertical axis.
 * @property y distance from the horizontal axis.
 */
// !hide-focused
@Poko
public class Coordinates(
    public val x: Int,
    public val y: Int,
)
```


## Notes

- `data object`s are not reported: with no constructor properties, none of `copy`, `componentN`, or a
  per-instance constructor are generated.
- `@PublishedApi internal` data classes are still reported: their generated constructors, `copy`,
  and `componentN` functions belong to the binary API available to public inline functions.

## Exemption

Apply `@IntentionallyDataClass` to the class declaration when the property list is a deliberate,
stable part of the contract:

```kotlin
// !hide-focused(1:6)
/**
 * A stable position in Cartesian coordinate space used by the wire format.
 *
 * @property x distance from the vertical axis.
 * @property y distance from the horizontal axis.
 */
@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public data class Coordinates(public val x: Int, public val y: Int)
```

## Configuration

```kotlin
apiWatchdog {
    dataClassPublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlin.library.api.watchdog:diagnosticSeverity=DATA_CLASS_PUBLIC_API:warning
```

## See also

- [Avoid using data classes in your API](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#avoid-using-data-classes-in-your-api)
- [Stateful classes without equals, hashCode, and toString](./stateful-class-without-equals-hashcode-to-string.md)
- [Exemptions and internal API](../exemptions.md)
