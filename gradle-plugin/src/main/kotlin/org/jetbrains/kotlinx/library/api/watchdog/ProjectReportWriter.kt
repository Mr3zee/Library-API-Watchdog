package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import org.gradle.api.logging.Logger

/** Turns a fixer response into the report files of a project. */
internal object ProjectReportWriter {
    /**
     * @param response the fixer response
     * @param sourcesUpdated whether the fixer rewrote the sources. Then the `exemption` entries
     *   hold the applied annotations, so the `fixed` entries aren't listed as not applied.
     * @param severities the configured severity of each diagnostic. A diagnostic that isn't
     *   listed is an error.
     * @param htmlFile the HTML report, or null when the format is off
     * @param sarifFile the SARIF report, or null when the format is off
     */
    fun write(
        response: Map<String, List<String>>,
        sourcesUpdated: Boolean,
        severities: Map<String, WatchdogSeverity>,
        projectPath: String,
        projectDirectory: File,
        projectDirectoryFromRoot: String,
        htmlFile: File?,
        sarifFile: File?,
        reportDataFile: File,
        logger: Logger,
    ) {
        fun relativize(path: String): String = File(path).relativeToOrSelf(projectDirectory).path
        fun severityOf(diagnostic: String): String =
            (severities[diagnostic] ?: WatchdogSeverity.ERROR).name.lowercase()

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
                    severity = severityOf(diagnostic),
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
                severity = severityOf(diagnostic),
            )
        }
        val report = ApiWatchdogProjectReport(
            projectPath = projectPath,
            projectDirectory = projectDirectoryFromRoot,
            applied = applied,
            notApplied = applicable + skipped,
        )
        ApiWatchdogReportData.write(report, reportDataFile)
        if (htmlFile != null) {
            ApiWatchdogHtmlReport.write(listOf(report), htmlFile, aggregate = false)
            logger.lifecycle("API Watchdog report: $htmlFile")
        }
        if (sarifFile != null) {
            ApiWatchdogSarifReport.write(listOf(report), sarifFile)
            logger.lifecycle("API Watchdog SARIF report: $sarifFile")
        }
    }
}
