@file:JvmName("BenchmarkBaseline")
@file:Suppress("KotlinPrintToLogpoint")

package org.jetbrains.kotlinx.library.api.watchdog.benchmark

import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.Properties
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import org.openjdk.jmh.profile.GCProfiler
import org.openjdk.jmh.results.RunResult
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import org.openjdk.jmh.runner.options.VerboseMode

/** The entry point of the Gradle `benchmarkBaseline` task. The benchmark README describes the check. */
fun main(args: Array<String>) {
    val options = BaselineOptions.parse(args)
    val machine = MachineProfile.current()
    val baselineFile = options.baselineDirectory.resolve("${machine.id}.properties")
    val settings = RunSettings(options.forks, options.warmupIterations, options.measurementIterations, options.corpusFiles)
    println("Machine profile: ${machine.id}")
    machine.details.forEach { (key, value) -> println("  $key = $value") }
    println("Baseline file: $baselineFile")

    // The measurement takes minutes, so a mismatch fails before it.
    val baseline = if (!options.record && Files.exists(baselineFile)) {
        BaselineFile.parse(baselineFile).also { it.requireComparable(machine, settings) }
    } else {
        null
    }

    val runner = BaselineRunner(settings)
    var measured = runner.measureAll()

    if (options.record) {
        Files.createDirectories(baselineFile.parent)
        baselineFile.writeText(BaselineFile.render(machine, settings, measured, Tolerances.DEFAULT))
        val summary = Summary.recorded(machine, measured, baselineFile)
        writeSummary(options.summaryFile, summary)
        println(summary)
        return
    }

    if (baseline == null) {
        val summary = Summary.missing(machine, measured, baselineFile)
        writeSummary(options.summaryFile, summary)
        println(summary)
        if (options.allowMissing) return
        exitProcess(1)
    }

    var comparison = Comparison(baseline, measured)
    var retriesLeft = options.retries
    while (comparison.regressions.isNotEmpty() && retriesLeft > 0) {
        retriesLeft--
        println()
        val regressed = comparison.regressions.mapTo(linkedSetOf()) { it.subject }
        println("Regressions in $regressed. Measuring them again.")
        // The overhead ratios need the `noPlugin` time of the same run.
        if (regressed.any { it.startsWith(Measured.WHOLE_PREFIX) }) {
            regressed += Measured.WHOLE_MODES.map { Measured.WHOLE_PREFIX + it }
        }
        val again = runner.measureSubjects(regressed)
        measured = measured.lowerOf(again)
        comparison = Comparison(baseline, measured)
    }

    val summary = Summary.compared(machine, comparison, baselineFile)
    writeSummary(options.summaryFile, summary)
    println(summary)
    if (comparison.regressions.isNotEmpty()) exitProcess(1)
}

private fun writeSummary(file: Path, text: String) {
    Files.createDirectories(file.parent)
    file.writeText(text)
}

internal class BaselineOptions(
    val baselineDirectory: Path,
    val summaryFile: Path,
    val record: Boolean,
    val allowMissing: Boolean,
    val forks: Int,
    val warmupIterations: Int,
    val measurementIterations: Int,
    val corpusFiles: Int,
    val retries: Int,
) {
    companion object {
        fun parse(args: Array<String>): BaselineOptions {
            val values = mutableMapOf<String, String>()
            val flags = mutableSetOf<String>()
            var index = 0
            while (index < args.size) {
                val arg = args[index]
                when (arg) {
                    "--record", "--allow-missing" -> flags += arg
                    "--baseline-dir", "--summary", "--forks", "--warmup", "--measurement", "--corpus-files", "--retries" -> {
                        values[arg] = args.getOrNull(index + 1) ?: error("Option $arg needs a value.")
                        index++
                    }
                    else -> error("Unknown option '$arg'.")
                }
                index++
            }
            fun required(name: String) = values[name] ?: error("Option $name is required.")
            fun int(name: String, default: Int) = values[name]?.toInt() ?: default
            return BaselineOptions(
                baselineDirectory = Path.of(required("--baseline-dir")),
                summaryFile = Path.of(required("--summary")),
                record = "--record" in flags,
                allowMissing = "--allow-missing" in flags,
                forks = int("--forks", 2),
                warmupIterations = int("--warmup", 3),
                measurementIterations = int("--measurement", 5),
                corpusFiles = int("--corpus-files", 200),
                retries = int("--retries", 1),
            )
        }
    }
}

