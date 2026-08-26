package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.compiler.plugin.devkit.KotlinToolingVersion
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution

/** Runs the compiler-version-specific fixer process without exposing its protocol to Gradle tasks. */
internal object BackwardsCompatibilityExemptsFixerRunner {
    fun run(
        temporaryDirectory: File,
        diagnosticReports: Collection<File>,
        sourceFiles: Collection<File>,
        fixerArtifact: FileCollection,
        fixerClasspath: FileCollection,
        kotlinVersion: String,
        execOperations: ExecOperations,
        updateSources: Boolean,
    ): Map<String, List<String>> {
        val workDirectory = temporaryDirectory.resolve("fixer").apply {
            deleteRecursively()
            mkdirs()
        }
        val requestFile = workDirectory.resolve("request.txt")
        val responseFile = workDirectory.resolve("response.txt")
        val fixerClasses = unpackFixer(
            artifact = fixerArtifact.singleFile,
            kotlinVersion = kotlinVersion,
            outputDirectory = workDirectory.resolve("classes"),
        )
        requestFile.writeText(buildString {
            diagnosticReports.sortedBy(File::getAbsolutePath).forEach {
                appendLine("reportFile=${it.absolutePath}")
            }
            sourceFiles.asSequence()
                .filter { it.isFile && it.extension == "kt" }
                .sortedBy(File::getAbsolutePath)
                .forEach { appendLine("sourceFile=${it.absolutePath}") }
            appendLine("updateSources=$updateSources")
            appendLine("responseFile=${responseFile.absolutePath}")
        })

        val execResult = execOperations.javaexec { spec ->
            spec.classpath(fixerClasses, fixerClasspath)
            spec.mainClass.set(FIXER_MAIN_CLASS)
            spec.args(requestFile.absolutePath)
            spec.isIgnoreExitValue = true
        }
        val response = parseResponse(responseFile, execResult.exitValue)
        response["error"]?.forEach {
            throw GradleException("The exempts fixer failed:\n${it.unescapeFixerNewlines()}")
        }
        if (execResult.exitValue != 0) {
            throw GradleException(
                "The exempts fixer exited with code ${execResult.exitValue} without reporting an error."
            )
        }
        return response
    }

    private fun parseResponse(responseFile: File, exitValue: Int): Map<String, List<String>> {
        if (!responseFile.isFile) {
            throw GradleException(
                "The exempts fixer produced no response (exit code $exitValue). " +
                        "See the process output above."
            )
        }
        return responseFile.readLines()
            .filter { it.isNotBlank() }
            .groupBy(keySelector = { it.substringBefore('=') }, valueTransform = { it.substringAfter('=') })
    }

    /** Materializes the compiler-library overlay compatible with the consuming Kotlin version. */
    private fun unpackFixer(
        artifact: File,
        kotlinVersion: String,
        outputDirectory: File,
    ): File {
        outputDirectory.deleteRecursively()
        check(outputDirectory.mkdirs()) { "Could not create $outputDirectory" }

        val currentVersion = KotlinToolingVersion(kotlinVersion)
        var selectedVersion: KotlinToolingVersion? = null
        val opened = VersionResolution.run {
            artifact.toPath().openDirectory { root ->
                selectedVersion = mutableListOf<java.nio.file.Path>().addOverlayAndDependencies(
                    FIXER_MULTI_RELEASE_ID,
                    root,
                    currentVersion,
                )
                val version = selectedVersion ?: return@openDirectory
                val source = root
                    .resolve(PLUGIN_PATH)
                    .resolve(FIXER_MULTI_RELEASE_ID)
                    .resolve(VERSIONS_PATH)
                    .resolve(version.toString())
                Files.walk(source).use { paths ->
                    paths.forEach { path ->
                        val relativePath = source.relativize(path).toString()
                        val destination = outputDirectory.toPath().resolve(relativePath)
                        if (Files.isDirectory(path)) {
                            Files.createDirectories(destination)
                        } else {
                            Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                        }
                    }
                }
            }
        }
        if (!opened || selectedVersion == null) {
            throw GradleException(
                "The exempts fixer has no compiler-API overlay compatible with Kotlin $kotlinVersion",
            )
        }
        return outputDirectory
    }

    private const val FIXER_MAIN_CLASS =
        "org.jetbrains.kotlinx.library.api.watchdog.fixer.ExemptsFixerMain"
    private const val FIXER_MULTI_RELEASE_ID = "org.jetbrains.kotlinx.library.api.watchdog.fixer"
}

internal fun String.unescapeFixerNewlines(): String {
    val result = StringBuilder(length)
    var index = 0
    while (index < length) {
        val character = this[index]
        if (character == '\\' && index + 1 < length) {
            index++
            when (this[index]) {
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                else -> result.append(this[index])
            }
        } else {
            result.append(character)
        }
        index++
    }
    return result.toString()
}
