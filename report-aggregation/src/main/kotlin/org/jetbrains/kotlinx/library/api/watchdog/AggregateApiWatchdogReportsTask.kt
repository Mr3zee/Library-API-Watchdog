package org.jetbrains.kotlinx.library.api.watchdog

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
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

    /** The aggregate HTML report. Default: `build/reports/api-watchdog/aggregate-report.html`. */
    @get:OutputFile
    public abstract val reportFile: RegularFileProperty

    @TaskAction
    public fun generateReport() {
        val reports = reportDataFiles.files
            .sortedBy { it.absolutePath }
            .map(ApiWatchdogReportData::read)
            .distinctBy { it.projectPath }
        ApiWatchdogHtmlReport.write(reports, reportFile.get().asFile, aggregate = true)
        logger.lifecycle("Aggregate API Watchdog report: ${reportFile.get().asFile}")
    }
}

