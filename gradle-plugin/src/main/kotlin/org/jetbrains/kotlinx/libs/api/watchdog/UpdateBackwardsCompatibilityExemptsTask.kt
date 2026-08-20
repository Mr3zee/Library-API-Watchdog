package org.jetbrains.kotlinx.libs.api.watchdog

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.compiler.plugin.devkit.KotlinToolingVersion
import org.jetbrains.kotlin.compiler.plugin.devkit.VersionResolution

/**
 * Rewrites the module's Kotlin sources so that every recorded watchdog diagnostic is
 * acknowledged with the matching `@Intentionally*` exemption annotation carrying
 * `reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY`.
 *
 * The task is meant for adopting the watchdog on an existing library: the already-shipped API
 * can't change shape without breaking users, so its diagnostics are acknowledged wholesale
 * and the checks keep guarding only the API added afterwards. New code should get a deliberate
 * decision instead: fix the shape, or pick the honest reason by hand.
 *
 * When this task is selected, every main Kotlin compilation writes a report through the regular
 * KGP compile task. This task depends on those compilations, hands all reports to one standalone
 * PSI fixer process, and never invokes a Kotlin compiler itself. Exact duplicates from common
 * sources compiled for several targets are removed by the fixer.
 */
@UntrackedTask(because = "Rewrites the Kotlin sources it analyzes, so it must always run")
public abstract class UpdateBackwardsCompatibilityExemptsTask : DefaultTask() {
    /** The main Kotlin compile task names KGP exposed to the compiler support plugin. */
    @get:Input
    public abstract val compilationNames: ListProperty<String>

    /** Reports written by the regular Kotlin compilation tasks. Missing reports are empty. */
    @get:Internal
    public abstract val diagnosticReports: ConfigurableFileCollection

    /** The dev-kit multi-version artifact containing the standalone fixer tool. */
    @get:Classpath
    public abstract val fixerArtifact: ConfigurableFileCollection

    /** kotlin-compiler and its runtime dependencies used for PSI parsing. */
    @get:Classpath
    public abstract val fixerClasspath: ConfigurableFileCollection

    /** The consuming project's Kotlin version, used to select the matching fixer overlay. */
    @get:Input
    public abstract val kotlinVersion: Property<String>

    /** The directory console output relativizes source paths against. */
    @get:Internal
    public abstract val projectDirectory: DirectoryProperty

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @TaskAction
    public fun updateExempts() {
        if (compilationNames.get().isEmpty()) {
            logger.warn("No main Kotlin compilation found. There are no watchdog diagnostics to exempt.")
            return
        }

        val workDir = temporaryDir.resolve("fixer").apply {
            deleteRecursively()
            mkdirs()
        }
        val requestFile = workDir.resolve("request.txt")
        val responseFile = workDir.resolve("response.txt")
        val fixerClasses = unpackFixer(workDir.resolve("classes"))
        requestFile.writeText(buildString {
            diagnosticReports.files.sortedBy(File::getAbsolutePath).forEach {
                appendLine("reportFile=${it.absolutePath}")
            }
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
            throw GradleException("The exempts fixer failed:\n${it.unescapeNewlines()}")
        }
        if (execResult.exitValue != 0) {
            throw GradleException(
                "The exempts fixer exited with code ${execResult.exitValue} without reporting an error."
            )
        }

        var appliedCount = 0
        response["fixed"].orEmpty().forEach { value ->
            val (diagnostic, annotation, line, path) = value.split('\t', limit = 4)
            appliedCount++
            logger.lifecycle("@$annotation acknowledges $diagnostic at ${relativize(path)}:$line")
        }
        val modifiedFiles = response["modifiedFile"].orEmpty().toSet()
        val skipped = response["skipped"].orEmpty().map { value ->
            val (diagnostic, line, path, reason) = value.split('\t', limit = 4)
            Triple(diagnostic, "${relativize(path)}:$line", reason.unescapeNewlines())
        }
        skipped.forEach { (diagnostic, location, reason) ->
            logger.warn("$diagnostic at $location needs manual attention: $reason")
        }

        if (appliedCount == 0 && skipped.isEmpty()) {
            logger.lifecycle("No watchdog diagnostics to exempt.")
        } else {
            logger.lifecycle(
                "Added $appliedCount backwards-compatibility exemption(s) in " +
                        "${modifiedFiles.size} file(s)." +
                        if (skipped.isEmpty()) "" else " ${skipped.size} diagnostic(s) need manual attention."
            )
        }
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

    private fun relativize(path: String): String =
        File(path).relativeToOrSelf(projectDirectory.get().asFile).path

    /**
     * Compiler-library artifacts use the same dev-kit overlay layout as compiler plugins, but a
     * standalone Java process does not go through compiler-plugin class loading. Select and
     * materialize the compatible overlay before starting the fixer process.
     */
    private fun unpackFixer(outputDirectory: File): File {
        outputDirectory.deleteRecursively()
        check(outputDirectory.mkdirs()) { "Could not create $outputDirectory" }

        val artifact = fixerArtifact.singleFile.toPath()
        val currentVersion = KotlinToolingVersion(kotlinVersion.get())
        var selectedVersion: KotlinToolingVersion? = null
        val opened = VersionResolution.run {
            artifact.openDirectory { root ->
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
                "The exempts fixer has no compiler-API overlay compatible with Kotlin " +
                    kotlinVersion.get(),
            )
        }
        return outputDirectory
    }

    private fun String.unescapeNewlines(): String {
        val result = StringBuilder(length)
        var index = 0
        while (index < length) {
            val char = this[index]
            if (char == '\\' && index + 1 < length) {
                index++
                when (this[index]) {
                    'n' -> result.append('\n')
                    'r' -> result.append('\r')
                    else -> result.append(this[index])
                }
            } else {
                result.append(char)
            }
            index++
        }
        return result.toString()
    }

    private companion object {
        private const val FIXER_MAIN_CLASS = "org.jetbrains.kotlinx.libs.api.watchdog.fixer.ExemptsFixerMain"
        private const val FIXER_MULTI_RELEASE_ID = "org.jetbrains.kotlin.library.api.watchdog.fixer"
    }
}
