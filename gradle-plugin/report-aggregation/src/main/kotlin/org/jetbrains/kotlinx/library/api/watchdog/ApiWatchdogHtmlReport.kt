package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import java.net.URLEncoder
import java.util.Base64
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

@InternalWatchdogApi
public object ApiWatchdogHtmlReport {
    private val stylesheet by lazy { resource("report.css") }
    private val highlighter by lazy { resource("prism-kotlin.js") }
    private val reportScript by lazy { resource("report.js") }
    private val favicon by lazy { Base64.getEncoder().encodeToString(resource("favicon.svg").toByteArray(Charsets.UTF_8)) }

    /** @param ideLinks how the **Open in IDE** buttons open a file. Null writes no buttons. */
    public fun write(
        reports: List<ApiWatchdogProjectReport>,
        output: File,
        aggregate: Boolean,
        ideLinks: ApiWatchdogIdeLinks? = null,
    ) {
        val sortedReports = reports.sortedBy { it.projectPath }
        val applied = sortedReports.flatMap { report ->
            val redundant = report.notApplied
                .filter { it.diagnostic == REDUNDANT_EXEMPTION && it.annotation != null }
                .mapTo(hashSetOf()) { it.location to it.annotation }
            report.applied.map { entry ->
                val diagnostics = WatchdogCheckDescriptions.diagnosticsByAnnotation[entry.annotation] ?: listOf("unknown")
                if (entry.location to entry.annotation in redundant) {
                    Finding(report, entry.location, diagnostics + REDUNDANT_EXEMPTION, entry.annotation, redundant = true)
                } else {
                    Finding(report, entry.location, diagnostics, entry.annotation)
                }
            }
        }
        val redundantCount = applied.count { it.redundant }
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
            appendLine("<link rel=\"icon\" type=\"image/svg+xml\" href=\"data:image/svg+xml;base64,$favicon\">")
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
            if (redundantCount > 0) appendLine("<li>${countLabel(redundantCount, "redundant annotation")}</li>")
            appendLine("</ul>")

            if (applied.isNotEmpty() || notApplied.isNotEmpty()) {
                appendControls(applied + notApplied, if (aggregate) sortedReports.map { it.projectPath } else emptyList(), ideLinks)
            }
            appendSection("not-applied", "Not applied", "No diagnostics without an exemption.", notApplied, aggregate, ideLinks)
            appendSection("applied", "Applied annotations", "No Watchdog @Intentionally* annotations found.", applied, aggregate, ideLinks)
            appendLine("</main><footer><a href=\"${WatchdogCheckDescriptions.DOCS_URL.escapeHtml()}\">Library API Watchdog</a> ")
            appendLine("${WatchdogCheckDescriptions.TOOL_VERSION.escapeHtml()}</footer>")
            // The report script highlights a snippet when it scrolls into view, so Prism must not highlight on load.
            appendLine("<script>window.Prism = {manual: true};\n$highlighter</script>")
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
        val redundant: Boolean = false,
    ) {
        val title = diagnostics.joinToString(", ") { diagnosticTitle(it) }
        val line = location.lineNumber()
        val sourcePath = if (line == null) location else location.substringBeforeLast(':')
    }

    private fun diagnosticTitle(name: String): String =
        WatchdogCheckDescriptions.checks[name]?.title ?: if (name == "unknown" || name.isBlank()) "Unknown diagnostic" else name

    /** @param projects the project paths of an aggregate report. The project filter needs two or more. */
    /** @param projects the project paths of an aggregate report. The project filter needs two or more. */
    private fun StringBuilder.appendControls(findings: List<Finding>, projects: List<String>, ideLinks: ApiWatchdogIdeLinks?) {
        append("<div class=\"report-controls\"")
        if (ideLinks != null) {
            append(" data-ide-project=\"${ideLinks.project.name.escapeHtml()}\" data-ide-root=\"${ideLinks.project.rootDirectory.escapeHtml()}\"")
            // `idea-url` is an `idea://open` link. Any other target is a JetBrains Toolbox tag.
            val target = if (ideLinks.toolbox) ideLinks.tags.singleOrNull() else "idea-url"
            if (target != null) append(" data-ide-target=\"${target.escapeHtml()}\"")
        }
        appendLine(" hidden>")
        appendLine("<label>View <select id=\"view-mode\"><option value=\"diagnostic\">By diagnostic</option><option value=\"directory\">Directory tree</option></select></label>")
        appendLine("<label>Diagnostic <select id=\"diagnostic-filter\"><option value=\"\">All diagnostics</option>")
        findings.flatMap { it.diagnostics }.distinct().sortedBy { diagnosticTitle(it) }.forEach { diagnostic ->
            appendLine("<option value=\"${diagnostic.filterKey().escapeHtml()}\">${diagnosticTitle(diagnostic).escapeHtml()}</option>")
        }
        appendLine("</select></label>")
        if (projects.size > 1) {
            appendLine("<label>Project <select id=\"project-filter\"><option value=\"\">All projects</option>")
            projects.forEach { project -> appendLine("<option value=\"${project.escapeHtml()}\">${project.escapeHtml()}</option>") }
            appendLine("</select></label>")
        }
        appendLine("<button type=\"button\" id=\"reset-filter\" disabled>Reset filter</button>")
        appendLine("<output id=\"filter-summary\" aria-live=\"polite\"></output>")
        if (ideLinks != null && ideLinks.toolbox && ideLinks.tags.size > 1) {
            append("<label class=\"ide-target\">Open in <select id=\"ide-target\">")
            ideLinks.tags.forEach { tag ->
                append("<option value=\"${tag.escapeHtml()}\">${(ApiWatchdogIdeLinks.SUPPORTED_TAGS[tag] ?: tag).escapeHtml()}</option>")
            }
            appendLine("</select></label>")
        }
        appendLine("</div>")
    }

