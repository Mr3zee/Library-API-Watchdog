package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class ExemptsFixerConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val generateCheckNames = tasks.register(
                "generateCheckNames",
                GenerateCheckNames::class.java,
            ) {
                description = "Generates the WatchdogChecks diagnostic name constants from the shared diagnostics.json."
                diagnostics.set(
                    target.isolated.rootProject.projectDirectory.file("diagnostics.json"),
                )
                packageName.set("org.jetbrains.kotlinx.library.api.watchdog.fixer")
                internalVisibility.set(true)
                outputDirectory.set(
                    layout.buildDirectory.dir("generated/sources/checkNames/main/kotlin"),
                )
            }

            extensions.configure(KotlinMultiplatformExtension::class.java) {
                sourceSets.named("commonMain").configure {
                    kotlin.srcDir(generateCheckNames)
                }
            }
        }
    }
}
