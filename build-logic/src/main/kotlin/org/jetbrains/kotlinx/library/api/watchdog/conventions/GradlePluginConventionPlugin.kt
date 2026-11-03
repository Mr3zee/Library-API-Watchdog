package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

class GradlePluginConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val generateCheckNames = registerDiagnosticsGenerator<GenerateCheckNames>(
                name = "generateCheckNames",
                description = "Generates the WatchdogChecks enum from diagnostics.json.",
                packageName = "org.jetbrains.kotlinx.library.api.watchdog",
                outputDirectory = "checkNames/main/kotlin",
            )
            extensions.configure(KotlinProjectExtension::class.java) {
                sourceSets.named("main").configure {
                    kotlin.srcDir(generateCheckNames)
                }
            }
        }
    }
}
