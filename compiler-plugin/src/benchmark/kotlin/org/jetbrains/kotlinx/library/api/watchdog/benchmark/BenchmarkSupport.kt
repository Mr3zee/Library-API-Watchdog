package org.jetbrains.kotlinx.library.api.watchdog.benchmark

import java.io.File
import java.nio.file.Path
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlinx.library.api.watchdog.PluginInfo

/**
 * Values the Gradle `benchmark` task passes into the benchmark JVM: the assembled compiler
 * plugin jar and the classpath the synthetic corpus compiles against (the watchdog annotations
 * library plus its dependencies, including the Kotlin stdlib).
 */
internal object BenchmarkEnv {
    val pluginJar: String by lazy { requireProperty("watchdog.benchmark.pluginJar") }

    val corpusClasspath: List<String> by lazy {
        requireProperty("watchdog.benchmark.corpusClasspath")
            .split(File.pathSeparator)
            .filter { it.isNotEmpty() }
    }

    const val pluginId: String = PluginInfo.PLUGIN_ID

    private fun requireProperty(name: String): String =
        System.getProperty(name)
            ?: error("Missing system property '$name'. Run the benchmarks through the Gradle 'benchmark' task.")
}

/**
 * Swallows everything below error severity: the corpus deliberately produces warnings, and
 * printing them would dominate the benchmark output. Errors are kept for failure messages.
 */
internal class ErrorSummaryMessageCollector : MessageCollector {
    private val errors = mutableListOf<String>()

    override fun clear() {
        errors.clear()
    }

    override fun hasErrors(): Boolean = errors.isNotEmpty()

    override fun report(
        severity: CompilerMessageSeverity,
        message: String,
        location: CompilerMessageSourceLocation?,
    ) {
        if (severity.isError) {
            errors += if (location != null) "$message ($location)" else message
        }
    }

    fun summary(): String = errors.joinToString("\n")
}

/** Compiler arguments shared by both benchmarks; the plugin is configured on top of these. */
internal fun corpusCompilerArguments(sourceRoot: Path, destination: Path): K2JVMCompilerArguments =
    K2JVMCompilerArguments().apply {
        freeArgs = listOf(sourceRoot.toAbsolutePath().toString())
        classpath = BenchmarkEnv.corpusClasspath.joinToString(File.pathSeparator)
        this.destination = destination.toAbsolutePath().toString()
        moduleName = "watchdog-benchmark-corpus"
        explicitApi = "warning"
        noStdlib = true
        noReflect = true
    }

/** Deletes a temporary directory tree, tolerating files that are already gone. */
internal fun deleteRecursively(root: Path) {
    val file = root.toFile()
    if (file.exists()) {
        file.deleteRecursively()
    }
}
