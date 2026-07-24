package org.jetbrains.kotlinx.libs.api.watchdog

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations

/**
 * Rewrites the module's Kotlin sources so that every recorded watchdog diagnostic is
 * acknowledged with the matching `@Intentionally*` exemption annotation carrying
 * `reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY`.
 *
 * The task is meant for adopting the watchdog on an existing library: the already-shipped API
 * cannot change shape without breaking clients, so its diagnostics are acknowledged wholesale
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

    /** The standalone fixer tool and kotlin-compiler-embeddable used for PSI parsing. */
    @get:Classpath
    public abstract val fixerClasspath: ConfigurableFileCollection

    /** The directory console output relativizes source paths against. */
    @get:Input
    public abstract val projectDirectory: Property<File>

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @TaskAction
    public fun updateExempts() {
        if (compilationNames.get().isEmpty()) {
            logger.warn("No main Kotlin compilation found; there are no watchdog diagnostics to exempt.")
            return
        }

        val workDir = temporaryDir.resolve("fixer").apply {
            deleteRecursively()
            mkdirs()
        }
        val requestFile = workDir.resolve("request.txt")
        val responseFile = workDir.resolve("response.txt")
        requestFile.writeText(buildString {
            diagnosticReports.files.sortedBy(File::getAbsolutePath).forEach {
                appendLine("reportFile=${it.absolutePath}")
            }
            appendLine("responseFile=${responseFile.absolutePath}")
        })

        val execResult = execOperations.javaexec { spec ->
            spec.classpath = fixerClasspath
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
                        "${modifiedFiles.size} file(s)" +
                        if (skipped.isEmpty()) "." else "; ${skipped.size} diagnostic(s) need manual attention."
            )
        }
    }

    private fun parseResponse(responseFile: File, exitValue: Int): Map<String, List<String>> {
        if (!responseFile.isFile) {
            throw GradleException(
                "The exempts fixer produced no response (exit code $exitValue); " +
                        "see the process output above."
            )
        }
        return responseFile.readLines()
            .filter { it.isNotBlank() }
            .groupBy(keySelector = { it.substringBefore('=') }, valueTransform = { it.substringAfter('=') })
    }

    private fun relativize(path: String): String =
        File(path).relativeToOrSelf(projectDirectory.get()).path

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
    }
}
