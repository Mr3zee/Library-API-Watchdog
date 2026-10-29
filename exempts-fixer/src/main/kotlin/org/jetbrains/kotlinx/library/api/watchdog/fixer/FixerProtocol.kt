package org.jetbrains.kotlinx.library.api.watchdog.fixer

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A recorded compiler diagnostic. The first four fields identify the source element. Fields five
 * and six hold the reported range. The remaining fields hold message parameters with escaped
 * tabs, newlines, and backslashes.
 */
internal data class RecordedDiagnostic(
    val name: String,
    val filePath: String,
    val startOffset: Int,
    val endOffset: Int,
    val messageParameters: List<String> = emptyList(),
    val reportedStartOffset: Int? = null,
    val reportedEndOffset: Int? = null,
) {
    companion object {
        /** A multiplatform build reports a common source file for each target, so the result has no duplicates. */
        fun parseReports(reportFiles: List<Path>): List<RecordedDiagnostic> = reportFiles
            .asSequence()
            .filter { Files.isRegularFile(it) }
            .flatMap { Files.readAllLines(it) }
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split('\t')
                require(parts.size >= 4) { "Malformed diagnostics report line: '$line'" }
                RecordedDiagnostic(
                    parts[0], parts[1], parts[2].toInt(), parts[3].toInt(),
                    parts.drop(6).map { it.unescapeField() },
                    parts.getOrNull(4)?.toInt(), parts.getOrNull(5)?.toInt(),
                )
            }
            .distinctBy { it.copy(messageParameters = emptyList()) }
            .toList()
    }
}

/** The request of the Gradle task: a file of `key=value` lines. A list key repeats. */
internal class FixerRequest(
    val reportFiles: List<Path>,
    /** The files to scan for exemptions. */
    val sourceFiles: List<Path>,
    val diagnosticSeverities: Map<String, String>,
    val updateSources: Boolean,
    val responseFile: Path,
) {
    /** A redundant exemption is a warning, and the fixer removes it anyway. */
    fun fixes(diagnostic: String): Boolean =
        diagnostic == WatchdogChecks.REDUNDANT_EXEMPTION.name ||
                (diagnosticSeverities[diagnostic] ?: ERROR_SEVERITY) == ERROR_SEVERITY

    companion object {
        const val REPORT_FILE = "reportFile"
        const val SOURCE_FILE = "sourceFile"
        const val DIAGNOSTIC_SEVERITY = "diagnosticSeverity"
        const val UPDATE_SOURCES = "updateSources"
        const val RESPONSE_FILE = "responseFile"
        const val ERROR_SEVERITY = "error"

        fun parse(file: Path): FixerRequest {
            val values = mutableMapOf<String, MutableList<String>>()
            Files.readAllLines(file).forEach { line ->
                if (line.isBlank()) return@forEach
                val key = line.substringBefore('=')
                require(key != line) { "Malformed fixer request line: '$line'" }
                values.getOrPut(key) { mutableListOf() }.add(line.substring(key.length + 1))
            }

            fun single(key: String): String = requireNotNull(values[key]?.singleOrNull()) {
                "Expected exactly one '$key' entry in the fixer request"
            }

            return FixerRequest(
                reportFiles = values[REPORT_FILE].orEmpty().map(Paths::get),
                sourceFiles = values[SOURCE_FILE].orEmpty().map(Paths::get),
                diagnosticSeverities = values[DIAGNOSTIC_SEVERITY].orEmpty().associate { entry ->
                    val separator = entry.lastIndexOf(':')
                    require(separator > 0 && separator < entry.lastIndex) {
                        "Malformed '$DIAGNOSTIC_SEVERITY' entry in the fixer request: '$entry'"
                    }
                    entry.substring(0, separator) to entry.substring(separator + 1)
                },
                updateSources = single(UPDATE_SOURCES).toBooleanStrict(),
                responseFile = Paths.get(single(RESPONSE_FILE)),
            )
        }
    }
}

