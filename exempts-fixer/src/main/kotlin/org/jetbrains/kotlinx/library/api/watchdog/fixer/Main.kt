@file:JvmName("ExemptsFixerMain")

package org.jetbrains.kotlinx.library.api.watchdog.fixer

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * The argument is the path of a [FixerRequest] file. A non-zero exit code means that the fixer
 * failed, not that the project has diagnostics.
 */
fun main(args: Array<String>) {
    val request = FixerRequest.parse(Paths.get(args.singleOrNull() ?: error("Usage: <request file>")))
    val response = FixerResponse()
    try {
        run(request, response)
    } catch (e: Throwable) {
        response.error = e.stackTraceToString()
        response.writeTo(request.responseFile)
        exitProcess(1)
    }
    response.writeTo(request.responseFile)
}

private fun run(request: FixerRequest, response: FixerResponse) {
    val diagnosticsByFile = RecordedDiagnostic.parseReports(request.reportFiles).groupBy { it.filePath }
    if (diagnosticsByFile.isEmpty() && request.sourceFiles.isEmpty()) {
        return
    }

    KotlinFileParser().use { parser ->
        val fixer = ExemptionFixer(parser)
        for ((filePath, diagnostics) in diagnosticsByFile.toSortedMap()) {
            val file = Paths.get(filePath)
            if (!Files.isRegularFile(file)) {
                diagnostics.forEach {
                    response.skipped += SkippedDiagnostic(
                        it.name, filePath, 0, "the source file no longer exists", it.messageParameters,
                    )
                }
                continue
            }
            val fixResult = fixer.fix(
                filePath,
                Files.readString(file),
                diagnostics,
                rewriteLocations = request.updateSources,
                fixes = request::fixes,
            )
            response.applied += fixResult.applied
            response.skipped += fixResult.skipped
            response.warnings += fixResult.warnings
            if (request.updateSources && fixResult.newText != null) {
                Files.writeString(file, fixResult.newText)
                response.modifiedFiles += filePath
            }
        }

        val scanner = ExemptionScanner(parser)
        request.sourceFiles.distinct().sorted().forEach { file ->
            if (Files.isRegularFile(file)) {
                response.exemptions += scanner.scan(file.toString(), Files.readString(file))
            }
        }
    }
}
