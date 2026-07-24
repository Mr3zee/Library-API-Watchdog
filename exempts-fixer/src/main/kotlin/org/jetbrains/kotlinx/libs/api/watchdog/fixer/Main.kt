@file:JvmName("ExemptsFixerMain")

package org.jetbrains.kotlinx.libs.api.watchdog.fixer

import java.io.File
import kotlin.system.exitProcess
import org.jetbrains.kotlin.buildtools.api.CompilationResult

/**
 * Entry point of the backwards-compatibility exempts fixer. The single argument is the path of a
 * [FixerRequest] file; the results are written to the response file named in the request, so the
 * launching Gradle task never parses process output. A non-zero exit code means the fixer itself
 * broke, not that the analyzed project has diagnostics.
 */
fun main(args: Array<String>) {
    val request = FixerRequest.parse(File(args.singleOrNull() ?: error("Usage: <request file>")))
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
    val workDir = request.workDir.apply { mkdirs() }
    val reportFile = File(workDir, "watchdog-diagnostics.tsv").apply { delete() }
    val classesDir = File(workDir, "classes").apply { mkdirs() }

    val result = compileRecordingDiagnostics(request, reportFile, classesDir) {
        response.compilerMessages += it
    }
    response.compilationResult = result.name
    if (result == CompilationResult.COMPILATION_OOM_ERROR || result == CompilationResult.COMPILER_INTERNAL_ERROR) {
        error("The Kotlin compilation failed with $result")
    }

    val diagnosticsByFile = RecordedDiagnostic.parseReport(reportFile).groupBy { it.filePath }
    if (diagnosticsByFile.isEmpty()) {
        return
    }
    // Compiler messages only matter for diagnosing a compilation that recorded nothing.
    response.compilerMessages.clear()

    KotlinFileParser().use { parser ->
        val fixer = ExemptionFixer(parser)
        for ((filePath, diagnostics) in diagnosticsByFile.toSortedMap()) {
            val file = File(filePath)
            if (!file.isFile) {
                diagnostics.forEach {
                    response.skipped += SkippedDiagnostic(it.name, filePath, 0, "the source file no longer exists")
                }
                continue
            }
            val fixResult = fixer.fix(filePath, file.readText(), diagnostics)
            response.applied += fixResult.applied
            response.skipped += fixResult.skipped
            if (fixResult.newText != null) {
                file.writeText(fixResult.newText)
                response.modifiedFiles += filePath
            }
        }
    }
}
