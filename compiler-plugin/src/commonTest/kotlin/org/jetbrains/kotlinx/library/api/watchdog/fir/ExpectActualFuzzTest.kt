package org.jetbrains.kotlinx.library.api.watchdog.fir

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.fail
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.test.services.KotlinTestInfo

/**
 * A differential fuzz test of the checks on `expect`/`actual` pairs. The test compiles each generated
 * multiplatform program and its two single-module views ([Form.COMMON_VIEW] and [Form.JVM_VIEW]).
 * A diagnostic gets the path of the innermost generated element around it, the same on an `expect`
 * and its `actual`. Two oracles:
 *
 * - The multiplatform program reports exactly the diagnostics of the JVM view and the
 *   [signatureDiagnostics] of the common view, each on one half of a pair. One exception: a
 *   common-only overload doesn't see the JVM-only overloads, so only a JVM-only overload reports their order.
 * - `REDUNDANT_EXEMPTION` agrees with blanking each exemption, as in [RedundantExemptionFuzzTest].
 *   The test blanks an exemption of an `expect` and its copy on the `actual` together. Both get one verdict.
 *   An exemption that reads the sibling overloads isn't judged.
 */
class ExpectActualFuzzTest {
    private val seed: Long = System.getProperty("watchdog.fuzz.seed")?.toLong() ?: 20261104L
    private val programs: Int = System.getProperty("watchdog.fuzz.programs")?.toInt() ?: 8
    private val directory: Path = Files.createTempDirectory("watchdog-expect-actual-fuzz")
    private val runner = FuzzDiagnosticRunner()

