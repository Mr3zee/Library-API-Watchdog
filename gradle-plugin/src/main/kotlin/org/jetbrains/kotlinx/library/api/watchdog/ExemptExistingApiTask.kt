package org.jetbrains.kotlinx.library.api.watchdog

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.UntrackedTask

/**
 * The `exemptExistingApi` task. It exempts every watchdog error in the Kotlin sources of every
 * compilation except tests: the matching `@Intentionally*` annotation with
 * `reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY`, or a placeholder KDoc for `UNDOCUMENTED_PUBLIC_API`.
 * Then it writes the [API Watchdog report](https://mr3zee.github.io/Library-API-Watchdog/report).
 * It doesn't exempt warnings. Use it when you adopt the watchdog on a library with a released API.
 *
 * See [Adding the plugin to existing libraries](https://mr3zee.github.io/Library-API-Watchdog/existing-libs) for more details.
 */
@UntrackedTask(because = "Rewrites the Kotlin sources it analyzes")
public abstract class ExemptExistingApiTask internal constructor() : ApiWatchdogReportTask() {
    @get:Internal
    override val updatesSources: Boolean
        get() = true

    @get:Internal
    override val noCompilationWarning: String
        get() = "No Kotlin compilation except tests found, so there are no watchdog diagnostics to exempt."

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
            logger.lifecycle("No watchdog errors to exempt.")
        } else {
            logger.lifecycle(
                "Added $exemptionCount backwards-compatibility exemption(s)" +
                        (if (kdocCount == 0) "" else " and $kdocCount placeholder KDoc(s)") +
                        " in ${response.modifiedFiles.size} file(s)." +
                        if (response.skipped.isEmpty()) "" else " ${response.skipped.size} diagnostic(s) need manual attention."
            )
        }
        if (response.warnings.isNotEmpty()) {
            logger.lifecycle(
                "${response.warnings.size} warning(s) stay, because the task exempts errors only. " +
                        "Check out the report for the full list."
            )
        }
    }
}
