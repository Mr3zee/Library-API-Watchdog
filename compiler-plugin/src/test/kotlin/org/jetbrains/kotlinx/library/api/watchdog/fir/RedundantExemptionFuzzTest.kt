package org.jetbrains.kotlinx.library.api.watchdog.fir

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.fail
import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmDiagnosticTest
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.test.builders.configureFirHandlersStep
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives.DISABLE_FIR_DUMP_HANDLER
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirAnalysisHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.firDiagnosticCollectorService
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.KotlinTestInfo
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.sourceFileProvider
import org.jetbrains.kotlinx.library.api.watchdog.runners.configureWatchdogDiagnosticTest
import org.opentest4j.AssertionFailedError
import org.opentest4j.MultipleFailuresError

/**
 * A differential fuzz test of `REDUNDANT_EXEMPTION`. The test compiles each generated program, then
 * compiles it again for each exemption with that exemption blanked out. The checker must report the
 * exemption exactly when the watchdog diagnostics don't change.
 *
 * Gradle forwards these project properties as system properties:
 *
 * - `watchdog.fuzz.programs`: the number of programs.
 * - `watchdog.fuzz.seed`: the seed. A failure message holds the seed and the program.
 * - `watchdog.fuzz.file`: a test-data file. The test compiles only that file, prints its diagnostics,
 *   and writes the marked-up text of the framework next to it as `<path>.actual`.
 */
class RedundantExemptionFuzzTest {
    private val seed: Long = System.getProperty("watchdog.fuzz.seed")?.toLong() ?: 20261029L
    private val programs: Int = System.getProperty("watchdog.fuzz.programs")?.toInt() ?: 8
    private val directory: Path = Files.createTempDirectory("watchdog-redundancy-fuzz")
    private val runner = FuzzDiagnosticRunner()

