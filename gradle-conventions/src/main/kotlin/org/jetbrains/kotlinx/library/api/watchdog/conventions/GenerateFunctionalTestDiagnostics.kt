package org.jetbrains.kotlinx.library.api.watchdog.conventions

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class GenerateFunctionalTestDiagnostics : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val diagnostics: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val root = JsonSlurper().parse(diagnostics.get().asFile) as Map<String, Any>
        val docsBaseUrl = root["docsBaseUrl"] as String

        @Suppress("UNCHECKED_CAST")
        val entries = root["diagnostics"] as List<Map<String, Any>>

        val packageName = packageName.get()
        val outputFile = outputDirectory.get().asFile
            .resolve(packageName.replace('.', '/'))
            .resolve("Diagnostic.kt")
        outputFile.parentFile.mkdirs()
        outputFile.writeText(buildString { appendFile(entries, docsBaseUrl, packageName) })
    }

    private fun StringBuilder.appendFile(
        entries: List<Map<String, Any>>,
        docsBaseUrl: String,
        packageName: String,
    ) {
        appendLine("// Generated from diagnostics.json. Do not edit.")
        appendLine()
        appendLine("package $packageName")
        appendLine()
        appendLine("/**")
        appendLine(" * The watchdog diagnostics as the functional tests see them, generated from the shared")
        appendLine(" * `diagnostics.json`. [template] is the first paragraph of the message, with the `{n}`")
        appendLine(" * placeholders the compiler fills in. [javaInterop] marks the `javaInterop { }` group of")
        appendLine(" * the Gradle extension.")
        appendLine(" */")
        appendLine("enum class Diagnostic(")
        appendLine("    val title: String,")
        appendLine("    val docsUrl: String,")
        appendLine("    val template: String,")
        appendLine("    val javaInterop: Boolean,")
        appendLine(") {")
        for (entry in entries) {
            val name = entry["name"] as String
            val title = entry["title"] as String
            val docs = entry["docs"] as String
            val template = (entry["message"] as List<*>).firstParagraph().plainLinks()
            appendLine("    /** [$title]($docsBaseUrl$docs) */")
            appendLine("    $name(")
            appendLine("        title = \"${title.escaped()}\",")
            appendLine("        docsUrl = \"$docsBaseUrl$docs\",")
            appendLine("        template = \"${template.escaped()}\",")
            appendLine("        javaInterop = ${docs.startsWith(JAVA_INTEROP_DOCS)},")
            appendLine("    ),")
        }
        appendLine("}")
    }

    private fun List<*>.firstParagraph(): String =
        map { it as String }.takeWhile { it.isNotEmpty() }.joinToString(" ")

    /** The same rewrite as in [GenerateDiagnosticMessages]. */
    private fun String.plainLinks(): String =
        replace(MARKDOWN_LINK) { "${it.groupValues[1]} (${it.groupValues[2]})" }

    private fun String.escaped(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

    private companion object {
        const val JAVA_INTEROP_DOCS = "checks/java-interop/"
        val MARKDOWN_LINK = Regex("\\[([^]]+)]\\(([^)]+)\\)")
    }
}
