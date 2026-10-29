package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import java.net.URI

/**
 * Writes the diagnostics without an exemption as a
 * [SARIF 2.1.0](https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html) log. A location
 * is relative to the root directory of the build, so a code scanning service resolves it against
 * the repository checkout.
 */
@InternalWatchdogApi
public object ApiWatchdogSarifReport {
    public fun write(reports: List<ApiWatchdogProjectReport>, output: File) {
        val entries = reports.sortedBy { it.projectPath }
            .flatMap { report -> report.notApplied.map { report to it } }
            .sortedWith(compareBy({ it.first.projectPath }, { it.second.location }, { it.second.diagnostic }))
        val ruleIds = entries.map { it.second.diagnostic }.distinct().sorted()
        val rules = ruleIds.map { id ->
            val description = WatchdogCheckDescriptions.checks[id]
            linkedMapOf<String, Any?>(
                "id" to id,
                "shortDescription" to mapOf("text" to (description?.title ?: id)),
                "helpUri" to description?.docsUrl,
            )
        }
        val results = entries.map { (report, entry) ->
            val (path, line) = entry.location.splitLocation()
            val uri = listOf(report.projectDirectory, path).filter(String::isNotEmpty).joinToString("/")
            linkedMapOf<String, Any?>(
                "ruleId" to entry.diagnostic,
                "ruleIndex" to ruleIds.indexOf(entry.diagnostic),
                "level" to entry.severity,
                "message" to mapOf("text" to entry.message()),
                "locations" to listOf(
                    mapOf(
                        "physicalLocation" to linkedMapOf(
                            "artifactLocation" to linkedMapOf(
                                "uri" to uri.toUriPath(),
                                "uriBaseId" to SOURCE_ROOT,
                            ),
                            "region" to line?.let { mapOf("startLine" to it) },
                        ),
                    ),
                ),
            )
        }
        val log = linkedMapOf(
            "\$schema" to SCHEMA,
            "version" to "2.1.0",
            "runs" to listOf(
                linkedMapOf(
                    "tool" to mapOf(
                        "driver" to linkedMapOf(
                            "name" to TOOL_NAME,
                            "version" to WatchdogCheckDescriptions.TOOL_VERSION,
                            "informationUri" to WatchdogCheckDescriptions.DOCS_URL,
                            "rules" to rules,
                        ),
                    ),
                    "results" to results,
                ),
            ),
        )
        output.parentFile.mkdirs()
        output.writeText(buildString { appendJson(log, indent = 0); appendLine() })
    }

    private const val SCHEMA =
        "https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json"
    private const val TOOL_NAME = "Library API Watchdog"
    private const val SOURCE_ROOT = "%SRCROOT%"

    private fun NotAppliedExemptionReportEntry.message(): String = listOfNotNull(
        (WatchdogCheckDescriptions.checks[diagnostic]?.title ?: diagnostic) + ".",
        annotation?.let { "Exemption: @$it." },
        reason.ifEmpty { null },
    ).joinToString(" ")

    private fun String.splitLocation(): Pair<String, Int?> {
        val line = substringAfterLast(':', "").toIntOrNull()
        return if (line == null) this to null else substringBeforeLast(':') to line
    }

    private fun String.toUriPath(): String = URI(null, null, replace(File.separatorChar, '/'), null).rawPath

    private fun StringBuilder.appendJson(value: Any?, indent: Int) {
        when (value) {
            null -> append("null")
            is String -> appendJsonString(value)
            is Number, is Boolean -> append(value.toString())
            is Map<*, *> -> appendJsonObject(value, indent)
            is List<*> -> appendJsonArray(value, indent)
            else -> error("Unsupported JSON value: $value")
        }
    }

    /** The writer skips a null entry, so an optional field is a nullable value. */
    private fun StringBuilder.appendJsonObject(value: Map<*, *>, indent: Int) {
        val entries = value.entries.filter { it.value != null }
        if (entries.isEmpty()) {
            append("{}")
            return
        }
        append("{\n")
        entries.forEachIndexed { index, (key, entryValue) ->
            appendIndent(indent + 1)
            appendJsonString(key as String)
            append(": ")
            appendJson(entryValue, indent + 1)
            if (index < entries.lastIndex) append(',')
            append('\n')
        }
        appendIndent(indent)
        append('}')
    }

    private fun StringBuilder.appendJsonArray(value: List<*>, indent: Int) {
        if (value.isEmpty()) {
            append("[]")
            return
        }
        append("[\n")
        value.forEachIndexed { index, item ->
            appendIndent(indent + 1)
            appendJson(item, indent + 1)
            if (index < value.lastIndex) append(',')
            append('\n')
        }
        appendIndent(indent)
        append(']')
    }

    private fun StringBuilder.appendIndent(indent: Int) {
        repeat(indent) { append("  ") }
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        for (character in value) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }
}
