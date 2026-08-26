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

Any `data class` in the public API or declared as `@PublishedApi internal`.

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

The check is about what `data` adds on top of a regular public class. The compiler generates three
additional API surfaces from the primary-constructor property list:

- a positional `copy` function whose parameters and defaults mirror the constructor,
- one positional `componentN` function per property, which is what destructuring compiles to,
- `equals`, `hashCode`, and `toString` defined over exactly that ordered list.

A regular class has none of this surface unless you choose to write it,
and what you write you can shape and evolve.

The three surfaces come as a package that can't be split. A tool like
[Poko](https://github.com/drewhamilton/Poko) only generates `equals`, `hashCode`, and
`toString` and leaves `copy` and `componentN` out, giving you a better control.

In user code, the following public API becomes available:

```kotlin
// !hide-focused(1:6)
/**
 * A crop region in pixels.
 *
 * @property width horizontal extent of the region.
 * @property height vertical extent of the region.
 */
// !diag[/Region/] DATA_CLASS_PUBLIC_API ["Region"]
public data class Region(public val width: Int, public val height: Int)

private fun clientCode() {
    val region = Region(1920, 1080)
    val cropped = region.copy(1280)          // positional copy
    val (width, height) = region             // componentN destructuring
    val same = region == Region(1920, 1080)  // structural equality
}
```

The generated members also carry behavior a regular class never acquires silently: 
- The `copy` function is shallow.
- A mutable primary property can change `hashCode` while the instance sits in a hash-based
  collection. 
- A state declared in the class body is excluded from all the generated members:

  ```kotlin
  // !hide-focused(1:5)
  /**
   * A session token.
   *
   * @property value the raw token value.
   */
  // !diag[/Session/] DATA_CLASS_PUBLIC_API ["Session"]
  public data class Session(public val value: String) {
      // !hide-focused
      /** Milliseconds since epoch at which the session was created. */
      public val createdAt: Long = System.currentTimeMillis()
  }
  ```

  Two sessions created a day apart still compare equal and print identically, and `copy` quietly
  resets `createdAt`. On a regular class each of these behaviors exists only if you wrote it.

### The workaround negates the convenience

The signature breaks can be patched by hand. Keeping existing compiled callers of `Region`
linking after `offsetX` is appended means replaying the previous shape in the class body: a
secondary constructor with the old parameter list and a `copy` overload with the old signature.

```kotlin
// !hide-focused(1:7)
/**
 * A crop region in pixels.
 *
 * @property width horizontal extent of the region.
 * @property height vertical extent of the region.
 * @property offsetX horizontal start of the region.
 */
// !diag[/Region/] DATA_CLASS_PUBLIC_API ["Region"]
public data class Region(
    public val width: Int,
    public val height: Int,
    public val offsetX: Int,
) {
    // !hide-focused
    /** Keeps the previous constructor signature callable from existing compiled code. */
    public constructor(width: Int, height: Int) : this(width, height, 0)

    // !hide-focused
    /** Keeps the previous copy signature callable from existing compiled code. */
    public fun copy(width: Int, height: Int): Region = Region(width, height, offsetX)
}
```

Compiled callers link again, but is the data class worth it now? The `data` modifier was chosen so
that exactly these members would not have to be written by hand.

From the Kotlin library authors' guide on
[avoiding data classes in your API](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#avoid-using-data-classes-in-your-api):

> It's possible to work around these issues by manually writing a secondary constructor and
> overriding the `copy` method. However, the effort involved negates the convenience of using a
> data class.

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
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
public class Coordinates(
    public val x: Int,
    public val y: Int,
)
```

## Notes

- `data object`s are not reported: with no constructor properties, none of `copy`, `componentN`, or a
  per-instance constructor are generated.
- A non-public primary constructor with non-public properties shrinks the generated public surface,
  but equality, hashing, and string output stay observable behavior, and older Kotlin versions
  still generate a public `copy` for a non-public constructor.
- `@PublishedApi internal` data classes are reported because their generated constructors, `copy`,
  and `componentN` functions belong to the binary API available to public inline functions.

Upcoming Kotlin features provide additional options when designing and evolving classes in public
APIs:

- Use [version overloading](https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0431-version-overloading.md)
  when appending a constructor property with a default value. It can keep earlier constructor callable.
- When becomes available, consider [multi-field value classes](https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0454-better-immutability-value-classes-MFVC.md)
  for new types that represent genuine values and need structural equality without `copy` or
  positional `componentN` functions.

## Exemption

<!-- diagnostic-exemption: DATA_CLASS_PUBLIC_API -->
If this API shape is intentional, apply `@IntentionallyDataClass` to the class.

Use the exemption when the data class is deliberate, stable part of the contract with
`copy` and positional destructuring being intended API rather than incidental compiler output.

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

For an already-published data class, removing `data` is itself a source- and binary-incompatible
change. Use `ExemptionReason.FOR_BACKWARDS_COMPATIBILITY` when keeping the data class is a
compatibility decision rather than a design choice.

## Configuration

```kotlin
apiWatchdog {
    dataClassPublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=DATA_CLASS_PUBLIC_API:warning
```

## See also

- [Avoid using data classes in your API](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#avoid-using-data-classes-in-your-api)
- [Name-based destructuring](https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0438-name-based-destructuring.md)
- [Version overloading](https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0431-version-overloading.md)
- [Multi-field value classes](https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0454-better-immutability-value-classes-MFVC.md)
- [Stateful classes without equals, hashCode, and toString](./stateful-class-without-equals-hashcode-to-string.md)
- [Exemptions and internal API](../exemptions.md)
