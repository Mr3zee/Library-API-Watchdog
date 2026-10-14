@file:Suppress("RedundantVisibilityModifier", "FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.Source
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.kotlinx.library.api.watchdog.Severity.WARNING
import org.junit.Test

/**
 * Compiles every public API sample of the docs with every check on. The compiler must report
 * exactly the `!diag` annotations of each sample: the same diagnostics, at the ranges the
 * annotations select, with the parameters they list. The test resolves a range like Code Hike:
 *
 * - The regex runs on the rendered line after the annotation. A rendered line is a line that
 *   isn't a `// !` comment.
 * - The `m` flag extends the search to the lines after it.
 * - The `g` flag selects every match.
 * - Capture group 1 narrows the range when the regex has one.
 * - A compiler range that spans lines counts by its first line.
 *
 * Each sample compiles in its own package, and under the file name of its code fence when the
 * fence has one. The test maps the package back to the one the sample declares before it
 * compares the parameters.
 */
class DocsExamplesTest {
    @Test
    fun `examples report exactly their annotated diagnostics`() {
        val samples = documentationSamples()
        val project = jvmProject {
            apiWatchdog {
                suggestAbiValidation = false
                severityOfEveryCheck(WARNING)
            }
            buildScript(
                """
                    kotlin {
                        compilerOptions.freeCompilerArgs.addAll(
                            "-P",
                            "plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticsOutputFile=" +
                                layout.projectDirectory.file("$REPORT_FILE").asFile.absolutePath,
                        )
                    }
                    // The samples show the dependency with the coordinates the message echoes.
                    configurations.all {
                        resolutionStrategy.dependencySubstitution {
                            substitute(module("$EXTERNAL_MODELS")).using(project(":exampleModels"))
                        }
                    }
                """
            )
            kotlinFile("DocsSupport", text = supportFile)
            sources(*samples.map(DocumentationSample::source).toTypedArray())
            dependencies(implementation("$EXTERNAL_MODELS:$EXTERNAL_MODELS_VERSION"))
            subproject("exampleModels") {
                kotlinFile(
                    "ExternalModel",
                    text = """
                        package com.example.models

                        /** A model supplied by an external dependency. */
                        public class ExternalModel
                    """,
                )
            }
        }

        // Some samples show always-error diagnostics, so the compilation fails.
        val result = project.buildAndFail("compileKotlin", "--stacktrace")
        result.assertOutputLacks("Unresolved reference")
        result.assertOutputLacks("Syntax error")
        val unexpectedCompilerErrors = result.output.lineSequence()
            .filter { it.startsWith("e: ") }
            .filterNot { line -> alwaysErrorDiagnostics.any { it.pattern.containsMatchIn(line) } }
            .toList()
        assertEquals(emptyList(), unexpectedCompilerErrors, unexpectedCompilerErrors.joinToString("\n"))

        val report = project.file(REPORT_FILE)
        assertTrue(report.isFile, "The compiler wrote no docs diagnostics report:\n${result.output}")
        val pokoSamples = samples.filter { "@Poko" in it.code }.mapTo(mutableSetOf(), DocumentationSample::directory)
        val recordedBySample = report.readLines()
            .filter(String::isNotBlank)
            .map(RecordedDiagnostic::parse)
            .groupBy { File(it.filePath).parentFile.name }
            .mapValues { (sample, diagnostics) ->
                // The project has its own `@Poko` annotation, not the Poko compiler plugin.
                diagnostics.filterNot { sample in pokoSamples && it.name in POKO_GENERATED_DIAGNOSTICS }
            }
        val unexpectedSources = recordedBySample.keys - samples.map(DocumentationSample::directory).toSet()
        assertEquals(emptySet(), unexpectedSources, "Diagnostics from audit support sources: $unexpectedSources")

        val mismatches = samples.mapNotNull { sample ->
            val expected = sample.expectedReports().sorted().map(SampleReport::describe)
            val actual = sample.actualReports(recordedBySample[sample.directory].orEmpty()).sorted().map(SampleReport::describe)
            if (actual == expected) null else buildString {
                appendLine("${sample.location}:")
                appendLine("  annotated:")
                expected.forEach { appendLine("    $it") }
                appendLine("  compiler reported:")
                actual.forEach { appendLine("    $it") }
            }
        }
        assertEquals(emptyList(), mismatches, mismatches.joinToString(separator = "\n"))
    }

    private fun documentationSamples(): List<DocumentationSample> {
        val docs = findRepositoryRoot().resolve("docs/pages")
        var index = 0
        return docs.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .sortedBy { it.path }
            .flatMap { file ->
                extractKotlinBlocks(file).asSequence().mapNotNull { block ->
                    if (!PUBLIC_DECLARATION.containsMatchIn(block.code)) return@mapNotNull null
                    index++
                    val name = "Sample%03d".format(index)
                    DocumentationSample(
                        name = name,
                        fileName = block.fileName?.removeSuffix(".kt") ?: name,
                        location = "${file.relativeTo(findRepositoryRoot()).invariantSeparatorsPath}:${block.line}",
                        code = block.code,
                    )
                }
            }
            .toList()
    }

    private fun extractKotlinBlocks(file: File): List<CodeBlock> {
        val lines = file.readLines()
        val result = mutableListOf<CodeBlock>()
        var openingLine = -1
        var fileName: String? = null
        var contents = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val fence = if (openingLine < 0) KOTLIN_FENCE.matchEntire(line) else null
            when {
                fence != null -> {
                    openingLine = index + 1
                    fileName = fence.groups[1]?.value?.takeIf { it.endsWith(".kt") }
                    contents = mutableListOf()
                }
                openingLine >= 0 && line == "```" -> {
                    result += CodeBlock(openingLine + 1, fileName, contents.joinToString("\n"))
                    openingLine = -1
                }
                openingLine >= 0 -> contents += line
            }
        }
        require(openingLine < 0) { "Unclosed Kotlin code fence in ${file.path}:${openingLine + 1}" }
        return result
    }

    private fun findRepositoryRoot(): File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { it.resolve("docs/pages").isDirectory && it.resolve("settings.gradle.kts").isFile }

    /** [fileName] is the `.kt` name after the fence, or null. */
    private data class CodeBlock(val line: Int, val fileName: String?, val code: String)

    /** A line of the `diagnosticsOutputFile` of the compiler plugin. */
    private class RecordedDiagnostic(
        val name: String,
        val filePath: String,
        val rangeStart: Int,
        val rangeEnd: Int,
        val parameters: List<String>,
    ) {
        companion object {
            fun parse(line: String): RecordedDiagnostic {
                val parts = line.split('\t')
                require(parts.size >= 6) { "Malformed diagnostics report line: '$line'" }
                return RecordedDiagnostic(
                    name = parts[0],
                    filePath = parts[1],
                    rangeStart = parts[4].toInt(),
                    rangeEnd = parts[5].toInt(),
                    parameters = parts.drop(6).map(::unescape),
                )
            }

            private fun unescape(field: String): String = buildString {
                var index = 0
                while (index < field.length) {
                    val char = field[index++]
                    if (char != '\\' || index == field.length) {
                        append(char)
                        continue
                    }
                    when (val escaped = field[index++]) {
                        't' -> append('\t')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        else -> append(escaped)
                    }
                }
            }
        }
    }

    /**
     * One diagnostic of a sample. [line] is the 1-based line in the rendered sample, or null when the
     * compiler reported it outside the sample's own lines. [fromColumn] and [toColumn] are the
     * 1-based inclusive columns on that line, as Code Hike counts them.
     */
    private data class SampleReport(
        val name: String,
        val line: Int?,
        val fromColumn: Int,
        val toColumn: Int,
        val text: String,
        val parameters: List<String>,
    ) : Comparable<SampleReport> {
        override fun compareTo(other: SampleReport): Int = compareValuesBy(
            this, other,
            { it.line ?: Int.MAX_VALUE }, { it.fromColumn }, { it.toColumn }, { it.name }, { it.parameters.toString() },
        )

        fun describe(): String = buildString {
            append(name)
            if (line == null) append(" outside the sample lines") else append(" at $line:$fromColumn-$toColumn")
            append(" '").append(text.replace("\n", "\\n")).append("' ")
            append(parameters.map { it.replace("\n", "\\n") })
        }
    }

    /** A line of the compiled file: its text and its 1-based line in the rendered sample, or null. */
    private class SourceLine(val text: String, val renderedLine: Int?)

    private class DocumentationSample(
        val name: String,
        val fileName: String,
        val location: String,
        val code: String,
    ) {
        /** The source directory of the sample under `src/main/kotlin/docs`, and its package segment. */
        val directory: String = name.lowercase()

        private val lines: List<String> = code.lines()

        private val declaredPackage: String? = lines.firstOrNull { it.startsWith("package ") }
            ?.removePrefix("package ")?.trim()

        /** The 1-based rendered line of each line of [code], or null for a Code Hike comment. */
        private val renderedLineOf: List<Int?> = buildList {
            var next = 1
            for (line in lines) add(if (line.isCodeHikeComment()) null else next++)
        }

        private val renderedLines: List<String> = lines.filterNot { it.isCodeHikeComment() }

        private val sourceLines: List<SourceLine> = standaloneSource()

        fun source(): Source = Source.kotlin(sourceLines.joinToString("\n", transform = SourceLine::text))
            .withPath("docs/$directory", fileName)
            .build()

        fun expectedReports(): List<SampleReport> = buildList {
            var nextRendered = 0
            for (line in lines) {
                val annotation = DIAGNOSTIC_ANNOTATION.matchEntire(line)
                if (annotation == null) {
                    if (!line.isCodeHikeComment()) nextRendered++
                    continue
                }
                val (pattern, flags, diagnosticName, encodedParameters) = annotation.destructured
                val diagnostic = Diagnostic.entries.firstOrNull { it.name == diagnosticName }
                    ?: error("Unknown diagnostic $diagnosticName in $location")
                val parameters = parseParameters(encodedParameters)
                    .mapIndexed { index, parameter -> diagnostic.resolve(index, parameter) }
                val regex = Regex(pattern, if ('i' in flags) setOf(RegexOption.IGNORE_CASE) else emptySet())
                val candidates = if ('m' in flags) renderedLines.drop(nextRendered) else renderedLines.drop(nextRendered).take(1)
                var found = false
                for ((offset, candidate) in candidates.withIndex()) {
                    val matches = if ('g' in flags) regex.findAll(candidate).toList() else listOfNotNull(regex.find(candidate))
                    for (match in matches) {
                        val group = (if (match.groups.size > 1) match.groups[1] else null) ?: match.groups[0]!!
                        add(
                            SampleReport(
                                name = diagnosticName,
                                line = nextRendered + offset + 1,
                                fromColumn = group.range.first + 1,
                                toColumn = group.range.last + 1,
                                text = group.value,
                                parameters = parameters,
                            )
                        )
                    }
                    found = found || matches.isNotEmpty()
                    if (found && 'g' !in flags) break
                }
                require(found) { "Diagnostic range /$pattern/$flags matches no code in $location" }
            }
        }

        fun actualReports(recorded: List<RecordedDiagnostic>): List<SampleReport> {
            if (recorded.isEmpty()) return emptyList()
            val written = File(recorded.first().filePath).readText()
            assertEquals(
                sourceLines.joinToString("\n", transform = SourceLine::text).trimEnd(),
                written.trimEnd(),
                "The compiled file of $name differs from the sample at $location",
            )
            return recorded.map { diagnostic ->
                val lineStart = written.lastIndexOf('\n', diagnostic.rangeStart - 1) + 1
                val lineIndex = written.substring(0, diagnostic.rangeStart).count { it == '\n' }
                val lineEnd = written.indexOf('\n', lineStart).let { if (it < 0) written.length else it }
                val rangeEnd = minOf(diagnostic.rangeEnd, lineEnd)
                SampleReport(
                    name = diagnostic.name,
                    line = sourceLines.getOrNull(lineIndex)?.renderedLine,
                    fromColumn = diagnostic.rangeStart - lineStart + 1,
                    toColumn = rangeEnd - lineStart,
                    text = written.substring(diagnostic.rangeStart, rangeEnd),
                    parameters = diagnostic.parameters.map { it.inDeclaredPackage() },
                )
            }
        }

        /** The parameter with the test package replaced by the package the sample declares. */
        private fun String.inDeclaredPackage(): String {
            val testPackage = "docs.$directory"
            return if (declaredPackage == null) replace("$testPackage.", "") else replace(testPackage, declaredPackage)
        }

        private fun standaloneSource(): List<SourceLine> {
            val indexed = lines.withIndex().toList()
            val packageIndex = lines.indexOfFirst { it.startsWith("package ") }
            val prefixSize = if (packageIndex >= 0) packageIndex else fileAnnotationPrefix(lines).size
            val prefix = indexed.subList(0, prefixSize)
            val body = indexed.drop(prefixSize).filterNot { (index, _) -> index == packageIndex }
            val imports = body.filter { it.value.startsWith("import ") }
            val rest = body.filterNot { it.value.startsWith("import ") }.dropWhile { it.value.isBlank() }

            fun IndexedValue<String>.sampleLine() = SourceLine(value, renderedLineOf[index])
            fun synthesized(text: String) = SourceLine(text, renderedLine = null)

            return buildList {
                prefix.mapTo(this) { it.sampleLine() }
                if (prefix.isNotEmpty() && prefix.last().value.isNotBlank()) add(synthesized(""))
                add(synthesized("package docs.$directory"))
                add(synthesized(""))
                add(synthesized("import com.example.models.ExternalModel"))
                add(synthesized("import docs.support.*"))
                add(synthesized("import kotlin.reflect.KClass"))
                add(synthesized("import org.jetbrains.kotlinx.library.api.watchdog.*"))
                imports.mapTo(this) { it.sampleLine() }
                add(synthesized(""))
                rest.mapTo(this) { it.sampleLine() }
            }
        }

        private fun fileAnnotationPrefix(lines: List<String>): List<String> {
            val annotation = lines.indexOfFirst { it.startsWith("@file:") }
            if (annotation < 0 || lines.take(annotation).any { line ->
                    line.isNotBlank() && !line.trimStart().startsWith("//")
                }) {
                return emptyList()
            }
            val blank = lines.indices.firstOrNull { it > annotation && lines[it].isBlank() } ?: -1
            return (if (blank < 0) lines else lines.subList(0, blank)).toList()
        }
    }

    private companion object {
        const val REPORT_FILE = "docs-diagnostics.tsv"
        const val EXTERNAL_MODELS = "com.example:models"
        const val EXTERNAL_MODELS_VERSION = "1.0"
        val PUBLIC_DECLARATION = Regex("""\bpublic\s+(?:[a-z]+\s+)*(?:class|interface|object|annotation|typealias|fun|val|var)\b""")
        val KOTLIN_FENCE = Regex("""```kotlin(?:\s+(\S.*?))?\s*""")
        val CODE_HIKE_COMMENT = Regex("""\s*// !\S.*""")
        val DIAGNOSTIC_ANNOTATION =
            Regex("""\s*// !diag\[/(.+)/([a-z]*)]\s+([A-Z][A-Z0-9_]*)(?:\s+(\[.*]))?\s*""")
        val JSON_STRING = Regex(""""((?:[^"\\]|\\.)*)"""")
        val PARAMETER_REFERENCE = Regex("""\$([A-Za-z][A-Za-z0-9]*)(?:\(([^()]*)\))?""")
        val POKO_GENERATED_DIAGNOSTICS = setOf(
            "STATEFUL_CLASS_WITHOUT_EQUALS",
            "STATEFUL_CLASS_WITHOUT_HASH_CODE",
            "STATEFUL_CLASS_WITHOUT_TO_STRING",
        )
        val isMac = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

        fun String.isCodeHikeComment(): Boolean = CODE_HIKE_COMMENT.matches(this)

        /** The `["a","b"]` list of an annotation. */
        fun parseParameters(encoded: String): List<String> = JSON_STRING.findAll(encoded)
            .map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") }
            .toList()

        /**
         * The parameter as the compiler renders it. A `$name(args)` reference is a value of
         * `diagnostics.json`, like `resolveParameter` in docs/src/components/Code.tsx resolves it.
         * The shortcut value follows the host OS, like the compiler.
         */
        fun Diagnostic.resolve(index: Int, parameter: String): String {
            val resolved = PARAMETER_REFERENCE.matchEntire(parameter)?.let { reference ->
                val valueName = reference.groupValues[1]
                    .let { if (it == "ideaGenerateShortcut" && isMac) "ideaGenerateShortcutMac" else it }
                val template = parameterValues[valueName] ?: return@let null
                val arguments = reference.groups[2]?.value?.split(',')?.map(String::trim).orEmpty()
                arguments.foldIndexed(template) { position, result, argument -> result.replace("{$position}", argument) }
            } ?: parameter
            // Code.tsx aligns the table of this parameter, like the checker does.
            return if (this == Diagnostic.INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS && index == 2) {
                resolved.alignedTable()
            } else {
                resolved
            }
        }

        fun String.alignedTable(): String {
            val rows = trim().lines().map { it.trim().split(" | ") }
            val widths = rows.first().indices.map { column -> rows.maxOf { it[column].length } }
            return rows.joinToString("\n") { row ->
                "    " + row.mapIndexed { column, cell -> cell.padEnd(widths[column]) }.joinToString(" | ").trimEnd()
            }
        }
    }
}

/** The declarations that the docs samples refer to. */
private val supportFile = """
    @file:org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName(
        reason = org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason.API_DESIGN,
    )

    package docs.support

    import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
    import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInlinedLogic
    import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

    /** Stand-in for the Poko compiler-plugin annotation used by documentation samples. */
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.SOURCE)
    public annotation class Poko

    /** A DSL marker supplied to isolated documentation snippets. */
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
    public annotation class TreeDsl

    /** A receiver supplied to isolated documentation snippets. */
    @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
    public open class Tag

    /** An opt-in marker supplied to isolated documentation snippets. */
    @RequiresOptIn
    public annotation class InternalMyLibrarySubclassApi

    /** An identifier supplied to isolated documentation snippets. */
    @JvmInline
    public value class UserId(
        /** The underlying value. */
        public val raw: String,
    )

    /** A connection supplied to isolated documentation snippets. */
    public class Connection

    /** Returns stand-in data for isolated documentation snippets. */
    public fun fetchLatest(): String = ""

    /** Returns a stand-in array size for isolated documentation snippets. */
    public fun calculateArraysSizeImpl(): Int = 0

    /** Executes [block] for isolated documentation snippets. */
    @IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
    public inline fun <T> withCache(block: () -> T): T = block()

    /** Stand-in reflection members for isolated documentation snippets. */
    public val kotlin.reflect.KClass<*>.memberFunctions: List<Unit>
        get() = emptyList()
""".trimIndent()
