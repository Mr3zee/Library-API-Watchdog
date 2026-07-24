@file:Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")

package org.jetbrains.kotlinx.libs.api.watchdog.fixer

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitTest
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.runners.AbstractFirPhasedDiagnosticTest
import org.jetbrains.kotlin.test.services.KotlinTestInfo
import org.jetbrains.kotlinx.libs.api.watchdog.WatchdogCompilerPluginRegistrar

/**
 * Runs the fixer over the compiler plugin's own diagnostics fixtures.
 *
 * The inline `<!DIAGNOSTIC!>...<!>` expectations are the diagnostics report: removing the
 * markers gives the source handed to [ExemptionFixer], and their ranges become
 * [RecordedDiagnostic] offsets. The fixed source is then compiled by the same diagnostics test
 * runner. Only markers for deliberately unfixable diagnostics are carried into that generated
 * expectation; every fixable diagnostic must have disappeared because of the inserted exemption.
 */
class CompilerPluginTestDataFixerTest {
    private val parser = KotlinFileParser()
    private val fixer = ExemptionFixer(parser)
    private val tempDir = createTempDirectory("watchdog-fixer-compiler-data").toFile()

    @AfterTest
    fun tearDownFixerTestData() {
        parser.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun fixesCompilerPluginDiagnosticsTestData() {
        val diagnosticsDirectory = assertNotNull(
            javaClass.getResource("/diagnostics"),
            "compiler-plugin/src/test/data must be on the exempts-fixer test classpath",
        ).let { File(it.toURI()) }
        val fixtures = diagnosticsDirectory.listFiles { file -> file.extension == "kt" }
            .orEmpty()
            .sortedBy(File::getName)

        assertTrue(fixtures.isNotEmpty(), "No compiler-plugin diagnostics fixtures found")
        val generatedFiles = fixtures.map { fixture ->
            val generatedExpectation = fixFixture(fixture)
            File(tempDir, fixture.name).apply {
                writeText(generatedExpectation)
            }
        }
        validateWithCompilerPlugin(generatedFiles)
    }

    private fun fixFixture(fixture: File): String {
        val fixtureText = fixture.readText()
        val sections = TestDataSection.split(fixtureText, fixture.name)
        val generated = buildString {
            sections.forEach { section ->
                append(section.prefix)
                append(fixSourceFile(fixture.name, section.fileName, section.markedText))
            }
        }
        // These sources retain the original fixture's deliberately muted diagnostics, so a clean
        // expected marker set does not necessarily mean that code generation can run.
        return "// DISABLE_NEXT_PHASE_SUGGESTION\n$generated"
    }

    private fun fixSourceFile(fixtureName: String, fileName: String, markedText: String): String {
        val source = MarkedSource.parse(markedText)
        if (source.diagnostics.isEmpty()) return source.text

        val filePath = "/compiler-plugin-test-data/$fixtureName/$fileName"
        val diagnostics = source.diagnostics.map {
            RecordedDiagnostic(it.name, filePath, it.startOffset, it.endOffset)
        }
        val result = fixer.fix(filePath, source.text, diagnostics)
        val fixedText = result.newText ?: source.text

        result.skipped.forEach { skipped ->
            val resolution = ExemptionRegistry.resolutionFor(skipped.diagnostic)
            assertTrue(
                resolution is FixResolution.Unfixable ||
                        skipped.diagnostic == "UNDOCUMENTED_PUBLIC_API" &&
                        skipped.reason.contains("no declaration accepting @IntentionallyUndocumented"),
                "A fixable marker in $fixtureName/$fileName was unexpectedly skipped: " +
                        "${skipped.diagnostic}: ${skipped.reason}",
            )
        }

        val remainingDiagnostics = diagnosticsThatWereSkipped(source, result.skipped)
            .map { diagnostic ->
                diagnostic.copy(
                    startOffset = result.mapOffset(diagnostic.startOffset, afterInsertionsAtOffset = true),
                    endOffset = result.mapOffset(diagnostic.endOffset, afterInsertionsAtOffset = false),
                )
            }

        return MarkedSource.render(fixedText, remainingDiagnostics)
    }

    private fun diagnosticsThatWereSkipped(
        source: MarkedSource,
        skipped: List<SkippedDiagnostic>,
    ): List<MarkedDiagnostic> {
        val remainingByNameAndLine = skipped.groupingBy { it.diagnostic to it.line }.eachCount().toMutableMap()
        val remaining = source.diagnostics.filter { diagnostic ->
            val line = diagnostic.lineIn(source.text)
            val key = diagnostic.name to line
            val count = remainingByNameAndLine.getOrDefault(key, 0)
            if (count == 0) {
                false
            } else {
                remainingByNameAndLine[key] = count - 1
                true
            }
        }
        assertTrue(
            remainingByNameAndLine.values.all { it == 0 },
            "Could not map skipped diagnostics back to source markers: $remainingByNameAndLine",
        )
        return remaining
    }

    private fun FileFixResult.mapOffset(offset: Int, afterInsertionsAtOffset: Boolean): Int =
        offset + insertions.sumOf { insertion ->
            if (insertion.offset < offset || afterInsertionsAtOffset && insertion.offset == offset) {
                insertion.text.length
            } else {
                0
            }
        }

    /**
     * The fixer needs the relocated compiler embeddable while Kotlin's diagnostics test framework
     * needs the regular compiler. Their classes share package names and cannot coexist in one
     * classloader, so compile the generated expectations in a clean child JVM.
     */
    private fun validateWithCompilerPlugin(generatedFiles: List<File>) {
        val runtimeClasspath = System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .map(::File)
        val compilerClasspath = runtimeClasspath
            .filterNot { it.name.contains("compiler-embeddable") || it.name.contains("daemon-embeddable") }
            .joinToString(File.pathSeparator, transform = File::getAbsolutePath)
        val annotationsClasspath = runtimeClasspath
            .filter { "plugin-annotations" in it.path }
            .joinToString(File.pathSeparator, transform = File::getAbsolutePath)

        assertTrue(annotationsClasspath.isNotEmpty(), "plugin-annotations is missing from the test runtime")
        val javaExecutable = File(System.getProperty("java.home"), "bin/java")
        val command = mutableListOf(
            javaExecutable.absolutePath,
            "-DdefaultTestDataLibraries.jvm.classpath=$annotationsClasspath",
            "-DdefaultTestDataLibraries.js.classpath=$annotationsClasspath",
        )
        command += standardLibraryProperties(runtimeClasspath)
        command += listOf(
            "-cp",
            compilerClasspath,
            FixedOutputCompilerTestMain::class.java.name,
        )
        command += generatedFiles.map(File::getAbsolutePath)

        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), output)
    }

    private fun standardLibraryProperties(runtimeClasspath: List<File>): List<String> {
        fun jarMatching(description: String, predicate: (String) -> Boolean): String {
            val file = runtimeClasspath.firstOrNull { it.isFile && predicate(it.name) }
            return assertNotNull(file, "$description is missing from the test runtime").absolutePath
        }

        val stdlib = jarMatching("kotlin-stdlib") {
            it.startsWith("kotlin-stdlib-") && !it.startsWith("kotlin-stdlib-jdk")
        }
        val stdlibJdk8 = jarMatching("kotlin-stdlib-jdk8") { it.startsWith("kotlin-stdlib-jdk8-") }
        val reflect = jarMatching("kotlin-reflect") { it.startsWith("kotlin-reflect-") }
        val kotlinTest = jarMatching("kotlin-test") {
            it.startsWith("kotlin-test-") && !it.startsWith("kotlin-test-junit")
        }
        val scriptRuntime = jarMatching("kotlin-script-runtime") { it.startsWith("kotlin-script-runtime-") }
        val annotations = jarMatching("annotations") { it.startsWith("annotations-") }

        return listOf(
            "-Dorg.jetbrains.kotlin.test.kotlin-stdlib=$stdlib",
            "-Dorg.jetbrains.kotlin.test.kotlin-stdlib-jdk8=$stdlibJdk8",
            "-Dorg.jetbrains.kotlin.test.kotlin-reflect=$reflect",
            "-Dorg.jetbrains.kotlin.test.kotlin-test=$kotlinTest",
            "-Dorg.jetbrains.kotlin.test.kotlin-script-runtime=$scriptRuntime",
            "-Dorg.jetbrains.kotlin.test.kotlin-annotations-jvm=$annotations",
        )
    }
}

