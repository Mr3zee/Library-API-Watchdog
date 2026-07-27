@file:JvmName("ExemptsFixerMain")

package org.jetbrains.kotlinx.libs.api.watchdog.fixer

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Entry point of the backwards-compatibility exempts fixer. The single argument is the path of a
 * [FixerRequest] file. The results are written to the response file named in the request, so the
 * launching Gradle task never parses process output. A non-zero exit code means the fixer itself
 * broke, not that the analyzed project has diagnostics.
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
    if (diagnosticsByFile.isEmpty()) {
        return
    }

    KotlinFileParser().use { parser ->
        val fixer = ExemptionFixer(parser)
        for ((filePath, diagnostics) in diagnosticsByFile.toSortedMap()) {
            val file = Paths.get(filePath)
            if (!Files.isRegularFile(file)) {
                diagnostics.forEach {
                    response.skipped += SkippedDiagnostic(it.name, filePath, 0, "the source file no longer exists")
                }
                continue
            }
            val fixResult = fixer.fix(filePath, Files.readString(file), diagnostics)
            response.applied += fixResult.applied
            response.skipped += fixResult.skipped
            if (fixResult.newText != null) {
                Files.writeString(file, fixResult.newText)
                response.modifiedFiles += filePath
            }
        }
    }
}