internal data class AppliedFix(
    val diagnostic: String,
    /** Null for a placeholder KDoc. */
    val annotation: String?,
    val filePath: String,
    val line: Int,
    val messageParameters: List<String> = emptyList(),
    val sourceRange: DiagnosticSourceRange? = null,
)

internal data class SkippedDiagnostic(
    val diagnostic: String,
    val filePath: String,
    val line: Int,
    val reason: String,
    val messageParameters: List<String> = emptyList(),
    val sourceRange: DiagnosticSourceRange? = null,
)

/** The fixer leaves them untouched. */
internal data class WarningDiagnostic(
    val diagnostic: String,
    val filePath: String,
    val line: Int,
    val messageParameters: List<String> = emptyList(),
    val sourceRange: DiagnosticSourceRange? = null,
)

internal data class DiagnosticSourceRange(
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
)

internal data class AppliedExemption(
    val annotation: String,
    val filePath: String,
    val line: Int,
)

/** The response to the Gradle task: a file of `key=value` lines with tab-separated fields. */
internal class FixerResponse {
    val applied = mutableListOf<AppliedFix>()
    val skipped = mutableListOf<SkippedDiagnostic>()
    val warnings = mutableListOf<WarningDiagnostic>()
    val exemptions = mutableListOf<AppliedExemption>()
    val modifiedFiles = mutableListOf<String>()
    var error: String? = null

    fun writeTo(file: Path) {
        file.parent?.let { Files.createDirectories(it) }
        Files.writeString(file, buildString {
            applied.forEach {
                appendDiagnostic(
                    FIXED, listOf(it.diagnostic, it.annotation.orEmpty(), it.line.toString(), it.filePath), it.messageParameters, it.sourceRange,
                )
            }
            skipped.forEach {
                val annotation = ExemptionRegistry.annotationFor(it.diagnostic, it.messageParameters).orEmpty()
                appendDiagnostic(
                    SKIPPED, listOf(it.diagnostic, annotation, it.line.toString(), it.filePath, it.reason), it.messageParameters, it.sourceRange,
                )
            }
            warnings.forEach {
                val annotation = ExemptionRegistry.annotationFor(it.diagnostic, it.messageParameters).orEmpty()
                appendDiagnostic(
                    WARNING, listOf(it.diagnostic, annotation, it.line.toString(), it.filePath), it.messageParameters, it.sourceRange,
                )
            }
            exemptions.forEach {
                appendLine("$EXEMPTION=${it.annotation}\t${it.line}\t${it.filePath}")
            }
            modifiedFiles.forEach { appendLine("$MODIFIED_FILE=$it") }
            error?.let { appendLine("$ERROR=${it.escapeNewlines()}") }
        })
    }

    private fun StringBuilder.appendDiagnostic(
        kind: String,
        fields: List<String>,
        parameters: List<String>,
        sourceRange: DiagnosticSourceRange?,
    ) {
        append(kind).append('=')
        val range = listOf(sourceRange?.startLine, sourceRange?.startColumn, sourceRange?.endLine, sourceRange?.endColumn)
        appendLine((fields + range.map { it?.toString().orEmpty() } + parameters).joinToString("\t") { it.escapeField() })
    }

    private fun String.escapeNewlines() =
        replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun String.escapeField() = escapeNewlines().replace("\t", "\\t")

    companion object {
        const val FIXED = "fixed"
        const val SKIPPED = "skipped"
        const val WARNING = "warning"
        const val EXEMPTION = "exemption"
        const val MODIFIED_FILE = "modifiedFile"
        const val ERROR = "error"
    }
}

private fun String.unescapeField(): String = buildString {
    var index = 0
    while (index < this@unescapeField.length) {
        val character = this@unescapeField[index++]
        if (character == '\\' && index < this@unescapeField.length) {
            append(
                when (val escaped = this@unescapeField[index++]) {
                    't' -> '\t'
                    'n' -> '\n'
                    'r' -> '\r'
                    else -> escaped
                }
            )
        } else {
            append(character)
        }
    }
}
