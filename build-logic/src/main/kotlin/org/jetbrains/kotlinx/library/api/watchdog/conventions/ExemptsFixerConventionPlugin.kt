package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class ExemptsFixerConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val generateCheckNames = registerDiagnosticsGenerator<GenerateCheckNames>(
                name = "generateCheckNames",
                description = "Generates the WatchdogChecks enum from diagnostics.json.",
                packageName = "org.jetbrains.kotlinx.library.api.watchdog.fixer",
                outputDirectory = "checkNames/main/kotlin",
            ) {
                internalVisibility.set(true)
            }

            extensions.configure(KotlinMultiplatformExtension::class.java) {
                sourceSets.named("commonMain").configure {
                    kotlin.srcDir(generateCheckNames)
                }
            }
        }
    }
}
