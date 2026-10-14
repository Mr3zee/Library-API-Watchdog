package org.jetbrains.kotlinx.library.api.watchdog.benchmark

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
 * One in-process `K2JVMCompiler` run over the corpus for each operation. The modes:
 *
 * - `noPlugin`: the compiler alone.
 * - `pluginBaseline`: every configurable diagnostic set to `none`. `ExemptionExplanationChecker`
 *   and `InternalApiTypeExposureChecker` still run.
 * - `allCheckers`: every configurable diagnostic at `warning`, with the dependency-path options.
 * - `<CheckerName>`: the diagnostics of that checker at `warning`, the rest `none`. The cost of
 *   the checker is this mode minus `pluginBaseline`.
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

    private fun buildPluginOptions(): List<String> = when (mode) {
        "pluginBaseline" -> corpusPluginOptions(enabledDiagnostics = emptySet(), withDependencyPaths = false)
        "allCheckers" -> corpusPluginOptions(
            enabledDiagnostics = WatchdogDiagnostics.allDiagnostics.mapTo(hashSetOf()) { it.name },
            withDependencyPaths = true,
        )
        else -> {
            val subject = CheckerSubjects.byName[mode]
                ?: error("Unknown benchmark mode '$mode'. Update CheckerSubjects and the @Param list together.")
            corpusPluginOptions(subject.configurableDiagnostics.toSet(), subject.needsDependencyPaths)
        }
    }
}