internal class RunSettings(
    val forks: Int,
    val warmupIterations: Int,
    val measurementIterations: Int,
    val corpusFiles: Int,
) {
    val asProperties: Map<String, String> = mapOf(
        "settings.forks" to forks.toString(),
        "settings.warmupIterations" to warmupIterations.toString(),
        "settings.measurementIterations" to measurementIterations.toString(),
        "settings.corpusFiles" to corpusFiles.toString(),
    )
}

/** A baseline is comparable on a machine with the same [id] only. */
internal class MachineProfile(
    val os: String,
    val arch: String,
    val cpus: Int,
    val jdk: Int,
    val image: String,
    val details: Map<String, String>,
) {
    val id: String = "$os-$arch-${cpus}cpu-jdk$jdk-$image"

    val pinned: Map<String, String> = mapOf(
        "machine.os" to os,
        "machine.arch" to arch,
        "machine.cpus" to cpus.toString(),
        "machine.jdk" to jdk.toString(),
        "machine.image" to image,
    )

    companion object {
        fun current(): MachineProfile {
            val osName = System.getProperty("os.name").lowercase(Locale.ROOT)
            val os = when {
                osName.startsWith("mac") -> "macos"
                osName.startsWith("windows") -> "windows"
                else -> osName.replace(Regex("[^a-z0-9]+"), "")
            }
            val memoryBytes = (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)
                ?.totalMemorySize
            return MachineProfile(
                os = os,
                arch = System.getProperty("os.arch"),
                cpus = Runtime.getRuntime().availableProcessors(),
                jdk = Runtime.version().feature(),
                image = System.getenv("ImageOS") ?: "local",
                details = buildMap {
                    put("machine.cpuModel", cpuModel() ?: "unknown")
                    put("machine.memoryGb", memoryBytes?.let { (it / (1L shl 30)).toString() } ?: "unknown")
                    put("machine.jvm", "${System.getProperty("java.vm.vendor")} ${System.getProperty("java.runtime.version")}")
                    System.getenv("GITHUB_SHA")?.let { put("recorded.commit", it) }
                },
            )
        }

        private fun cpuModel(): String? = runCatching {
            val cpuInfo = Path.of("/proc/cpuinfo")
            if (Files.exists(cpuInfo)) {
                return cpuInfo.readLines().firstOrNull { it.startsWith("model name") }?.substringAfter(':')?.trim()
            }
            if (System.getProperty("os.name").startsWith("Mac")) {
                val process = ProcessBuilder("sysctl", "-n", "machdep.cpu.brand_string").redirectErrorStream(true).start()
                return process.inputStream.bufferedReader().readText().trim().takeIf { process.waitFor() == 0 }
            }
            null
        }.getOrNull()
    }
}

/** A metric key is `<subject>.<metric>`, for example `isolated.OpenApiChecker.ms`. */
internal class Measured(val values: Map<String, Double>) {
    fun lowerOf(other: Measured): Measured =
        Measured(values.mapValues { (key, value) -> other.values[key]?.let { minOf(it, value) } ?: value })

    companion object {
        const val WHOLE_PREFIX = "whole."
        const val ISOLATED_PREFIX = "isolated."
        val WHOLE_MODES = listOf("noPlugin", "pluginBaseline", "allCheckers")

        fun subjectOf(key: String): String = key.substringBeforeLast('.')

        fun metricOf(key: String): String = key.substringAfterLast('.')
    }
}

/** Each tolerance is a fraction of the baseline value. The `ms` tolerance has a floor in milliseconds. */
internal class Tolerances(val ms: Double, val msFloor: Double, val bytes: Double, val overhead: Double) {
    fun forMetric(metric: String): Double = when (metric) {
        "ms" -> ms
        "bytes" -> bytes
        "overhead" -> overhead
        else -> error("Unknown metric '$metric'.")
    }

    val asProperties: Map<String, String> = mapOf(
        "tolerance.ms" to ms.toString(),
        "tolerance.msFloor" to msFloor.toString(),
        "tolerance.bytes" to bytes.toString(),
        "tolerance.overhead" to overhead.toString(),
    )

    companion object {
        val DEFAULT = Tolerances(ms = 0.30, msFloor = 1.0, bytes = 0.05, overhead = 0.10)
    }
}

internal class BaselineRunner(private val settings: RunSettings) {
    private val isolatedSubjects: List<String> = listOf("none") + CheckerSubjects.all.map { it.name }

    fun measureAll(): Measured = measureSubjects(
        isolatedSubjects.mapTo(linkedSetOf()) { Measured.ISOLATED_PREFIX + it } +
            Measured.WHOLE_MODES.map { Measured.WHOLE_PREFIX + it },
    )

