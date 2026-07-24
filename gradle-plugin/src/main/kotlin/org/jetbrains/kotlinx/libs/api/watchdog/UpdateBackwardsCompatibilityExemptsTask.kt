package org.jetbrains.kotlinx.libs.api.watchdog

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations

/**
 * Rewrites the module's Kotlin sources so that every watchdog diagnostic is acknowledged with the
 * matching `@Intentionally*` exemption annotation carrying
 * `reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY`.
 *
 * The task is meant for adopting the watchdog on an existing library: the already-shipped API
 * cannot change shape without breaking clients, so its diagnostics are acknowledged wholesale and
 * the checks keep guarding only the API added afterwards. New code should get a deliberate
 * decision instead: fix the shape, or pick the honest reason by hand.
 *
 * For each main JVM compilation the task launches the `exempts-fixer` tool in a separate JVM,
 * which recompiles the sources through the Kotlin Build Tools API with the watchdog compiler
 * plugin recording every diagnostic it reports (position included), and then inserts the
 * annotations into the sources. Diagnostics with no automatic fix (for example
 * `EXEMPTION_WITHOUT_EXPLANATION`) are listed as warnings for manual follow-up. Explicit API mode
 * is forced to its `warning` variant for the analysis, so the task also works while the module
 * has not enabled explicit API mode yet.
 */
@UntrackedTask(because = "Rewrites the Kotlin sources it analyzes, so it must always run")
public abstract class UpdateBackwardsCompatibilityExemptsTask : DefaultTask() {
    /** One entry per main JVM compilation of the project. */
    @get:Nested
    public abstract val compilations: ListProperty<WatchdogCompilationInput>

    /** The `exempts-fixer` tool plus the Build Tools API implementation it compiles with. */
    @get:Classpath
    public abstract val fixerClasspath: ConfigurableFileCollection

    /** The watchdog compiler plugin id the diagnostics-recording option is addressed to. */
    @get:Input
    public abstract val pluginId: Property<String>

    /** The directory console output relativizes source paths against. */
    @get:Input
    public abstract val projectDirectory: Property<File>

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @TaskAction
    public fun updateExempts() {
        val inputs = compilations.get()
        if (inputs.isEmpty()) {
            logger.warn(
                "No main JVM compilation found: the watchdog can only collect its diagnostics " +
                        "through a JVM compilation. Add a JVM target to run $name."
            )
            return
        }

        val totals = FixTotals()
        // Sequential on purpose: compilations may share source files (a multiplatform project's
        // common source set), and each fixer run compiles the current on-disk state, so a later
        // run always sees the previous run's insertions.
        inputs.forEach { input -> runFixer(input, totals) }

        totals.skipped.forEach { (diagnostic, location, reason) ->
            logger.warn("$diagnostic at $location needs manual attention: $reason")
        }
        if (totals.appliedCount == 0 && totals.skipped.isEmpty()) {
            logger.lifecycle("No watchdog diagnostics to exempt.")
        } else {
            logger.lifecycle(
                "Added ${totals.appliedCount} backwards-compatibility exemption(s) in " +
                        "${totals.modifiedFiles.size} file(s)" +
                        if (totals.skipped.isEmpty()) "." else "; ${totals.skipped.size} diagnostic(s) need manual attention."
            )
        }
    }

    private class FixTotals {
        var appliedCount: Int = 0
        val modifiedFiles = mutableSetOf<String>()
        val skipped = mutableListOf<Triple<String, String, String>>()
    }

