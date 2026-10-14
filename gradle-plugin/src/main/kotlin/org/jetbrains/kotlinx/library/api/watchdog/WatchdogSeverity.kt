package org.jetbrains.kotlinx.library.api.watchdog

/**
 * The severity of a watchdog diagnostic.
 *
 * See [Severities](https://mr3zee.github.io/Library-API-Watchdog/configuration#severities) for more details about the defaults.
 */
public enum class WatchdogSeverity {
    /** The diagnostic fails the compilation. */
    ERROR,

    /** The diagnostic is a compiler warning. */
    WARNING,

    /** The check is off. */
    NONE,
}
