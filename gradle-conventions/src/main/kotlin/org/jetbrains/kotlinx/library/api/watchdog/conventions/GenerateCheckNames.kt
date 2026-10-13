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

abstract class GenerateCheckNames : DefaultTask() {
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
            .resolve("WatchdogChecks.kt")
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
        appendLine(" * The diagnostic names of the watchdog checks, generated from the shared `diagnostics.json`.")
        appendLine(" * Pass them to [WatchdogGradleExtension.ignore] instead of spelling a name out:")
        appendLine(" *")
        appendLine(" * ```kotlin")
        appendLine(" * import org.jetbrains.kotlinx.library.api.watchdog.WatchdogChecks")
        appendLine(" *")
        appendLine(" * apiWatchdog {")
        appendLine(" *     ignore(WatchdogChecks.STATEFUL_CLASS_WITHOUT_EQUALS, whenAnnotatedWith = listOf(\"com.example.GeneratedValueMembers\"))")
        appendLine(" * }")
        appendLine(" * ```")
        appendLine(" *")
        appendLine(" * Every check is listed, including the always-error checks that accept no ignore rules.")
        appendLine(" * [WatchdogGradleExtension.ignore] rejects those by name.")
        appendLine(" */")
        appendLine("public object WatchdogChecks {")
        for ((index, entry) in entries.withIndex()) {
            val name = entry["name"] as String
            val title = entry["title"] as String
            val docsUrl = docsBaseUrl + (entry["docs"] as String)
            if (index > 0) appendLine()
            appendLine("    /** [$title]($docsUrl) */")
            appendLine("    public const val $name: String = \"$name\"")
        }
        appendLine("}")
    }
}
