package org.jetbrains.kotlinx.library.api.watchdog

/**
 * Marks a declaration that the watchdog plugins share between their artifacts. It is public
 * for technical reasons only. It can change or disappear in any release.
 */
@RequiresOptIn(
    message = "This API is internal to the Library API Watchdog plugins. It can change in any release.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
public annotation class InternalWatchdogApi
