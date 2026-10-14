package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

abstract class GenerateCheckDescriptions : DiagnosticsJsonGeneratorTask() {
    @get:Input
    abstract val toolVersion: Property<String>

    override val fileName: String get() = "WatchdogCheckDescriptions.kt"

    override fun StringBuilder.appendDeclarations(diagnostics: DiagnosticsJson) {
        appendLine("internal class WatchdogCheckDescription(val title: String, val docsUrl: String)")
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
            appendLine("        \"${entry.name}\" to WatchdogCheckDescription(\"$title\", \"$docsUrl\"),")
        }
        appendLine("    )")
        appendLine("}")
    }
}
