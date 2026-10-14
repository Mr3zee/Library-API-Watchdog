package org.jetbrains.kotlinx.library.api.watchdog.conventions

abstract class GenerateDiagnosticMessages : DiagnosticsJsonGeneratorTask() {
    override val fileName: String get() = "WatchdogDiagnosticMessages.kt"

    override fun StringBuilder.appendDeclarations(diagnostics: DiagnosticsJson) {
        appendLine("/**")
        appendLine(" * Message templates of the watchdog diagnostics, generated from the shared")
        appendLine(" * `diagnostics.json` that the documentation website reads too. Templates of diagnostics")
        appendLine(" * with parameters are rendered through `java.text.MessageFormat`, so their single quotes")
        appendLine(" * arrive here already doubled.")
        appendLine(" */")
        appendLine("internal object WatchdogDiagnosticMessages {")
        appendLine("    /** Message template per diagnostic name. */")
        appendLine("    val messages: Map<String, String> = mapOf(")
        for (entry in diagnostics.entries) appendEntry(entry)
        appendLine("    )")
        appendLine()
        appendLine("    /** The template of [name], failing loudly when diagnostics.json does not know it. */")
        appendLine("    fun messageFor(name: String): String =")
        appendLine("        messages[name] ?: error(\"No message for the diagnostic '\$name' in diagnostics.json\")")
        appendLine()
        appendParameterValues(diagnostics.entries)
        appendLine()
        appendSharedParameterValues(diagnostics.sharedParameterValues)
        appendLine()
        appendLine("    /**")
        appendLine("     * Resolves a named parameter value of [diagnostic], or a shared one, and fills its own")
        appendLine("     * placeholders.")
        appendLine("     */")
        appendLine("    fun parameterValueFor(diagnostic: String, value: String, vararg parameters: String): String {")
        appendLine("        val template = parameterValues[diagnostic]?.get(value) ?: sharedParameterValues[value]")
        appendLine("            ?: error(\"No parameter value '\$value' for diagnostic '\$diagnostic' in diagnostics.json\")")
        appendLine("        return parameters.foldIndexed(template) { index, result, parameter ->")
        appendLine("            result.replace(\"{\$index}\", parameter)")
        appendLine("        }")
        appendLine("    }")
        appendLine("}")
    }

    private fun StringBuilder.appendParameterValues(entries: List<DiagnosticEntry>) {
        appendLine("    /** Named values used as parameters inside diagnostic message templates. */")
        appendLine("    private val parameterValues: Map<String, Map<String, String>> = mapOf(")
        for (entry in entries) {
            val values = entry.parameterValues ?: continue
            appendLine("        \"${entry.name}\" to mapOf(")
            for ((name, lines) in values) appendParameterValue(name, lines, indent = "            ")
            appendLine("        ),")
        }
        appendLine("    )")
    }

    private fun StringBuilder.appendParameterValue(name: String, lines: List<String>, indent: String) {
        appendLine("$indent\"$name\" to")
        val chunks = lines.joinTextLines().plainLinks().wrap()
        for ((index, chunk) in chunks.withIndex()) {
            val tail = if (index == chunks.lastIndex) "," else " +"
            appendLine("$indent    \"${chunk.escapedForKotlinString()}\"$tail")
        }
    }

    private fun StringBuilder.appendSharedParameterValues(values: Map<String, List<String>>) {
        appendLine("    /** Named values every diagnostic can use as a parameter. */")
        appendLine("    private val sharedParameterValues: Map<String, String> = mapOf(")
        for ((name, lines) in values) appendParameterValue(name, lines, indent = "        ")
        appendLine("    )")
    }

    private fun StringBuilder.appendEntry(entry: DiagnosticEntry) {
        val message = buildString {
            append(entry.message.joinTextLines())
            append("\n\nSee more: ").append(entry.docsUrl)
        }.plainLinks()
        val template = if (PARAMETER.containsMatchIn(message)) message.replace("'", "''") else message
        appendLine("        \"${entry.name}\" to")
        val chunks = template.wrap()
        for ((index, chunk) in chunks.withIndex()) {
            val indent = if (index == 0) "            " else "                "
            val tail = if (index == chunks.lastIndex) "," else " +"
            appendLine("$indent\"${chunk.escapedForKotlinString()}\"$tail")
        }
    }

    /** The line rules are in the `$comment` of `diagnostics.json`. */
    private fun List<String>.joinTextLines(): String = buildString {
        for ((index, line) in this@joinTextLines.withIndex()) {
            if (index > 0) {
                val previous = this@joinTextLines[index - 1]
                val hardBreak = line.isEmpty() || previous.isEmpty() || line.isCodeLine() || previous.isCodeLine()
                append(if (hardBreak) '\n' else ' ')
            }
            append(line)
        }
    }

    private fun String.isCodeLine(): Boolean = startsWith("    ")

    private fun String.wrap(): List<String> = buildList {
        var remaining = this@wrap
        while (remaining.length > MAX_CHUNK_LENGTH) {
            val splitAt = remaining.lastIndexOf(' ', MAX_CHUNK_LENGTH)
                .takeIf { it >= 0 }
                ?: remaining.indexOf(' ', MAX_CHUNK_LENGTH)
            if (splitAt < 0) break
            add(remaining.substring(0, splitAt + 1))
            remaining = remaining.substring(splitAt + 1)
        }
        add(remaining)
    }

    private companion object {
        val PARAMETER = Regex("\\{\\d+}")
        const val MAX_CHUNK_LENGTH = 88
    }
}
