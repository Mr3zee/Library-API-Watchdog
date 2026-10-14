package org.jetbrains.kotlinx.library.api.watchdog

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.UntrackedTask

/**
 * The `generateApiWatchdogReport` task. It writes the [API Watchdog report](https://mr3zee.github.io/Library-API-Watchdog/report)
 * of the project: the applied `@Intentionally*` annotations and the diagnostics without one.
 * It changes no source.
 */
@UntrackedTask(because = "Reads the diagnostic reports that the Kotlin compile tasks write")
public abstract class GenerateApiWatchdogReportTask internal constructor() : ApiWatchdogReportTask() {
    @get:Internal
    override val updatesSources: Boolean
        get() = false

    @get:Internal
    override val noCompilationWarning: String
        get() = "No main Kotlin compilation found, so the API Watchdog report is empty."
}
