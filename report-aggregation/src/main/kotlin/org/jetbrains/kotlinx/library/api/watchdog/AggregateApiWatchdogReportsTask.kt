package org.jetbrains.kotlinx.library.api.watchdog

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Combines the report variants declared in `apiWatchdogReports`. */
@CacheableTask
public abstract class AggregateApiWatchdogReportsTask : DefaultTask() {
    /** The module reports to combine. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val reportDataFiles: ConfigurableFileCollection

    /** The aggregate reports. Default: `build/reports/api-watchdog/aggregate-report.html` and `.sarif`. */
    @get:Nested
    public val reports: WatchdogReports = project.objects.newInstance(WatchdogReports::class.java)

    /** Configures [reports]. */
    public fun reports(action: Action<WatchdogReports>) {
        action.execute(reports)
    }

    @TaskAction
    public fun generateReport() {
        val reports = reportDataFiles.files
            .sortedBy { it.absolutePath }
            .map(ApiWatchdogReportData::read)
            .distinctBy { it.projectPath }
        this.reports.htmlOutput.orNull?.asFile?.let { file ->
            ApiWatchdogHtmlReport.write(reports, file, aggregate = true)
            logger.lifecycle("Aggregate API Watchdog report: $file")
        }
        this.reports.sarifOutput.orNull?.asFile?.let { file ->
            ApiWatchdogSarifReport.write(reports, file)
            logger.lifecycle("Aggregate API Watchdog SARIF report: $file")
        }
    }
}

