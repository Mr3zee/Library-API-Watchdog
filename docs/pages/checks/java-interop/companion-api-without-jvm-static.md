# Companion function without JvmStatic

|                  |                                                              |
|------------------|--------------------------------------------------------------|
| Diagnostic       | `COMPANION_API_WITHOUT_JVM_STATIC`                           |
| Default severity | Error                                                        |
| Applies to       | JVM compilations only                                        |
| Gradle property  | [`companionApiWithoutJvmStatic`](../../configuration.md)     |
| Exemption        | [`@IntentionallyNonStaticCompanionApi`](../../exemptions.md) |

## What it reports

Functions in the public API declared directly in a class or interface companion object without
`@JvmStatic` or `@JvmSynthetic`.

```kotlin
// !hide-focused
/** Factory for application services. */
public class ServiceFactory {
    // !hide-focused
    /** Creates service factory instances. */
    public companion object {
        // !hide-focused
        /** Creates an empty service factory. */
        // !diag[/create/] COMPANION_API_WITHOUT_JVM_STATIC ["ServiceFactory","create","Companion"]
        public fun create(): ServiceFactory = ServiceFactory()
    }
}
```

## Rationale

Without `@JvmStatic`, Java callers must write `Registry.Companion.create()` instead of
`Registry.create()`.

### Don't

```kotlin
// !hide-focused
/** Registry of application services. */
public class Registry {
    // !hide-focused
    /** Creates registry instances. */
    public companion object {
        // !hide-focused
        /** Creates an empty registry. */
        // !diag[/create/] COMPANION_API_WITHOUT_JVM_STATIC ["Registry","create","Companion"]
        public fun create(): Registry = Registry()
    }
}
```

### Do

```kotlin
// !hide-focused
/** Registry of application services. */
public class Registry {
    // !hide-focused
    /** Creates registry instances. */
    public companion object {
        // !hide-focused
        /** Creates an empty registry. */
        @JvmStatic
        public fun create(): Registry = Registry()
    }
}
```

## Notes

- The check includes overrides.
- `suspend` functions are also reported by [Kotlin-only API without JvmSynthetic](./kotlin-only-api-without-jvm-synthetic.md).
  `@JvmSynthetic` resolves both diagnostics.
- The check skips `@PublishedApi internal` functions.

## Exemption

<!-- diagnostic-exemption: COMPANION_API_WITHOUT_JVM_STATIC -->
If this is intended, apply `@IntentionallyNonStaticCompanionApi` to the function or an
enclosing class.

Use the exemption when the declaration is part of the API design or must stay for backwards compatibility.

```kotlin
// !hide-focused
/** Registry of application services. */
public class Registry {
    // !hide-focused
    /** Creates registry instances. */
    public companion object {
        // !hide-focused
        /** Creates a registry through the companion instance. */
        @IntentionallyNonStaticCompanionApi(
            reason = ExemptionReason.API_DESIGN,
        )
        public fun create(): Registry = Registry()
    }
}
```

## Configuration

```kotlin
apiWatchdog {
    javaInterop {
        companionApiWithoutJvmStatic = WatchdogSeverity.ERROR
    }
}
```

`javaInterop { enabled = false }` disables all [Java interop checks](./java-interop.md).

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=COMPANION_API_WITHOUT_JVM_STATIC:error
```

## See also

- [Static methods](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-methods)
- [Companion property without static access](./companion-property-without-static-access.md)
- [Java interop checks](./java-interop.md)
- [Exemptions and internal API](../../exemptions.md)