    private fun StringBuilder.appendSection(
        id: String,
        title: String,
        emptyText: String,
        findings: List<Finding>,
        aggregate: Boolean,
        ideLinks: ApiWatchdogIdeLinks?,
    ) {
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
                    val project = if (aggregate) " data-project=\"${finding.report.projectPath.escapeHtml()}\"" else ""
                    appendLine("<li class=\"finding\" data-diagnostics=\"${finding.diagnostics.joinToString(" ") { it.filterKey() }.escapeHtml()}\" data-path=\"${path.escapeHtml()}\" data-line=\"$line\"$project>")
                    appendLine("<div class=\"finding-heading\">")
                    appendFindingHeading(finding)
                    appendLine("</div>")
                    appendLocation(finding, aggregate, ideLinks)
                    val entry = finding.diagnostic
                    if (entry == null) {
                        val (statusClass, statusText) = if (finding.redundant) "redundant" to "Redundant" else "applied" to "Applied"
                        appendLine("<p class=\"check\"><span class=\"fix-status $statusClass\">$statusText</span></p>")
                    } else {
                        val severityClass = if (entry.severity == "warning") "warning" else "error"
                        val statusClass = if (entry.autoFixAvailable) "not-fixed" else "unfixable"
                        val fixStatus = if (entry.autoFixAvailable) "Wasn't auto-fixed" else "Can't be auto-fixed"
                        appendLine("<p class=\"check\"><span class=\"severity $severityClass\">${entry.severity.escapeHtml()}</span> <span class=\"fix-status $statusClass\">$fixStatus</span></p>")
                        DiagnosticMessageHtml.render(entry)?.let { message ->
                            appendLine("<details class=\"diagnostic-message\"><summary>Diagnostic message</summary>$message</details>")
                        }
                        if (entry.reason.isNotEmpty()) appendLine("<p class=\"reason\">${entry.reason.escapeHtml()}</p>")
                    }
                    appendSource(finding.report, finding.location, entry?.sourceRange, entry?.let { if (it.severity == "warning") "warning" else "error" })
                    appendLine("</li>")
                }
                appendLine("</ul></details>")
            }
        appendLine("</div><div class=\"directory-tree\" hidden></div>")
        appendLine("<p class=\"filter-empty\" hidden>No findings match the filters.</p></section>")
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

    private fun StringBuilder.appendLocation(finding: Finding, aggregate: Boolean, ideLinks: ApiWatchdogIdeLinks?) {
        if (aggregate) appendLine("<p class=\"project\">Project <code>${finding.report.projectPath.escapeHtml()}</code></p>")
        append("<p class=\"location\"><code>${finding.location.escapeHtml()}</code>")
        val path = if (ideLinks == null) null else idePath(finding)
        if (path != null) {
            // The report script sets the link for the IDE that the reader selects.
            val range = finding.diagnostic?.sourceRange?.takeIf { it.startLine >= 1 && it.startColumn >= 1 }
            val line = range?.startLine ?: finding.line ?: 0
            append(" <a class=\"open-in-ide\" data-ide-path=\"${path.escapeHtml()}\" data-ide-line=\"$line\" ")
            append("data-ide-column=\"${range?.startColumn ?: 0}\" hidden>Open in IDE</a>")
        }
        appendLine("</p>")
    }

    /**
     * The source path relative to the build root, or null when the IDE can't open it. The `navigate` command of the
     * IDE reads a colon as the start of the line number.
     */
    private fun idePath(finding: Finding): String? {
        if (finding.sourcePath.isEmpty()) return null
        val path = normalizePath(
            listOf(finding.report.projectDirectory, finding.sourcePath).filter(String::isNotEmpty).joinToString("/").replace('\\', '/'),
        )
        return path.takeUnless { ':' in it }
    }

    /** Removes the empty, `.`, and resolvable `..` segments of [path]. A leading `/` stays. */
    private fun normalizePath(path: String): String {
        val segments = ArrayDeque<String>()
        for (segment in path.split('/')) {
            when {
                segment.isEmpty() || segment == "." -> {}
                segment == ".." && segments.isNotEmpty() && segments.last() != ".." -> segments.removeLast()
                else -> segments.addLast(segment)
            }
        }
        return (if (path.startsWith('/')) "/" else "") + segments.joinToString("/")
    }

    /**
     * The IDE project of the build in [rootDirectory]. The name is the text of `.idea/.name`, or the directory name when
     * the file is missing or empty.
     */
    public fun ideProject(providers: ProviderFactory, rootDirectory: Directory): Provider<ApiWatchdogIdeProject> =
        providers.fileContents(rootDirectory.file(".idea/.name")).asText
            .map { text -> text.trim().ifEmpty { null } }
            .orElse(rootDirectory.asFile.name)
            .map { name -> ApiWatchdogIdeProject(name, rootDirectory.asFile.absoluteFile.invariantSeparatorsPath) }

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

    private const val REDUNDANT_EXEMPTION = "REDUNDANT_EXEMPTION"

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
