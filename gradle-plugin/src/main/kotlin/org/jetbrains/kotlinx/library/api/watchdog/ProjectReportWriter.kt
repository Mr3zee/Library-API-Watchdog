package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import org.gradle.api.logging.Logger

internal object ProjectReportWriter {
    /**
     * @param sourcesUpdated whether the fixer rewrote the sources. Then the `exemption` entries
     *   hold the applied annotations, so the `fixed` entries aren't listed as not applied.
     */
    fun write(
        response: FixerResponse,
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
        fun severityOf(diagnostic: String): String =
            (severities[diagnostic] ?: WatchdogSeverity.ERROR).name.lowercase()

        val applied = response.exemptions.map { exemption ->
            AppliedExemptionReportEntry(exemption.annotation, exemption.location(projectDirectory))
        }
        val applicable = if (sourcesUpdated) {
            emptyList()
        } else {
            response.fixed.map { fix ->
                NotAppliedExemptionReportEntry(
                    annotation = fix.annotation,
                    diagnostic = fix.diagnostic,
                    location = fix.location(projectDirectory),
                    reason = if (fix.annotation == null) {
                        "exemptExistingApi can add a placeholder KDoc."
                    } else {
                        "exemptExistingApi can apply it."
                    },
                    severity = severityOf(fix.diagnostic),
                )
            }
        }
        val skipped = response.skipped.map { skip ->
            NotAppliedExemptionReportEntry(
                annotation = skip.annotation,
                diagnostic = skip.diagnostic,
                location = skip.location(projectDirectory),
                reason = skip.reason,
                severity = severityOf(skip.diagnostic),
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
