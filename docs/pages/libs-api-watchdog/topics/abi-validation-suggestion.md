# Binary compatibility validation suggestion

This is a build-level check performed by the Gradle plugin, not the compiler plugin. The watchdog
reviews the shape of new API declarations, but it doesn't notice when API that already shipped
changes incompatibly. That part is covered by binary compatibility validation, which compares
each build against a committed dump of the released API surface.

## What happens

The Gradle plugin checks that either the Kotlin Gradle plugin's built-in
[ABI validation](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html)
(Kotlin 2.2 or newer) or the standalone
[Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator)
plugin is enabled alongside it. When neither is, the build prints a warning with a setup snippet:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
}
```

On Kotlin 2.2 and 2.3, write `abiValidation { enabled.set(true) }` instead of the
`abiValidation()` call.

## Disable it

Turn off the suggestion through the extension:

```kotlin
apiWatchdog {
    suggestAbiValidation = false
}
```

## See also

- [ABI validation in the Kotlin Gradle plugin](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html)
- [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator)
