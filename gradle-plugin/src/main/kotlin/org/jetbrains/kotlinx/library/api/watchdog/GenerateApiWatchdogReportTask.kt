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
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations

/**
 * The `generateApiWatchdogReport` task. It writes the API Watchdog report of the project: the
 * applied `@Intentionally*` annotations and the diagnostics without one. It changes no source.
 */
@UntrackedTask(because = "Reads the diagnostic reports that the Kotlin compile tasks write")
public abstract class GenerateApiWatchdogReportTask : DefaultTask() {
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

    /**
     * The project directory relative to the root directory of the build, with `/` separators.
     * The SARIF report prefixes it to every source path.
     */
    @get:Input
    public abstract val projectDirectoryFromRoot: Property<String>

    /** The configured severity of each diagnostic. The SARIF report uses it as the result level. */
    @get:Input
    public abstract val diagnosticSeverities: MapProperty<String, WatchdogSeverity>

    /** The report formats. Default: the `reports` block of the `apiWatchdog` extension. */
    @get:Nested
    public val reports: WatchdogReports = project.objects.newInstance(WatchdogReports::class.java)

    /** The report data that the aggregation plugin reads. */
    @get:OutputFile
    public abstract val reportDataFile: RegularFileProperty

    /** Configures [reports]. */
    public fun reports(action: Action<WatchdogReports>) {
        action.execute(reports)
    }

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @TaskAction
    public fun generateReport() {
        val response = if (compilationNames.get().isEmpty()) {
            logger.warn("No main Kotlin compilation found, so the API Watchdog report is empty.")
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
                updateSources = false,
            )
        }
        ProjectReportWriter.write(
            response = response,
            sourcesUpdated = false,
            severities = diagnosticSeverities.get(),
            projectPath = projectIdentityPath.get(),
            projectDirectory = projectDirectory.get().asFile,
            projectDirectoryFromRoot = projectDirectoryFromRoot.get(),
            htmlFile = reports.htmlOutput.orNull?.asFile,
            sarifFile = reports.sarifOutput.orNull?.asFile,
            reportDataFile = reportDataFile.get().asFile,
            logger = logger,
        )
    }
}
