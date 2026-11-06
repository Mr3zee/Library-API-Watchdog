package org.jetbrains.kotlinx.library.api.watchdog.benchmark

import com.intellij.openapi.util.Disposer
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
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
import org.jetbrains.kotlin.fir.pipeline.SingleModuleFrontendOutput
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
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole

/**
 * One pass over the resolved FIR of the corpus with one checker for each operation. The `none`
 * value runs the pass without a checker and is the baseline to subtract.
 *
 * The JVM compiles the corpus once for each `corpusFiles` value and keeps the FIR for every
 * subject that runs in it. In a forked run, that is one compilation per fork. In a non-forked
 * run, every checker reuses the one compilation.
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
        "ExemptingCheckers",
        "RedundantExemptionProbingChecker",
    )
    @JvmField
    var checker: String = "none"

    @Param("200")
    @JvmField
    var corpusFiles: Int = 200

    private lateinit var firFiles: List<FirFile>
    private lateinit var collector: CliDiagnosticsCollector

    @Setup
    fun setUp() {
        val output = ResolvedCorpus.of(corpusFiles).output
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

/**
 * The resolved FIR of the corpus with [corpusFiles] files. One instance for each file count exists
 * until the JVM exits, so every subject in the JVM sweeps the same FIR.
 */
private class ResolvedCorpus private constructor(val output: SingleModuleFrontendOutput) {

    companion object {
        private val instances = ConcurrentHashMap<Int, ResolvedCorpus>()

        fun of(corpusFiles: Int): ResolvedCorpus = instances.computeIfAbsent(corpusFiles, ::compile)

        private fun compile(corpusFiles: Int): ResolvedCorpus {
            setIdeaIoUseFallback()
            val sourceRoot = Files.createTempDirectory("watchdog-bench-fir-src")
            val outputDir = Files.createTempDirectory("watchdog-bench-fir-out")
            BenchmarkCorpus.generate(sourceRoot, corpusFiles)
            val disposable = Disposer.newDisposable("watchdog benchmark")
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    Disposer.dispose(disposable)
                    deleteRecursively(sourceRoot)
                    deleteRecursively(outputDir)
                },
            )

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
            return ResolvedCorpus(frontendArtifact.frontendOutput.outputs.single())
        }
    }
}
