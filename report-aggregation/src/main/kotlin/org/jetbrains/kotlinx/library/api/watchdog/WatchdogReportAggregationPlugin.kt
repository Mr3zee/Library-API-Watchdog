package org.jetbrains.kotlinx.library.api.watchdog

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage

/** Adds Dokka-style, dependency-driven aggregation for module exemption reports. */
public class WatchdogReportAggregationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val reportDependencies = target.configurations.register(
            EXEMPTS_REPORT_DEPENDENCIES_CONFIGURATION,
        ) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = false
            configuration.description =
                "Projects whose backwards-compatibility exemptions reports are aggregated"
        }
        val reportFiles = target.configurations.register(
            "backwardsCompatibilityExemptsReportFiles",
        ) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.description =
                "Resolved backwards-compatibility exemptions module reports"
            configuration.extendsFrom(reportDependencies.get())
            configuration.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                target.objects.named(Usage::class.java, EXEMPTS_REPORT_USAGE),
            )
            configuration.attributes.attribute(
                Category.CATEGORY_ATTRIBUTE,
                target.objects.named(Category::class.java, Category.DOCUMENTATION),
            )
        }

        target.tasks.register(
            AGGREGATE_REPORT_TASK_NAME,
            AggregateBackwardsCompatibilityExemptsReportTask::class.java,
        ) { task ->
            task.group = "api watchdog"
            task.description =
                "Aggregates backwards-compatibility exemption reports from declared projects"
            task.reportDataFiles.from(reportFiles)
            task.reportFile.convention(
                target.layout.buildDirectory.file(
                    "reports/api-watchdog/backwards-compatibility-exempts-aggregate.html"
                )
            )
        }
    }

    private companion object {
        private const val AGGREGATE_REPORT_TASK_NAME =
            "aggregateBackwardsCompatibilityExemptsReport"
    }
}

