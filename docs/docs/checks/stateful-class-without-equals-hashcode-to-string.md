# Stateful classes without equals, hashCode, and toString

Three diagnostics report public stateful classes that rely on the implementations of
`equals`, `hashCode`, and `toString` from `kotlin.Any`.

|                  |                                                                             |
|------------------|-----------------------------------------------------------------------------|
| Diagnostic       | `STATEFUL_CLASS_WITHOUT_EQUALS`                                             |
| Default severity | Error                                                                       |
| Gradle property  | [`statefulClassWithoutEquals`](../configuration.md)                         |
| Exemption        | [`@IntentionallyWithoutEquals`](../exemptions.md) or the combined exemption |

|                  |                                                                               |
|------------------|-------------------------------------------------------------------------------|
| Diagnostic       | `STATEFUL_CLASS_WITHOUT_HASH_CODE`                                            |
| Default severity | Error                                                                         |
| Gradle property  | [`statefulClassWithoutHashCode`](../configuration.md)                         |
| Exemption        | [`@IntentionallyWithoutHashCode`](../exemptions.md) or the combined exemption |

|                  |                                                                               |
|------------------|-------------------------------------------------------------------------------|
| Diagnostic       | `STATEFUL_CLASS_WITHOUT_TO_STRING`                                            |
| Default severity | Error                                                                         |
| Gradle property  | [`statefulClassWithoutToString`](../configuration.md)                         |
| Exemption        | [`@IntentionallyWithoutToString`](../exemptions.md) or the combined exemption |

Combined exemption: [`@IntentionallyWithoutEqualsHashCodeOrToString`](../exemptions.md)

## What they report

The checks flag a public or protected regular class that has at least one property storing its
value in a backing field and that doesn't declare or inherit the corresponding implementation:

```kotlin
// !hide-focused(1:5)
/**
 * Describes a remote service endpoint.
 *
 * @property host network host serving requests.
 */
// !diag[/Endpoint/] STATEFUL_CLASS_WITHOUT_EQUALS ["Endpoint","$generationHint","$ideaGenerateShortcut"]
// !diag[/Endpoint/] STATEFUL_CLASS_WITHOUT_HASH_CODE ["Endpoint","$generationHint","$ideaGenerateShortcut"]
// !diag[/Endpoint/] STATEFUL_CLASS_WITHOUT_TO_STRING ["Endpoint","$generationHint","$ideaGenerateShortcut"]
public class Endpoint(public val host: String)
```

Each member is checked independently. A class that implements `toString` but not `equals` and
`hashCode`, for example, receives the first two diagnostics.

## Rationale

State usually gives instances their meaning. Identity equality treats two instances with the same
state as different values. Identity hashing carries that behavior into sets and map keys. An
instance that only prints as `Connection@1a2b3c4d` reveals nothing in a log line, exception message,
or debugger watch.

Generate all three members together so they use the same state. [Poko](#poko) does this for Kotlin
classes without exposing the `copy` and `componentN` API of a data class. In IntelliJ IDEA, press
<IdeaGenerateShortcut /> or choose `Code | Generate` to generate the methods without another
library.

See the Kotlin library authors' guidelines on
[providing a toString method for stateful types](https://kotlinlang.org/docs/api-guidelines-debuggability.html#provide-a-tostring-method-for-stateful-types).


### Don't

```kotlin
// !hide-focused(1:5)
/**
 * Describes a connection to a remote service.
 *
 * @property host network host serving requests.
 */
// !diag[/Connection/] STATEFUL_CLASS_WITHOUT_EQUALS ["Connection","$generationHint","$ideaGenerateShortcut"]
// !diag[/Connection/] STATEFUL_CLASS_WITHOUT_HASH_CODE ["Connection","$generationHint","$ideaGenerateShortcut"]
// !diag[/Connection/] STATEFUL_CLASS_WITHOUT_TO_STRING ["Connection","$generationHint","$ideaGenerateShortcut"]
public class Connection(public val host: String)
```

### Do

Generate the members with Poko:

```kotlin
// !hide-focused(1:5)
/**
 * Describes a connection to a remote service.
 *
 * @property host network host serving requests.
 */
@Poko
public class Connection(public val host: String)
```

### Or Do

```kotlin
// !hide-focused(1:5)
/**
 * Describes a connection to a remote service.
 *
 * @property host network host serving requests.
 */
public class Connection(public val host: String) {
    public override fun equals(other: Any?): Boolean =
        this === other || (other is Connection && host == other.host)

    public override fun hashCode(): Int = host.hashCode()

    public override fun toString(): String = "Connection(host=$host)"
}
```


## Notes

- An implementation inherited from any supertype other than `kotlin.Any` counts as provided, so a
  subclass that adds its own state is not reported for that member. Whether the inherited behavior
  should include the new state is left to the author.
- Data and value classes get compiler-generated implementations and are not reported here. Data
  classes are reported by [`DATA_CLASS_PUBLIC_API`](./data-class-public-api.md) instead.
- Enum entries, objects (including companion objects), interfaces, and annotation classes are not
  reported. Enums and singleton objects have deliberate identity semantics, while interfaces and
  annotation classes can't hold backing fields.
- A delegated property stores its value in the delegate, not in a backing field, so it doesn't
  make a class stateful on its own.
- `@PublishedApi` internal classes are not reported because users can't reference them in source.

## Exemptions

Apply `@IntentionallyWithoutEqualsHashCodeOrToString` when all three behaviors are intentional. For
example, a sensitive handle may intentionally use identity equality and avoid rendering its state:

```kotlin
// !hide-focused(1:5)
/**
 * Describes a live connection to a remote service.
 *
 * @property host network host serving requests.
 */
@IntentionallyWithoutEqualsHashCodeOrToString(
    reason = ExemptionReason.API_DESIGN,
    description = "A live connection uses identity semantics and omits configuration from logs.",
)
public class Connection(public val host: String)
```

The individual `@IntentionallyWithoutEquals`, `@IntentionallyWithoutHashCode`, and
`@IntentionallyWithoutToString` exemptions remain available when only some behaviors are
intentional. For example, a class can acknowledge identity equality and hashing while still
providing a safe `toString`.

## Configuration

### Poko

[Poko](https://github.com/drewhamilton/Poko) supports Kotlin/JVM and Kotlin Multiplatform. Apply the
version compatible with the Kotlin version used by the project; for Kotlin 2.4.0:

```kotlin build.gradle.kts
plugins {
    id("dev.drewhamilton.poko") version "0.23.0"
}
```

Annotate each class whose `equals`, `hashCode`, and `toString` should be generated with `@Poko`, as
shown in the [usage sample](#do).

### Check severity

```kotlin
apiWatchdog {
    statefulClassWithoutEquals = WatchdogSeverity.WARNING
    statefulClassWithoutHashCode = WatchdogSeverity.WARNING
    statefulClassWithoutToString = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:

```text
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=STATEFUL_CLASS_WITHOUT_EQUALS:warning
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=STATEFUL_CLASS_WITHOUT_HASH_CODE:warning
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=STATEFUL_CLASS_WITHOUT_TO_STRING:warning
```

## See also

- [Data classes in public API](./data-class-public-api.md)
- [Exemptions and internal API](../exemptions.md)
