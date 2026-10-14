package org.jetbrains.kotlinx.library.api.watchdog.conventions

import java.io.File
import java.util.Properties
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
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
            val generateFunctionalTestDiagnostics = registerDiagnosticsGenerator<GenerateFunctionalTestDiagnostics>(
                name = "generateFunctionalTestDiagnostics",
                description = "Generates the Diagnostic enum of the functional tests from diagnostics.json.",
                packageName = "org.jetbrains.kotlinx.library.api.watchdog",
                outputDirectory = "functionalTestDiagnostics/functionalTest/kotlin",
            )
            extensions.configure(KotlinProjectExtension::class.java) {
                sourceSets.named("main").configure {
                    kotlin.srcDir(generateCheckNames)
                }
                sourceSets.named("functionalTest").configure {
                    kotlin.srcDir(generateFunctionalTestDiagnostics)
                }
            }

            tasks.named("installForFunctionalTest").configure {
                dependsOn(
                    ":kotlin-library-api-watchdog-compiler-plugin:installForFunctionalTest",
                    ":kotlin-library-api-watchdog-exempts-fixer:installForFunctionalTest",
                    ":kotlin-library-api-watchdog-report-aggregation:publishAllPublicationsToFunctionalTestRepository",
                    ":kotlin-library-api-watchdog-plugin-annotations:publishJsPublicationToFunctionalTestRepository",
                    ":kotlin-library-api-watchdog-plugin-annotations:publishJvmPublicationToFunctionalTestRepository",
                    ":kotlin-library-api-watchdog-plugin-annotations:publishKotlinMultiplatformPublicationToFunctionalTestRepository",
                )
            }

            val versions = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
            tasks.withType(Test::class.java).configureEach {
                systemProperty("watchdog.test.agp8Version", versions.findVersion("agp8").get().requiredVersion)
                systemProperty("watchdog.test.agp9Version", versions.findVersion("agp9").get().requiredVersion)
                androidSdkDirOrNull(target)?.let { sdk ->
                    systemProperty("watchdog.test.androidHome", sdk.absolutePath)
                }
            }
        }
    }

    private fun androidSdkDirOrNull(project: Project): File? {
        @Suppress("UnstableApiUsage")
        val localProperties = project.isolated.rootProject.projectDirectory.file("local.properties").asFile
        if (localProperties.exists()) {
            val properties = Properties()
            localProperties.inputStream().use(properties::load)
            properties.getProperty("sdk.dir")?.let(::File)?.takeIf(File::isDirectory)?.let { return it }
        }
        val home = File(System.getProperty("user.home"))
        return sequenceOf(
            System.getenv("ANDROID_HOME")?.let(::File),
            System.getenv("ANDROID_SDK_ROOT")?.let(::File),
            home.resolve("Library/Android/sdk"),
            home.resolve("Android/Sdk"),
            System.getenv("LOCALAPPDATA")?.let { File(it).resolve("Android/Sdk") },
        ).filterNotNull().firstOrNull(File::isDirectory)
    }
}