    @AfterTest
    fun deleteGeneratedPrograms() {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `the checker agrees with blanking each exemption`() {
        System.getProperty("watchdog.fuzz.file")?.let { debugFile ->
            FuzzDiagnosticsSink.begin()
            runner.initTestInfo(KotlinTestInfo(className = javaClass.name, methodName = "debug", tags = emptySet()))
            var actual: String? = null
            val failureSummary = StringBuilder()
            try {
                runner.runTest(debugFile)
            } catch (failure: Throwable) {
                val failures = (failure as? MultipleFailuresError)?.failures ?: listOf(failure)
                actual = failures.filterIsInstance<AssertionFailedError>().firstOrNull { it.isActualDefined }?.actual?.stringRepresentation
                for (each in failures) {
                    if (each is AssertionFailedError && each.isActualDefined) continue
                    failureSummary.append(each.toString().lines().take(3).joinToString("\n")).append('\n')
                    each.stackTrace.take(12).forEach { failureSummary.append("    at ").append(it).append('\n') }
                }
            }
            val diagnostics = FuzzDiagnosticsSink.end()
            Files.writeString(Path.of("$debugFile.actual"), actual ?: Files.readString(Path.of(debugFile)))
            fail("Diagnostics of $debugFile:\n${diagnostics.sortedWith(compareBy({ it.file }, { it.start })).joinToString("\n")}\n\nOther failures:\n$failureSummary")
        }
        val generator = FuzzProgramGenerator(seed)
        val mismatches = mutableListOf<String>()
        for (index in 0 until programs) {
            val program = generator.generate(index)
            mismatches += check(program)
        }
        if (mismatches.isNotEmpty()) {
            fail("${mismatches.size} mismatch(es) with seed $seed:\n\n${mismatches.joinToString("\n\n")}")
        }
    }

    private fun check(program: FuzzProgram): List<String> {
        val baseline = compile(program, "p${program.index}", program.render())
        // The framework prepends the header to the first file and the `// FILE:` line to each later file.
        // The offsets of a file shift by the length difference.
        val bases: Map<String, Int> = program.files.associate { file ->
            val analyzed = FuzzDiagnosticsSink.lastFileTexts[file.name]
                ?: fail("The framework didn't analyze ${file.name} of program ${program.index} of seed $seed")
            if (!analyzed.endsWith(file.text)) {
                fail("The analyzed ${file.name} doesn't end with the generated text in program ${program.index} of seed $seed:\n$analyzed")
            }
            file.name to analyzed.length - file.text.length
        }
        fun ReportedDiagnostic.matches(exemption: FuzzExemption): Boolean =
            file == exemption.file && start - bases.getValue(file) == exemption.start && end - bases.getValue(file) == exemption.end

        val errors = baseline.filter { it.severity == Severity.ERROR && it.name !in comparedDiagnostics }
        if (errors.isNotEmpty()) {
            val snippets = errors.joinToString("\n") { error ->
                val text = FuzzDiagnosticsSink.lastFileTexts[error.file].orEmpty()
                "$error: ...${text.substring((error.start - 1500).coerceAtLeast(0), (error.end + 120).coerceAtMost(text.length)).replace("\n", "⏎")}..."
            }
            return listOf("Program ${program.index} doesn't compile (a generator bug):\n$snippets\n${program.render()}")
        }
        val reportedRedundant = baseline.filter { it.name == WatchdogDiagnostics.REDUNDANT_EXEMPTION.name }
        val unmatched = reportedRedundant.filter { reported -> program.exemptions.none { reported.matches(it) } }
        if (unmatched.isNotEmpty()) {
            fail("REDUNDANT_EXEMPTION outside the generated exemptions in program ${program.index} of seed $seed: $unmatched\n${program.render()}")
        }

        val mismatches = mutableListOf<String>()
        program.exemptions.forEachIndexed { exemptionIndex, exemption ->
            val reported = reportedRedundant.any { it.matches(exemption) }
            val variant = compile(program, "p${program.index}e$exemptionIndex", program.render(blanked = exemption))
            // A blanked annotation at the start of an element moves the start of the element's range to the
            // next token. Both runs skip the blanks of the variant, so the same diagnostic has the same key.
            val variantTexts = FuzzDiagnosticsSink.lastFileTexts
            val baselineKeys = baseline.comparable(variantTexts)
            val variantKeys = variant.comparable(variantTexts)
            val changed = baselineKeys != variantKeys
            if (reported == changed) {
                val verdict = if (reported) "reported as redundant, but blanking it changes the diagnostics" else "not reported, but blanking it changes nothing"
                mismatches += buildString {
                    appendLine("Program ${program.index}, @${exemption.annotation} in ${exemption.file} at ${exemption.start}..${exemption.end}: $verdict.")
                    appendLine("  gone:  ${(baselineKeys - variantKeys).sorted()}")
                    appendLine("  new:   ${(variantKeys - baselineKeys).sorted()}")
                    append(program.renderMarked(exemption))
                }
            }
        }
        return mismatches
    }

    private fun compile(program: FuzzProgram, label: String, text: String): List<ReportedDiagnostic> {
        val path = directory.resolve("$label.kt")
        Files.writeString(path, text)
        FuzzDiagnosticsSink.begin()
        runner.initTestInfo(KotlinTestInfo(className = javaClass.name, methodName = label, tags = emptySet()))
        try {
            runner.runTest(path.toString())
        } catch (failure: AssertionError) {
            // The file has no markers, so the comparison of the framework fails on every reported diagnostic.
            if (!FuzzDiagnosticsSink.completed) throw failure
        }
        return FuzzDiagnosticsSink.end()
    }

    /** The watchdog diagnostics that count as the output, keyed by range after the whitespace of [texts]. */
    private fun List<ReportedDiagnostic>.comparable(texts: Map<String, String>): Set<String> =
        filter { it.name in comparedDiagnostics }.mapTo(hashSetOf()) { diagnostic ->
            val text = texts[diagnostic.file]
            var start = diagnostic.start
            if (text != null) {
                while (start < text.length && start < diagnostic.end && text[start].isWhitespace()) start++
            }
            "${diagnostic.file}:$start..${diagnostic.end} ${diagnostic.name}"
        }

    private companion object {
        val comparedDiagnostics: Set<String> = buildSet {
            WatchdogDiagnostics.allDiagnostics.mapTo(this) { it.name }
            add(WatchdogDiagnostics.PUBLIC_TYPE_WITH_INTERNAL_API.name)
            add(WatchdogDiagnostics.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY.name)
        }
    }
}

private fun FuzzProgram.renderMarked(exemption: FuzzExemption): String = buildString {
    append(header)
    for (file in files) {
        append("// FILE: ").append(file.name).append('\n')
        if (file.name != exemption.file) {
            append(file.text)
        } else {
            append(file.text, 0, exemption.start).append("<!HERE!>")
            append(file.text, exemption.start, exemption.end).append("<!>")
            append(file.text, exemption.end, file.text.length)
        }
    }
}

internal class ReportedDiagnostic(
    val module: String,
    val file: String,
    val name: String,
    val start: Int,
    val end: Int,
    val severity: Severity,
) {
    override fun toString(): String = "$module/$file:$start..$end $name"
}

/** The diagnostics of the compilation that runs on this thread. */
internal object FuzzDiagnosticsSink {
    private class Run(
        val diagnostics: MutableList<ReportedDiagnostic> = mutableListOf(),
        val fileTexts: MutableMap<String, String> = mutableMapOf(),
        var completed: Boolean = false,
    )

