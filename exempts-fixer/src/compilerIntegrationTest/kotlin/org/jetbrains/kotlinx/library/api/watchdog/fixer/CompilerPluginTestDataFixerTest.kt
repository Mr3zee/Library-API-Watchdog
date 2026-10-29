@file:Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")

package org.jetbrains.kotlinx.library.api.watchdog.fixer

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitTest
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageFeature.*
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.directives.LanguageSettingsDirectives.LANGUAGE
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.runners.AbstractFirPhasedDiagnosticTest
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.KotlinTestInfo
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlinx.library.api.watchdog.WatchdogComponentRegistrar
import org.jetbrains.kotlinx.library.api.watchdog.WatchdogConfigurationKeys
import org.opentest4j.AssertionFailedError

private val PATH_SEPARATOR = File.pathSeparator
private const val REDUNDANT_EXEMPTION = "REDUNDANT_EXEMPTION"

/**
 * A callable fix can make parameter exemptions redundant, so the verification run hides `REDUNDANT_EXEMPTION`.
 */
class CompilerPluginTestDataFixerTest {
    private val parser = KotlinFileParser()
    private val fixer = ExemptionFixer(parser)
    private val tempDir = Files.createTempDirectory("watchdog-fixer-compiler-data")

    @AfterTest
    fun tearDownFixerTestData() {
        parser.close()
        Files.walk(tempDir).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `fixes compiler plugin diagnostics test data`() {
        val diagnosticsDirectory = assertNotNull(
            javaClass.getResource("/diagnostics"),
            "compiler-plugin/src/test/data must be on the exempts-fixer test classpath",
        ).let { Paths.get(it.toURI()) }
        val fixtures = Files.list(diagnosticsDirectory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".kt") }
                .sorted(compareBy { it.fileName.toString() })
                .iterator()
                .asSequence()
                .toList()
        }

        assertTrue(fixtures.isNotEmpty(), "No compiler-plugin diagnostics fixtures found")
        val generatedFiles = fixtures.map { fixture ->
            val generatedExpectation = fixFixture(fixture)
            tempDir.resolve(fixture.fileName).also { Files.writeString(it, generatedExpectation) }
        }
        validateWithCompilerPlugin(generatedFiles)
    }

    private fun fixFixture(fixture: Path): String {
        val fixtureName = fixture.fileName.toString()
        val fixtureText = Files.readString(fixture)
        val sections = TestDataSection.split(fixtureText, fixtureName)
        val generated = buildString {
            sections.forEach { section ->
                append(section.prefix)
                append(fixSourceFile(fixtureName, section.fileName, section.markedText))
            }
        }
        return buildString {
            appendLine("// DISABLE_NEXT_PHASE_SUGGESTION")
            appendLine("// DIAGNOSTICS: -$REDUNDANT_EXEMPTION")
            append(generated)
        }
    }

    private fun fixSourceFile(fixtureName: String, fileName: String, markedText: String): String {
        val source = MarkedSource.parse(markedText)
        if (source.diagnostics.isEmpty()) {
            return source.text
        }

        val filePath = "/compiler-plugin-test-data/$fixtureName/$fileName"
        val diagnostics = source.diagnostics.map {
            RecordedDiagnostic(it.name, filePath, it.startOffset, it.endOffset)
        }
        val result = fixer.fix(filePath, source.text, diagnostics)
        val fixedText = result.newText ?: source.text

        result.skipped.forEach { skipped ->
            val resolution = ExemptionRegistry.resolutionFor(skipped.diagnostic)
            assertTrue(
                resolution is FixResolution.Unfixable,
                "A fixable marker in $fixtureName/$fileName was unexpectedly skipped: " +
                        "${skipped.diagnostic}: ${skipped.reason}",
            )
        }

        val remainingDiagnostics = diagnosticsThatWereSkipped(source, fixedText, result, result.skipped)
            .filterNot { diagnostic ->
                result.edits.any { it.text.isEmpty() && it.offset <= diagnostic.startOffset && diagnostic.endOffset <= it.endOffset }
            }
            .map { diagnostic ->
                diagnostic.copy(
                    startOffset = result.mapOffset(diagnostic.startOffset, afterEditsAtOffset = true),
                    endOffset = result.mapOffset(diagnostic.endOffset, afterEditsAtOffset = false),
                )
            }

        return MarkedSource.render(fixedText, remainingDiagnostics)
    }

    private fun diagnosticsThatWereSkipped(
        source: MarkedSource,
        fixedText: String,
        result: FileFixResult,
        skipped: List<SkippedDiagnostic>,
    ): List<MarkedDiagnostic> {
        val remainingByNameAndLine = skipped.groupingBy { it.diagnostic to it.line }.eachCount().toMutableMap()
        val remaining = source.diagnostics.filter { diagnostic ->
            val relocatedDiagnostic = diagnostic.copy(
                startOffset = result.mapOffset(diagnostic.startOffset, afterEditsAtOffset = true),
            )
            val line = relocatedDiagnostic.lineIn(fixedText)
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

    private fun FileFixResult.mapOffset(offset: Int, afterEditsAtOffset: Boolean): Int =
        offset + edits.sumOf { edit ->
            if (edit.endOffset < offset || afterEditsAtOffset && edit.endOffset == offset) {
                edit.lengthDelta
            } else {
                0
            }
        }

    /**
     * [KotlinFileParser] already set up a standalone PSI application in this JVM, so the
     * diagnostics framework runs in a child JVM.
     */
    private fun validateWithCompilerPlugin(generatedFiles: List<Path>) {
        val runtimeClasspath = System.getProperty("java.class.path")
            .split(PATH_SEPARATOR)
            .map(Paths::get)
        val compilerClasspath = runtimeClasspath
            .joinToString(PATH_SEPARATOR) { it.toAbsolutePath().toString() }
        val annotationsClasspath = runtimeClasspath
            .filter { "plugin-annotations" in it.toString() }
            .joinToString(PATH_SEPARATOR) { it.toAbsolutePath().toString() }

        assertTrue(annotationsClasspath.isNotEmpty(), "plugin-annotations is missing from the test runtime")
        val javaExecutable = Paths.get(System.getProperty("java.home"), "bin", "java")
        val command = mutableListOf(
            javaExecutable.toAbsolutePath().toString(),
            "-DdefaultTestDataLibraries.jvm.classpath=$annotationsClasspath",
            "-DdefaultTestDataLibraries.js.classpath=$annotationsClasspath",
        )
        command += standardLibraryProperties(runtimeClasspath)
        command += listOf(
            "-cp",
            compilerClasspath,
            FixedOutputCompilerTestMain::class.java.name,
        )
        command += generatedFiles.map { it.toAbsolutePath().toString() }

        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), output)
    }

    private fun standardLibraryProperties(runtimeClasspath: List<Path>): List<String> {
        fun jarMatching(description: String, predicate: (String) -> Boolean): String {
            val file = runtimeClasspath.firstOrNull {
                Files.isRegularFile(it) && predicate(it.fileName.toString())
            }
            return assertNotNull(file, "$description is missing from the test runtime")
                .toAbsolutePath()
                .toString()
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

object FixedOutputCompilerTestMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val runner = FixedOutputDiagnosticRunner()
        args.forEach { filePath ->
            runner.initTestInfo(
                KotlinTestInfo(
                    className = CompilerPluginTestDataFixerTest::class.java.name,
                    methodName = Paths.get(filePath).fileName.toString().substringBeforeLast('.'),
                    tags = emptySet(),
                )
            )
            try {
                runner.run(filePath)
            } catch (failure: AssertionFailedError) {
                System.err.println("Expected test data:\n${Files.readString(Paths.get(filePath))}")
                System.err.println("Actual test data:\n${failure.actual?.value}")
                throw failure
            }
        }
    }
}

