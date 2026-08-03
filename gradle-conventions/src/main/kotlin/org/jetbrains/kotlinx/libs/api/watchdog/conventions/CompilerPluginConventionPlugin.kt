package org.jetbrains.kotlinx.libs.api.watchdog.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class CompilerPluginConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            tasks.named("animalsnifferMain").configure { enabled = false }

            val generateDiagnosticMessages = tasks.register(
                "generateDiagnosticMessages",
                GenerateDiagnosticMessages::class.java,
            ) {
                description =
                    "Generates the diagnostic message templates from the shared diagnostics.json."
                diagnostics.set(
                    target.isolated.rootProject.projectDirectory.file("diagnostics.json"),
                )
                packageName.set("org.jetbrains.kotlinx.libs.api.watchdog.fir")
                outputDirectory.set(
                    layout.buildDirectory.dir("generated/sources/diagnostics/main/kotlin"),
                )
            }

            extensions.configure(KotlinJvmProjectExtension::class.java) {
                sourceSets.named("main").configure {
                    kotlin.srcDir(generateDiagnosticMessages)
                }
            }
        }
    }
}
