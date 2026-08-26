@file:JvmName("CorpusAudit")
@file:Suppress("KotlinPrintToLogpoint")

package org.jetbrains.kotlinx.library.api.watchdog.benchmark

import java.io.File
import java.nio.file.Files
import kotlin.io.path.readLines
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlinx.library.api.watchdog.fir.WatchdogDiagnostics

/**
 * Compiles the synthetic corpus once with every watchdog diagnostic enabled as a warning and
 * prints how often each diagnostic fired, using the plugin's `diagnosticsOutputFile` recorder.
 * Run it through the Gradle `benchmarkCorpusAudit` task after changing the corpus templates to
 * verify that every benchmark-eligible checker still has work to do; it doubles as a smoke test
 * of the whole-compilation benchmark path. Always-error diagnostics have no triggering corpus
 * shape.
 */
fun main(args: Array<String>) {
    val fileCount = args.firstOrNull()?.toInt() ?: 200
    setIdeaIoUseFallback()

    val sourceRoot = Files.createTempDirectory("watchdog-audit-src")
    val outputDir = Files.createTempDirectory("watchdog-audit-out")
    val report = Files.createTempFile("watchdog-audit", ".tsv")
    try {
        BenchmarkCorpus.generate(sourceRoot, fileCount)
        val collector = ErrorSummaryMessageCollector()
        val arguments = corpusCompilerArguments(sourceRoot, outputDir).apply {
            pluginClasspaths = arrayOf(BenchmarkEnv.pluginJar)
            pluginOptions = buildList {
                for (diagnostic in WatchdogDiagnostics.allDiagnostics) {
                    add("plugin:${BenchmarkEnv.pluginId}:diagnosticSeverity=${diagnostic.name}:warning")
                }
                val classpath = BenchmarkEnv.corpusClasspath
                add(
                    "plugin:${BenchmarkEnv.pluginId}:compileDependencyPaths=" +
                        classpath.joinToString(File.pathSeparator),
                )
                add(
                    "plugin:${BenchmarkEnv.pluginId}:transitiveDependencyPaths=" +
                        classpath.filter { it.contains("kotlin-stdlib") }.joinToString(File.pathSeparator),
                )
                add("plugin:${BenchmarkEnv.pluginId}:diagnosticsOutputFile=${report.toAbsolutePath()}")
            }.toTypedArray()
        }

        val exitCode = K2JVMCompiler().exec(collector, Services.EMPTY, arguments)
        check(exitCode == ExitCode.OK) {
            "Corpus audit compilation failed ($exitCode):\n${collector.summary()}"
        }

        val counts = report.readLines()
            .filter { it.isNotBlank() }
            .groupingBy { it.substringBefore('\t') }
            .eachCount()
            .toSortedMap()

        println("Corpus: $fileCount files. Diagnostics reported:")
        val width = counts.keys.maxOfOrNull { it.length } ?: 0
        for ((name, count) in counts) {
            println("  ${name.padEnd(width)}  $count")
        }

        val silent = WatchdogDiagnostics.allDiagnostics.map { it.name }
            .filter { it !in counts } +
            listOf("EXEMPTION_WITHOUT_EXPLANATION", "PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY")
                .filter { it !in counts }
        if (silent.isNotEmpty()) {
            println()
            println("Diagnostics that never fired (their checkers still scan the corpus):")
            silent.forEach { println("  $it") }
        }
    } finally {
        deleteRecursively(sourceRoot)
        deleteRecursively(outputDir)
        Files.deleteIfExists(report)
    }
}
