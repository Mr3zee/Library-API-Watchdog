package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class CompilerPluginConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val generateDiagnosticMessages = registerDiagnosticsGenerator<GenerateDiagnosticMessages>(
                name = "generateDiagnosticMessages",
                description = "Generates the diagnostic message templates from the shared diagnostics.json.",
                packageName = "org.jetbrains.kotlinx.library.api.watchdog.fir",
                outputDirectory = "diagnostics/main/kotlin",
            )

            extensions.configure(KotlinMultiplatformExtension::class.java) {
                sourceSets.named("commonMain").configure {
                    kotlin.srcDir(generateDiagnosticMessages)
                    kotlin.srcDir("src/main/kotlin")
                }
                sourceSets.named("commonTestFixtures").configure {
                    kotlin.srcDir("src/testFixtures/kotlin")
                }
                sourceSets.named("commonTest").configure {
                    kotlin.srcDir("src/test/kotlin")
                    resources.srcDir("src/test/data")
                }
            }
        }
    }
}
