@file:Suppress("RedundantVisibilityModifier")

package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.Source
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.kotlinx.library.api.watchdog.Severity.WARNING
import org.junit.Test

/** Compiles every public API sample of the docs with every check on. */
class DocsExamplesTest {
    @Test
    fun examplesReportExactlyTheirAnnotatedDiagnostics() {
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
                """
            )
            kotlinFile("DocsSupport", text = supportFile)
            sources(*samples.map(DocumentationSample::source).toTypedArray())
            dependencies(implementation(":exampleModels"))
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
        val pokoSamples = samples.filter { "@Poko" in it.code }.mapTo(mutableSetOf(), DocumentationSample::name)
        val actualBySample = report.readLines()
            .filter(String::isNotBlank)
            .map { line ->
                val parts = line.split('\t')
                require(parts.size == 4) { "Malformed diagnostics report line: '$line'" }
                File(parts[1]).nameWithoutExtension to parts[0]
            }
            .groupBy(keySelector = Pair<String, String>::first, valueTransform = Pair<String, String>::second)
            .mapValues { (sample, diagnostics) ->
                diagnostics
                    // The project has its own `@Poko` annotation, not the Poko compiler plugin.
                    .filterNot { sample in pokoSamples && it in POKO_GENERATED_DIAGNOSTICS }
                    .sorted()
            }
        val unexpectedSources = actualBySample.keys - samples.map(DocumentationSample::name).toSet()
        assertEquals(emptySet(), unexpectedSources, "Diagnostics from audit support sources: $unexpectedSources")

        val mismatches = samples.mapNotNull { sample ->
            val actual = actualBySample[sample.name].orEmpty()
            val expected = sample.expectedDiagnostics.sorted()
            if (actual == expected) null else buildString {
                append(sample.location)
                append(": expected ")
                append(expected)
                append(", compiler reported ")
                append(actual)
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
                    DocumentationSample(
                        name = "Sample%03d".format(index),
                        location = "${file.relativeTo(findRepositoryRoot()).invariantSeparatorsPath}:${block.line}",
                        code = block.code,
                        expectedDiagnostics = DIAGNOSTIC_ANNOTATION.findAll(block.code)
                            .map { it.groupValues[1] }
                            .toList(),
                    )
                }
            }
            .toList()
    }

    private fun extractKotlinBlocks(file: File): List<CodeBlock> {
        val lines = file.readLines()
        val result = mutableListOf<CodeBlock>()
        var openingLine = -1
        var contents = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            when {
                openingLine < 0 && KOTLIN_FENCE.matches(line) -> {
                    openingLine = index + 1
                    contents = mutableListOf()
                }
                openingLine >= 0 && line == "```" -> {
                    result += CodeBlock(openingLine + 1, contents.joinToString("\n"))
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

    private data class CodeBlock(val line: Int, val code: String)

    private data class DocumentationSample(
        val name: String,
        val location: String,
        val code: String,
        val expectedDiagnostics: List<String>,
    ) {
        fun source(): Source = Source.kotlin(standaloneSource())
            .withPath("docs/${name.lowercase()}", name)
            .build()

        private fun standaloneSource(): String {
            val lines = code.lines().toMutableList()
            val packageIndex = lines.indexOfFirst { it.startsWith("package ") }
            val prefix = if (packageIndex >= 0) lines.subList(0, packageIndex).toList() else fileAnnotationPrefix(lines)
            if (packageIndex >= 0) lines.removeAt(packageIndex)
            repeat(prefix.size) { lines.removeAt(0) }

            val imports = lines.filter { it.startsWith("import ") }
            lines.removeAll(imports.toSet())
            while (lines.firstOrNull()?.isBlank() == true) lines.removeAt(0)

            return buildString {
                prefix.forEach(::appendLine)
                if (prefix.isNotEmpty() && prefix.last().isNotBlank()) appendLine()
                appendLine("package docs.${name.lowercase()}")
                appendLine()
                appendLine("import com.example.models.ExternalModel")
                appendLine("import docs.support.*")
                appendLine("import kotlin.reflect.KClass")
                appendLine("import org.jetbrains.kotlinx.library.api.watchdog.*")
                imports.forEach(::appendLine)
                appendLine()
                append(lines.joinToString("\n"))
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
        val PUBLIC_DECLARATION = Regex("""\bpublic\s+(?:[a-z]+\s+)*(?:class|interface|object|annotation|typealias|fun|val|var)\b""")
        val KOTLIN_FENCE = Regex("""```kotlin(?:\s.*)?""")
        val DIAGNOSTIC_ANNOTATION =
            Regex("""(?m)^\s*// !diag\[/.*/[a-z]*]\s+([A-Z][A-Z0-9_]*)""")
        val POKO_GENERATED_DIAGNOSTICS = setOf(
            "STATEFUL_CLASS_WITHOUT_EQUALS",
            "STATEFUL_CLASS_WITHOUT_HASH_CODE",
            "STATEFUL_CLASS_WITHOUT_TO_STRING",
        )
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
