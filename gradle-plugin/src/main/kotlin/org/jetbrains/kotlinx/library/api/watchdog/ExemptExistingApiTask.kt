package org.jetbrains.kotlinx.library.api.watchdog

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.UntrackedTask

/**
 * The `exemptExistingApi` task. It adds the matching `@Intentionally*` annotation with
 * `reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY` to every declaration with a watchdog
 * diagnostic in the main Kotlin sources, then writes the API Watchdog report. Use it when you
 * adopt the watchdog on a library with a released API.
 */
@UntrackedTask(because = "Rewrites the Kotlin sources it analyzes")
public abstract class ExemptExistingApiTask : ApiWatchdogReportTask() {
    @get:Internal
    override val updatesSources: Boolean
        get() = true

    @get:Internal
    override val noCompilationWarning: String
        get() = "No main Kotlin compilation found, so there are no watchdog diagnostics to exempt."

    override fun logResponse(response: FixerResponse) {
        val projectDirectory = projectDirectory.get().asFile
        var exemptionCount = 0
        var kdocCount = 0
        response.fixed.forEach { fix ->
            val location = fix.location(projectDirectory)
            if (fix.annotation == null) {
                kdocCount++
                logger.lifecycle("A placeholder KDoc documents ${fix.diagnostic} at $location")
            } else {
                exemptionCount++
                logger.lifecycle("@${fix.annotation} acknowledges ${fix.diagnostic} at $location")
            }
        }
        response.skipped.forEach { skip ->
            logger.warn(
                "${skip.diagnostic} at ${skip.location(projectDirectory)} needs manual attention: ${skip.reason}"
            )
        }
        if (exemptionCount == 0 && kdocCount == 0 && response.skipped.isEmpty()) {
            logger.lifecycle("No watchdog diagnostics to exempt.")
        } else {
            logger.lifecycle(
                "Added $exemptionCount backwards-compatibility exemption(s)" +
                        (if (kdocCount == 0) "" else " and $kdocCount placeholder KDoc(s)") +
                        " in ${response.modifiedFiles.size} file(s)." +
                        if (response.skipped.isEmpty()) "" else " ${response.skipped.size} diagnostic(s) need manual attention."
            )
        }
    }
}
