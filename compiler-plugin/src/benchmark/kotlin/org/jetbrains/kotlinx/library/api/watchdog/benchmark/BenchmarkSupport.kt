package org.jetbrains.kotlinx.library.api.watchdog.benchmark

import java.io.File
import java.nio.file.Path
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlinx.library.api.watchdog.PluginInfo
import org.jetbrains.kotlinx.library.api.watchdog.fir.WatchdogDiagnostics

/** The system properties that the Gradle `benchmark` task passes to the benchmark JVM. */
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

/** Collects errors only, because the corpus produces many warnings. */
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

/**
 * The plugin options of a corpus compilation. Every configurable diagnostic is at `warning` when
 * [enabledDiagnostics] names it and at `none` otherwise. With [withDependencyPaths] the standard
 * library is the only transitive dependency.
 */
internal fun corpusPluginOptions(enabledDiagnostics: Set<String>, withDependencyPaths: Boolean): List<String> =
    buildList {
        for (diagnostic in WatchdogDiagnostics.allDiagnostics) {
            val severity = if (diagnostic.name in enabledDiagnostics) "warning" else "none"
            add(pluginOption("diagnosticSeverity", "${diagnostic.name}:$severity"))
        }
        if (withDependencyPaths) {
            val classpath = BenchmarkEnv.corpusClasspath
            add(pluginOption("compileDependencyPaths", classpath.joinToString(File.pathSeparator)))
            add(
                pluginOption(
                    "transitiveDependencyPaths",
                    classpath.filter { it.contains("kotlin-stdlib") }.joinToString(File.pathSeparator),
                ),
            )
        }
    }

internal fun pluginOption(name: String, value: String): String = "plugin:${BenchmarkEnv.pluginId}:$name=$value"

internal fun deleteRecursively(root: Path) {
    val file = root.toFile()
    if (file.exists()) {
        file.deleteRecursively()
    }
}
