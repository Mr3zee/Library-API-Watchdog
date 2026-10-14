package org.jetbrains.kotlinx.library.api.watchdog

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * The base of [ExemptExistingApiTask] and [GenerateApiWatchdogReportTask]. Both write the
 * [API Watchdog report](https://mr3zee.github.io/Library-API-Watchdog/report).
 */
public abstract class ApiWatchdogReportTask internal constructor() : DefaultTask() {
    /** The names of the main Kotlin compile tasks. */
    @get:Input
    internal abstract val compilationNames: ListProperty<String>

    /** A missing report is empty. */
    @get:Internal
    internal abstract val diagnosticReports: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    internal abstract val sourceFiles: ConfigurableFileCollection

    @get:Classpath
    internal abstract val fixerArtifact: ConfigurableFileCollection

    @get:Classpath
    internal abstract val fixerClasspath: ConfigurableFileCollection

    @get:Input
    internal abstract val kotlinVersion: Property<String>

    /** The directory that source paths in the console output and in the report are relative to. */
    @get:Internal
    internal abstract val projectDirectory: DirectoryProperty

    @get:Input
    internal abstract val projectIdentityPath: Property<String>

    /**
     * The project directory relative to the root directory of the build, with `/` separators.
     * The SARIF report prefixes it to every source path.
     */
    @get:Input
    internal abstract val projectDirectoryFromRoot: Property<String>

    @get:Input
    internal abstract val diagnosticSeverities: MapProperty<String, WatchdogSeverity>

    /** The report formats. Default: the `reports` block of the `apiWatchdog` extension. */
    @get:Nested
    public val reports: WatchdogReports = project.objects.newInstance(WatchdogReports::class.java)

    /** The report data that the aggregation plugin reads. */
    @get:OutputFile
    internal abstract val reportDataFile: RegularFileProperty

    /** Configures [reports]. */
    public fun reports(action: Action<WatchdogReports>) {
        action.execute(reports)
    }

    @get:Inject
    internal abstract val execOperations: ExecOperations

    @get:Internal
    internal abstract val updatesSources: Boolean

    @get:Internal
    internal abstract val noCompilationWarning: String

    internal open fun logResponse(response: FixerResponse) {}

    @TaskAction
    internal fun writeReport() {
        val response = if (compilationNames.get().isEmpty()) {
            logger.warn(noCompilationWarning)
            FixerResponse.EMPTY
        } else {
            ExemptionFixerRunner.run(
                temporaryDirectory = temporaryDir,
                diagnosticReports = diagnosticReports.files,
                sourceFiles = sourceFiles.files,
                fixerArtifact = fixerArtifact,
                fixerClasspath = fixerClasspath,
                diagnosticSeverities = diagnosticSeverities.get(),
                kotlinVersion = kotlinVersion.get(),
                execOperations = execOperations,
                updateSources = updatesSources,
            )
        }
        logResponse(response)
        ProjectReportWriter.write(
            response = response,
            sourcesUpdated = updatesSources,
            severities = diagnosticSeverities.get(),
            projectPath = projectIdentityPath.get(),
            projectDirectory = projectDirectory.get().asFile,
            projectDirectoryFromRoot = projectDirectoryFromRoot.get(),
            htmlFile = if (reports.html.get()) reports.htmlFile.get().asFile else null,
            sarifFile = if (reports.sarif.get()) reports.sarifFile.get().asFile else null,
            reportDataFile = reportDataFile.get().asFile,
            logger = logger,
        )
    }
}
