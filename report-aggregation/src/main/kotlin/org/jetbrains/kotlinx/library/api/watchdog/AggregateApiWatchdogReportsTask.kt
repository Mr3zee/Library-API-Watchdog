package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Combines the API Watchdog reports of the projects declared in `apiWatchdogReports`.
 *
 * See [Multi-project builds](https://mr3zee.github.io/Library-API-Watchdog/report#multi-project-builds) for more details.
 */
@CacheableTask
public abstract class AggregateApiWatchdogReportsTask internal constructor() : DefaultTask() {
    /** The project reports to combine. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val reportDataFiles: ConfigurableFileCollection

    @get:Input
    internal val projectDirectoryPrefixes: MapProperty<String, String> =
        project.objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())

    /** The report formats. Default files are `build/reports/api-watchdog/aggregate-report.html` and `.sarif`. */
    @get:Nested
    public val reports: WatchdogReports = project.objects.newInstance(WatchdogReports::class.java)

    /** Configures [reports]. */
    public fun reports(action: Action<WatchdogReports>) {
        action.execute(reports)
    }

    @TaskAction
    internal fun generateReport() {
        val prefixes = projectDirectoryPrefixes.getOrElse(emptyMap())
        val reports = reportDataFiles.files
            .sortedBy { it.absolutePath }
            .map(ApiWatchdogReportData::read)
            .distinctBy { it.projectPath }
            .map { report ->
                val prefix = prefixes[report.projectPath].orEmpty()
                if (prefix.isEmpty()) report else report.copy(
                    projectDirectory = File(prefix).resolve(report.projectDirectory).normalize().invariantSeparatorsPath,
                )
            }
        this.reports.htmlOutput.orNull?.asFile?.let { file ->
            ApiWatchdogHtmlReport.write(reports, file, aggregate = true)
            logger.lifecycle("Aggregate API Watchdog report: ${file.toPath().toUri()}")
        }
        this.reports.sarifOutput.orNull?.asFile?.let { file ->
            ApiWatchdogSarifReport.write(reports, file)
            logger.lifecycle("Aggregate API Watchdog SARIF report: ${file.toPath().toUri()}")
        }
    }
}