/** Entry point for the non-embeddable compiler process launched by the fixer test. */
@OptIn(ExperimentalCompilerApi::class)
object FixedOutputCompilerTestMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val runner = FixedOutputDiagnosticRunner()
        args.forEach { filePath ->
            runner.initTestInfo(
                KotlinTestInfo(
                    className = CompilerPluginTestDataFixerTest::class.java.name,
                    methodName = File(filePath).nameWithoutExtension,
                    tags = emptySet(),
                )
            )
            runner.run(filePath)
        }
    }
}

@OptIn(ExperimentalCompilerApi::class)
private class FixedOutputDiagnosticRunner : DevKitTest(
    object : AbstractFirPhasedDiagnosticTest(FirParser.LightTree) {},
    {
        defaultDirectives {
            +JvmEnvironmentConfigurationDirectives.FULL_JDK
            +CodegenTestDirectives.IGNORE_DEXING
            +FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS
        }
    },
    { configurePlugin(WatchdogCompilerPluginRegistrar()) },
) {
    fun run(filePath: String) = runTest(filePath)
}

private data class TestDataSection(
    /** Fixture directives and the `// FILE:` line preceding [markedText]. */
    val prefix: String,
    val fileName: String,
    val markedText: String,
) {
    companion object {
        private val fileDirective = Regex(
            "(?m)^(?:// MODULE: [^\\r\\n]+\\R)?// FILE: ([^\\r\\n]+)\\R"
        )

        fun split(text: String, defaultFileName: String): List<TestDataSection> {
            val matches = fileDirective.findAll(text).toList()
            if (matches.isEmpty()) return listOf(TestDataSection("", defaultFileName, text))

            return matches.mapIndexed { index, match ->
                val prefixStart = if (index == 0) 0 else match.range.first
                val contentStart = match.range.last + 1
                val nextStart = matches.getOrNull(index + 1)?.range?.first ?: text.length
                TestDataSection(
                    prefix = text.substring(prefixStart, contentStart),
                    fileName = match.groupValues[1],
                    markedText = text.substring(contentStart, nextStart),
                )
            }
        }
    }
}

