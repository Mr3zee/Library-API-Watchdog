package org.jetbrains.kotlinx.library.api.watchdog

/**
 * What the Gradle plugin does when a project applies the watchdog without
 * [explicit API mode](https://kotlinlang.org/docs/api-guidelines-simplicity.html#use-explicit-api-mode).
 *
 * See [Explicit API mode](https://kotlin.github.io/Library-API-Watchdog/configuration#explicit-api-mode) for more details.
 */
public enum class ExplicitApiRequirement {
    /**
     * The build fails when the task graph has a compilation that the watchdog checks.
     * `exemptExistingApi` and `generateApiWatchdogReport` still run. The default.
     */
    FAIL,

    /** The plugin logs a warning after Gradle evaluates the project. The checks run. */
    WARN,

    /** The checks run without a message. */
    SKIP,
}
