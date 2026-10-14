# Companion property without static access

|                  |                                                                  |
|------------------|------------------------------------------------------------------|
| Diagnostic       | `COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS`                       |
| Default severity | Error                                                            |
| Applies to       | JVM compilations only                                            |
| Gradle property  | [`companionPropertyWithoutStaticAccess`](../../configuration.md) |
| Exemption        | [`@IntentionallyNonStaticCompanionApi`](../../exemptions.md)     |

## What it reports

Companion properties in the public API with at least one Java-visible accessor without static access.
The check includes stored, computed, delegated, extension, and override properties.

```kotlin
// !hide-focused
/** Values managed by a registry. */
public class Registry {
    // !hide-focused
    /** Registry-wide values. */
    public companion object {
        // !hide-focused
        /** The active endpoint. */
        // !diag[/currentEndpoint/] COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS ["Registry.Companion","currentEndpoint","$getterAndSetter","$getterAndSetterFix(Registry)"]
        public var currentEndpoint: String = "local"
    }
}
```

## Rationale

Without static access, Java callers must use `Registry.Companion.getCurrentEndpoint()` and
`Registry.Companion.setCurrentEndpoint(value)`. `@JvmStatic` adds accessors on `Registry`.

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
        // !diag[/currentEndpoint/] COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS ["Registry.Companion","currentEndpoint","$getterAndSetter","$getterAndSetterFix(Registry)"]
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
        @JvmStatic
        public var currentEndpoint: String = "local"
    }
}
```

## Notes

- The check treats the getter and setter separately. A `var` with only `@get:JvmStatic` still reports its setter.
  Add `@set:JvmStatic`, hide it with `@set:JvmSynthetic`, or make it non-public.
- A private or internal setter needs no static access.
- The check skips `const val`, `@JvmField`, and `lateinit var` with a public setter, which expose static fields.
  A `lateinit var` with a non-public setter still reports its getter.
  For custom accessors, delegates, properties without a backing field, and overrides, use `@JvmStatic`.
- The check skips properties with no Java-visible accessors.
- The check skips `@PublishedApi internal` properties.

## Exemption

<!-- diagnostic-exemption: COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS -->
If this is intended, apply `@IntentionallyNonStaticCompanionApi` to the property or an
enclosing class.

Use the exemption when the declaration is part of the API design or must stay for backwards compatibility.

```kotlin
// !hide-focused
/** Values managed by a registry. */
public class Registry {
    // !hide-focused
    /** Registry-wide values. */
    public companion object {
        // !hide-focused
        /** The endpoint accessed through the companion instance. */
        @IntentionallyNonStaticCompanionApi(
            reason = ExemptionReason.API_DESIGN,
        )
        public var currentEndpoint: String = "local"
    }
}
```

## Configuration

```kotlin build.gradle.kts
apiWatchdog {
    javaInterop {
        companionPropertyWithoutStaticAccess = WatchdogSeverity.ERROR
    }
}
```

`javaInterop { enabled = false }` disables all [Java interop checks](./java-interop.md).

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS:error
```
