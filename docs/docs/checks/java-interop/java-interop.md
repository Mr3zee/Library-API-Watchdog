# Java interop checks

`library-api-watchdog` includes six checks that keep a JVM library ergonomic for Java consumers. They flag
shapes that compile fine but that Java callers can't use idiomatically, or can't use at all. All
six only run in JVM compilations.

## The checks

- [Companion API without JvmStatic](./companion-api-without-jvm-static.md) - companion object functions compile to instance methods and
  are only reachable from Java through the companion instance getter.
- [Companion constants without JvmField](./companion-constant-without-jvm-field.md) - constant-shaped companion properties are only reachable from Java through the companion instance getter.
- [Default parameters without JvmOverloads](./default-parameters-without-jvm-overloads.md) - default parameter values are a Kotlin-only feature.
  Without `@JvmOverloads` Java callers must specify every argument.
- [Kotlin-only API without JvmSynthetic](./kotlin-only-api-without-jvm-synthetic.md) - `suspend` functions, reified generics, and Kotlin-specific function types
  stay visible to Java even though Java can't call them idiomatically.
- [Mangled JVM names in public API](./mangled-jvm-name-public-api.md) - a value class in a signature makes the JVM backend mangle the compiled name, so Java can't call it.
- [Top-level API without JvmName](./top-level-api-without-jvm-name.md) - a file's public top-level declarations compile into a facade class named after the file,
  so renaming the file breaks Java callers.

## Kotlin-only audience

These checks only pay off for libraries that support Java consumers. A library with a Kotlin-only
audience turns off the whole group instead of demoting each check individually:

```kotlin
apiWatchdog {
    javaInterop {
        enabled = false
    }
}
```

The `enabled` switch wins over the per-check severities configured inside the same `javaInterop { }`
block: once it is `false`, none of the six diagnostics run, no matter what their individual
severity properties are set to. See the [Configuration](../../configuration.md) for the full
list of severity properties.

## Per-declaration exceptions

A library that generally supports Java can still let individual declarations sacrifice Java
ergonomics on purpose. An exemption fits when the declaration stays usable from Java, just not
idiomatically - for example, a callback kept as a Kotlin function type, which Java callers can
still pass a lambda to. Acknowledge the wart in place with the matching `@Intentionally*`
exemption annotation for the check, using the `IGNORE_JAVA_INTEROP` reason and a description of
why this declaration ignores Java ergonomics:

```kotlin
// !hide-focused
/** Watches application configuration for changes. */
public class ConfigWatcher {
    // !hide-focused
    /** Invokes [listener] on every configuration change. */
    @IntentionallyKotlinOnlyApi(
        reason = ExemptionReason.IGNORE_JAVA_INTEROP,
        description = "Java callers can pass a lambda that returns Unit.INSTANCE. " +
                "Forking the API into a fun interface overload is not worth it.",
    )
    public fun onChange(listener: (String) -> Unit) { }
}
```

An exemption is not a fix: the reported shape stays in the Java-visible API surface. A declaration
Java callers should not see at all is better hidden with `@JvmSynthetic`, the fix suggested by
[Kotlin-only API without JvmSynthetic](./kotlin-only-api-without-jvm-synthetic.md).

`IGNORE_JAVA_INTEROP` only categorizes the exemption. The description still has to state the
reason. See [Exemptions and internal API](../../exemptions.md) for the full exemption model, including
[`EXEMPTION_WITHOUT_EXPLANATION`](../special/exemption-without-explanation.md).

## See also

- [Kotlin's Java-to-Kotlin interop guide](https://kotlinlang.org/docs/java-to-kotlin-interop.html)
- [Mangled JVM names in public API](./mangled-jvm-name-public-api.md)
- [Kotlin-only API without JvmSynthetic](./kotlin-only-api-without-jvm-synthetic.md)
- [Companion API without JvmStatic](./companion-api-without-jvm-static.md)
- [Companion constants without JvmField](./companion-constant-without-jvm-field.md)
- [Top-level API without JvmName](./top-level-api-without-jvm-name.md)
- [Default parameters without JvmOverloads](./default-parameters-without-jvm-overloads.md)