    /** The text of each file of the last run as the framework analyzed it, by file name. */
    var lastFileTexts: Map<String, String> = emptyMap()
        private set

    fun fileText(name: String, text: String) {
        current.get()?.fileTexts?.put(name, text)
    }

    private val current = ThreadLocal<Run?>()

    val completed: Boolean get() = current.get()?.completed == true

    fun begin() {
        current.set(Run())
    }

    fun add(diagnostic: ReportedDiagnostic) {
        current.get()?.diagnostics?.add(diagnostic)
    }

    fun complete() {
        current.get()?.completed = true
    }

    fun end(): List<ReportedDiagnostic> {
        val run = current.get() ?: return emptyList()
        current.remove()
        lastFileTexts = run.fileTexts
        return run.diagnostics
    }
}

internal class CollectingDiagnosticsHandler(testServices: TestServices) : FirAnalysisHandler(testServices) {
    override fun processModule(module: TestModule, info: FirOutputArtifact) {
        val diagnosticsPerFile = testServices.firDiagnosticCollectorService.getFrontendDiagnosticsForModule(info)
        for (part in info.partsForDependsOnModules) {
            for (file in part.module.files) {
                val firFile = info.mainFirFilesByTestFile[file] ?: continue
                FuzzDiagnosticsSink.fileText(file.name, testServices.sourceFileProvider.getOrCreateRealFileForSourceFile(file).readText())
                for (reported in diagnosticsPerFile[firFile]) {
                    val diagnostic = reported.diagnostic
                    FuzzDiagnosticsSink.add(
                        ReportedDiagnostic(
                            module = module.name,
                            file = file.name,
                            name = diagnostic.factory.name,
                            start = diagnostic.firstRange.startOffset,
                            end = diagnostic.firstRange.endOffset,
                            severity = diagnostic.severity,
                        )
                    )
                }
            }
        }
    }

    override fun processAfterAllModules(someAssertionWasFailed: Boolean) {
        FuzzDiagnosticsSink.complete()
    }
}

private class FuzzDiagnosticRunner : DevKitJvmDiagnosticTest({
    configureWatchdogDiagnosticTest()
    defaultDirectives {
        +DISABLE_FIR_DUMP_HANDLER
    }
    configureFirHandlersStep {
        useHandlers(::CollectingDiagnosticsHandler)
    }
})
