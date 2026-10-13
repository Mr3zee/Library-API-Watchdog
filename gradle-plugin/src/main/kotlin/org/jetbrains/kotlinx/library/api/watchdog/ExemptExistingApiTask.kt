package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations

/**
 * The `exemptExistingApi` task. It adds the matching `@Intentionally*` annotation with
 * `reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY` to every declaration with a watchdog
 * diagnostic in the main Kotlin sources, then writes the API Watchdog report. Use it when you
 * adopt the watchdog on a library with a released API.
 */
@UntrackedTask(because = "Rewrites the Kotlin sources it analyzes")
public abstract class ExemptExistingApiTask : DefaultTask() {
    /** The names of the main Kotlin compile tasks. */
    @get:Input
    public abstract val compilationNames: ListProperty<String>

    /** The diagnostic reports of the Kotlin compile tasks. A missing report is empty. */
    @get:Internal
    public abstract val diagnosticReports: ConfigurableFileCollection

    /** The main Kotlin sources. The report lists their applied annotations. */
    @get:Internal
    public abstract val sourceFiles: ConfigurableFileCollection

    /** The multi-version artifact of the fixer. */
    @get:Classpath
    public abstract val fixerArtifact: ConfigurableFileCollection

    /** The Kotlin compiler and its runtime dependencies. */
    @get:Classpath
    public abstract val fixerClasspath: ConfigurableFileCollection

    /** The Kotlin version of the project. It selects the fixer overlay. */
    @get:Input
    public abstract val kotlinVersion: Property<String>

    /** The directory that source paths in the console output and in the report are relative to. */
    @get:Internal
    public abstract val projectDirectory: DirectoryProperty

    /** The project path shown in the report. */
    @get:Input
    public abstract val projectIdentityPath: Property<String>

    /** The HTML report. */
    @get:OutputFile
    public abstract val reportFile: RegularFileProperty

    /** The report data that the aggregation plugin reads. */
    @get:OutputFile
    public abstract val reportDataFile: RegularFileProperty

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @TaskAction
    public fun exemptExistingApi() {
        val response = if (compilationNames.get().isEmpty()) {
            logger.warn("No main Kotlin compilation found, so there are no watchdog diagnostics to exempt.")
            emptyMap()
        } else {
            ExemptionFixerRunner.run(
                temporaryDirectory = temporaryDir,
                diagnosticReports = diagnosticReports.files,
                sourceFiles = sourceFiles.files,
                fixerArtifact = fixerArtifact,
                fixerClasspath = fixerClasspath,
                kotlinVersion = kotlinVersion.get(),
                execOperations = execOperations,
                updateSources = true,
            )
        }

        var exemptionCount = 0
        var kdocCount = 0
        response["fixed"].orEmpty().forEach { value ->
            val (diagnostic, annotation, line, path) = value.split('\t', limit = 4)
            // An empty annotation field marks a placeholder KDoc.
            if (annotation.isEmpty()) {
                kdocCount++
                logger.lifecycle("A placeholder KDoc documents $diagnostic at ${relativize(path)}:$line")
            } else {
                exemptionCount++
                logger.lifecycle("@$annotation acknowledges $diagnostic at ${relativize(path)}:$line")
            }
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

        if (exemptionCount == 0 && kdocCount == 0 && skipped.isEmpty()) {
            logger.lifecycle("No watchdog diagnostics to exempt.")
        } else {
            logger.lifecycle(
                "Added $exemptionCount backwards-compatibility exemption(s)" +
                        (if (kdocCount == 0) "" else " and $kdocCount placeholder KDoc(s)") +
                        " in ${modifiedFiles.size} file(s)." +
                        if (skipped.isEmpty()) "" else " ${skipped.size} diagnostic(s) need manual attention."
            )
        }
        ProjectReportWriter.write(
            response = response,
            sourcesUpdated = true,
            projectPath = projectIdentityPath.get(),
            projectDirectory = projectDirectory.get().asFile,
            reportFile = reportFile.get().asFile,
            reportDataFile = reportDataFile.get().asFile,
            logger = logger,
        )
    }

    private fun relativize(path: String): String =
        File(path).relativeToOrSelf(projectDirectory.get().asFile).path
}
