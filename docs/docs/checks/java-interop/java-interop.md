# Java interop checks

`%product%` includes six checks that keep a JVM library ergonomic for Java consumers. They flag
shapes that compile fine but that Java callers can't use idiomatically, or can't use at all. All
six only run in JVM compilations.

## The checks

- [](mangled-jvm-name-public-api.md) - a value class in a signature makes the JVM backend mangle the compiled name, so Java can't call it.
- [](kotlin-only-api-without-jvm-synthetic.md) - `suspend` functions, reified generics, and Kotlin-specific function types 
  stay visible to Java even though Java can't call them idiomatically.
- [](companion-api-without-jvm-static.md) - companion object functions compile to instance methods and 
  are only reachable from Java through the companion instance getter.
- [](companion-constant-without-jvm-field.md) - constant-shaped companion properties are only reachable from Java through the companion instance getter.
- [](top-level-api-without-jvm-name.md) - a file's public top-level declarations compile into a facade class named after the file, 
  so renaming the file breaks Java callers.
- [](default-parameters-without-jvm-overloads.md) - default parameter values are a Kotlin-only feature. 
  Without `@JvmOverloads` Java callers must specify every argument.

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
severity properties are set to. See the [](configuration.md) for the full
list of severity properties.

## Per-declaration exceptions

A library that generally supports Java can still let individual declarations sacrifice Java
ergonomics on purpose - for example, a coroutine-based API with no blocking bridge planned.
Acknowledge that in place with the matching `@Intentionally*` exemption annotation for the check,
using the `IGNORE_JAVA_INTEROP` reason and a description of why this declaration ignores Java
callers:

```kotlin
@IntentionallyKotlinOnlyApi(
    reason = ExemptionReason.IGNORE_JAVA_INTEROP,
    description = "Coroutine-only API," +
            "no blocking or CompletableFuture bridge planned.",
)
public suspend fun refresh(): String = fetchLatest()
```

`IGNORE_JAVA_INTEROP` only categorizes the exemption. The description still has to state the
reason. See [](exemptions.md) for the full exemption model, including
[`EXEMPTION_WITHOUT_EXPLANATION`](exemption-without-explanation.md).

## See also

- [Kotlin's Java-to-Kotlin interop guide](https://kotlinlang.org/docs/java-to-kotlin-interop.html)
  for background on how Kotlin declarations compile for Java callers.
- [](mangled-jvm-name-public-api.md)
- [](kotlin-only-api-without-jvm-synthetic.md)
- [](companion-api-without-jvm-static.md)
- [](companion-constant-without-jvm-field.md)
- [](top-level-api-without-jvm-name.md)
- [](default-parameters-without-jvm-overloads.md)
