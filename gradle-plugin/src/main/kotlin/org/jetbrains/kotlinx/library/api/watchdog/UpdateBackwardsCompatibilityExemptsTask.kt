package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
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

        val response = BackwardsCompatibilityExemptsFixerRunner.run(
            temporaryDirectory = temporaryDir,
            diagnosticReports = diagnosticReports.files,
            sourceFiles = emptySet(),
            fixerArtifact = fixerArtifact,
            fixerClasspath = fixerClasspath,
            kotlinVersion = kotlinVersion.get(),
            execOperations = execOperations,
            updateSources = true,
        )

        var appliedCount = 0
        response["fixed"].orEmpty().forEach { value ->
            val (diagnostic, annotation, line, path) = value.split('\t', limit = 4)
            appliedCount++
            logger.lifecycle("@$annotation acknowledges $diagnostic at ${relativize(path)}:$line")
        }
        val modifiedFiles = response["modifiedFile"].orEmpty().toSet()
        val skipped = response["skipped"].orEmpty().map { value ->
            val (diagnostic, _, line, path, reason) = value.split('\t', limit = 5)
            Triple(diagnostic, "${relativize(path)}:$line", reason.unescapeFixerNewlines())
        }
        skipped.forEach { (diagnostic, location, reason) ->
            logger.warn(
                "$diagnostic at $location needs manual attention: $reason"
            )
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

    private fun relativize(path: String): String =
        File(path).relativeToOrSelf(projectDirectory.get().asFile).path
}
