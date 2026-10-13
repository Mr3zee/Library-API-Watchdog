package org.jetbrains.kotlinx.library.api.watchdog.fixer

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * One line of the `diagnosticsOutputFile` of the compiler plugin: the diagnostic name, the
 * absolute file path, the start offset, and the end offset, separated by tabs.
 */
internal data class RecordedDiagnostic(
    val name: String,
    val filePath: String,
    val startOffset: Int,
    val endOffset: Int,
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
                require(parts.size == 4) { "Malformed diagnostics report line: '$line'" }
                RecordedDiagnostic(parts[0], parts[1], parts[2].toInt(), parts[3].toInt())
            }
            .distinct()
            .toList()
    }
}

/** The request of the Gradle task: a file of `key=value` lines. A list key repeats. */
internal class FixerRequest(
    val reportFiles: List<Path>,
    /** The files to scan for exemptions. */
    val sourceFiles: List<Path>,
    val updateSources: Boolean,
    val responseFile: Path,
) {
    companion object {
        const val REPORT_FILE = "reportFile"
        const val SOURCE_FILE = "sourceFile"
        const val UPDATE_SOURCES = "updateSources"
        const val RESPONSE_FILE = "responseFile"

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
)

internal data class SkippedDiagnostic(
    val diagnostic: String,
    val filePath: String,
    val line: Int,
    val reason: String,
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
    val exemptions = mutableListOf<AppliedExemption>()
    val modifiedFiles = mutableListOf<String>()
    var error: String? = null

    fun writeTo(file: Path) {
        file.parent?.let { Files.createDirectories(it) }
        Files.writeString(file, buildString {
            applied.forEach {
                appendLine("$FIXED=${it.diagnostic}\t${it.annotation.orEmpty()}\t${it.line}\t${it.filePath}")
            }
            skipped.forEach {
                // The reason is free text, so the writer escapes its line breaks. Tabs are fine in a last field.
                val annotation = ExemptionRegistry.annotationFor(it.diagnostic).orEmpty()
                appendLine(
                    "$SKIPPED=${it.diagnostic}\t$annotation\t${it.line}\t${it.filePath}\t${it.reason.escapeNewlines()}"
                )
            }
            exemptions.forEach {
                appendLine("$EXEMPTION=${it.annotation}\t${it.line}\t${it.filePath}")
            }
            modifiedFiles.forEach { appendLine("$MODIFIED_FILE=$it") }
            error?.let { appendLine("$ERROR=${it.escapeNewlines()}") }
        })
    }

    private fun String.escapeNewlines() =
        replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    companion object {
        const val FIXED = "fixed"
        const val SKIPPED = "skipped"
        const val EXEMPTION = "exemption"
        const val MODIFIED_FILE = "modifiedFile"
        const val ERROR = "error"
    }
}
