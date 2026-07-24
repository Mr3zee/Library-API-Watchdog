package org.jetbrains.kotlinx.libs.api.watchdog.fixer

import java.io.File

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
        fun parseReports(reportFiles: List<File>): List<RecordedDiagnostic> = reportFiles
            .filter(File::isFile)
            .flatMap(File::readLines)
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split('\t')
                require(parts.size == 4) { "Malformed diagnostics report line: '$line'" }
                RecordedDiagnostic(parts[0], parts[1], parts[2].toInt(), parts[3].toInt())
            }
            .distinct()
    }
}

/**
 * The request the Gradle task hands to the fixer process as a file of `key=value` lines, one
 * value per line, keys repeating for list entries. A file sidesteps command-line length limits
 * and quoting rules.
 */
internal class FixerRequest(
    /** Reports produced by the regular Kotlin compile tasks for every main target. */
    val reportFiles: List<File>,
    /** Where the fixer writes its [FixerResponse]. */
    val responseFile: File,
) {
    companion object {
        const val REPORT_FILE = "reportFile"
        const val RESPONSE_FILE = "responseFile"

        fun parse(file: File): FixerRequest {
            val values = mutableMapOf<String, MutableList<String>>()
            file.readLines().forEach { line ->
                if (line.isBlank()) return@forEach
                val key = line.substringBefore('=')
                require(key != line) { "Malformed fixer request line: '$line'" }
                values.getOrPut(key) { mutableListOf() }.add(line.substring(key.length + 1))
            }

            fun single(key: String): String = requireNotNull(values[key]?.singleOrNull()) {
                "Expected exactly one '$key' entry in the fixer request"
            }

            return FixerRequest(
                reportFiles = values[REPORT_FILE].orEmpty().map(::File),
                responseFile = File(single(RESPONSE_FILE)),
            )
        }
    }
}

/** An exemption annotation the fixer added to a source file. */
internal data class AppliedFix(
    val diagnostic: String,
    val annotation: String,
    val filePath: String,
    val line: Int,
)

/** A recorded diagnostic the fixer could not resolve into an annotation insertion. */
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

    fun writeTo(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(buildString {
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

    private fun String.escapeNewlines() = replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")

    companion object {
        const val FIXED = "fixed"
        const val SKIPPED = "skipped"
        const val MODIFIED_FILE = "modifiedFile"
        const val ERROR = "error"
    }
}
