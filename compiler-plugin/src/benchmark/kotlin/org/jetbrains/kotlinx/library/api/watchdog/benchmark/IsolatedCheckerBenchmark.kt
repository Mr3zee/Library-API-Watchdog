package org.jetbrains.kotlinx.library.api.watchdog.benchmark

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.common.messages.GroupingMessageCollector
import org.jetbrains.kotlin.cli.pipeline.ArgumentsPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmConfigurationPipelinePhase
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmFrontendPipelinePhase
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.diagnostics.impl.DiagnosticsCollectorImpl
import org.jetbrains.kotlin.diagnostics.impl.PendingDiagnosticsReporterImpl
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckersDiagnosticComponent
import org.jetbrains.kotlin.fir.analysis.checkers.type.TypeCheckersDiagnosticComponent
import org.jetbrains.kotlin.fir.analysis.collectors.CliDiagnosticsCollector
import org.jetbrains.kotlin.fir.analysis.collectors.DiagnosticCollectorComponents
import org.jetbrains.kotlin.fir.analysis.collectors.components.ReportCommitterDiagnosticComponent
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.util.PerformanceManagerImpl
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
import org.openjdk.jmh.infra.Blackhole

/**
 * Isolated cost of a single watchdog checker over the fully resolved FIR of the synthetic
 * corpus.
 *
 * The setup compiles the corpus to resolved FIR once, without the watchdog. Each operation then
 * sweeps the whole corpus with the compiler's own checker-running collector visitor, wired to
 * exactly one checker (the `none` value runs the traversal with zero checkers and is the
 * baseline to subtract). Time is the per-sweep average. The GC profiler's `gc.alloc.rate.norm`
 * is bytes allocated per sweep.
 *
 * Compared to the whole-compilation benchmark this excludes plugin loading, message rendering,
 * and the backend, so it measures the checker logic itself with far less noise.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
open class IsolatedCheckerBenchmark {

    @Param(
        "none",
        "OpenApiChecker",
        "ExhaustiveApiChecker",
        "DataClassChecker",
        "StatefulClassWithoutGeneratedMembersChecker",
        "DslMarkerTargetsChecker",
        "UndocumentedApiChecker",
        "ExemptionExplanationChecker",
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
    var checker: String = "none"

    @Param("200")
    @JvmField
    var corpusFiles: Int = 200

    private lateinit var sourceRoot: Path
    private lateinit var outputDir: Path
    private lateinit var disposable: Disposable
    private lateinit var firFiles: List<FirFile>
    private lateinit var collector: CliDiagnosticsCollector

    @Setup
    fun setUp() {
        setIdeaIoUseFallback()
        sourceRoot = Files.createTempDirectory("watchdog-bench-fir-src")
        outputDir = Files.createTempDirectory("watchdog-bench-fir-out")
        BenchmarkCorpus.generate(sourceRoot, corpusFiles)
        disposable = Disposer.newDisposable("watchdog benchmark")

        val messageCollector = ErrorSummaryMessageCollector()
        val argumentsArtifact = ArgumentsPipelineArtifact(
            corpusCompilerArguments(sourceRoot, outputDir),
            Services.EMPTY,
            disposable,
            GroupingMessageCollector(messageCollector, false, false),
            PerformanceManagerImpl(JvmPlatforms.defaultJvmPlatform, "watchdog benchmark"),
        )
        val configurationArtifact = JvmConfigurationPipelinePhase.executePhase(argumentsArtifact)
            ?: error("Compiler configuration failed:\n${messageCollector.summary()}")
        val frontendArtifact = JvmFrontendPipelinePhase.executePhase(configurationArtifact)
            ?: error("Corpus frontend compilation failed:\n${messageCollector.summary()}")
        check(!messageCollector.hasErrors()) {
            "Corpus frontend compilation reported errors:\n${messageCollector.summary()}"
        }

        val output = frontendArtifact.frontendOutput.outputs.single()
        firFiles = output.fir
        val checkers = when (checker) {
            "none" -> CheckerSubjects.emptyCheckers
            else -> {
                val subject = CheckerSubjects.byName[checker]
                    ?: error("Unknown checker '$checker'. Update CheckerSubjects and the @Param list together.")
                subject.createCheckers(CheckerEnvironment(BenchmarkEnv.corpusClasspath, output.session))
            }
        }
        collector = CliDiagnosticsCollector(output.session, output.scopeSession) { reporter ->
            DiagnosticCollectorComponents(
                arrayOf(
                    DeclarationCheckersDiagnosticComponent(output.session, reporter, checkers.declarations),
                    TypeCheckersDiagnosticComponent(output.session, reporter, checkers.types),
                ),
                ReportCommitterDiagnosticComponent(output.session, reporter),
            )
        }
    }

    @TearDown
    fun tearDown() {
        Disposer.dispose(disposable)
        deleteRecursively(sourceRoot)
        deleteRecursively(outputDir)
    }

    @Benchmark
    fun sweepCorpus(blackhole: Blackhole) {
        val sink = DiagnosticsCollectorImpl()
        val reporter = PendingDiagnosticsReporterImpl(sink)
        for (file in firFiles) {
            collector.collectDiagnostics(file, reporter)
        }
        blackhole.consume(sink.diagnostics.size)
    }
}
