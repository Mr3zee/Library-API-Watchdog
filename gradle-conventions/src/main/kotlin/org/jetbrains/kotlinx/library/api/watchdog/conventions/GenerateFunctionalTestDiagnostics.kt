package org.jetbrains.kotlinx.library.api.watchdog.conventions

abstract class GenerateFunctionalTestDiagnostics : DiagnosticsJsonGeneratorTask() {
    override val fileName: String get() = "Diagnostic.kt"

    override fun StringBuilder.appendDeclarations(diagnostics: DiagnosticsJson) {
        appendLine("/**")
        appendLine(" * [template] is the first paragraph of the message, with its `{n}` placeholders.")
        appendLine(" * [javaInterop] is true for a check of the `javaInterop { }` group of the Gradle extension.")
        appendLine(" * [parameterValues] holds the named parameter values as the compiler renders them, the")
        appendLine(" * shared values included.")
        appendLine(" */")
        appendLine("enum class Diagnostic(")
        appendLine("    val title: String,")
        appendLine("    val docsUrl: String,")
        appendLine("    val template: String,")
        appendLine("    val javaInterop: Boolean,")
        appendLine("    val parameterValues: Map<String, String>,")
        appendLine(") {")
        for (entry in diagnostics.entries) {
            val template = entry.message.firstParagraph().plainLinks()
            appendLine("    /** [${entry.title}](${entry.docsUrl}) */")
            appendLine("    ${entry.name}(")
            appendLine("        title = \"${entry.title.escapedForKotlinString()}\",")
            appendLine("        docsUrl = \"${entry.docsUrl.escapedForKotlinString()}\",")
            appendLine("        template = \"${template.escapedForKotlinString()}\",")
            appendLine("        javaInterop = ${entry.docs.startsWith(JAVA_INTEROP_DOCS)},")
            appendLine("        parameterValues = mapOf(")
            val values = diagnostics.sharedParameterValues + entry.parameterValues.orEmpty()
            for ((name, lines) in values) {
                val value = lines.joinTextLines().plainLinks().escapedForKotlinString()
                appendLine("            \"$name\" to \"$value\",")
            }
            appendLine("        ),")
            appendLine("    ),")
        }
        appendLine("}")
    }

    private fun List<String>.firstParagraph(): String = takeWhile { it.isNotEmpty() }.joinToString(" ")

    private companion object {
        const val JAVA_INTEROP_DOCS = "checks/java-interop/"
    }
}
