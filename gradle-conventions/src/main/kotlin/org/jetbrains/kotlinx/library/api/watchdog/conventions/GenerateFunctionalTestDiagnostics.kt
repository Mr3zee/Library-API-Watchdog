package org.jetbrains.kotlinx.library.api.watchdog.conventions

abstract class GenerateFunctionalTestDiagnostics : DiagnosticsJsonGeneratorTask() {
    override val fileName: String get() = "Diagnostic.kt"

    override fun StringBuilder.appendDeclarations(diagnostics: DiagnosticsJson) {
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
        for (entry in diagnostics.entries) {
            val template = entry.message.firstParagraph().plainLinks()
            appendLine("    /** [${entry.title}](${entry.docsUrl}) */")
            appendLine("    ${entry.name}(")
            appendLine("        title = \"${entry.title.escapedForKotlinString()}\",")
            appendLine("        docsUrl = \"${entry.docsUrl}\",")
            appendLine("        template = \"${template.escapedForKotlinString()}\",")
            appendLine("        javaInterop = ${entry.docs.startsWith(JAVA_INTEROP_DOCS)},")
            appendLine("    ),")
        }
        appendLine("}")
    }

    private fun List<String>.firstParagraph(): String = takeWhile { it.isNotEmpty() }.joinToString(" ")

    private companion object {
        const val JAVA_INTEROP_DOCS = "checks/java-interop/"
    }
}
