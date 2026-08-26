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

/** Generates a report without modifying any Kotlin source file. */
@UntrackedTask(because = "Consumes diagnostic side files written by Kotlin compilation tasks")
public abstract class GenerateBackwardsCompatibilityExemptsReportTask : DefaultTask() {
    /** The main Kotlin compile task names KGP exposed to the compiler support plugin. */
    @get:Input
    public abstract val compilationNames: ListProperty<String>

    /** Reports written by the regular Kotlin compilation tasks. */
    @get:Internal
    public abstract val diagnosticReports: ConfigurableFileCollection

    /** Main Kotlin sources, including files whose exemptions suppress every diagnostic. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val sourceFiles: ConfigurableFileCollection

    /** The dev-kit multi-version artifact containing the standalone analysis tool. */
    @get:Classpath
    public abstract val fixerArtifact: ConfigurableFileCollection

    /** kotlin-compiler and its runtime dependencies used for PSI parsing. */
    @get:Classpath
    public abstract val fixerClasspath: ConfigurableFileCollection

    /** The consuming project's Kotlin version, used to select the matching fixer overlay. */
    @get:Input
    public abstract val kotlinVersion: Property<String>

    /** The directory report locations are relativized against. */
    @get:Internal
    public abstract val projectDirectory: DirectoryProperty

    /** The isolated Gradle project path displayed in module and aggregate reports. */
    @get:Input
    public abstract val projectIdentityPath: Property<String>

    /** The standalone, human-readable report for this Gradle project. */
    @get:OutputFile
    public abstract val reportFile: RegularFileProperty

    /** The module report variant consumed by the aggregation plugin. */
    @get:OutputFile
    public abstract val reportDataFile: RegularFileProperty

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @TaskAction
    public fun generateReport() {
        val response = if (compilationNames.get().isEmpty()) {
            logger.warn("No main Kotlin compilation found. The exemptions report will be empty.")
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
            NotAppliedExemptionReportEntry(
                annotation = annotation,
                diagnostic = diagnostic,
                location = "${relativize(path)}:$line",
                reason = "Can be applied by updateBackwardsCompatibilityExempts.",
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
