package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension

class PluginPortalPublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.gradle.plugin-publish")
            // The release of a plugin goes to the Gradle Plugin Portal, not to Maven Central.
            excludeFromArtifactLists()
            extensions.configure(GradlePluginDevelopmentExtension::class.java) {
                website.set("https://kotlin.github.io/Library-API-Watchdog/")
                vcsUrl.set("https://github.com/Kotlin/Library-API-Watchdog")
                plugins.configureEach {
                    tags.set(listOf("kotlin", "api", "static-analysis", "library"))
                }
            }
            val validateEnvironment = tasks.register(
                "validatePluginPortalEnvironment",
                ValidatePublicationEnvironment::class.java,
            ) {
                group = "publishing"
                description = "Checks the credentials for Gradle Plugin Portal publication."
                requiredVariables.set(listOf("GRADLE_PUBLISH_KEY", "GRADLE_PUBLISH_SECRET"))
            }
            tasks.named("publishPlugins").configure {
                dependsOn(validateEnvironment)
            }
        }
    }
}
