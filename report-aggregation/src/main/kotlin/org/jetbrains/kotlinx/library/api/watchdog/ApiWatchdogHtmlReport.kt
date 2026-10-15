package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import java.net.URLEncoder

@InternalWatchdogApi
public object ApiWatchdogHtmlReport {
    private val stylesheet by lazy { resource("report.css") }
    private val highlighter by lazy { resource("prism-kotlin.js") }
    private val reportScript by lazy { resource("report.js") }

    public fun write(
        reports: List<ApiWatchdogProjectReport>,
        output: File,
        aggregate: Boolean,
    ) {
        val sortedReports = reports.sortedBy { it.projectPath }
        val applied = sortedReports.flatMap { report ->
            report.applied.map { entry ->
                Finding(report, entry.location, WatchdogCheckDescriptions.diagnosticsByAnnotation[entry.annotation] ?: listOf("unknown"), entry.annotation)
            }
        }
        val notApplied = sortedReports.flatMap { report ->
            report.notApplied.map { entry ->
                val annotation = entry.annotation ?: WatchdogCheckDescriptions.diagnosticsByAnnotation.entries
                    .firstOrNull { entry.diagnostic in it.value }?.key
                Finding(report, entry.location, listOf(entry.diagnostic), annotation, entry)
            }
        }
        output.parentFile?.mkdirs()
        output.writeText(buildString {
            appendLine("<!doctype html>")
            appendLine("<html lang=\"en\"><head><meta charset=\"utf-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            appendLine("<title>API Watchdog report</title>")
            appendLine("<style>$stylesheet</style>")
            appendLine("</head><body><main>")
            appendLine("<h1>API Watchdog report</h1>")
            val scope = if (aggregate) {
                "Aggregate report for ${countLabel(sortedReports.size, "project")}"
            } else {
                "Project ${sortedReports.singleOrNull()?.projectPath.orEmpty()}"
            }
            appendLine("<p class=\"scope\">${scope.escapeHtml()}</p>")
            appendLine("<ul class=\"totals\">")
            appendLine("<li><a href=\"#not-applied\">${notApplied.size} not applied</a></li>")
            appendLine("<li>${countLabel(notApplied.count { it.diagnostic?.severity == "error" }, "error")}</li>")
            appendLine("<li>${countLabel(notApplied.count { it.diagnostic?.severity == "warning" }, "warning")}</li>")
            appendLine("<li><a href=\"#applied\">${countLabel(applied.size, "applied annotation")}</a></li>")
            appendLine("</ul>")

            if (applied.isNotEmpty() || notApplied.isNotEmpty()) {
                appendControls(applied + notApplied)
            }
            appendSection("not-applied", "Not applied", "No diagnostics without an exemption.", notApplied, aggregate)
            appendSection("applied", "Applied annotations", "No Watchdog @Intentionally* annotations found.", applied, aggregate)
            appendLine("</main><footer><a href=\"${WatchdogCheckDescriptions.DOCS_URL.escapeHtml()}\">Library API Watchdog</a> ")
            appendLine("${WatchdogCheckDescriptions.TOOL_VERSION.escapeHtml()}</footer>")
            appendLine("<script>$highlighter</script>")
            appendLine("<script>$reportScript</script>")
            appendLine("</body></html>")
        })
    }

    private class Finding(
        val report: ApiWatchdogProjectReport,
        val location: String,
        val diagnostics: List<String>,
        val annotation: String?,
        val diagnostic: NotAppliedExemptionReportEntry? = null,
    ) {
        val title = diagnostics.joinToString(", ") { diagnosticTitle(it) }
        val line = location.lineNumber()
        val sourcePath = if (line == null) location else location.substringBeforeLast(':')
    }

    private fun diagnosticTitle(name: String): String =
        WatchdogCheckDescriptions.checks[name]?.title ?: if (name == "unknown" || name.isBlank()) "Unknown diagnostic" else name

    private fun StringBuilder.appendControls(findings: List<Finding>) {
        appendLine("<div class=\"report-controls\" hidden>")
        appendLine("<label>View <select id=\"view-mode\"><option value=\"diagnostic\">By diagnostic</option><option value=\"directory\">Directory tree</option></select></label>")
        appendLine("<label>Diagnostic <select id=\"diagnostic-filter\"><option value=\"\">All diagnostics</option>")
        findings.flatMap { it.diagnostics }.distinct().sortedBy { diagnosticTitle(it) }.forEach { diagnostic ->
            appendLine("<option value=\"${diagnostic.filterKey().escapeHtml()}\">${diagnosticTitle(diagnostic).escapeHtml()}</option>")
        }
        appendLine("</select></label><button type=\"button\" id=\"reset-filter\" disabled>Reset filter</button>")
        appendLine("<output id=\"filter-summary\" aria-live=\"polite\"></output></div>")
    }

    private fun StringBuilder.appendSection(id: String, title: String, emptyText: String, findings: List<Finding>, aggregate: Boolean) {
        appendLine("<section class=\"report-section\" data-view=\"diagnostic\" aria-labelledby=\"$id\">")
        appendLine("<h2 id=\"$id\">$title</h2>")
        if (findings.isEmpty()) {
            appendLine("<p class=\"empty\">$emptyText</p></section>")
            return
        }
        appendLine("<div class=\"diagnostic-groups\">")
        findings.groupBy { it.diagnostics to it.annotation }.values.sortedWith(compareBy({ it.first().title }, { it.first().annotation }))
            .forEach { entries ->
                val first = entries.first()
                appendLine("<details class=\"diagnostic-group\" data-diagnostics=\"${first.diagnostics.joinToString(" ") { it.filterKey() }.escapeHtml()}\" data-annotation=\"${first.annotation.orEmpty().escapeHtml()}\" open><summary>")
                appendFindingHeading(first)
                appendLine("<span class=\"group-count\">(${entries.size})</span></summary>")
                appendLine("<ul class=\"findings\">")
                entries.sortedWith(compareBy({ it.report.projectPath }, { it.location })).forEach { finding ->
                    val path = listOf(if (aggregate) finding.report.projectDirectory else "", finding.sourcePath)
                        .filter(String::isNotEmpty).joinToString("/").replace('\\', '/')
                    val line = finding.line ?: 0
                    appendLine("<li class=\"finding\" data-diagnostics=\"${finding.diagnostics.joinToString(" ") { it.filterKey() }.escapeHtml()}\" data-path=\"${path.escapeHtml()}\" data-line=\"$line\">")
                    appendLine("<div class=\"finding-heading\">")
                    appendFindingHeading(finding)
                    appendLine("</div>")
                    appendLocation(finding.report, finding.location, aggregate)
                    val entry = finding.diagnostic
                    if (entry == null) {
                        appendLine("<p class=\"check\"><span class=\"fix-status applied\">Applied</span></p>")
                    } else {
                        val severityClass = if (entry.severity == "warning") "warning" else "error"
                        val statusClass = if (entry.autoFixAvailable) "not-fixed" else "unfixable"
                        val fixStatus = if (entry.autoFixAvailable) "Wasn't auto-fixed" else "Can't be auto-fixed"
                        appendLine("<p class=\"check\"><span class=\"severity $severityClass\">${entry.severity.escapeHtml()}</span> <span class=\"fix-status $statusClass\">$fixStatus</span></p>")
                        DiagnosticMessageHtml.render(entry)?.let { message ->
                            appendLine("<details class=\"diagnostic-message\"><summary>Diagnostic message</summary>$message</details>")
                        }
                        appendLine("<p class=\"reason\">${entry.reason.escapeHtml()}</p>")
                    }
                    appendSource(finding.report, finding.location, entry?.sourceRange, entry?.let { if (it.severity == "warning") "warning" else "error" })
                    appendLine("</li>")
                }
                appendLine("</ul></details>")
            }
        appendLine("</div><div class=\"directory-tree\" hidden></div>")
        appendLine("<p class=\"filter-empty\" hidden>No findings match this diagnostic.</p></section>")
    }

    private fun StringBuilder.appendFindingHeading(finding: Finding) {
        append("<span class=\"group-title\">")
        finding.diagnostics.forEachIndexed { index, diagnostic ->
            if (index > 0) append(", ")
            val description = WatchdogCheckDescriptions.checks[diagnostic]
            if (description == null) {
                append(diagnosticTitle(diagnostic).escapeHtml())
            } else {
                append("<a href=\"${description.docsUrl.escapeHtml()}\">${description.title.escapeHtml()}</a>")
            }
        }
        appendLine("</span>")
        if (finding.annotation == null) {
            appendLine("<span class=\"group-annotation\">No exemption annotation</span>")
        } else {
            appendLine("<code class=\"group-annotation\">@${finding.annotation.escapeHtml()}</code>")
        }
    }

    private fun StringBuilder.appendLocation(report: ApiWatchdogProjectReport, location: String, aggregate: Boolean) {
        if (aggregate) appendLine("<p class=\"project\">Project <code>${report.projectPath.escapeHtml()}</code></p>")
        appendLine("<p class=\"location\"><code>${location.escapeHtml()}</code></p>")
    }

    private fun StringBuilder.appendSource(
        report: ApiWatchdogProjectReport,
        location: String,
        sourceRange: ApiWatchdogSourceRange?,
        severity: String?,
    ) {
        val snippet = report.sourceSnippets[location]?.let { it.copy(code = it.code.replace("\r\n", "\n").replace('\r', '\n')) }
        if (snippet == null) {
            appendLine("<p class=\"source-unavailable\">Source unavailable.</p>")
            return
        }
        val numbered = snippet.startLine > 0
        val line = sourceRange?.startLine ?: location.lineNumber()
        val highlightedLine = line?.takeIf {
            numbered && it >= snippet.startLine && it.toLong() < snippet.startLine.toLong() + snippet.code.lines().size
        }
        append("<pre")
        if (numbered) append(" class=\"line-numbers\" data-start=\"${snippet.startLine}\"")
        if (highlightedLine != null) {
            append(" data-line=\"$highlightedLine\" data-line-offset=\"${snippet.startLine - 1}\"")
        }
        if (severity != null) append(" data-severity=\"$severity\"")
        sourceRange?.takeIf { numbered }?.offsetsIn(snippet)?.let { (start, end) ->
            append(" data-range-start=\"$start\" data-range-end=\"$end\"")
        }
        appendLine("><code class=\"language-kotlin\">${snippet.code.escapeHtml()}</code></pre>")
    }

    private fun ApiWatchdogSourceRange.offsetsIn(snippet: ApiWatchdogSourceSnippet): Pair<Int, Int>? {
        if (startLine < 1 || startColumn < 1 || endLine < startLine || endColumn < 1) return null
        val lines = snippet.code.split('\n')
        fun offsetAt(line: Int, column: Int): Int? {
            val index = line - snippet.startLine
            if (index < 0) return 0
            if (index >= lines.size) return snippet.code.length
            if (column > lines[index].length + 1) return null
            return lines.take(index).sumOf { it.length + 1 } + column - 1
        }
        val start = offsetAt(startLine, startColumn) ?: return null
        val end = offsetAt(endLine, endColumn) ?: return null
        return if (end > start) start to end else null
    }

    private fun countLabel(count: Int, singular: String): String =
        "$count $singular${if (count == 1) "" else "s"}"

    private fun String.lineNumber(): Int? = substringAfterLast(':', "").toIntOrNull()?.takeIf { it > 0 }

    private fun String.filterKey(): String =
        if (isEmpty()) "~" else URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")

    private fun resource(name: String): String =
        checkNotNull(ApiWatchdogHtmlReport::class.java.getResourceAsStream(name)).bufferedReader(Charsets.UTF_8).use { it.readText() }

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
