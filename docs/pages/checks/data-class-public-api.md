# Data classes in public API

|                  |                                               |
|------------------|-----------------------------------------------|
| Diagnostic       | `DATA_CLASS_PUBLIC_API`                       |
| Default severity | Error                                         |
| Gradle property  | [`dataClassPublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyDataClass`](../exemptions.md) |

## What it reports

A `data class` in the public API or declared as `@PublishedApi internal`.

```kotlin
// !hide-focused
/** A position in Cartesian coordinate space. */
// !diag[/Point/] DATA_CLASS_PUBLIC_API ["Point"]
public data class Point(
    // !hide-focused
    /** Distance from the vertical axis. */
    public val x: Int,
    // !hide-focused
    /** Distance from the horizontal axis. */
    public val y: Int,
)
```

## Rationale

A data class exposes its primary constructor properties through these generated members:

- `copy`: One parameter for each property, in declaration order.
- `componentN`: One function for each property, in declaration order.
- `equals`, `hashCode`, and `toString`

Data classes can define `equals`, `hashCode`, and `toString`, or inherit final implementations.
Kotlin still generates `copy` and `componentN`.

In a regular class, you choose which members to declare.
[Poko](https://github.com/drewhamilton/Poko) generates `equals`, `hashCode`, and `toString`
without `copy` or `componentN`.

Callers can use the generated API:

```kotlin
// !hide-focused
/** A crop region in pixels. */
// !diag[/Region/] DATA_CLASS_PUBLIC_API ["Region"]
public data class Region(
    // !hide-focused
    /** Horizontal extent of the region. */
    public val width: Int,
    // !hide-focused
    /** Vertical extent of the region. */
    public val height: Int,
)

private fun clientCode() {
    val region = Region(1920, 1080)
    val cropped = region.copy(1280)          // positional copy
    val (width, height) = region             // componentN destructuring
    val same = region == Region(1920, 1080)  // structural equality
}
```

The generated members also have these behaviors:

- The `copy` function is shallow, so the copy shares references to objects stored in properties.
- A change to a primary constructor property can change the instance's hash code.
  This can prevent lookup in hash-based collections.
- The generated members exclude properties declared in the class body:

  ```kotlin
  // !hide-focused
  /** A session token. */
  // !diag[/Session/] DATA_CLASS_PUBLIC_API ["Session"]
  public data class Session(
      // !hide-focused
      /** The raw token value. */
      public val value: String,
  ) {
      // !hide-focused
      /** Milliseconds since epoch at which the session was created. */
      public val createdAt: Long = System.currentTimeMillis()
  }
  ```

  Sessions with the same `value` compare equal and have the same string representation, even if their `createdAt` values differ.
  A call to `copy` initializes `createdAt` again.

### Compatibility overloads {#the-workaround-negates-the-convenience}

If `Region` gains an `offsetX` property, existing compiled callers still need the old constructor and `copy` signatures.
The secondary constructor and `copy` overload below restore calls that pass both `width` and `height`.

```kotlin
// !hide-focused
/** A crop region in pixels. */
// !diag[/Region/] DATA_CLASS_PUBLIC_API ["Region"]
public data class Region(
    // !hide-focused
    /** Horizontal extent of the region. */
    public val width: Int,
    // !hide-focused
    /** Vertical extent of the region. */
    public val height: Int,
    // !hide-focused
    /** Horizontal start of the region. */
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

On the JVM, compiled calls that omit `copy` arguments use `copy$default`.
This example doesn't restore that method's old signature.

From the Kotlin library authors' guide,
[Avoid using data classes in your API](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#avoid-using-data-classes-in-your-api):

> It's possible to work around these issues by manually writing a secondary constructor and
> overriding the `copy` method. However, the effort involved negates the convenience of using a
> data class.

### Don't

```kotlin
// !hide-focused
/** A position in Cartesian coordinate space. */
// !diag[/Coordinates/] DATA_CLASS_PUBLIC_API ["Coordinates"]
public data class Coordinates(
    // !hide-focused
    /** Distance from the vertical axis. */
    public val x: Int,
    // !hide-focused
    /** Distance from the horizontal axis. */
    public val y: Int,
)
```

### Do

```kotlin
// !hide-focused
/** A position in Cartesian coordinate space. */
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
public class Coordinates(
    // !hide-focused
    /** Distance from the vertical axis. */
    public val x: Int,
    // !hide-focused
    /** Distance from the horizontal axis. */
    public val y: Int,
)
```

## Notes

- `data object`s aren't reported.
- A non-public primary constructor or non-public properties don't exempt a data class.

## Exemption

<!-- diagnostic-exemption: DATA_CLASS_PUBLIC_API -->
If this is intended, apply `@IntentionallyDataClass` to the class.

Use the exemption when the declaration is part of the API design or must stay for backwards compatibility.

```kotlin
// !hide-focused
/** A stable position in Cartesian coordinate space used by the wire format. */
@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public data class Coordinates(
    // !hide-focused
    /** Distance from the vertical axis. */
    public val x: Int,
    // !hide-focused
    /** Distance from the horizontal axis. */
    public val y: Int,
)
```

## Configuration

```kotlin
apiWatchdog {
    dataClassPublicApi = WatchdogSeverity.ERROR
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=DATA_CLASS_PUBLIC_API:error
```

## See also

- [Avoid using data classes in your API](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#avoid-using-data-classes-in-your-api)
- [Name-based destructuring](https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0438-name-based-destructuring.md)
- [Version overloading](https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0431-version-overloading.md)
- [Multi-field value classes](https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0454-better-immutability-value-classes-MFVC.md)
- [Stateful classes without equals, hashCode, and toString](./stateful-class-without-equals-hashcode-to-string.md)
- [Exemptions and internal API](../exemptions.md)
