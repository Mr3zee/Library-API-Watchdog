package org.jetbrains.kotlinx.library.api.watchdog.benchmark

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlinx.library.api.watchdog.fir.WatchdogDiagnostics
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import java.nio.file.Path

/**
 * End-to-end cost of the watchdog in a real in-process `K2JVMCompiler` run over the synthetic
 * corpus, one full compilation per operation.
 *
 * Modes:
 * - `noPlugin` - the compiler alone, without the watchdog jar.
 * - `pluginBaseline` - the plugin applied with every configurable diagnostic set to `none`. The
 *   non-configurable `ExemptionExplanationChecker` still runs; this is the honest floor every
 *   per-checker mode is compared against.
 * - `allCheckers` - every configurable diagnostic enabled at `warning` severity,
 *   dependency-path options included.
 * - `<CheckerName>` - only that checker's diagnostics enabled at `warning`, everything else
 *   `none`. The per-checker cost is this mode minus `pluginBaseline`.
 *
 * `ExemptionExplanationChecker` has no mode of its own: it can't be enabled separately, and its
 * cost is `pluginBaseline` minus `noPlugin` less the fixed plugin-infrastructure overhead.
 *
 * Allocation numbers come from JMH's GC profiler, which the Gradle `benchmark` task enables.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
open class WholeCompilationBenchmark {

    @Param(
        "noPlugin",
        "pluginBaseline",
        "allCheckers",
        "OpenApiChecker",
        "ExhaustiveApiChecker",
        "DataClassChecker",
        "StatefulClassWithoutGeneratedMembersChecker",
        "DslMarkerTargetsChecker",
        "UndocumentedApiChecker",
        "MutableCollectionChecker",
        "PairOrTripleChecker",
        "NullableBooleanChecker",
        "NonTransitiveDependencyChecker",
        "BooleanParameterChecker",
        "RequiredParameterAfterOptionalChecker",
        "OverloadParameterOrderChecker",
        "KotlinOnlyApiChecker",
        "JvmOverloadsChecker",
        "TopLevelJvmNameChecker",
        "FunctionTypeAliasChecker",
        "DslMarkerTypePositionChecker",
        "InlineFunctionLogicChecker",
        "MangledJvmNameChecker",
        "CompanionJvmExposureChecker",
    )
    @JvmField
    var mode: String = "noPlugin"

    @Param("200")
    @JvmField
    var corpusFiles: Int = 200

    private lateinit var sourceRoot: Path
    private lateinit var outputDir: Path
    private lateinit var pluginOptionArgs: List<String>
    private var usePlugin: Boolean = false

    @Setup
    fun setUp() {
        setIdeaIoUseFallback()
        sourceRoot = Files.createTempDirectory("watchdog-bench-src")
        outputDir = Files.createTempDirectory("watchdog-bench-out")
        BenchmarkCorpus.generate(sourceRoot, corpusFiles)
        usePlugin = mode != "noPlugin"
        pluginOptionArgs = if (usePlugin) buildPluginOptions() else emptyList()
    }

    @TearDown
    fun tearDown() {
        deleteRecursively(sourceRoot)
        deleteRecursively(outputDir)
    }

    @Benchmark
    fun compile(): ExitCode {
        val collector = ErrorSummaryMessageCollector()
        val arguments = corpusCompilerArguments(sourceRoot, outputDir).apply {
            if (usePlugin) {
                pluginClasspaths = arrayOf(BenchmarkEnv.pluginJar)
                pluginOptions = pluginOptionArgs.toTypedArray()
            }
        }
        val exitCode = K2JVMCompiler().exec(collector, Services.EMPTY, arguments)
        check(exitCode == ExitCode.OK) {
            "Corpus compilation failed in mode '$mode' ($exitCode):\n${collector.summary()}"
        }
        return exitCode
    }

    private fun buildPluginOptions(): List<String> {
        val allConfigurable = WatchdogDiagnostics.allDiagnostics.map { it.name }
        val enabled: Set<String>
        val withDependencyPaths: Boolean
        when (mode) {
            "pluginBaseline" -> {
                enabled = emptySet()
                withDependencyPaths = false
            }
            "allCheckers" -> {
                enabled = allConfigurable.toSet()
                withDependencyPaths = true
            }
            else -> {
                val subject = CheckerSubjects.byName[mode]
                    ?: error("Unknown benchmark mode '$mode'. Update CheckerSubjects and the @Param list together.")
                enabled = subject.configurableDiagnostics.toSet()
                withDependencyPaths = subject.needsDependencyPaths
            }
        }
        return buildList {
            for (diagnostic in allConfigurable) {
                val severity = if (diagnostic in enabled) "warning" else "none"
                add(option("diagnosticSeverity", "$diagnostic:$severity"))
            }
            if (withDependencyPaths) {
                val classpath = BenchmarkEnv.corpusClasspath
                add(option("compileDependencyPaths", classpath.joinToString(File.pathSeparator)))
                add(
                    option(
                        "transitiveDependencyPaths",
                        classpath.filter { it.contains("kotlin-stdlib") }.joinToString(File.pathSeparator),
                    ),
                )
            }
        }
    }

    private fun option(name: String, value: String): String =
        "plugin:${BenchmarkEnv.pluginId}:$name=$value"
}
