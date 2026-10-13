package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import org.gradle.api.logging.Logger

/** Turns a fixer response into the report files of a project. */
internal object ProjectReportWriter {
    /**
     * @param response the fixer response
     * @param sourcesUpdated whether the fixer rewrote the sources. Then the `exemption` entries
     *   hold the applied annotations, so the `fixed` entries aren't listed as not applied.
     */
    fun write(
        response: Map<String, List<String>>,
        sourcesUpdated: Boolean,
        projectPath: String,
        projectDirectory: File,
        reportFile: File,
        reportDataFile: File,
        logger: Logger,
    ) {
        fun relativize(path: String): String = File(path).relativeToOrSelf(projectDirectory).path

        val applied = response["exemption"].orEmpty().map { value ->
            val (annotation, line, path) = value.split('\t', limit = 3)
            AppliedExemptionReportEntry(annotation, "${relativize(path)}:$line")
        }
        val applicable = if (sourcesUpdated) {
            emptyList()
        } else {
            response["fixed"].orEmpty().map { value ->
                val (diagnostic, annotation, line, path) = value.split('\t', limit = 4)
                // An empty annotation field marks a placeholder KDoc.
                NotAppliedExemptionReportEntry(
                    annotation = annotation.ifEmpty { null },
                    diagnostic = diagnostic,
                    location = "${relativize(path)}:$line",
                    reason = if (annotation.isEmpty()) {
                        "exemptExistingApi can add a placeholder KDoc."
                    } else {
                        "exemptExistingApi can apply it."
                    },
                )
            }
        }
        val skipped = response["skipped"].orEmpty().map { value ->
            val (diagnostic, annotation, line, path, reason) = value.split('\t', limit = 5)
            NotAppliedExemptionReportEntry(
                annotation = annotation.ifEmpty { null },
                diagnostic = diagnostic,
                location = "${relativize(path)}:$line",
                reason = reason.unescapeFixerNewlines(),
            )
        }
        val report = ApiWatchdogProjectReport(
            projectPath = projectPath,
            applied = applied,
            notApplied = applicable + skipped,
        )
        ApiWatchdogReportData.write(report, reportDataFile)
        ApiWatchdogHtmlReport.write(listOf(report), reportFile, aggregate = false)
        logger.lifecycle("API Watchdog report: $reportFile")
    }
}
