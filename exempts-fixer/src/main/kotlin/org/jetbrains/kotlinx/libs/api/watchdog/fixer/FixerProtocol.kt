package org.jetbrains.kotlinx.libs.api.watchdog.fixer

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A watchdog diagnostic recorded by the compiler plugin's `diagnosticsOutputFile` option: one
 * tab-separated line of diagnostic name, absolute source file path, start offset, and end offset
 * of the source element the diagnostic was reported on.
 */
internal data class RecordedDiagnostic(
    val name: String,
    val filePath: String,
    val startOffset: Int,
    val endOffset: Int,
) {
    companion object {
        /**
         * Merges the compilation reports and drops exact duplicates: multiplatform compilations
         * revisit common source files for metadata and each platform target.
         */
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

/**
 * The request the Gradle task hands to the fixer process as a file of `key=value` lines, one
 * value per line, keys repeating for list entries. A file sidesteps command-line length limits
 * and quoting rules.
 */
internal class FixerRequest(
    /** Reports produced by the regular Kotlin compile tasks for every main target. */
    val reportFiles: List<Path>,
    /** Where the fixer writes its [FixerResponse]. */
    val responseFile: Path,
) {
    companion object {
        const val REPORT_FILE = "reportFile"
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
                responseFile = Paths.get(single(RESPONSE_FILE)),
            )
        }
    }
}

/** An exemption annotation the fixer added, located in the rewritten source file. */
internal data class AppliedFix(
    val diagnostic: String,
    val annotation: String,
    val filePath: String,
    val line: Int,
)

/** A diagnostic the fixer could not resolve, located after any other fixes to its source file. */
internal data class SkippedDiagnostic(
    val diagnostic: String,
    val filePath: String,
    val line: Int,
    val reason: String,
)

/**
 * What the fixer reports back, as a file of `key=value` lines mirroring [FixerRequest]. Tabs
 * separate the fields inside a value.
 */
internal class FixerResponse {
    val applied = mutableListOf<AppliedFix>()
    val skipped = mutableListOf<SkippedDiagnostic>()
    val modifiedFiles = mutableListOf<String>()
    var error: String? = null

    fun writeTo(file: Path) {
        file.parent?.let { Files.createDirectories(it) }
        Files.writeString(file, buildString {
            applied.forEach {
                appendLine("$FIXED=${it.diagnostic}\t${it.annotation}\t${it.line}\t${it.filePath}")
            }
            skipped.forEach {
                // The reason is free-form text and the line's last field: escaping its line
                // breaks keeps the line-based protocol intact (tabs are fine in a last field).
                appendLine("$SKIPPED=${it.diagnostic}\t${it.line}\t${it.filePath}\t${it.reason.escapeNewlines()}")
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
        const val MODIFIED_FILE = "modifiedFile"
        const val ERROR = "error"
    }
}
