package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import org.gradle.api.logging.Logger

internal object ProjectReportWriter {
    internal const val WARNING_REASON = "exemptExistingApi exempts errors only."

    /**
     * @param sourcesUpdated whether the fixer rewrote the sources. Then each `fixed` entry is also
     *   an `exemption` entry, so the report lists it as applied only.
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
        val warnings = response.warnings.map { warning ->
            NotAppliedExemptionReportEntry(
                annotation = warning.annotation,
                diagnostic = warning.diagnostic,
                location = warning.location(projectDirectory),
                reason = WARNING_REASON,
                severity = severityOf(warning.diagnostic),
            )
        }
        val report = ApiWatchdogProjectReport(
            projectPath = projectPath,
            projectDirectory = projectDirectoryFromRoot,
            applied = applied,
            notApplied = buildList {
                addAll(applicable)
                addAll(skipped)
                addAll(warnings)
            },
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
