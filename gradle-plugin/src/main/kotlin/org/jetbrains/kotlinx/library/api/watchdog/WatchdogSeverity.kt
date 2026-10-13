package org.jetbrains.kotlinx.library.api.watchdog

/** The severity of a watchdog diagnostic. */
public enum class WatchdogSeverity {
    /** The diagnostic fails the compilation. */
    ERROR,

    /** The diagnostic is a compiler warning. */
    WARNING,

    /** The check is off. */
    NONE,
}
