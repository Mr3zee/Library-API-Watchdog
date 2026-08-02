import groovy.json.JsonSlurper

plugins {
    kotlin("compiler.plugin.devkit.compiler-plugin")
    // Native loads compiler plugins in an isolated process, so the dev-kit registrar runtime
    // must travel inside the compiler plugin artifact.
    id("com.gradleup.shadow")
}

pluginDevKit {
    testDataLibraries { common(project(":plugin-annotations")) }
}

dependencies {
    // The dev-kit runtime's published testFixtures metadata carries no dependency on the runtime's
    // main jar (it is wired as a local file dependency there), so declare it explicitly; the
    // testFixtures runners subclass DevKitCompilerPluginRegistrar from it.
    "testFixturesApi"("org.jetbrains.kotlin.compiler.plugin.devkit:compiler-plugin-runtime:0.0.1-SNAPSHOT")
}

tasks.named("animalsnifferMain") {
    enabled = false
}

/**
 * Turns the shared `diagnostics.json` into the `WatchdogDiagnosticMessages` object the diagnostic
 * renderer factory reads its message templates from. The same file feeds the documentation
 * website, which is why the link to a check page is derived here instead of being spelled out in
 * every message.
 */
abstract class GenerateDiagnosticMessages : DefaultTask() {
    /** The shared diagnostics source of truth, `diagnostics.json` in the repository root. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val diagnostics: RegularFileProperty

    /** The package of the generated object. */
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
        for (entry in entries) {
            appendEntry(entry, docsBaseUrl)
        }
        appendLine("    )")
        appendLine()
        appendLine("    /** The template of [name], failing loudly when diagnostics.json does not know it. */")
        appendLine("    fun messageFor(name: String): String =")
        appendLine("        messages[name] ?: error(\"No message for the diagnostic '\$name' in diagnostics.json\")")
        appendLine("}")
    }

    private fun StringBuilder.appendEntry(entry: Map<String, Any>, docsBaseUrl: String) {
        val name = entry["name"] as String
        val docsUrl = docsBaseUrl + (entry["docs"] as String)
        val trailer = entry["messageTrailer"] as String?
        val message = buildString {
            append(entry["message"] as String)
            append(" See ").append(docsUrl).append(" for details.")
            if (trailer != null) append(' ').append(trailer)
        }
        // MessageFormat only reaches diagnostics that have parameters; the others are rendered
        // verbatim and would show the doubled quotes.
        val template = if (PARAMETER.containsMatchIn(message)) message.replace("'", "''") else message

        appendLine("        \"$name\" to")
        val chunks = template.wrap()
        for ((index, chunk) in chunks.withIndex()) {
            val indent = if (index == 0) "            " else "                "
            val tail = if (index == chunks.lastIndex) "," else " +"
            appendLine("$indent\"${chunk.escaped()}\"$tail")
        }
    }

    /** Splits a template into line-sized chunks at spaces, keeping the space that ended a chunk. */
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
        replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

    private companion object {
        val PARAMETER = Regex("\\{\\d+}")
        const val MAX_CHUNK_LENGTH = 88
    }
}

val generateDiagnosticMessages by tasks.registering(GenerateDiagnosticMessages::class) {
    description = "Generates the diagnostic message templates from the shared diagnostics.json."
    diagnostics = rootProject.layout.projectDirectory.file("diagnostics.json")
    packageName = "org.jetbrains.kotlinx.libs.api.watchdog.fir"
    outputDirectory = layout.buildDirectory.dir("generated/sources/diagnostics/main/kotlin")
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generateDiagnosticMessages)
}
