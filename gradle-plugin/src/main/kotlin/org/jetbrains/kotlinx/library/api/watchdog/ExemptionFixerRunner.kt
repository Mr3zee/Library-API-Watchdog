package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import java.nio.file.Path

internal object ExemptionFixerRunner {
    fun run(
        temporaryDirectory: File,
        diagnosticReports: Collection<File>,
        sourceFiles: Collection<File>,
        fixerArtifact: FileCollection,
        fixerClasspath: FileCollection,
        diagnosticSeverities: Map<String, WatchdogSeverity>,
        kotlinVersion: String,
        execOperations: ExecOperations,
        updateSources: Boolean,
    ): FixerResponse {
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
            diagnosticSeverities.entries
                .sortedBy { it.key }
                .forEach { (diagnostic, severity) ->
                    appendLine("diagnosticSeverity=$diagnostic:${severity.name.lowercase()}")
                }
            appendLine("updateSources=$updateSources")
            appendLine("responseFile=${responseFile.absolutePath}")
        })

        val execResult = execOperations.javaexec { spec ->
            spec.classpath(fixerClasses, fixerClasspath)
            spec.mainClass.set(FIXER_MAIN_CLASS)
            spec.args(requestFile.absolutePath)
            spec.isIgnoreExitValue = true
        }
        val response = readResponse(responseFile, execResult.exitValue)
        response["error"]?.forEach {
            throw GradleException("The exempts fixer failed:\n${it.unescapeFixerNewlines()}")
        }
        if (execResult.exitValue != 0) {
            throw GradleException(
                "The exempts fixer exited with code ${execResult.exitValue} without reporting an error."
            )
        }
        return try {
            FixerResponse.parse(response)
        } catch (failure: IllegalArgumentException) {
            throw GradleException("The exempts fixer wrote a malformed response.", failure)
        }
    }

    private fun readResponse(responseFile: File, exitValue: Int): Map<String, List<String>> {
        if (!responseFile.isFile) {
            throw GradleException(
                "The exempts fixer didn't write a response (exit code $exitValue). " +
                        "The process output above has the cause."
            )
        }
        return responseFile.readLines()
            .filter { it.isNotBlank() }
            .onEach { line ->
                if (line.indexOf('=') <= 0) {
                    throw GradleException("The exempts fixer wrote a malformed response: expected a key=value entry.")
                }
            }
            .groupBy(keySelector = { it.substringBefore('=') }, valueTransform = { it.substringAfter('=') })
    }

    private fun unpackFixer(
        artifact: File,
        kotlinVersion: String,
        outputDirectory: File,
    ): File {
        outputDirectory.deleteRecursively()
        check(outputDirectory.mkdirs()) { "Can't create $outputDirectory" }

        val currentVersion = KotlinToolingVersion(kotlinVersion)
        var selectedVersion: KotlinToolingVersion? = null
        val opened = VersionResolution.run {
            artifact.toPath().openDirectory { root ->
                selectedVersion = mutableListOf<Path>().addOverlayAndDependencies(
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
                "The exempts fixer doesn't support Kotlin $kotlinVersion",
            )
        }
        return outputDirectory
    }

    private const val FIXER_MAIN_CLASS =
        "org.jetbrains.kotlinx.library.api.watchdog.fixer.ExemptsFixerMain"
    private const val FIXER_MULTI_RELEASE_ID = "org.jetbrains.kotlinx.library.api.watchdog.fixer"
}

/** The response of the fixer. [FixerLocation.path] is absolute. */
internal class FixerResponse(
    val fixed: List<FixedDiagnostic>,
    val skipped: List<SkippedDiagnostic>,
    val warnings: List<WarningDiagnostic>,
    val exemptions: List<AppliedExemption>,
    val modifiedFiles: Set<String>,
) {
    companion object {
        val EMPTY: FixerResponse = FixerResponse(emptyList(), emptyList(), emptyList(), emptyList(), emptySet())

        fun parse(lines: Map<String, List<String>>): FixerResponse = FixerResponse(
            fixed = lines["fixed"].orEmpty().map { value ->
                val fields = value.responseFields("fixed", 8)
                val (diagnostic, annotation, line, path) = fields
                FixedDiagnostic(diagnostic, annotation.ifEmpty { null }, line, path, fields.drop(8), fields.sourceRange(4))
            },
            skipped = lines["skipped"].orEmpty().map { value ->
                val fields = value.responseFields("skipped", 9)
                val (diagnostic, annotation, line, path, reason) = fields
                SkippedDiagnostic(diagnostic, annotation.ifEmpty { null }, line, path, reason, fields.drop(9), fields.sourceRange(5))
            },
            warnings = lines["warning"].orEmpty().map { value ->
                val fields = value.responseFields("warning", 8)
                val (diagnostic, annotation, line, path) = fields
                WarningDiagnostic(diagnostic, annotation.ifEmpty { null }, line, path, fields.drop(8), fields.sourceRange(4))
            },
            exemptions = lines["exemption"].orEmpty().map { value ->
                val fields = value.split('\t', limit = 3)
                require(fields.size == 3) { "An 'exemption' entry needs three fields." }
                val (annotation, line, path) = fields
                AppliedExemption(annotation, line, path)
            },
            modifiedFiles = lines["modifiedFile"].orEmpty().toSet(),
        )
    }
}

private fun String.responseFields(kind: String, minimumSize: Int): List<String> =
    split('\t').map { it.unescapeFixerNewlines() }.also { fields ->
        require(fields.size >= minimumSize) { "A '$kind' entry needs at least $minimumSize fields." }
    }

private fun List<String>.sourceRange(start: Int): ApiWatchdogSourceRange? {
    val coordinates = subList(start, start + 4)
    if (coordinates.all(String::isEmpty)) return null
    require(coordinates.none(String::isEmpty)) { "A source range needs four coordinates." }
    return ApiWatchdogSourceRange(
        coordinates[0].toInt(), coordinates[1].toInt(), coordinates[2].toInt(), coordinates[3].toInt(),
    )
}

internal interface FixerLocation {
    val line: String
    val path: String
    val sourceRange: ApiWatchdogSourceRange? get() = null
}

internal fun FixerLocation.location(projectDirectory: File): String =
    "${File(path).relativeToOrSelf(projectDirectory).path}:$line"

/** A null [annotation] marks a placeholder KDoc. */
internal class FixedDiagnostic(
    val diagnostic: String,
    val annotation: String?,
    override val line: String,
    override val path: String,
    val messageParameters: List<String>,
    override val sourceRange: ApiWatchdogSourceRange?,
) : FixerLocation

/** [annotation] is the exemption of the diagnostic, or null when none exists. */
internal class SkippedDiagnostic(
    val diagnostic: String,
    val annotation: String?,
    override val line: String,
    override val path: String,
    val reason: String,
    val messageParameters: List<String>,
    override val sourceRange: ApiWatchdogSourceRange?,
) : FixerLocation

internal class WarningDiagnostic(
    val diagnostic: String,
    val annotation: String?,
    override val line: String,
    override val path: String,
    val messageParameters: List<String>,
    override val sourceRange: ApiWatchdogSourceRange?,
) : FixerLocation

internal class AppliedExemption(
    val annotation: String,
    override val line: String,
    override val path: String,
) : FixerLocation

internal fun String.unescapeFixerNewlines(): String {
    val result = StringBuilder(length)
    var index = 0
    while (index < length) {
        val character = this[index]
        if (character == '\\' && index + 1 < length) {
            index++
            when (this[index]) {
                't' -> result.append('\t')
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
