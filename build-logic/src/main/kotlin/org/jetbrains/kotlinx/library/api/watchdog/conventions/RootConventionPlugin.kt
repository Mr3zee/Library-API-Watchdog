package org.jetbrains.kotlinx.library.api.watchdog.conventions

import java.time.Year
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.plugins.DokkaHtmlPluginParameters

class RootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val conventionModuleVersion = target.version.toString()
        val logo = target.isolated.rootProject.projectDirectory.file("docs/static/img/logo-icon.svg")
        val footer = "© ${Year.now()} JetBrains s.r.o and contributors."
        with(target) {
            pluginManager.apply("library-api-watchdog.dokka-conventions")

            extensions.configure(DokkaExtension::class.java) {
                moduleVersion.set(conventionModuleVersion)
                pluginsConfiguration.withType(DokkaHtmlPluginParameters::class.java).configureEach {
                    customAssets.from(logo)
                    footerMessage.set(footer)
                    homepageLink.set("https://mr3zee.github.io/Library-API-Watchdog/")
                }
            }

            // The modules are in included builds, so the dependencies use their coordinates.
            for (module in DOCUMENTED_MODULES) {
                dependencies.add("dokka", "${target.group}:$module:${target.version}")
            }

            // A task name on the command line selects the tasks of this build only. These tasks of the root project
            // add the publication tasks of the included builds, so `./gradlew publishPlugins` publishes
            // every module.
            for ((taskName, includedTasks) in INCLUDED_PUBLICATION_TASKS) {
                tasks.register(taskName) {
                    group = "publishing"
                    description = "Runs $taskName in the included builds."
                    for ((build, projectPaths) in includedTasks) {
                        val includedBuild = gradle.includedBuild(build)
                        for (projectPath in projectPaths) {
                            dependsOn(includedBuild.task("$projectPath:$taskName"))
                        }
                    }
                }
            }

            // The settings plugin of the artifacts validator registers these tasks in every project of every build.
            for (taskName in ARTIFACT_LIST_TASKS) {
                tasks.named(taskName) {
                    for ((build, projectPaths) in PUBLISHED_PROJECTS) {
                        val includedBuild = gradle.includedBuild(build)
                        for (projectPath in projectPaths) {
                            dependsOn(includedBuild.task("$projectPath:$taskName"))
                        }
                    }
                }
            }
        }
    }
}

private const val GRADLE_PLUGIN_BUILD = "gradle-plugin"
private const val ANNOTATIONS_BUILD = "plugin-annotations"
private const val REPORT_AGGREGATION_PROJECT = ":kotlin-library-api-watchdog-report-aggregation"

/** For each included build, the paths of the projects that have a Maven publication. */
private val PUBLISHED_PROJECTS: Map<String, List<String>> = mapOf(
    GRADLE_PLUGIN_BUILD to listOf("", REPORT_AGGREGATION_PROJECT),
    ANNOTATIONS_BUILD to listOf(""),
)

private val ARTIFACT_LIST_TASKS = listOf("dumpArtifacts", "checkArtifacts")

/** The task name and, for each included build, the paths of the projects that have the task. */
private val INCLUDED_PUBLICATION_TASKS: Map<String, Map<String, List<String>>> = mapOf(
    "publishAllPublicationsToSpaceRepository" to PUBLISHED_PROJECTS,
    "publishAllPublicationsToBuildRepository" to PUBLISHED_PROJECTS,
    "publishPlugins" to mapOf(
        GRADLE_PLUGIN_BUILD to listOf("", REPORT_AGGREGATION_PROJECT),
    ),
)

private val DOCUMENTED_MODULES = listOf(
    "kotlin-library-api-watchdog-plugin-annotations",
    "kotlin-library-api-watchdog-gradle-plugin",
    "kotlin-library-api-watchdog-report-aggregation",
)
