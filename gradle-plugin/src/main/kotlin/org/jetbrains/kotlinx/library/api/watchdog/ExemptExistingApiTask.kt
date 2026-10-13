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

    override fun logResponse(response: Map<String, List<String>>) {
        var exemptionCount = 0
        var kdocCount = 0
        response["fixed"].orEmpty().forEach { value ->
            val (diagnostic, annotation, line, path) = value.split('\t', limit = 4)
            // An empty annotation field marks a placeholder KDoc.
            if (annotation.isEmpty()) {
                kdocCount++
                logger.lifecycle("A placeholder KDoc documents $diagnostic at ${relativize(path)}:$line")
            } else {
                exemptionCount++
                logger.lifecycle("@$annotation acknowledges $diagnostic at ${relativize(path)}:$line")
            }
        }
        val modifiedFiles = response["modifiedFile"].orEmpty().toSet()
        val skipped = response["skipped"].orEmpty().map { value ->
            val (diagnostic, _, line, path, reason) = value.split('\t', limit = 5)
            Triple(diagnostic, "${relativize(path)}:$line", reason.unescapeFixerNewlines())
        }
        skipped.forEach { (diagnostic, location, reason) ->
            logger.warn(
                "$diagnostic at $location needs manual attention: $reason"
            )
        }

        if (exemptionCount == 0 && kdocCount == 0 && skipped.isEmpty()) {
            logger.lifecycle("No watchdog diagnostics to exempt.")
        } else {
            logger.lifecycle(
                "Added $exemptionCount backwards-compatibility exemption(s)" +
                        (if (kdocCount == 0) "" else " and $kdocCount placeholder KDoc(s)") +
                        " in ${modifiedFiles.size} file(s)." +
                        if (skipped.isEmpty()) "" else " ${skipped.size} diagnostic(s) need manual attention."
            )
        }
    }
}
