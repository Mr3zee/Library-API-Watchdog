# Java interop checks

These six checks run only in JVM compilations. Use them for libraries that support Java callers.

## The checks

| Check | Reports |
|---|---|
| [Companion function without JvmStatic](./companion-api-without-jvm-static.md) | Companion functions without a static entry point. |
| [Companion property without static access](./companion-property-without-static-access.md) | Companion accessors without static access. |
| [Default parameters without JvmOverloads](./default-parameters-without-jvm-overloads.md) | Default parameters that Java callers can't omit. |
| [Kotlin-only API without JvmSynthetic](./kotlin-only-api-without-jvm-synthetic.md) | Kotlin-specific function shapes visible to Java. |
| [Mangled JVM names in public API](./mangled-jvm-name-public-api.md) | Value-class signatures that Java sources can't call. |
| [Top-level API without JvmName](./top-level-api-without-jvm-name.md) | File facades whose names depend on source file names. |

## Kotlin-only audience

For a library without Java callers, disable the group:

```kotlin
apiWatchdog {
    javaInterop {
        enabled = false
    }
}
```

`enabled` defaults to `true`. `false` overrides every per-check severity.

## Per-declaration exceptions

To exempt one declaration, use the check's `@Intentionally*` annotation.
For `ExemptionReason.IGNORE_JAVA_INTEROP`, add a `description`.

An exemption leaves the declaration visible to Java. To hide it, use `@JvmSynthetic`.

See [Exemptions and internal API](../../exemptions.md) for more details about exemption reasons.

## See also

- [Kotlin's Java-to-Kotlin interop guide](https://kotlinlang.org/docs/java-to-kotlin-interop.html)
