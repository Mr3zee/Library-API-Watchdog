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

/** Generates the `WatchdogCheckDescriptions` table that the SARIF report reads its rules from. */
abstract class GenerateCheckDescriptions : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val diagnostics: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    /** The version of the published artifacts. SARIF reports it as the tool version. */
    @get:Input
    abstract val toolVersion: Property<String>

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
            .resolve("WatchdogCheckDescriptions.kt")
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
        appendLine("/** The title and the check page of one diagnostic. */")
        appendLine("internal class WatchdogCheckDescription(val title: String, val docsUrl: String)")
        appendLine()
        appendLine("/** The descriptions of the watchdog checks by diagnostic name, generated from the shared `diagnostics.json`. */")
        appendLine("internal object WatchdogCheckDescriptions {")
        appendLine("    const val TOOL_VERSION: String = \"${toolVersion.get().escape()}\"")
        appendLine()
        appendLine("    const val DOCS_URL: String = \"${docsBaseUrl.escape()}\"")
        appendLine()
        appendLine("    val checks: Map<String, WatchdogCheckDescription> = mapOf(")
        for (entry in entries) {
            val name = entry["name"] as String
            val title = (entry["title"] as String).escape()
            val docsUrl = (docsBaseUrl + (entry["docs"] as String)).escape()
            appendLine("        \"$name\" to WatchdogCheckDescription(\"$title\", \"$docsUrl\"),")
        }
        appendLine("    )")
        appendLine("}")
    }

    private fun String.escape(): String = replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
}