private data class MarkedSource(
    val text: String,
    val diagnostics: List<MarkedDiagnostic>,
) {
    companion object {
        private val openingMarker = Regex("<!([A-Z][A-Z0-9_]*(?:, [A-Z][A-Z0-9_]*)*)!>")

        fun parse(markedText: String): MarkedSource {
            val text = StringBuilder(markedText.length)
            val open = ArrayDeque<OpenMarker>()
            val diagnostics = mutableListOf<MarkedDiagnostic>()
            var sourceOffset = 0

            while (sourceOffset < markedText.length) {
                if (markedText.startsWith("<!>", sourceOffset)) {
                    val marker = open.removeLastOrNull()
                        ?: error("Closing diagnostic marker without an opening marker")
                    marker.names.forEach { name ->
                        diagnostics += MarkedDiagnostic(name, marker.startOffset, text.length)
                    }
                    sourceOffset += 3
                    continue
                }

                val opening = openingMarker.find(markedText, sourceOffset)
                    ?.takeIf { it.range.first == sourceOffset }
                if (opening != null) {
                    open += OpenMarker(opening.groupValues[1].split(", "), text.length)
                    sourceOffset = opening.range.last + 1
                    continue
                }

                text.append(markedText[sourceOffset])
                sourceOffset++
            }

            check(open.isEmpty()) { "Unclosed diagnostic marker(s): ${open.joinToString { it.names.joinToString() }}" }
            return MarkedSource(
                text = text.toString(),
                diagnostics = diagnostics.sortedWith(compareBy(MarkedDiagnostic::startOffset, MarkedDiagnostic::endOffset)),
            )
        }

        fun render(text: String, diagnostics: List<MarkedDiagnostic>): String {
            if (diagnostics.isEmpty()) return text

            val ranges = diagnostics.groupBy { it.startOffset to it.endOffset }
                .map { (range, diagnosticsAtRange) ->
                    DiagnosticRange(
                        names = diagnosticsAtRange.map(MarkedDiagnostic::name).sorted(),
                        startOffset = range.first,
                        endOffset = range.second,
                    )
                }
            val openings = ranges.groupBy(DiagnosticRange::startOffset)
            val closings = ranges.groupBy(DiagnosticRange::endOffset)

            return buildString(text.length + ranges.size * 16) {
                for (offset in 0..text.length) {
                    closings[offset].orEmpty()
                        .sortedByDescending(DiagnosticRange::startOffset)
                        .forEach { append("<!>") }
                    openings[offset].orEmpty()
                        .sortedByDescending(DiagnosticRange::endOffset)
                        .forEach { append("<!${it.names.joinToString()}!>") }
                    if (offset < text.length) append(text[offset])
                }
            }
        }
    }
}

private data class OpenMarker(val names: List<String>, val startOffset: Int)

private data class DiagnosticRange(
    val names: List<String>,
    val startOffset: Int,
    val endOffset: Int,
)

private data class MarkedDiagnostic(
    val name: String,
    val startOffset: Int,
    val endOffset: Int,
) {
    fun lineIn(source: String): Int = source.take(startOffset).count { it == '\n' } + 1
}
