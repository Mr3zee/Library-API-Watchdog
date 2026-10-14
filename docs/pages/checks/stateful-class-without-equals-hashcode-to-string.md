# Stateful classes without equals, hashCode, and toString

| Diagnostic | Gradle property | Exemption |
|------------|-----------------|-----------|
| `STATEFUL_CLASS_WITHOUT_EQUALS` | [`statefulClassWithoutEquals`](../configuration.md) | [`@IntentionallyWithoutEquals`](../exemptions.md) |
| `STATEFUL_CLASS_WITHOUT_HASH_CODE` | [`statefulClassWithoutHashCode`](../configuration.md) | [`@IntentionallyWithoutHashCode`](../exemptions.md) |
| `STATEFUL_CLASS_WITHOUT_TO_STRING` | [`statefulClassWithoutToString`](../configuration.md) | [`@IntentionallyWithoutToString`](../exemptions.md) |

Default severity: error for all three checks.

Combined exemption: [`@IntentionallyWithoutEqualsHashCodeOrToString`](../exemptions.md)

## What they report

Regular classes in the public API with at least one non-delegated backing-field property.
Each check reports a missing implementation of its member, independently of the other two.
A supertype's implementation counts unless it comes from `kotlin.Any`.

```kotlin
// !hide-focused
/** Describes a remote service endpoint. */
// !diag[/Endpoint/] STATEFUL_CLASS_WITHOUT_EQUALS ["Endpoint","$generationHint","$ideaGenerateShortcut"]
// !diag[/Endpoint/] STATEFUL_CLASS_WITHOUT_HASH_CODE ["Endpoint","$generationHint","$ideaGenerateShortcut"]
// !diag[/Endpoint/] STATEFUL_CLASS_WITHOUT_TO_STRING ["Endpoint","$generationHint","$ideaGenerateShortcut"]
public class Endpoint(
    // !hide-focused
    /** Network host serving requests. */
    public val host: String,
)
```

## Rationale

Default equality and hashing use object identity, so equal state doesn't make two instances equal.
The default `toString` doesn't show the state in logs or debuggers.

[Poko](#poko) generates all three members without a data class's `copy` and `componentN` functions.
In IntelliJ IDEA, use <IdeaGenerateShortcut /> or `Code | Generate` to generate the methods.

### Don't

```kotlin
// !hide-focused
/** Describes a connection to a remote service. */
// !diag[/Connection/] STATEFUL_CLASS_WITHOUT_EQUALS ["Connection","$generationHint","$ideaGenerateShortcut"]
// !diag[/Connection/] STATEFUL_CLASS_WITHOUT_HASH_CODE ["Connection","$generationHint","$ideaGenerateShortcut"]
// !diag[/Connection/] STATEFUL_CLASS_WITHOUT_TO_STRING ["Connection","$generationHint","$ideaGenerateShortcut"]
public class Connection(
    // !hide-focused
    /** Network host serving requests. */
    public val host: String,
)
```

### Do

```kotlin
// !hide-focused
/** Describes a connection to a remote service. */
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
public class Connection(
    // !hide-focused
    /** Network host serving requests. */
    public val host: String,
)
```

### Or Do {#do-2}

```kotlin
// !hide-focused
/** Describes a connection to a remote service. */
public class Connection(
    // !hide-focused
    /** Network host serving requests. */
    public val host: String,
) {
    public override fun equals(other: Any?): Boolean =
        this === other || (other is Connection && host == other.host)

    public override fun hashCode(): Int = host.hashCode()

    public override fun toString(): String = "Connection(host=$host)"
}
```


## Notes

The checks skip:

- Data and value classes.
- Enums, objects, interfaces, and annotation classes.
- Classes with only delegated properties or properties without backing fields.
- `@PublishedApi internal` classes.

Check whether inherited implementations include any state that the subclass adds.

## Exemptions

<!-- diagnostic-exemption-table -->
| Missing behavior | Individual exemption            | Combined exemption                              |
|------------------|---------------------------------|-------------------------------------------------|
| `equals`         | `@IntentionallyWithoutEquals`   | `@IntentionallyWithoutEqualsHashCodeOrToString` |
| `hashCode`       | `@IntentionallyWithoutHashCode` | `@IntentionallyWithoutEqualsHashCodeOrToString` |
| `toString`       | `@IntentionallyWithoutToString` | `@IntentionallyWithoutEqualsHashCodeOrToString` |

Use the exemptions when the behavior is part of the API design or must stay for backwards compatibility.
An individual exemption covers one behavior, the combined exemption all three.

```kotlin
// !hide-focused
/** Describes a live connection to a remote service. */
@IntentionallyWithoutEqualsHashCodeOrToString(reason = ExemptionReason.API_DESIGN)
public class Connection(
    // !hide-focused
    /** Network host serving requests. */
    public val host: String,
)
```

## Configuration

### Poko

[Poko](https://github.com/drewhamilton/Poko) supports Kotlin/JVM and Kotlin Multiplatform. Apply the
version compatible with the Kotlin version used by the project. For Kotlin `{{kotlinVersion}}`:

```kotlin build.gradle.kts
plugins {
    id("dev.drewhamilton.poko") version "0.23.0"
}
```

Add `@Poko` to each class that needs generated `equals`, `hashCode`, and `toString`.

### Check severity

```kotlin
apiWatchdog {
    statefulClassWithoutEquals = WatchdogSeverity.ERROR
    statefulClassWithoutHashCode = WatchdogSeverity.ERROR
    statefulClassWithoutToString = WatchdogSeverity.ERROR
}
```

For a direct compiler call:
```text
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=STATEFUL_CLASS_WITHOUT_EQUALS:error
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=STATEFUL_CLASS_WITHOUT_HASH_CODE:error
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=STATEFUL_CLASS_WITHOUT_TO_STRING:error
```

## See also

- [Kotlin library authors' guidelines for toString ](https://kotlinlang.org/docs/api-guidelines-debuggability.html#provide-a-tostring-method-for-stateful-types)
- [Data classes in public API](./data-class-public-api.md)
- [Exemptions and internal API](../exemptions.md)
