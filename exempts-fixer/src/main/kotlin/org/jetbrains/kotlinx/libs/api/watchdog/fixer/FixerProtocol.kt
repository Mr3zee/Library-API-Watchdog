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
         * Parses the report, dropping exact duplicates: a multiplatform compilation records the
         * common fragment's diagnostics once per session that revisits it.
         */
        fun parseReport(reportFile: File): List<RecordedDiagnostic> {
            if (!reportFile.isFile) return emptyList()
            return reportFile.readLines()
                .filter { it.isNotBlank() }
                .map { line ->
                    val parts = line.split('\t')
                    require(parts.size == 4) { "Malformed diagnostics report line: '$line'" }
                    RecordedDiagnostic(parts[0], parts[1], parts[2].toInt(), parts[3].toInt())
                }
                .distinct()
        }
    }
}

/**
 * The request the Gradle task hands to the fixer process as a file of `key=value` lines, one
 * value per line, keys repeating for list entries. A file sidesteps command-line length limits
 * and quoting rules.
 */
internal class FixerRequest(
    /** All source files of the compilation, Java files included; only Kotlin files are fixed. */
    val sources: List<File>,
    /** Raw Kotlin CLI compiler arguments: classpath, plugins, plugin options, language settings. */
    val compilerArgs: List<String>,
    /** The watchdog compiler plugin id, for the `diagnosticsOutputFile` plugin option. */
    val pluginId: String,
    /** Scratch directory owned by this run: compiled classes and the diagnostics report. */
    val workDir: File,
    /** Where the fixer writes its [FixerResponse]. */
    val responseFile: File,
) {
    companion object {
        const val SOURCE = "source"
        const val COMPILER_ARG = "compilerArg"
        const val PLUGIN_ID = "pluginId"
        const val WORK_DIR = "workDir"
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
                sources = values[SOURCE].orEmpty().map(::File),
                compilerArgs = values[COMPILER_ARG].orEmpty(),
                pluginId = single(PLUGIN_ID),
                workDir = File(single(WORK_DIR)),
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
    val compilerMessages = mutableListOf<String>()
    var compilationResult: String? = null
    var error: String? = null

    fun writeTo(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(buildString {
            compilationResult?.let { appendLine("$COMPILATION_RESULT=$it") }
            applied.forEach {
                appendLine("$FIXED=${it.diagnostic}\t${it.annotation}\t${it.line}\t${it.filePath}")
            }
            skipped.forEach {
                // The reason is free-form text and the line's last field: escaping its line
                // breaks keeps the line-based protocol intact (tabs are fine in a last field).
                appendLine("$SKIPPED=${it.diagnostic}\t${it.line}\t${it.filePath}\t${it.reason.escapeNewlines()}")
            }
            modifiedFiles.forEach { appendLine("$MODIFIED_FILE=$it") }
            compilerMessages.forEach { appendLine("$COMPILER_MESSAGE=${it.escapeNewlines()}") }
            error?.let { appendLine("$ERROR=${it.escapeNewlines()}") }
        })
    }

    private fun String.escapeNewlines() = replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")

    companion object {
        const val COMPILATION_RESULT = "compilationResult"
        const val FIXED = "fixed"
        const val SKIPPED = "skipped"
        const val MODIFIED_FILE = "modifiedFile"
        const val COMPILER_MESSAGE = "compilerMessage"
        const val ERROR = "error"
    }
}
