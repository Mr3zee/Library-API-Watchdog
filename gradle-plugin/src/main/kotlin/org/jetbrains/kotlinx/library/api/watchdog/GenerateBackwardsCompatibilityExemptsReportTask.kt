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
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations

/**
 * The `generateBackwardsCompatibilityExemptsReport` task. It reports the applied and the missing
 * backwards-compatibility exemptions of the project and changes no source.
 */
@UntrackedTask(because = "Reads the diagnostic reports that the Kotlin compile tasks write")
public abstract class GenerateBackwardsCompatibilityExemptsReportTask : DefaultTask() {
    /** The names of the main Kotlin compile tasks. */
    @get:Input
    public abstract val compilationNames: ListProperty<String>

    /** The diagnostic reports of the Kotlin compile tasks. */
    @get:Internal
    public abstract val diagnosticReports: ConfigurableFileCollection

    /** The main Kotlin sources. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
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

    /** The directory that source paths in the report are relative to. */
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
    public fun generateReport() {
        val response = if (compilationNames.get().isEmpty()) {
            logger.warn("No main Kotlin compilation found, so the exemptions report is empty.")
            emptyMap()
        } else {
            BackwardsCompatibilityExemptsFixerRunner.run(
                temporaryDirectory = temporaryDir,
                diagnosticReports = diagnosticReports.files,
                sourceFiles = sourceFiles.files,
                fixerArtifact = fixerArtifact,
                fixerClasspath = fixerClasspath,
                kotlinVersion = kotlinVersion.get(),
                execOperations = execOperations,
                updateSources = false,
            )
        }

        val applied = response["exemption"].orEmpty().map { value ->
            val (annotation, line, path) = value.split('\t', limit = 3)
            AppliedExemptionReportEntry(annotation, "${relativize(path)}:$line")
        }
        val applicable = response["fixed"].orEmpty().map { value ->
            val (diagnostic, annotation, line, path) = value.split('\t', limit = 4)
            // An empty annotation field marks a placeholder KDoc.
            NotAppliedExemptionReportEntry(
                annotation = annotation.ifEmpty { null },
                diagnostic = diagnostic,
                location = "${relativize(path)}:$line",
                reason = if (annotation.isEmpty()) {
                    "updateBackwardsCompatibilityExempts can add a placeholder KDoc."
                } else {
                    "updateBackwardsCompatibilityExempts can apply it."
                },
            )
        }
        val skipped = response["skipped"].orEmpty().map { value ->
            val (diagnostic, annotation, line, path, reason) = value.split('\t', limit = 5)
            NotAppliedExemptionReportEntry(
                annotation = annotation.ifEmpty { null },
                diagnostic = diagnostic,
                location = "${relativize(path)}:$line",
                reason = reason.unescapeFixerNewlines(),
            )
        }
        val report = BackwardsCompatibilityExemptsProjectReport(
            projectPath = projectIdentityPath.get(),
            applied = applied,
            notApplied = applicable + skipped,
        )
        BackwardsCompatibilityExemptsReportData.write(report, reportDataFile.get().asFile)
        BackwardsCompatibilityExemptsHtmlReport.write(listOf(report), reportFile.get().asFile, aggregate = false)
        logger.lifecycle("Backwards-compatibility exemptions report: ${reportFile.get().asFile}")
    }

    private fun relativize(path: String): String =
        File(path).relativeToOrSelf(projectDirectory.get().asFile).path
}