    private fun runFixer(input: WatchdogCompilationInput, totals: FixTotals) {
        val compilationName = input.compilationName.get()
        val sources = input.sources.files.filter { it.isFile }
        if (sources.none { it.extension == "kt" || it.extension == "kts" }) {
            logger.info("Skipping $compilationName: it has no Kotlin sources.")
            return
        }

        val workDir = temporaryDir.resolve(compilationName).apply {
            deleteRecursively()
            mkdirs()
        }
        val requestFile = workDir.resolve("request.txt")
        val responseFile = workDir.resolve("response.txt")
        requestFile.writeText(buildString {
            sources.forEach { appendLine("source=${it.absolutePath}") }

            fun arg(value: String) = appendLine("compilerArg=$value")
            // The classpath provides the stdlib and the exemption annotations; the compiler must
            // not layer its own bundled stdlib on top.
            arg("-no-stdlib")
            arg("-no-reflect")
            val classpath = input.libraries.files.filter { it.exists() }
            if (classpath.isNotEmpty()) {
                arg("-classpath")
                arg(classpath.joinToString(File.pathSeparator))
            }
            input.pluginClasspath.files.forEach { arg("-Xplugin=${it.absolutePath}") }
            val friendPaths = input.friendPaths.files.filter { it.exists() }
            if (friendPaths.isNotEmpty()) {
                arg("-Xfriend-paths=${friendPaths.joinToString(",")}")
            }
            input.compilerArgs.get().forEach { arg(it) }
            // Last, so it wins over an explicit-api setting from the compilation's own arguments:
            // the checkers need explicit API mode, but its violations must not become errors here.
            arg("-Xexplicit-api=warning")

            appendLine("pluginId=${pluginId.get()}")
            appendLine("workDir=${workDir.resolve("compile").absolutePath}")
            appendLine("responseFile=${responseFile.absolutePath}")
        })

        val execResult = execOperations.javaexec { spec ->
            spec.classpath = fixerClasspath
            spec.mainClass.set(FIXER_MAIN_CLASS)
            spec.args(requestFile.absolutePath)
            spec.isIgnoreExitValue = true
        }

        val response = parseResponse(responseFile, compilationName, execResult.exitValue)
        response["error"]?.forEach {
            throw GradleException("The exempts fixer failed for $compilationName:\n${it.unescapeNewlines()}")
        }
        if (execResult.exitValue != 0) {
            throw GradleException(
                "The exempts fixer exited with code ${execResult.exitValue} for $compilationName " +
                        "without reporting an error."
            )
        }

        response["fixed"].orEmpty().forEach { value ->
            val (diagnostic, annotation, line, path) = value.split('\t', limit = 4)
            totals.appliedCount++
            logger.lifecycle("@$annotation acknowledges $diagnostic at ${relativize(path)}:$line")
        }
        response["modifiedFile"].orEmpty().forEach { totals.modifiedFiles += it }
        response["skipped"].orEmpty().forEach { value ->
            val (diagnostic, line, path, reason) = value.split('\t', limit = 4)
            totals.skipped += Triple(diagnostic, "${relativize(path)}:$line", reason.unescapeNewlines())
        }

        // A failed analysis compilation is expected while watchdog diagnostics are errors, but a
        // failure that recorded no diagnostics at all points at code that does not compile.
        val recordedNothing = response["fixed"].orEmpty().isEmpty() && response["skipped"].orEmpty().isEmpty()
        if (recordedNothing && response["compilationResult"]?.singleOrNull() == "COMPILATION_ERROR") {
            val compilerErrors = response["compilerMessage"].orEmpty()
                .map { it.unescapeNewlines() }
                .filter { it.startsWith("e: ") }
            logger.warn(
                "The analysis compilation of $compilationName failed before the watchdog could " +
                        "report anything; fix the compilation errors first:\n" +
                        compilerErrors.take(10).joinToString("\n")
            )
        }
    }

    private fun parseResponse(responseFile: File, compilationName: String, exitValue: Int): Map<String, List<String>> {
        if (!responseFile.isFile) {
            throw GradleException(
                "The exempts fixer produced no response for $compilationName " +
                        "(exit code $exitValue); see the process output above."
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

/** The inputs of one JVM compilation [UpdateBackwardsCompatibilityExemptsTask] analyzes. */
public abstract class WatchdogCompilationInput {
    /** The compile task name, used for scratch directories and log lines. */
    @get:Input
    public abstract val compilationName: Property<String>

    /** All source files of the compilation; Java files ride along for cross-resolution. */
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract val sources: ConfigurableFileCollection

    /** The compile classpath, including the automatically added exemption annotations library. */
    @get:Classpath
    public abstract val libraries: ConfigurableFileCollection

    /** The compiler plugins of the real compilation, the watchdog plugin among them. */
    @get:Classpath
    public abstract val pluginClasspath: ConfigurableFileCollection

    /** Friend paths of the compilation, for `internal` access to associated outputs. */
    @get:Classpath
    public abstract val friendPaths: ConfigurableFileCollection

    /** The compilation's settings rendered as Kotlin CLI arguments. */
    @get:Input
    public abstract val compilerArgs: ListProperty<String>
}
