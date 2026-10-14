package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

/** Generates the `WatchdogCheckDescriptions` table that the SARIF report reads its rules from. */
abstract class GenerateCheckDescriptions : DiagnosticsJsonGeneratorTask() {
    /** The version of the published artifacts. SARIF reports it as the tool version. */
    @get:Input
    abstract val toolVersion: Property<String>

    override val fileName: String get() = "WatchdogCheckDescriptions.kt"

    override fun StringBuilder.appendDeclarations(diagnostics: DiagnosticsJson) {
        appendLine("/** The title and the check page of one diagnostic. */")
        appendLine("internal class WatchdogCheckDescription(val title: String, val docsUrl: String)")
        appendLine()
        appendLine("/** The descriptions of the watchdog checks by diagnostic name, generated from the shared `diagnostics.json`. */")
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
