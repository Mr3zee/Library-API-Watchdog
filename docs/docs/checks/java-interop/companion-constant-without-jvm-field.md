# Companion constants without JvmField

`COMPANION_CONSTANT_WITHOUT_JVM_FIELD` reports a public constant-shaped companion object property
- a final `val`, initialized in place, with the default getter, neither `const` nor delegated -
that Java can only read through the companion instance getter.

|                  |                                                        |
|------------------|--------------------------------------------------------|
| Diagnostic       | `COMPANION_CONSTANT_WITHOUT_JVM_FIELD`                 |
| Default severity | Error                                                  |
| Applies to       | JVM compilations only                                  |
| Gradle property  | [`companionConstantWithoutJvmField`](../../configuration.md) |
| Exemption        | [`@IntentionallyNonStaticCompanionApi`](../../exemptions.md) |

## What it reports

A companion `val` that just holds a constant value - no `const`, no custom getter, no delegate.

```kotlin
// !hide-focused
/** Names and metadata used to locate a catalog. */
public class Catalog {
    // !hide-focused
    /** Well-known catalog metadata. */
    public companion object {
        // !hide-focused
        /** Name used when the caller does not supply one. */
        // !diag[/DEFAULT_NAME/] COMPANION_CONSTANT_WITHOUT_JVM_FIELD ["Catalog","DEFAULT_NAME"]
        public val DEFAULT_NAME: String = "catalog"
    }
}
```

## Rationale

Java has no notion of a companion object instance: `Registry.Companion.getDEFAULT_NAME()` reads as an
implementation detail rather than the static field or constant a Java caller expects on `Registry`
itself. Kotlin has three ways to put the value on the outer class instead, and this check exists
because none of them is the default. See Kotlin's guide to
[static fields](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-fields) for how
`@JvmField`, `const val`, and `@JvmStatic` each compile.


### Don't

```kotlin
// !hide-focused
/** Names and metadata used to locate a registry. */
public class Registry {
    // Java only sees Registry.Companion.getDEFAULT_NAME().
    // !hide-focused
    /** Well-known registry metadata. */
    public companion object {
        // !hide-focused
        /** Name used when the caller does not supply one. */
        // !diag[/DEFAULT_NAME/] COMPANION_CONSTANT_WITHOUT_JVM_FIELD ["Registry","DEFAULT_NAME"]
        public val DEFAULT_NAME: String = "registry"
    }
}
```

### Do

```kotlin
// !hide-focused
/** Names and metadata used to locate a registry. */
public class Registry {
    // !hide-focused
    /** Well-known registry metadata. */
    public companion object {
        // !hide-focused
        /** Name used when the caller does not supply one. */
        public const val DEFAULT_NAME: String = "registry"

        // !hide-focused
        /** Origin assigned to locally created registries. */
        @JvmField
        public val ORIGIN: String = "field"

        // !hide-focused
        /** Metadata exposed to Java through a static getter. */
        @JvmStatic
        public val EXPOSED: String = "static getter"
    }
}
```

`const val` compiles to a real static final field but only accepts a compile-time constant
(primitives and strings). `@JvmField` exposes any other final value the same way, as a plain
static field. `@JvmStatic` instead compiles a static getter, useful when the value needs a
computed default.


## Notes

- `var` properties are not reported: they expose mutable state, not a constant.
- A property with a custom getter or setter, or a delegate (`by lazy { }` and similar), is not
  reported: it exposes behavior rather than a fixed value, and `@JvmField` would not apply to most
  of these shapes anyway.
- Overrides are not reported: their Java-facing shape is fixed by the overridden declaration.
- `@PublishedApi internal` properties are not reported: their public bytecode entry is a binary
  implementation detail, not supported Java source API.
- `@JvmSynthetic` members are hidden from Java on purpose and are not reported.
- Non-JVM compilations never register this check at all.

## Exemption

Acknowledge the companion-instance access path with `@IntentionallyNonStaticCompanionApi` when
keeping it is a deliberate choice:

```kotlin
// !hide-focused
/** Names and metadata used to locate a registry. */
public class Registry {
    // !hide-focused
    /** Well-known registry metadata. */
    public companion object {
        // !hide-focused
        /** The deliberately companion-only default name. */
        @IntentionallyNonStaticCompanionApi(
            reason = ExemptionReason.API_DESIGN,
        )
        public val DEFAULT_NAME: String = "registry"
    }
}
```

The same annotation also acknowledges [`COMPANION_API_WITHOUT_JVM_STATIC`](./companion-api-without-jvm-static.md).

## Configuration

```kotlin
apiWatchdog {
    javaInterop {
        companionConstantWithoutJvmField = WatchdogSeverity.WARNING
    }
}
```

The property lives inside the `javaInterop { }` block. `javaInterop { enabled = false }` turns off
this check along with the rest of the [Java interop checks](./java-interop.md) group.

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlin.library.api.watchdog:diagnosticSeverity=COMPANION_CONSTANT_WITHOUT_JVM_FIELD:warning
```

## See also

- [Static fields in Java-to-Kotlin interop](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-fields)
- [Java interop checks](./java-interop.md)
- [Companion API without JvmStatic](./companion-api-without-jvm-static.md)
- [Exemptions and internal API](../../exemptions.md)
