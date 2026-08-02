# Stateful classes without equals, hashCode, and toString

`STATEFUL_CLASS_WITHOUT_TO_STRING` reports a public stateful class that neither declares nor
inherits a `toString` implementation.

|                  |                                                    |
|------------------|----------------------------------------------------|
| Diagnostic       | `STATEFUL_CLASS_WITHOUT_TO_STRING`                 |
| Default severity | Error                                              |
| Gradle property  | [`statefulClassWithoutToString`](../configuration.md) |
| Exemption        | [`@IntentionallyWithoutToString`](../exemptions.md)   |

## What it reports

The check flags a public or protected regular class that has at least one property storing its
value in a backing field, and that doesn't declare or inherit any `toString` implementation
besides the opaque default from `kotlin.Any`:

```kotlin
// STATEFUL_CLASS_WITHOUT_TO_STRING
public class Connection(public val host: String)
```

## Rationale

An instance that only ever prints as `Connection@1a2b3c4d` tells you nothing when it shows up in a
log line, an exception message, or a debugger watch. Adding a `toString` that renders the actual
state turns that noise into something you can act on, and it costs nothing to evolve later. See the
Kotlin library authors' guidelines on
[providing a toString method for stateful types](https://kotlinlang.org/docs/api-guidelines-debuggability.html#provide-a-tostring-method-for-stateful-types).

### Don't

```kotlin
// STATEFUL_CLASS_WITHOUT_TO_STRING
public class Connection(public val host: String)
```

### Do

```kotlin
public class Connection(public val host: String) {
    override fun toString(): String = "Connection(host=$host)"
}
```

### Don't {#dont-2}

```kotlin
// STATEFUL_CLASS_WITHOUT_TO_STRING
public abstract class StatefulBase(public val id: Int)
```

### Do {#do-2}

```kotlin
public abstract class StatefulBase(public val id: Int) {
    override fun toString(): String = "StatefulBase(id=$id)"
}
```

## Notes

- A `toString` inherited from any supertype other than `kotlin.Any` counts as provided, so a
  subclass that adds its own state is not flagged. Whether that inherited `toString` should be
  refined is left to the author's judgement.
- Data and value classes get a compiler-generated `toString` and are not checked here (data
  classes are reported by `DATA_CLASS_PUBLIC_API` instead).
- Enum entries, objects (including companion objects), interfaces, and annotation classes are not
  checked: enum entries render their name, objects typically hold constants rather than
  per-instance state, and interfaces and annotation classes can't hold backing fields.
- A delegated property stores its value in the delegate, not in a backing field, so it doesn't
  make a class stateful on its own.
- `@PublishedApi` internal classes are checked too, since they belong to the published binary API.

## Exemption

When the opaque rendering is intended, for example because the state is sensitive and must not
leak into logs, acknowledge it on the class:

```kotlin
@IntentionallyWithoutToString(
    description = "Holds an access token. " +
            "Must never be rendered in logs."
)
public class Credentials(public val token: String)
```

## Configuration

```kotlin
apiWatchdog {
    statefulClassWithoutToString = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=STATEFUL_CLASS_WITHOUT_TO_STRING:warning
```

## See also

- [Data classes in public API](./data-class-public-api.md)
- [Exemptions and internal API](../exemptions.md)
