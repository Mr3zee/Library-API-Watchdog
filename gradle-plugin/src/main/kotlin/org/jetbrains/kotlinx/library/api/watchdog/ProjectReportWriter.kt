package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import java.io.IOException
import org.gradle.api.logging.Logger

internal object ProjectReportWriter {
    internal const val WARNING_REASON = "exemptExistingApi exempts errors only."

    /**
     * @param sourcesUpdated whether the fixer rewrote the sources. Then each `fixed` entry with an
     *   annotation is also an `exemption` entry, so the report lists it as applied only. A placeholder
     *   KDoc fix is in neither list.
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
                    // The "Wasn't auto-fixed" tag already states that the task can apply an annotation.
                    reason = when {
                        fix.annotation == null -> "exemptExistingApi can add a placeholder KDoc."
                        fix.diagnostic == WatchdogChecks.REDUNDANT_EXEMPTION.name -> "exemptExistingApi can remove it."
                        else -> ""
                    },
                    severity = severityOf(fix.diagnostic),
                    messageParameters = fix.messageParameters,
                    sourceRange = fix.sourceRange,
                    autoFixAvailable = true,
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
                messageParameters = skip.messageParameters,
                sourceRange = skip.sourceRange,
            )
        }
        val warnings = response.warnings.map { warning ->
            NotAppliedExemptionReportEntry(
                annotation = warning.annotation,
                diagnostic = warning.diagnostic,
                location = warning.location(projectDirectory),
                reason = WARNING_REASON,
                severity = severityOf(warning.diagnostic),
                messageParameters = warning.messageParameters,
                sourceRange = warning.sourceRange,
                autoFixAvailable = warning.annotation != null || warning.diagnostic == "UNDOCUMENTED_PUBLIC_API",
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
            sourceSnippets = sourceSnippets(response, projectDirectory),
        )
        ApiWatchdogReportData.write(report, reportDataFile)
        if (htmlFile != null) {
            ApiWatchdogHtmlReport.write(listOf(report), htmlFile, aggregate = false)
            logger.lifecycle("API Watchdog report: ${htmlFile.toPath().toUri()}")
        }
        if (sarifFile != null) {
            ApiWatchdogSarifReport.write(listOf(report), sarifFile)
            logger.lifecycle("API Watchdog SARIF report: ${sarifFile.toPath().toUri()}")
        }
    }

    private fun sourceSnippets(
        response: FixerResponse,
        projectDirectory: File,
    ): Map<String, ApiWatchdogSourceSnippet> {
        val sources = mutableMapOf<String, List<String>>()
        val locations = buildList {
            addAll(response.exemptions)
            addAll(response.fixed)
            addAll(response.skipped)
            addAll(response.warnings)
        }
        return buildMap {
            locations.groupBy { it.location(projectDirectory) }.forEach { (key, entries) ->
                val location = entries.first()
                val line = location.line.toIntOrNull() ?: return@forEach
                val lines = sources.getOrPut(location.path) {
                    try {
                        File(location.path).readLines()
                    } catch (_: IOException) {
                        emptyList()
                    }
                }
                if (line !in 1..lines.size) return@forEach
                val firstLine = entries.minOf { it.sourceRange?.startLine ?: line }
                val lastLine = entries.maxOf { entry ->
                    entry.sourceRange?.let { it.endLine - if (it.endColumn == 1) 1 else 0 } ?: line
                }
                val start = (firstLine - 4).coerceIn(0, lines.lastIndex)
                val end = (lastLine + 3).coerceIn(start + 1, lines.size)
                put(
                    key,
                    ApiWatchdogSourceSnippet(start + 1, lines.subList(start, end).joinToString("\n")),
                )
            }
        }
    }
}
