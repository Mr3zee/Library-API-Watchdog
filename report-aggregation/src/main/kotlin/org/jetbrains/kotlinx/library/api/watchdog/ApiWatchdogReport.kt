package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File

@InternalWatchdogApi
public const val API_WATCHDOG_REPORT_USAGE: String =
    "library-api-watchdog-report"

@InternalWatchdogApi
public const val API_WATCHDOG_REPORT_ELEMENTS_CONFIGURATION: String =
    "apiWatchdogReportElements"

@InternalWatchdogApi
public const val API_WATCHDOG_REPORTS_CONFIGURATION: String =
    "apiWatchdogReports"

/** An `@Intentionally*` annotation in the sources. [location] is `path:line`, relative to the project directory. */
@InternalWatchdogApi
public data class AppliedExemptionReportEntry(
    public val annotation: String,
    public val location: String,
)

/**
 * A diagnostic without an exemption.
 *
 * @property annotation the annotation that exempts the declaration, or null when none exists
 * @property location `path:line`, relative to the project directory
 * @property severity `error` or `warning`
 * @property messageParameters the compiler's rendered arguments for the diagnostic message template
 * @property autoFixAvailable whether `exemptExistingApi` can apply a fix at this location
 */
@InternalWatchdogApi
public data class NotAppliedExemptionReportEntry(
    public val annotation: String?,
    public val diagnostic: String,
    public val location: String,
    public val reason: String,
    public val severity: String,
    public val messageParameters: List<String> = emptyList(),
    public val sourceRange: ApiWatchdogSourceRange? = null,
    public val autoFixAvailable: Boolean = false,
)

/** One-based source positions with an exclusive end. */
@InternalWatchdogApi
public data class ApiWatchdogSourceRange(
    public val startLine: Int,
    public val startColumn: Int,
    public val endLine: Int,
    public val endColumn: Int,
)

@InternalWatchdogApi
public data class ApiWatchdogSourceSnippet(
    public val startLine: Int,
    public val code: String,
)

/**
 * The report of one project.
 *
 * @property projectDirectory the project directory relative to the root directory of the build,
 *   with `/` separators. Empty for the root project.
 * @property sourceSnippets source context by `path:line`
 */
@InternalWatchdogApi
public data class ApiWatchdogProjectReport(
    public val projectPath: String,
    public val projectDirectory: String,
    public val applied: List<AppliedExemptionReportEntry>,
    public val notApplied: List<NotAppliedExemptionReportEntry>,
    public val sourceSnippets: Map<String, ApiWatchdogSourceSnippet> = emptyMap(),
)

@InternalWatchdogApi
public object ApiWatchdogReportData {
    private const val VERSION = "6"

    public fun write(report: ApiWatchdogProjectReport, file: File) {
        file.parentFile.mkdirs()
        file.writeText(buildString {
            appendLine("version\t$VERSION")
            appendLine("project\t${report.projectPath.escapeField()}")
            appendLine("directory\t${report.projectDirectory.escapeField()}")
            report.applied.forEach { entry ->
                appendLine("applied\t${entry.annotation.escapeField()}\t${entry.location.escapeField()}")
            }
            report.notApplied.forEach { entry ->
                appendLine(
                    (listOf(
                        "notApplied",
                        entry.annotation.orEmpty(),
                        entry.diagnostic,
                        entry.location,
                        entry.reason,
                        entry.severity,
                        entry.sourceRange?.startLine?.toString().orEmpty(),
                        entry.sourceRange?.startColumn?.toString().orEmpty(),
                        entry.sourceRange?.endLine?.toString().orEmpty(),
                        entry.sourceRange?.endColumn?.toString().orEmpty(),
                        entry.autoFixAvailable.toString(),
                    ) + entry.messageParameters).joinToString("\t") { it.escapeField() }
                )
            }
            report.sourceSnippets.toSortedMap().forEach { (location, snippet) ->
                appendLine(
                    listOf("source", location, snippet.startLine.toString(), snippet.code)
                        .joinToString("\t") { it.escapeField() }
                )
            }
        })
    }

    public fun read(file: File): ApiWatchdogProjectReport {
        var projectPath: String? = null
        var version: String? = null
        var projectDirectory: String? = null
        val applied = mutableListOf<AppliedExemptionReportEntry>()
        val notApplied = mutableListOf<NotAppliedExemptionReportEntry>()
        val sourceSnippets = mutableMapOf<String, ApiWatchdogSourceSnippet>()
        file.readLines().filter(String::isNotBlank).forEachIndexed { index, line ->
            val fields = line.split('\t').map { it.unescapeField() }
            when (fields.first()) {
                "version" -> {
                    require(fields.size == 2 && fields[1] in setOf("2", "3", "4", "5", VERSION)) {
                        "Unsupported API Watchdog report version in $file"
                    }
                    version = fields[1]
                }
                "project" -> {
                    require(fields.size == 2) { "Malformed project entry in $file:${index + 1}" }
                    projectPath = fields[1]
                }
                "directory" -> {
                    require(fields.size == 2) { "Malformed directory entry in $file:${index + 1}" }
                    projectDirectory = fields[1]
                }
                "applied" -> {
                    require(fields.size == 3) { "Malformed applied entry in $file:${index + 1}" }
                    applied += AppliedExemptionReportEntry(fields[1], fields[2])
                }
                "notApplied" -> {
                    val parameterStart = when (version) {
                        VERSION -> 11
                        "5" -> 10
                        else -> 6
                    }
                    require(fields.size >= parameterStart) { "Malformed not-applied entry in $file:${index + 1}" }
                    notApplied += NotAppliedExemptionReportEntry(
                        annotation = fields[1].ifEmpty { null },
                        diagnostic = fields[2],
                        location = fields[3],
                        reason = fields[4],
                        severity = fields[5],
                        messageParameters = fields.drop(parameterStart),
                        sourceRange = if (parameterStart >= 10 && fields[6].isNotEmpty()) {
                            ApiWatchdogSourceRange(fields[6].toInt(), fields[7].toInt(), fields[8].toInt(), fields[9].toInt())
                        } else null,
                        autoFixAvailable = if (version == VERSION) fields[10].toBooleanStrict() else {
                            fields[1].isNotEmpty() || fields[2] == "UNDOCUMENTED_PUBLIC_API"
                        },
                    )
                }
                "source" -> {
                    require(fields.size == 4) { "Malformed source entry in $file:${index + 1}" }
                    val startLine = fields[2].toIntOrNull()
                    require(startLine != null && startLine > 0) { "Invalid source line in $file:${index + 1}" }
                    sourceSnippets[fields[1]] = ApiWatchdogSourceSnippet(startLine, fields[3])
                }
                else -> error("Unknown API Watchdog report entry in $file:${index + 1}")
            }
        }
        return ApiWatchdogProjectReport(
            projectPath = requireNotNull(projectPath) { "No project entry in $file" },
            projectDirectory = requireNotNull(projectDirectory) { "No directory entry in $file" },
            applied = applied,
            notApplied = notApplied,
            sourceSnippets = sourceSnippets,
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
