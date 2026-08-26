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

/** Generates Kotlin diagnostic message templates from the shared documentation registry. */
abstract class GenerateDiagnosticMessages : DefaultTask() {
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
            .resolve("WatchdogDiagnosticMessages.kt")
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
        appendLine(" * Message templates of the watchdog diagnostics, generated from the shared")
        appendLine(" * `diagnostics.json` that the documentation website reads too. Templates of diagnostics")
        appendLine(" * with parameters are rendered through `java.text.MessageFormat`, so their single quotes")
        appendLine(" * arrive here already doubled.")
        appendLine(" */")
        appendLine("internal object WatchdogDiagnosticMessages {")
        appendLine("    /** Message template per diagnostic name. */")
        appendLine("    val messages: Map<String, String> = mapOf(")
        for (entry in entries) appendEntry(entry, docsBaseUrl)
        appendLine("    )")
        appendLine()
        appendLine("    /** The template of [name], failing loudly when diagnostics.json does not know it. */")
        appendLine("    fun messageFor(name: String): String =")
        appendLine("        messages[name] ?: error(\"No message for the diagnostic '\$name' in diagnostics.json\")")
        appendLine()
        appendParameterValues(entries)
        appendLine()
        appendLine("    /** Resolves a named diagnostic parameter value and fills its own placeholders. */")
        appendLine("    fun parameterValueFor(diagnostic: String, value: String, vararg parameters: String): String {")
        appendLine("        val template = parameterValues[diagnostic]?.get(value)")
        appendLine("            ?: error(\"No parameter value '\$value' for diagnostic '\$diagnostic' in diagnostics.json\")")
        appendLine("        return parameters.foldIndexed(template) { index, result, parameter ->")
        appendLine("            result.replace(\"{\$index}\", parameter)")
        appendLine("        }")
        appendLine("    }")
        appendLine("}")
    }

    private fun StringBuilder.appendParameterValues(entries: List<Map<String, Any>>) {
        appendLine("    /** Named values used as parameters inside diagnostic message templates. */")
        appendLine("    private val parameterValues: Map<String, Map<String, String>> = mapOf(")
        for (entry in entries) {
            @Suppress("UNCHECKED_CAST")
            val values = entry["parameterValues"] as Map<String, List<*>>? ?: continue
            appendLine("        \"${entry["name"]}\" to mapOf(")
            for ((name, lines) in values) {
                appendLine("            \"$name\" to")
                val chunks = lines.joinTextLines().plainLinks().wrap()
                for ((index, chunk) in chunks.withIndex()) {
                    val tail = if (index == chunks.lastIndex) "," else " +"
                    appendLine("                \"${chunk.escaped()}\"$tail")
                }
            }
            appendLine("        ),")
        }
        appendLine("    )")
    }

    private fun StringBuilder.appendEntry(entry: Map<String, Any>, docsBaseUrl: String) {
        val name = entry["name"] as String
        val docsUrl = docsBaseUrl + (entry["docs"] as String)
        val trailer = entry["messageTrailer"] as String?
        val message = buildString {
            append((entry["message"] as List<*>).joinTextLines())
            if (trailer != null) append(' ').append(trailer)
            append("\n\nSee more: ").append(docsUrl)
        }.plainLinks()
        val template = if (PARAMETER.containsMatchIn(message)) message.replace("'", "''") else message

        appendLine("        \"$name\" to")
        val chunks = template.wrap()
        for ((index, chunk) in chunks.withIndex()) {
            val indent = if (index == 0) "            " else "                "
            val tail = if (index == chunks.lastIndex) "," else " +"
            appendLine("$indent\"${chunk.escaped()}\"$tail")
        }
    }

    /** Joins source-wrapped lines while keeping empty lines as paragraph separators. */
    private fun List<*>.joinTextLines(): String =
        joinToString("\n") { it as String }.replace(SOFT_LINE_BREAK, " ")

    /** Rewrites Markdown links, which only the docs render natively, into `text (url)`. */
    private fun String.plainLinks(): String =
        replace(MARKDOWN_LINK) { "${it.groupValues[1]} (${it.groupValues[2]})" }

    private fun String.wrap(): List<String> {
        val chunks = mutableListOf<String>()
        val chunk = StringBuilder()
        for (word in splitToSequence(' ')) {
            if (chunk.isNotEmpty() && chunk.length + word.length > MAX_CHUNK_LENGTH) {
                chunks += chunk.toString()
                chunk.clear()
            }
            if (chunk.isNotEmpty()) chunk.append(' ')
            chunk.append(word)
        }
        chunks += chunk.toString()
        return chunks.mapIndexed { index, text -> if (index == chunks.lastIndex) text else "$text " }
    }

    private fun String.escaped(): String =
        replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"").replace("$", "\\$")

    private companion object {
        val PARAMETER = Regex("\\{\\d+}")
        val MARKDOWN_LINK = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
        val SOFT_LINE_BREAK = Regex("(?<!\\n)\\n(?!\\n)")
        const val MAX_CHUNK_LENGTH = 88
    }
}
