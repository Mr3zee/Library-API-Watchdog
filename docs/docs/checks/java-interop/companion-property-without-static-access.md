# Companion property without static access

`COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS` reports a public companion object property when at
least one Java-visible accessor exists only on the nested `Companion` class.

|                  |                                                                  |
|------------------|------------------------------------------------------------------|
| Diagnostic       | `COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS`                       |
| Default severity | Error                                                            |
| Applies to       | JVM compilations only                                            |
| Gradle property  | [`companionPropertyWithoutStaticAccess`](../../configuration.md) |
| Exemption        | [`@IntentionallyNonStaticCompanionApi`](../../exemptions.md)     |

## What it reports

The check covers immutable, mutable, computed, delegated, `lateinit`, extension, and override
properties in the public API. It reports only the accessors that remain visible through the
companion instance.

```kotlin
// !hide-focused
/** Values managed by a registry. */
public class Registry {
    // !hide-focused
    /** Registry-wide values. */
    public companion object {
        // !hide-focused
        /** The active endpoint. */
        // !diag[/currentEndpoint/] COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS ["Registry.Companion","currentEndpoint","getter and setter","$getterAndSetterFix(Registry)"]
        public var currentEndpoint: String = "local"

        // !hide-focused
        /** A display name computed on demand. */
        // !diag[/displayName/] COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS ["Registry.Companion","displayName","getter","$getterFix(Registry)"]
        public val displayName: String get() = "registry"

        // !hide-focused
        /** Metadata initialized on first access. */
        // !diag[/metadata/] COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS ["Registry.Companion","metadata","getter","$getterFix(Registry)"]
        public val metadata: String by lazy { "metadata" }
    }
}
```

## Rationale

Java has no companion-object syntax. Without static access, callers must use forms such as
`Registry.Companion.getCurrentEndpoint()` and `Registry.Companion.setCurrentEndpoint(value)`.
That exposes a Kotlin implementation detail in the Java API.

`@JvmStatic` works on every property shape and adds static accessors to the outer class. For
eligible stored properties, `@JvmField` exposes a static field instead. A `const val` is also a
static final field. See Kotlin's guides to
[static methods](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-methods) and
[static fields](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-fields).

### Don't

```kotlin
// !hide-focused
/** Values managed by a registry. */
public class Registry {
    // !hide-focused
    /** Registry-wide values. */
    public companion object {
        // !hide-focused
        /** The active endpoint. */
        // !diag[/currentEndpoint/] COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS ["Registry.Companion","currentEndpoint","getter and setter","$getterAndSetterFix(Registry)"]
        public var currentEndpoint: String = "local"
    }
}
```

### Do

```kotlin
// !hide-focused
/** Values managed by a registry. */
public class Registry {
    // !hide-focused
    /** Registry-wide values. */
    public companion object {
        // !hide-focused
        /** The active endpoint. */
        // Java can now call `Registry.getCurrentEndpoint()` 
        // and `Registry.setCurrentEndpoint(value)`.
        @JvmStatic
        public var currentEndpoint: String = "local"
    }
}
```

### Or do

```kotlin
// !hide-focused
/** Values managed by a registry. */
public class Registry {
    // !hide-focused
    /** Registry-wide values. */
    public companion object {
        // !hide-focused
        /** The active endpoint. */
        @JvmField
        public var currentEndpoint: String = "local"

        // !hide-focused
        /** The default registry name. */
        public const val DEFAULT_NAME: String = "registry"
    }
}
```

`@JvmField` does not apply to every property shape. Use `@JvmStatic` when a property has custom
accessors, a delegate, no backing field, or is an override.

## Notes

- Getter and setter exposure is evaluated independently. A `var` with only `@get:JvmStatic` is
  still reported for its instance setter. Add `@set:JvmStatic`, hide the setter with
  `@set:JvmSynthetic`, or make the setter non-public.
- A public property with a private or internal setter only needs static access for its getter.
- `const val` and `@JvmField` properties already have static field access and are not reported.
- A property is not reported when every supported Java accessor is hidden with `@JvmSynthetic`.
- Overrides are reported because `@JvmStatic` can add static accessors without changing the
  implemented property contract.
- Non-JVM compilations never register this check.
- `@PublishedApi internal` properties are not reported because their public bytecode entries are
  binary implementation details rather than supported Java source API.

## Exemption

<!-- diagnostic-exemption: COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS -->
If this API shape is intentional, apply `@IntentionallyNonStaticCompanionApi` to the property or an
enclosing class.

```kotlin
// !hide-focused
/** Values managed by a registry. */
public class Registry {
    // !hide-focused
    /** Registry-wide values. */
    public companion object {
        // !hide-focused
        /** The deliberately companion-only endpoint. */
        @IntentionallyNonStaticCompanionApi(
            reason = ExemptionReason.API_DESIGN,
        )
        public var currentEndpoint: String = "local"
    }
}
```

The same annotation also acknowledges
[`COMPANION_API_WITHOUT_JVM_STATIC`](./companion-api-without-jvm-static.md).

## Configuration

```kotlin
apiWatchdog {
    javaInterop {
        companionPropertyWithoutStaticAccess = WatchdogSeverity.WARNING
    }
}
```

The property lives inside the `javaInterop { }` block. `javaInterop { enabled = false }` turns off
this check along with the rest of the [Java interop checks](./java-interop.md) group.

With direct compiler invocation:

```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS:warning
```

## See also

- [Static methods in Java-to-Kotlin interop](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-methods)
- [Static fields in Java-to-Kotlin interop](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-fields)
- [Companion function without JvmStatic](./companion-api-without-jvm-static.md)
- [Java interop checks](./java-interop.md)
- [Exemptions and internal API](../../exemptions.md)
