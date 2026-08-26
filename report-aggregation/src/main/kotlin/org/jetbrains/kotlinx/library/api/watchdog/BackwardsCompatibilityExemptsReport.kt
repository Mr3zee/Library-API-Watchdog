package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File

public const val EXEMPTS_REPORT_USAGE: String =
    "library-api-watchdog-backwards-compatibility-exempts-report"
public const val EXEMPTS_REPORT_ELEMENTS_CONFIGURATION: String =
    "backwardsCompatibilityExemptsReportElements"
public const val EXEMPTS_REPORT_DEPENDENCIES_CONFIGURATION: String =
    "backwardsCompatibilityExemptsReports"

public data class AppliedExemptionReportEntry(
    public val annotation: String,
    public val location: String,
)

public data class NotAppliedExemptionReportEntry(
    public val annotation: String?,
    public val diagnostic: String,
    public val location: String,
    public val reason: String,
)

public data class BackwardsCompatibilityExemptsProjectReport(
    public val projectPath: String,
    public val applied: List<AppliedExemptionReportEntry>,
    public val notApplied: List<NotAppliedExemptionReportEntry>,
)

/** A small, versioned interchange format published as a Gradle report variant. */
public object BackwardsCompatibilityExemptsReportData {
    private const val VERSION = "1"

    public fun write(report: BackwardsCompatibilityExemptsProjectReport, file: File) {
        file.parentFile.mkdirs()
        file.writeText(buildString {
            appendLine("version\t$VERSION")
            appendLine("project\t${report.projectPath.escapeField()}")
            report.applied.forEach { entry ->
                appendLine("applied\t${entry.annotation.escapeField()}\t${entry.location.escapeField()}")
            }
            report.notApplied.forEach { entry ->
                appendLine(
                    listOf(
                        "notApplied",
                        entry.annotation.orEmpty(),
                        entry.diagnostic,
                        entry.location,
                        entry.reason,
                    ).joinToString("\t") { it.escapeField() }
                )
            }
        })
    }

    public fun read(file: File): BackwardsCompatibilityExemptsProjectReport {
        var projectPath: String? = null
        val applied = mutableListOf<AppliedExemptionReportEntry>()
        val notApplied = mutableListOf<NotAppliedExemptionReportEntry>()
        file.readLines().filter(String::isNotBlank).forEachIndexed { index, line ->
            val fields = line.split('\t').map { it.unescapeField() }
            when (fields.first()) {
                "version" -> require(fields == listOf("version", VERSION)) {
                    "Unsupported backwards-compatibility exempts report version in $file"
                }
                "project" -> {
                    require(fields.size == 2) { "Malformed project entry in $file:${index + 1}" }
                    projectPath = fields[1]
                }
                "applied" -> {
                    require(fields.size == 3) { "Malformed applied entry in $file:${index + 1}" }
                    applied += AppliedExemptionReportEntry(fields[1], fields[2])
                }
                "notApplied" -> {
                    require(fields.size == 5) { "Malformed not-applied entry in $file:${index + 1}" }
                    notApplied += NotAppliedExemptionReportEntry(
                        fields[1].ifEmpty { null },
                        fields[2],
                        fields[3],
                        fields[4],
                    )
                }
                else -> error("Unknown backwards-compatibility exempts report entry in $file:${index + 1}")
            }
        }
        return BackwardsCompatibilityExemptsProjectReport(
            projectPath = requireNotNull(projectPath) { "No project entry in $file" },
            applied = applied,
            notApplied = notApplied,
        )
    }

    private fun String.escapeField(): String = buildString(length) {
        for (character in this@escapeField) {
            append(
                when (character) {
                    '\\' -> "\\\\"
                    '\t' -> "\\t"
                    '\n' -> "\\n"
                    '\r' -> "\\r"
                    else -> character
                }
            )
        }
    }

    private fun String.unescapeField(): String = buildString(length) {
        var index = 0
        while (index < this@unescapeField.length) {
            val character = this@unescapeField[index]
            if (character == '\\' && index + 1 < this@unescapeField.length) {
                index++
                append(
                    when (this@unescapeField[index]) {
                        't' -> '\t'
                        'n' -> '\n'
                        'r' -> '\r'
                        else -> this@unescapeField[index]
                    }
                )
            } else {
                append(character)
            }
            index++
        }
    }
}

