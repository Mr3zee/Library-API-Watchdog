package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage

/**
 * Combines the reports declared in `apiWatchdogReports` into one HTML report and one SARIF report by default.
 *
 * Usage:
 *
 * - Apply this plugin to the root project of a multi-project or composite build.
 * - Declare included projects by their module coordinates, `group:name`.
 * - Select formats in the `reports` block of `aggregateApiWatchdogReports`.
 *
 * Projects without the Watchdog plugin don't add a report.
 * Source paths are relative to this build's root directory.
 *
 * See [Multi-project builds](https://mr3zee.github.io/Library-API-Watchdog/report#multi-project-builds) and
 * [Included builds](https://mr3zee.github.io/Library-API-Watchdog/report#included-builds) for more details.
 */
public class WatchdogReportAggregationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val reportDependencies = target.configurations.register(
            API_WATCHDOG_REPORTS_CONFIGURATION,
        ) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = false
            configuration.description =
                "Projects declared for API Watchdog report aggregation"
        }
        val reportFiles = target.configurations.register(
            "apiWatchdogReportFiles",
        ) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.description =
                "Resolved API Watchdog reports from the declared projects"
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

        @Suppress("UnstableApiUsage")
        val rootDirectory = target.isolated.rootProject.projectDirectory.asFile
        val reportArtifacts = reportFiles.map { configuration ->
            // Lenient resolution skips projects without the Watchdog.
            configuration.incoming.artifactView { view -> view.lenient(true) }
        }
        target.tasks.register(
            AGGREGATE_REPORT_TASK_NAME,
            AggregateApiWatchdogReportsTask::class.java,
        ) { task ->
            task.group = "api watchdog"
            task.description =
                "Combines the declared projects' API Watchdog reports"
            task.reportDataFiles.from(reportArtifacts.map { it.files })
            // Variant metadata stores absolute build roots so report data remains portable.
            task.projectDirectoryPrefixes.set(reportArtifacts.flatMap { it.artifacts.resolvedArtifacts }.map { artifacts ->
                artifacts.mapNotNull { artifact ->
                    val project = artifact.id.componentIdentifier as? ProjectComponentIdentifier
                        ?: return@mapNotNull null
                    val directory = artifact.variant.attributes.getAttribute(API_WATCHDOG_REPORT_ROOT_DIRECTORY_ATTRIBUTE)
                        ?: return@mapNotNull null
                    project.buildTreePath to File(directory).relativeToOrSelf(rootDirectory).invariantSeparatorsPath
                }.toMap()
            })
            task.reports.html.convention(true)
            task.reports.htmlFile.convention(target.layout.buildDirectory.file(AGGREGATE_HTML_REPORT))
            task.reports.sarif.convention(true)
            task.reports.sarifFile.convention(target.layout.buildDirectory.file(AGGREGATE_SARIF_REPORT))
        }
    }

    private companion object {
        private const val AGGREGATE_REPORT_TASK_NAME =
            "aggregateApiWatchdogReports"
        private const val AGGREGATE_HTML_REPORT = "reports/api-watchdog/aggregate-report.html"
        private const val AGGREGATE_SARIF_REPORT = "reports/api-watchdog/aggregate-report.sarif"
    }
}

