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

            dependencies.add(
                "dokka",
                dependencies.project(mapOf("path" to ":kotlin-library-api-watchdog-plugin-annotations")),
            )
            dependencies.add(
                "dokka",
                dependencies.project(mapOf("path" to ":kotlin-library-api-watchdog-gradle-plugin")),
            )
        }
    }
}
