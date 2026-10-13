package org.jetbrains.kotlinx.library.api.watchdog

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage

/**
 * Combines the API Watchdog reports of the projects in the `apiWatchdogReports` configuration
 * into one HTML report. Apply it to the root project of a multi-project build.
 */
public class WatchdogReportAggregationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val reportDependencies = target.configurations.register(
            API_WATCHDOG_REPORTS_CONFIGURATION,
        ) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = false
            configuration.description =
                "The projects whose API Watchdog reports are aggregated"
        }
        val reportFiles = target.configurations.register(
            "apiWatchdogReportFiles",
        ) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.description =
                "The resolved API Watchdog reports of the declared projects"
            configuration.extendsFrom(reportDependencies.get())
            configuration.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                target.objects.named(Usage::class.java, API_WATCHDOG_REPORT_USAGE),
            )
            configuration.attributes.attribute(
                Category.CATEGORY_ATTRIBUTE,
                target.objects.named(Category::class.java, Category.DOCUMENTATION),
            )
        }

        target.tasks.register(
            AGGREGATE_REPORT_TASK_NAME,
            AggregateApiWatchdogReportsTask::class.java,
        ) { task ->
            task.group = "api watchdog"
            task.description =
                "Combines the API Watchdog reports of the declared projects into one HTML report"
            task.reportDataFiles.from(reportFiles)
            task.reportFile.convention(
                target.layout.buildDirectory.file(
                    "reports/api-watchdog/aggregate-report.html"
                )
            )
        }
    }

    private companion object {
        private const val AGGREGATE_REPORT_TASK_NAME =
            "aggregateApiWatchdogReports"
    }
}

