package org.jetbrains.kotlinx.libs.api.watchdog.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

class DokkaConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val conventionModuleName = target.name
        val rootDirectory = target.isolated.rootProject.projectDirectory
        val sourceUrl = target.uri("https://github.com/Mr3zee/Library-API-Watchdog/blob/main")
        with(target) {
            pluginManager.apply("org.jetbrains.dokka")

            extensions.configure(DokkaExtension::class.java) {
                moduleName.set(conventionModuleName)
                dokkaSourceSets.configureEach {
                    documentedVisibilities.set(
                        setOf(VisibilityModifier.Public, VisibilityModifier.Protected),
                    )
                    sourceLink {
                        localDirectory.set(rootDirectory)
                        remoteUrl.set(sourceUrl)
                        remoteLineSuffix.set("#L")
                    }
                }
                dokkaPublications.configureEach {
                    suppressObviousFunctions.set(true)
                    failOnWarning.set(true)
                }
            }
        }
    }
}
