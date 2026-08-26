package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class CompilerPluginConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val generateDiagnosticMessages = tasks.register(
                "generateDiagnosticMessages",
                GenerateDiagnosticMessages::class.java,
            ) {
                description =
                    "Generates the diagnostic message templates from the shared diagnostics.json."
                diagnostics.set(
                    target.isolated.rootProject.projectDirectory.file("diagnostics.json"),
                )
                packageName.set("org.jetbrains.kotlinx.library.api.watchdog.fir")
                outputDirectory.set(
                    layout.buildDirectory.dir("generated/sources/diagnostics/main/kotlin"),
                )
            }

            extensions.configure(KotlinMultiplatformExtension::class.java) {
                sourceSets.named("commonMain").configure {
                    kotlin.srcDir(generateDiagnosticMessages)
                    kotlin.srcDir("src/main/kotlin")
                }
                sourceSets.named("commonTestFixtures").configure {
                    kotlin.srcDir("src/testFixtures/kotlin")
                }
                sourceSets.named("commonTest").configure {
                    resources.srcDir("src/test/data")
                }
            }
        }
    }
}
