# Companion API without JvmStatic

`COMPANION_API_WITHOUT_JVM_STATIC` reports public companion object functions that compile to an
instance method on the nested `Companion` class instead of a static entry point on the outer
class.

|                  |                                                        |
|------------------|--------------------------------------------------------|
| Diagnostic       | `COMPANION_API_WITHOUT_JVM_STATIC`                     |
| Default severity | Error                                                  |
| Applies to       | JVM compilations only                                  |
| Gradle property  | [`companionApiWithoutJvmStatic`](../../configuration.md)     |
| Exemption        | [`@IntentionallyNonStaticCompanionApi`](../../exemptions.md) |

## What it reports

Flags a public function declared directly in a companion object - of a class
or an interface - that carries neither `@JvmStatic` nor `@JvmSynthetic`.

```kotlin
public class Registry {
    public companion object {
        // COMPANION_API_WITHOUT_JVM_STATIC
        public fun create(): Registry = Registry()
    }
}
```

## Rationale

A companion function without `@JvmStatic` compiles only as an instance method on the generated
`Companion` class, so Java code must call `Outer.Companion.member(...)` for what looks, from
Kotlin, like a plain static factory or utility.
`@JvmStatic` additionally compiles a static `Outer.member(...)` entry point for Java, without
changing how Kotlin resolves the same call. See the Kotlin guide on
[static methods](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-methods).

### Don't

```kotlin
public class Registry {
    public companion object {
        // COMPANION_API_WITHOUT_JVM_STATIC
        public fun create(): Registry = Registry()
    }
}
```

### Do

```kotlin
public class Registry {
    public companion object {
        @JvmStatic
        public fun create(): Registry = Registry()
    }
}
```

## Notes

- `suspend` companion functions are exempt here - they are not Java-callable regardless of
  placement, and `KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC` reports them with the fitting fix.
- Overrides are not flagged: their Java-facing shape is fixed by the overridden declaration, and
  `@JvmStatic` can't be applied to an override anyway.
- Interface companions compile the same way and are checked identically to class companions.
- Internal functions are skipped unless marked `@PublishedApi`.
- `@JvmSynthetic` members are hidden from Java on purpose and are not flagged.
- Non-JVM compilations never register this check at all.

## Exemption

Acknowledge the companion-instance access path with `@IntentionallyNonStaticCompanionApi` when
keeping it is a deliberate choice:

```kotlin
public class Registry {
    public companion object {
        @IntentionallyNonStaticCompanionApi(
            reason = ExemptionReason.API_DESIGN,
        )
        public fun create(): Registry = Registry()
    }
}
```

The same annotation also acknowledges [`COMPANION_CONSTANT_WITHOUT_JVM_FIELD`](./companion-constant-without-jvm-field.md).

## Configuration

The property lives inside the `javaInterop { }` block:

```kotlin
apiWatchdog {
    javaInterop {
        companionApiWithoutJvmStatic = WatchdogSeverity.WARNING
    }
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=COMPANION_API_WITHOUT_JVM_STATIC:warning
```

The property lives inside the `javaInterop { }` block. `javaInterop { enabled = false }` turns off
this check along with the rest of the [Java interop checks](./java-interop.md) group.

## See also

- [Static methods](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-methods)
- [Companion constants without JvmField](./companion-constant-without-jvm-field.md), the sibling
  check for constant-shaped companion properties
- [Java interop checks](./java-interop.md)
- [Exemptions and internal API](../../exemptions.md)