private class FixedOutputDiagnosticRunner : DevKitTest(
    object : AbstractFirPhasedDiagnosticTest(FirParser.LightTree) {},
    {
        defaultDirectives {
            +JvmEnvironmentConfigurationDirectives.FULL_JDK
            +CodegenTestDirectives.IGNORE_DEXING
            +FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS
            LANGUAGE with listOf(
                "+$PropertyParamAnnotationDefaultTargetMode",
                "-$AnnotationDefaultTargetMigrationWarning",
                "+$ContextParameters",
            )
        }
    },
    {
        configurePlugin(WatchdogComponentRegistrar())
        useConfigurators(::WatchdogOptionsConfigurator)
    },
) {
    fun run(filePath: String) = runTest(filePath)
}

/** A copy of the directives of the compiler plugin, whose test fixtures aren't on this classpath. */
private object WatchdogTestDirectives : SimpleDirectivesContainer() {
    val INTERNAL_API_ANNOTATIONS by stringDirective(
        description = "Annotation FQ names configured as internal API markers (internalApiAnnotation option)",
    )

    val IGNORE_WHEN_ANNOTATED by stringDirective(
        description = "Annotation ignore rules in the form of the ignoreWhenAnnotated option",
    )

    val REPORT_UNDOCUMENTED_CLASS_PROPERTIES by directive(
        description = "Member properties of a class without KDoc are reported (reportUndocumentedClassProperties=true)",
    )
}

private class WatchdogOptionsConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override val directiveContainers: List<DirectivesContainer>
        get() = listOf(WatchdogTestDirectives)

    override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
        val annotations = module.directives[WatchdogTestDirectives.INTERNAL_API_ANNOTATIONS]
        if (annotations.isNotEmpty()) {
            configuration.put(WatchdogConfigurationKeys.INTERNAL_API_ANNOTATIONS, annotations.toSet())
        }
        val ignoreRules = module.directives[WatchdogTestDirectives.IGNORE_WHEN_ANNOTATED]
        if (ignoreRules.isNotEmpty()) {
            configuration.put(
                WatchdogConfigurationKeys.ANNOTATION_IGNORE_RULES,
                ignoreRules.groupBy({ it.substringBefore(':') }, { it.substringAfter(':') })
                    .mapValues { (_, annotations) -> annotations.toSet() },
            )
        }
        if (WatchdogTestDirectives.REPORT_UNDOCUMENTED_CLASS_PROPERTIES in module.directives) {
            configuration.put(WatchdogConfigurationKeys.REPORT_UNDOCUMENTED_CLASS_PROPERTIES, true)
        }
    }
}

private data class TestDataSection(
    /** The directives and the `// FILE:` line in front of [markedText]. */
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
                        .forEach { _ -> append("<!>") }
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