public object BackwardsCompatibilityExemptsHtmlReport {
    public fun write(
        reports: List<BackwardsCompatibilityExemptsProjectReport>,
        output: File,
        aggregate: Boolean,
    ) {
        val sortedReports = reports.sortedBy { it.projectPath }
        val applied = sortedReports.flatMap { report -> report.applied.map { report.projectPath to it } }
        val notApplied = sortedReports.flatMap { report -> report.notApplied.map { report.projectPath to it } }
        output.parentFile.mkdirs()
        output.writeText(buildString {
            appendLine("<!doctype html>")
            appendLine("<html lang=\"en\"><head><meta charset=\"utf-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            appendLine("<title>Backwards-compatibility exemptions report</title>")
            appendLine("""
                <style>
                :root{color-scheme:light dark;font-family:system-ui,sans-serif}body{max-width:1100px;margin:0 auto;padding:2rem}
                h1{margin-bottom:.25rem}.summary{display:flex;gap:1rem;margin:1.5rem 0}.count{border:1px solid #8885;border-radius:.6rem;padding:.8rem 1rem}
                details{border:1px solid #8885;border-radius:.6rem;margin:.7rem 0;padding:.7rem 1rem}summary{cursor:pointer;font-weight:650}
                table{border-collapse:collapse;width:100%;margin-top:.8rem}th,td{text-align:left;vertical-align:top;padding:.45rem;border-top:1px solid #8884}
                code{overflow-wrap:anywhere}.muted{opacity:.72}.empty{border:1px dashed #8888;border-radius:.6rem;padding:1rem}
                </style>
            """.trimIndent())
            appendLine("</head><body>")
            appendLine("<h1>Backwards-compatibility exemptions</h1>")
            val scope = if (aggregate) {
                "Aggregate report for ${sortedReports.size} project(s)"
            } else {
                "Project ${sortedReports.singleOrNull()?.projectPath.orEmpty()}"
            }
            appendLine("<p class=\"muted\">${scope.escapeHtml()}</p>")
            appendLine("<div class=\"summary\"><div class=\"count\"><strong>${applied.size}</strong><br>Applied</div>")
            appendLine("<div class=\"count\"><strong>${notApplied.size}</strong><br>Not applied</div></div>")
            appendLine("<h2>Applied annotations</h2>")
            if (applied.isEmpty()) {
                appendLine("<p class=\"empty\">No Watchdog @Intentionally* annotations found.</p>")
            } else {
                applied.groupBy { it.second.annotation }.toSortedMap().forEach { (annotation, entries) ->
                    appendLine("<details open><summary>@${annotation.escapeHtml()} (${entries.size})</summary>")
                    appendLine("<table><thead><tr>${projectHeader(aggregate)}<th>Location</th></tr></thead><tbody>")
                    entries.sortedWith(compareBy({ it.first }, { it.second.location })).forEach { (project, entry) ->
                        appendLine("<tr>${projectCell(project, aggregate)}<td><code>${entry.location.escapeHtml()}</code></td></tr>")
                    }
                    appendLine("</tbody></table></details>")
                }
            }
            appendLine("<h2>Not applied</h2>")
            if (notApplied.isEmpty()) {
                appendLine("<p class=\"empty\">Every recorded diagnostic was acknowledged automatically.</p>")
            } else {
                notApplied.groupBy { it.second.annotation }.toSortedMap(nullsFirst()).forEach { (annotation, entries) ->
                    val group = annotation?.let { "@$it" } ?: "No automatic annotation"
                    appendLine("<details open><summary>${group.escapeHtml()} (${entries.size})</summary>")
                    appendLine("<table><thead><tr>${projectHeader(aggregate)}<th>Diagnostic</th><th>Location</th><th>Reason</th></tr></thead><tbody>")
                    entries.sortedWith(compareBy({ it.first }, { it.second.location }, { it.second.diagnostic }))
                        .forEach { (project, entry) ->
                            appendLine(
                                "<tr>${projectCell(project, aggregate)}<td><code>${entry.diagnostic.escapeHtml()}</code></td>" +
                                        "<td><code>${entry.location.escapeHtml()}</code></td><td>${entry.reason.escapeHtml()}</td></tr>"
                            )
                        }
                    appendLine("</tbody></table></details>")
                }
            }
            appendLine("</body></html>")
        })
    }

    private fun projectHeader(aggregate: Boolean): String = if (aggregate) "<th>Project</th>" else ""

    private fun projectCell(project: String, aggregate: Boolean): String =
        if (aggregate) "<td><code>${project.escapeHtml()}</code></td>" else ""

    private fun String.escapeHtml(): String = buildString(length) {
        for (character in this@escapeHtml) {
            append(
                when (character) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> character
                }
            )
        }
    }
}
