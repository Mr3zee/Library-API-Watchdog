package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

abstract class GenerateCheckDescriptions : DiagnosticsJsonGeneratorTask() {
    @get:Input
    abstract val toolVersion: Property<String>

    override val fileName: String get() = "WatchdogCheckDescriptions.kt"

    override fun StringBuilder.appendDeclarations(diagnostics: DiagnosticsJson) {
        appendLine("internal class WatchdogCheckDescription(val title: String, val docsUrl: String, val message: String)")
        appendLine()
        appendLine("/** The check descriptions by diagnostic name. */")
        appendLine("internal object WatchdogCheckDescriptions {")
        appendLine("    const val TOOL_VERSION: String = \"${toolVersion.get().escapedForKotlinString()}\"")
        appendLine()
        appendLine("    const val DOCS_URL: String = \"${diagnostics.docsBaseUrl.escapedForKotlinString()}\"")
        appendLine()
        appendLine("    val checks: Map<String, WatchdogCheckDescription> = mapOf(")
        for (entry in diagnostics.entries) {
            val title = entry.title.escapedForKotlinString()
            val docsUrl = entry.docsUrl.escapedForKotlinString()
            val message = entry.message.joinTextLines().escapedForKotlinString()
            appendLine("        \"${entry.name}\" to WatchdogCheckDescription(\"$title\", \"$docsUrl\", \"$message\"),")
        }
        appendLine("    )")
        appendLine()
        appendLine("    val diagnosticsByAnnotation: Map<String, List<String>> = mapOf(")
        val annotations = linkedMapOf<String, MutableList<String>>()
        for (entry in diagnostics.entries) {
            Regex("Intentionally[A-Za-z0-9]+").findAll(entry.message.joinToString("\n"))
                .map { it.value }.distinct().forEach { annotation ->
                    annotations.getOrPut(annotation) { mutableListOf() }.add(entry.name)
                }
        }
        annotations.forEach { (annotation, names) ->
            val values = names.joinToString(", ") { "\"${it.escapedForKotlinString()}\"" }
            appendLine("        \"$annotation\" to listOf($values),")
        }
        appendLine("    )")
        appendLine("}")
    }
}
