package org.jetbrains.kotlinx.library.api.watchdog.conventions

import groovy.json.JsonSlurper
import java.io.File
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

/** The content of `diagnostics.json`. */
class DiagnosticsJson(
    val docsBaseUrl: String,
    val entries: List<DiagnosticEntry>,
    /** Named values every diagnostic can use as a parameter. */
    val sharedParameterValues: Map<String, List<String>>,
) {
    companion object {
        fun read(file: File): DiagnosticsJson {
            @Suppress("UNCHECKED_CAST")
            val root = JsonSlurper().parse(file) as Map<String, Any>
            val docsBaseUrl = root["docsBaseUrl"] as String
            @Suppress("UNCHECKED_CAST")
            val entries = root["diagnostics"] as List<Map<String, Any>>
            return DiagnosticsJson(
                docsBaseUrl = docsBaseUrl,
                entries = entries.map { DiagnosticEntry(it, docsBaseUrl) },
                sharedParameterValues = root["sharedParameterValues"].asTextLinesByName(),
            )
        }
    }
}

/** One entry of the `diagnostics` array. */
class DiagnosticEntry internal constructor(json: Map<String, Any>, docsBaseUrl: String) {
    val name: String = json["name"] as String
    val title: String = json["title"] as String

    /** The check page, relative to [DiagnosticsJson.docsBaseUrl]. */
    val docs: String = json["docs"] as String
    val docsUrl: String = docsBaseUrl + docs

    /** The message lines. The line rules are in the `$comment` of `diagnostics.json`. */
    val message: List<String> = (json["message"] as List<*>).map { it as String }

    /** Named values used as parameters inside the message template, or null. */
    val parameterValues: Map<String, List<String>>? =
        json["parameterValues"]?.asTextLinesByName()
}

private fun Any?.asTextLinesByName(): Map<String, List<String>> {
    @Suppress("UNCHECKED_CAST")
    val values = this as? Map<String, List<*>>? ?: return emptyMap()
    return values.mapValues { (_, lines) -> lines.map { it as String } }
}

private val MARKDOWN_LINK = Regex("\\[([^]]+)]\\(([^)]+)\\)")

/** `[text](url)` as `text (url)`. */
fun String.plainLinks(): String =
    replace(MARKDOWN_LINK) { "${it.groupValues[1]} (${it.groupValues[2]})" }

/** The text as the content of a Kotlin string literal. */
fun String.escapedForKotlinString(): String =
    replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"").replace("$", "\\$")

/**
 * A task that generates one Kotlin file from `diagnostics.json`. The file goes into the directory
 * of [packageName] under [outputDirectory] and starts with the generated-file header and the
 * package statement.
 */
abstract class DiagnosticsJsonGeneratorTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val diagnostics: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /** The name of the generated file. */
    @get:Internal
    protected abstract val fileName: String

    /** Appends the declarations after the package statement. */
    protected abstract fun StringBuilder.appendDeclarations(diagnostics: DiagnosticsJson)

    @TaskAction
    fun generate() {
        val diagnostics = DiagnosticsJson.read(diagnostics.get().asFile)
        val packageName = packageName.get()
        val outputFile = outputDirectory.get().asFile
            .resolve(packageName.replace('.', '/'))
            .resolve(fileName)
        outputFile.parentFile.mkdirs()
        outputFile.writeText(buildString {
            appendLine("// Generated from diagnostics.json. Do not edit.")
            appendLine()
            appendLine("package $packageName")
            appendLine()
            appendDeclarations(diagnostics)
        })
    }
}

/**
 * Registers a [DiagnosticsJsonGeneratorTask] that reads the `diagnostics.json` of the root
 * project. [outputDirectory] is relative to `build/generated/sources`.
 */
inline fun <reified T : DiagnosticsJsonGeneratorTask> Project.registerDiagnosticsGenerator(
    name: String,
    description: String,
    packageName: String,
    outputDirectory: String,
    noinline configure: T.() -> Unit = {},
): TaskProvider<T> = tasks.register(
    name,
    T::class.java,
    Action {
        this.description = description
        @Suppress("UnstableApiUsage")
        this.diagnostics.set(isolated.rootProject.projectDirectory.file("diagnostics.json"))
        this.packageName.set(packageName)
        this.outputDirectory.set(layout.buildDirectory.dir("generated/sources/$outputDirectory"))
        configure()
    },
)
