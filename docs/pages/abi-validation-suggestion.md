# Binary compatibility validation suggestion

The watchdog checks the shape of new API declarations. It doesn't find a change that breaks the
API that a library has already published. Binary compatibility validation finds such a change,
because it compares each build with a committed dump of the published API.

## What happens

The Gradle plugin, not the compiler plugin, does this check. After Gradle evaluates the project, the
plugin logs a warning when neither of these is on:

- The [ABI validation](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html) of
  the Kotlin Gradle plugin, Kotlin 2.2 or newer.
- The [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator)
  plugin, for older Kotlin versions.

The warning holds a setup snippet for the Kotlin version of the project:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
}
```

On Kotlin 2.2 and 2.3, the snippet has `abiValidation { enabled.set(true) }` instead of the
`abiValidation()` call.

## Disable it

Set `suggestAbiValidation` in the `apiWatchdog` extension. Default is `true`.

```kotlin
apiWatchdog {
    suggestAbiValidation = false
}
```