    fun measureSubjects(subjects: Set<String>): Measured {
        val values = linkedMapOf<String, Double>()
        val checkers = subjects.filter { it.startsWith(Measured.ISOLATED_PREFIX) }
            .map { it.removePrefix(Measured.ISOLATED_PREFIX) }
        val modes = subjects.filter { it.startsWith(Measured.WHOLE_PREFIX) }
            .map { it.removePrefix(Measured.WHOLE_PREFIX) }
        if (checkers.isNotEmpty()) {
            val results = run("IsolatedCheckerBenchmark.sweepCorpus", "checker", checkers)
            values += collect(results, "checker", Measured.ISOLATED_PREFIX)
        }
        if (modes.isNotEmpty()) {
            val results = run("WholeCompilationBenchmark.compile", "mode", modes)
            values += collect(results, "mode", Measured.WHOLE_PREFIX)
        }
        val noPluginMs = values[Measured.WHOLE_PREFIX + "noPlugin.ms"]
        if (noPluginMs != null) {
            for (mode in Measured.WHOLE_MODES) {
                val ms = values[Measured.WHOLE_PREFIX + "$mode.ms"] ?: continue
                if (mode != "noPlugin") values[Measured.WHOLE_PREFIX + "$mode.overhead"] = ms / noPluginMs
            }
        }
        return Measured(values)
    }

    private fun run(benchmark: String, parameter: String, parameterValues: List<String>): Collection<RunResult> {
        val options = OptionsBuilder()
            .include(".*\\.$benchmark$")
            .param(parameter, *parameterValues.toTypedArray())
            .param("corpusFiles", settings.corpusFiles.toString())
            .forks(settings.forks)
            .warmupIterations(settings.warmupIterations)
            .measurementIterations(settings.measurementIterations)
            .addProfiler(GCProfiler::class.java)
            .shouldFailOnError(true)
            .verbosity(VerboseMode.NORMAL)
            .build()
        return Runner(options).run()
    }

    private fun collect(results: Collection<RunResult>, parameter: String, prefix: String): Map<String, Double> {
        val values = linkedMapOf<String, Double>()
        for (result in results) {
            val subject = prefix + result.params.getParam(parameter)
            val iterations = result.benchmarkResults.flatMap { it.iterationResults }
            values["$subject.ms"] = iterations.map { it.primaryResult.getScore() }.median()
            values["$subject.bytes"] = iterations.mapNotNull { iteration ->
                ALLOCATION_METRIC_KEYS.firstNotNullOfOrNull { iteration.secondaryResults[it] }?.getScore()
            }.median()
        }
        return values
    }

