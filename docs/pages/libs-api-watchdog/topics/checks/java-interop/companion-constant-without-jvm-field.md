# Companion constants without JvmField

`COMPANION_CONSTANT_WITHOUT_JVM_FIELD` reports a public constant-shaped companion object property
- a final `val`, initialized in place, with the default getter, neither `const` nor delegated -
that Java can only read through the companion instance getter.

| | |
|---|---|
| Diagnostic | `COMPANION_CONSTANT_WITHOUT_JVM_FIELD` |
| Default severity | Error |
| Applies to | JVM compilations only |
| Gradle property | [`companionConstantWithoutJvmField`](configuration.md) |
| Exemption | [`@IntentionallyNonStaticCompanionApi`](exemptions.md) |

## What it reports

A companion `val` that just holds a constant value - no `const`, no custom getter, no delegate -
still compiles to an instance getter on the nested `Companion` class unless an annotation says
otherwise. Java callers have to go through `Outer.Companion` to read it:

```kotlin
public class Registry {
    public companion object {
        // COMPANION_CONSTANT_WITHOUT_JVM_FIELD
        public val DEFAULT_NAME: String = "registry" 
    }
}
```

## Rationale

Java has no notion of a companion instance: `Outer.Companion.getDEFAULT_NAME()` reads as an
implementation detail rather than the static field or constant a Java caller expects on `Outer`
itself. Kotlin has three ways to put the value on the outer class instead, and this check exists
because none of them is the default. See Kotlin's guide to
[static fields](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-fields) for how
`@JvmField`, `const val`, and `@JvmStatic` each compile.

## Don't

```kotlin
public class Registry {
    public companion object {
        // Java only sees Registry.Companion.getDEFAULT_NAME().
        public val DEFAULT_NAME: String = "registry"
    }
}
```

## Do

```kotlin
public class Registry {
    public companion object {
        public const val VERSION: Int = 1

        @JvmField
        public val ORIGIN: String = "field"

        @JvmStatic
        public val EXPOSED: String = "static getter"
    }
}
```

`const val` compiles to a real static final field but only accepts a compile-time constant
(primitives and strings). `@JvmField` exposes any other final value the same way, as a plain
static field. `@JvmStatic` instead compiles a static getter, useful when the value needs a
computed default. `@get:JvmSynthetic` is a fourth option when the property should not be visible
to Java at all.

Notable cases:

- `var` properties are not checked: they expose mutable state, not a constant.
- A property with a custom getter or setter, or a delegate (`by lazy { }` and similar), is not
  checked: it exposes behavior rather than a fixed value, and `@JvmField` would not apply to most
  of these shapes anyway.
- Overrides are exempt: their Java-facing shape is fixed by the overridden declaration.
- Non-JVM compilations never register this check at all.

## Exemption

Acknowledge the companion-instance access path with `@IntentionallyNonStaticCompanionApi` when
keeping it is a deliberate choice. Apply it to the property itself, or to an enclosing class - the
companion object or its outer class - to cover every member inside:

```kotlin
public class Registry {
    public companion object {
        @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
        public val DEFAULT_NAME: String = "registry"
    }
}
```

The same annotation also covers `COMPANION_API_WITHOUT_JVM_STATIC` on functions in the same
companion, so one class-level placement can acknowledge a companion that mixes both shapes.

## Configuration

```kotlin
apiWatchdog {
    javaInterop {
        companionConstantWithoutJvmField = WatchdogSeverity.WARNING
    }
}
```

The property lives inside the `javaInterop { }` block; `javaInterop { enabled = false }` turns off
this check along with the rest of the [Java interop checks](java-interop.md) group.

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=COMPANION_CONSTANT_WITHOUT_JVM_FIELD:warning
```

## See also

- [Static fields in Java-to-Kotlin interop](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-fields)
- [Java interop checks](java-interop.md)
- [Companion API without JvmStatic](companion-api-without-jvm-static.md)
- [Exemptions and internal API](exemptions.md)
