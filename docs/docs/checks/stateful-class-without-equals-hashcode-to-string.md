# Stateful classes without equals, hashCode, and toString

Three diagnostics report public stateful classes that rely on the implementations from
`kotlin.Any`: `STATEFUL_CLASS_WITHOUT_EQUALS`, `STATEFUL_CLASS_WITHOUT_HASH_CODE`, and
`STATEFUL_CLASS_WITHOUT_TO_STRING`.

| Diagnostic                            | Gradle property                         | Exemption                            |
|---------------------------------------|-----------------------------------------|--------------------------------------|
| `STATEFUL_CLASS_WITHOUT_EQUALS`       | `statefulClassWithoutEquals`            | `@IntentionallyWithoutEquals`        |
| `STATEFUL_CLASS_WITHOUT_HASH_CODE`    | `statefulClassWithoutHashCode`          | `@IntentionallyWithoutHashCode`      |
| `STATEFUL_CLASS_WITHOUT_TO_STRING`    | `statefulClassWithoutToString`          | `@IntentionallyWithoutToString`      |

All three have a default severity of Error. See [Configuration](../configuration.md) for the
Gradle properties and [Exemptions](../exemptions.md) for the annotations.

## What they report

The checks flag a public or protected regular class that has at least one property storing its
value in a backing field and that doesn't declare or inherit the corresponding implementation:

```kotlin
/**
 * Describes a connection to a remote service.
 *
 * @property host network host serving requests.
 */
// !diag[/Connection/] STATEFUL_CLASS_WITHOUT_EQUALS ["Connection","$jvmGenerationHint","$ideaGenerateShortcut"]
// !diag[/Connection/] STATEFUL_CLASS_WITHOUT_HASH_CODE ["Connection","$jvmGenerationHint","$ideaGenerateShortcut"]
// !diag[/Connection/] STATEFUL_CLASS_WITHOUT_TO_STRING ["Connection","$jvmGenerationHint","$ideaGenerateShortcut"]
public class Connection(public val host: String)
```

Each member is checked independently. A class that implements `toString` but not `equals` and
`hashCode`, for example, receives the first two diagnostics.

## Rationale

State usually gives instances their meaning. Identity equality treats two instances with the same
state as different values. Identity hashing carries that behavior into sets and map keys. An
instance that only prints as `Connection@1a2b3c4d` reveals nothing in a log line, exception message,
or debugger watch.

Generate all three members together so they use the same state. [Poko](https://github.com/drewhamilton/Poko)
does this without exposing the `copy` and `componentN` API of a data class. On JVM projects,
[Lombok](https://projectlombok.org/) is another generation option. In IntelliJ IDEA, press
<IdeaGenerateShortcut /> or choose `Code | Generate` to generate the methods without another
library.

See the Kotlin library authors' guidelines on
[providing a toString method for stateful types](https://kotlinlang.org/docs/api-guidelines-debuggability.html#provide-a-tostring-method-for-stateful-types).

### Don't

```kotlin
/**
 * Describes a connection to a remote service.
 *
 * @property host network host serving requests.
 */
// !diag[/Connection/] STATEFUL_CLASS_WITHOUT_EQUALS ["Connection","$jvmGenerationHint","$ideaGenerateShortcut"]
// !diag[/Connection/] STATEFUL_CLASS_WITHOUT_HASH_CODE ["Connection","$jvmGenerationHint","$ideaGenerateShortcut"]
// !diag[/Connection/] STATEFUL_CLASS_WITHOUT_TO_STRING ["Connection","$jvmGenerationHint","$ideaGenerateShortcut"]
public class Connection(public val host: String)
```

### Do

```kotlin
/**
 * Describes a connection to a remote service.
 *
 * @property host network host serving requests.
 */
@Poko
public class Connection(public val host: String)
```

## Notes

- An implementation inherited from any supertype other than `kotlin.Any` counts as provided, so a
  subclass that adds its own state is not flagged for that member. Whether the inherited behavior
  should include the new state is left to the author.
- Data and value classes get compiler-generated implementations and are not checked here. Data
  classes are reported by `DATA_CLASS_PUBLIC_API` instead.
- Enum entries, objects (including companion objects), interfaces, and annotation classes are not
  checked. Enums and singleton objects have deliberate identity semantics, while interfaces and
  annotation classes can't hold backing fields.
- A delegated property stores its value in the delegate, not in a backing field, so it doesn't
  make a class stateful on its own.
- `@PublishedApi` internal classes are checked too, since they belong to the published binary API.

## Exemptions

Apply the exemption matching each deliberately absent member. For example, a sensitive handle may
intentionally use identity equality and avoid rendering its state:

```kotlin
/**
 * Credentials used to authenticate an outgoing request.
 *
 * @property token bearer token sent to the remote service.
 */
@IntentionallyWithoutEquals(reason = ExemptionReason.API_DESIGN)
@IntentionallyWithoutHashCode(reason = ExemptionReason.API_DESIGN)
@IntentionallyWithoutToString(
    description = "Holds an access token. " +
            "Must never be rendered in logs."
)
public class Credentials(public val token: String)
```

The exemptions are independent. A class can acknowledge identity equality and hashing while still
providing a safe `toString`, for example.

## Configuration

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