    private fun List<Double>.median(): Double {
        check(isNotEmpty()) { "A benchmark result has no measurement iterations." }
        val sorted = sorted()
        val middle = size / 2
        return if (size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    private companion object {
        /** The GC profiler of JMH 1.37 prefixes the key with a middle dot. The JSON report doesn't. */
        val ALLOCATION_METRIC_KEYS = listOf("·gc.alloc.rate.norm", "gc.alloc.rate.norm")
    }
}

internal class Baseline(
    val pinned: Map<String, String>,
    val settings: Map<String, String>,
    val tolerances: Tolerances,
    val values: Map<String, Double>,
) {
    fun requireComparable(machine: MachineProfile, runSettings: RunSettings) {
        val differences = (machine.pinned.filter { (key, value) -> pinned[key] != value } +
            runSettings.asProperties.filter { (key, value) -> settings[key] != value })
        check(differences.isEmpty()) {
            "The baseline file doesn't match this run. Record it again or run with the recorded settings.\n" +
                differences.entries.joinToString("\n") { (key, value) ->
                    "  $key: baseline ${pinned[key] ?: settings[key]}, this run $value"
                }
        }
    }
}

internal object BaselineFile {
    fun render(machine: MachineProfile, settings: RunSettings, measured: Measured, tolerances: Tolerances): String =
        buildString {
            appendLine("# Benchmark baseline. The benchmarkBaseline Gradle task writes and reads this file.")
            appendLine("# machine.* and settings.* keys must match the run that compares. Other machine.* keys are information.")
            for ((key, value) in machine.pinned + machine.details.toSortedMap() + settings.asProperties + tolerances.asProperties) {
                appendLine("$key=$value")
            }
            for ((key, value) in measured.values.toSortedMap()) {
                appendLine("$key=${format(value)}")
            }
        }

    fun parse(file: Path): Baseline {
        val properties = Properties().apply { file.readText().reader().use { load(it) } }
        val entries = properties.entries.associate { (key, value) -> key.toString() to value.toString() }
        fun tolerance(name: String) = entries["tolerance.$name"]?.toDouble()
            ?: error("The baseline file $file has no tolerance.$name key.")
        return Baseline(
            pinned = entries.filterKeys { it.startsWith("machine.") },
            settings = entries.filterKeys { it.startsWith("settings.") },
            tolerances = Tolerances(tolerance("ms"), tolerance("msFloor"), tolerance("bytes"), tolerance("overhead")),
            values = entries.filterKeys { it.startsWith(Measured.ISOLATED_PREFIX) || it.startsWith(Measured.WHOLE_PREFIX) }
                .mapValues { (_, value) -> value.toDouble() },
        )
    }

    fun format(value: Double): String = String.format(Locale.ROOT, "%.3f", value)
}

internal class MetricComparison(
    val key: String,
    val baseline: Double?,
    val measured: Double?,
    val allowed: Double?,
) {
    val subject: String get() = Measured.subjectOf(key)
    val metric: String get() = Measured.metricOf(key)

    val change: Double? get() = if (baseline != null && measured != null && baseline != 0.0) measured / baseline - 1 else null

    val status: String
        get() = when {
            baseline == null -> "new, not in the baseline"
            measured == null -> "missing, not measured"
            measured > checkNotNull(allowed) -> "regression"
            else -> "ok"
        }

    val isRegression: Boolean get() = status == "regression"
    val isMissing: Boolean get() = status.startsWith("missing")
}

internal class Comparison(baseline: Baseline, measured: Measured) {
    val metrics: List<MetricComparison> = (baseline.values.keys + measured.values.keys).sorted().map { key ->
        val expected = baseline.values[key]
        val tolerance = baseline.tolerances
        val allowed = expected?.let {
            val margin = it * tolerance.forMetric(Measured.metricOf(key))
            it + if (Measured.metricOf(key) == "ms") maxOf(margin, tolerance.msFloor) else margin
        }
        MetricComparison(key, expected, measured.values[key], allowed)
    }

    /** A metric that the baseline has but the run didn't measure is a regression too, because a renamed subject hides a slowdown. */
    val regressions: List<MetricComparison> = metrics.filter { it.isRegression || it.isMissing }
}

internal object Summary {
    fun recorded(machine: MachineProfile, measured: Measured, file: Path): String = buildString {
        appendLine("## Benchmark baseline recorded")
        appendLine()
        appendLine("Machine profile `${machine.id}`. File `${file.fileName}`. Commit the file to keep the baseline.")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|---|---|")
        for ((key, value) in measured.values.toSortedMap()) {
            appendLine("| `$key` | ${BaselineFile.format(value)} |")
        }
    }

    fun missing(machine: MachineProfile, measured: Measured, file: Path): String = buildString {
        appendLine("## Benchmark baseline missing")
        appendLine()
        appendLine("No baseline for machine profile `${machine.id}` at `$file`.")
        appendLine("Run the `benchmarkBaseline` task with `-Pbenchmark.baseline.record=true` on this machine profile and commit the file.")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|---|---|")
        for ((key, value) in measured.values.toSortedMap()) {
            appendLine("| `$key` | ${BaselineFile.format(value)} |")
        }
    }

    fun compared(machine: MachineProfile, comparison: Comparison, file: Path): String = buildString {
        val regressions = comparison.regressions
        appendLine(if (regressions.isEmpty()) "## Benchmark baseline: no regression" else "## Benchmark baseline: ${regressions.size} regression(s)")
        appendLine()
        appendLine("Machine profile `${machine.id}`. Baseline `${file.fileName}`.")
        appendLine()
        appendLine("| Subject | Metric | Baseline | Measured | Change | Allowed | Status |")
        appendLine("|---|---|---|---|---|---|---|")
        for (metric in comparison.metrics) {
            appendLine(
                "| `${metric.subject}` | ${metric.metric} | ${metric.baseline?.let(BaselineFile::format) ?: ""} | " +
                    "${metric.measured?.let(BaselineFile::format) ?: ""} | ${metric.change?.let { percent(it) } ?: ""} | " +
                    "${metric.allowed?.let(BaselineFile::format) ?: ""} | ${metric.status} |",
            )
        }
    }

    private fun percent(change: Double): String = String.format(Locale.ROOT, "%+.1f%%", change * 100)
}