    @AfterTest
    fun deleteGeneratedPrograms() {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `a multiplatform program reports the diagnostics of its views`() {
        val generator = ExpectActualProgramGenerator(seed)
        val mismatches = mutableListOf<String>()
        for (index in 0 until programs) {
            mismatches += check(generator.generate(index))
        }
        if (mismatches.isNotEmpty()) {
            fail("${mismatches.size} mismatch(es) with seed $seed:\n\n${mismatches.joinToString("\n\n")}")
        }
    }

    private class Compiled(val diagnostics: List<ReportedDiagnostic>, val texts: Map<String, String>)

    private fun check(program: ExpectActualProgram): List<String> {
        val label = "p${program.index}"
        val mpp = compile("${label}mpp", program.renderMpp())
        val jvm = compile("${label}jvm", program.renderJvmView())
        val common = compile("${label}common", program.renderCommonView())
        for ((name, compiled, files, text) in listOf(
            Quad("multiplatform program", mpp, program.mpp, program.renderMpp()),
            Quad("JVM view", jvm, program.jvmView, program.renderJvmView()),
            Quad("common view", common, listOf(program.commonView), program.renderCommonView()),
        )) {
            val errors = compiled.diagnostics.filter { it.severity == Severity.ERROR && it.name !in comparedDiagnostics }
            if (errors.isNotEmpty()) {
                return listOf("The $name of program ${program.index} doesn't compile (a generator bug): $errors\n${text.numbered()}")
            }
            files.forEach { file ->
                val analyzed = compiled.texts[file.name] ?: fail("The framework didn't analyze ${file.name} of the $name of program ${program.index}")
                if (!analyzed.endsWith(file.text)) fail("The analyzed ${file.name} of the $name doesn't end with the generated text:\n$analyzed")
            }
        }

        val mismatches = mutableListOf<String>()
        val mppKeys = mpp.keys(program.mpp, flattened)
        val expected = jvm.keys(program.jvmView, flattened) + common.keys(listOf(program.commonView), signatureDiagnostics)
        val actual = mppKeys.map { it.key }.toSet()
        val inconsistentOrder = WatchdogDiagnostics.INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS.name
        val commonOnlyOverloads = common.keys(listOf(program.commonView), setOf(inconsistentOrder)).map { it.key }.toSet()
        val allowedMissing = expected.map { it.key }.filterTo(hashSetOf()) { key ->
            key.endsWith(" $inconsistentOrder") && key.substringBefore(' ') in program.commonOnlyPaths && key !in commonOnlyOverloads
        }
        val twice = mppKeys.groupBy { it.key }.filterValues { keys -> keys.map { it.file }.distinct().size > 1 }.keys
        val missing = expected.map { it.key }.toSet() - actual - allowedMissing
        if (missing.isNotEmpty() || (actual - expected.map { it.key }.toSet()).isNotEmpty() || twice.isNotEmpty()) {
            mismatches += buildString {
                appendLine("Program ${program.index}: the multiplatform diagnostics differ from the views.")
                appendLine("  missing:    ${missing.sorted()}")
                appendLine("  unexpected: ${(actual - expected.map { it.key }.toSet()).sorted()}")
                appendLine("  on both halves: ${twice.sorted()}")
                appendLine("--- multiplatform program")
                appendLine(program.renderMpp().numbered())
                appendLine("--- JVM view")
                append(program.renderJvmView().numbered())
            }
        }

        val baselineRedundant = mpp.diagnostics.filter { it.name == WatchdogDiagnostics.REDUNDANT_EXEMPTION.name }
        val bases = mpp.bases(program.mpp)
        fun ReportedDiagnostic.matches(exemption: FuzzExemption): Boolean =
            file == exemption.file && start - bases.getValue(file) == exemption.start && end - bases.getValue(file) == exemption.end
        val units = program.exemptions.groupBy { it.group ?: it }.values
        units.forEachIndexed { unitIndex, unit ->
            if (unit.first().annotation == "IntentionallyInconsistentParameterOrder") return@forEachIndexed
            val verdicts = unit.map { exemption -> baselineRedundant.any { it.matches(exemption) } }.distinct()
            val variant = compile("${label}e$unitIndex", program.renderMpp(blanked = unit))
            val baselineKeys = mpp.comparable(variant.texts)
            val variantKeys = variant.comparable(variant.texts)
            val changed = baselineKeys != variantKeys
            if (verdicts.size > 1 || verdicts.single() == changed) {
                val verdict = when {
                    verdicts.size > 1 -> "the copies got different verdicts"
                    changed -> "reported as redundant, but blanking it changes the diagnostics"
                    else -> "not reported, but blanking it changes nothing"
                }
                mismatches += buildString {
                    appendLine("Program ${program.index}, @${unit.first().annotation} at ${unit.joinToString { "${it.file}:${it.start}..${it.end}" }}: $verdict.")
                    appendLine("  gone: ${(baselineKeys - variantKeys).sorted()}")
                    appendLine("  new:  ${(variantKeys - baselineKeys).sorted()}")
                    append(program.renderMarked(unit).numbered())
                }
            }
        }
        return mismatches
    }

    private data class Quad(val name: String, val compiled: Compiled, val files: List<RenderedFile>, val text: String)

    private class Key(val key: String, val file: String)

    /** The length of the header and the `// FILE:` lines that the framework adds before each file. */
    private fun Compiled.bases(files: List<RenderedFile>): Map<String, Int> =
        files.associate { it.name to texts.getValue(it.name).length - it.text.length }

    private fun Compiled.keys(files: List<RenderedFile>, names: Set<String>): List<Key> {
        val bases = bases(files)
        val byName = files.associateBy { it.name }
        return diagnostics.filter { it.name in names }.map { diagnostic ->
            val file = byName.getValue(diagnostic.file)
            val end = diagnostic.end - bases.getValue(file.name)
            val path = if (diagnostic.name == WatchdogDiagnostics.TOP_LEVEL_API_WITHOUT_JVM_NAME.name) {
                "file:${file.name}"
            } else {
                file.spans.filter { it.start < end && end <= it.end }.minByOrNull { it.end - it.start }?.path ?: "file:${file.name}"
            }
            Key("$path ${diagnostic.name}", file.name)
        }
    }

    /** A range starts after its leading whitespace, because a blanked exemption becomes spaces. */
    private fun Compiled.comparable(texts: Map<String, String>): Set<String> =
        diagnostics.filter { it.name in comparedDiagnostics }.mapTo(hashSetOf()) { diagnostic ->
            val text = texts[diagnostic.file]
            var start = diagnostic.start
            if (text != null) {
                while (start < text.length && start < diagnostic.end && text[start].isWhitespace()) start++
            }
            "${diagnostic.file}:$start..${diagnostic.end} ${diagnostic.name}"
        }

    private fun compile(label: String, text: String): Compiled {
        val path = directory.resolve("$label.kt")
        Files.writeString(path, text)
        FuzzDiagnosticsSink.begin()
        runner.initTestInfo(KotlinTestInfo(className = javaClass.name, methodName = label, tags = emptySet()))
        try {
            runner.runTest(path.toString())
        } catch (failure: AssertionError) {
            // The file has no markers, so the framework fails on every diagnostic.
            if (!FuzzDiagnosticsSink.completed) throw failure
        }
        val diagnostics = FuzzDiagnosticsSink.end()
        return Compiled(diagnostics, FuzzDiagnosticsSink.lastFileTexts)
    }

    private fun String.numbered(): String = lines().mapIndexed { index, line -> "${(index + 1).toString().padStart(3)}  $line" }.joinToString("\n")

    private fun ExpectActualProgram.renderMarked(exemptions: List<FuzzExemption>): String {
        val text = StringBuilder(renderMpp())
        for (exemption in exemptions.sortedByDescending { text.indexOf("// FILE: ${it.file}\n") * 100_000 + it.start }) {
            val base = text.indexOf("// FILE: ${exemption.file}\n") + "// FILE: ${exemption.file}\n".length
            text.insert(base + exemption.end, "<!>").insert(base + exemption.start, "<!HERE!>")
        }
        return text.toString()
    }

    private companion object {
        val comparedDiagnostics: Set<String> = buildSet {
            WatchdogDiagnostics.allDiagnostics.mapTo(this) { it.name }
            add(WatchdogDiagnostics.PUBLIC_TYPE_WITH_INTERNAL_API.name)
            add(WatchdogDiagnostics.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY.name)
        }

        val flattened: Set<String> = comparedDiagnostics

        /** The diagnostics that a common consumer sees. The others are about JVM bytecode or bodies. */
        val signatureDiagnostics: Set<String> = setOf(
            WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN, WatchdogDiagnostics.SUBCLASS_OPT_IN_WITHOUT_MARKERS,
            WatchdogDiagnostics.EXHAUSTIVE_PUBLIC_API, WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API,
            WatchdogDiagnostics.FUNCTION_TYPE_ALIAS_PUBLIC_API, WatchdogDiagnostics.MUTABLE_COLLECTION_PUBLIC_API,
            WatchdogDiagnostics.PAIR_OR_TRIPLE_PUBLIC_API, WatchdogDiagnostics.REQUIRED_PARAMETER_AFTER_OPTIONAL,
            WatchdogDiagnostics.INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS, WatchdogDiagnostics.BOOLEAN_PARAMETER_PUBLIC_API,
            WatchdogDiagnostics.NULLABLE_BOOLEAN_PUBLIC_API,
        ).mapTo(hashSetOf()) { it.name } + WatchdogDiagnostics.PUBLIC_TYPE_WITH_INTERNAL_API.name
    }
}
