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

/** Combines the report variants declared in `backwardsCompatibilityExemptsReports`. */
@CacheableTask
public abstract class AggregateBackwardsCompatibilityExemptsReportTask : DefaultTask() {
    /** Machine-readable module reports resolved through Gradle variant selection. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val reportDataFiles: ConfigurableFileCollection

    /** The aggregate HTML report. */
    @get:OutputFile
    public abstract val reportFile: RegularFileProperty

    @TaskAction
    public fun generateReport() {
        val reports = reportDataFiles.files
            .sortedBy { it.absolutePath }
            .map(BackwardsCompatibilityExemptsReportData::read)
            .distinctBy { it.projectPath }
        BackwardsCompatibilityExemptsHtmlReport.write(reports, reportFile.get().asFile, aggregate = true)
        logger.lifecycle("Aggregate backwards-compatibility exemptions report: ${reportFile.get().asFile}")
    }
}

