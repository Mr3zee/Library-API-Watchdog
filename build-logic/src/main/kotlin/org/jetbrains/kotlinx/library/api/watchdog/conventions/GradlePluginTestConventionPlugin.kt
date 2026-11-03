package org.jetbrains.kotlinx.library.api.watchdog.conventions

import java.io.File
import java.util.Properties
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.compiler.plugin.devkit.GradlePluginConfigExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

class GradlePluginTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val generateFunctionalTestDiagnostics = registerDiagnosticsGenerator<GenerateFunctionalTestDiagnostics>(
                name = "generateFunctionalTestDiagnostics",
                description = "Generates the Diagnostic enum of the functional tests from diagnostics.json.",
                packageName = "org.jetbrains.kotlinx.library.api.watchdog",
                outputDirectory = "functionalTestDiagnostics/functionalTest/kotlin",
            )
            extensions.configure(KotlinProjectExtension::class.java) {
                sourceSets.named("functionalTest").configure {
                    kotlin.srcDir(generateFunctionalTestDiagnostics)
                }
            }

            // The Gradle plugin resolves the fixer from the repository of the test build. No functional test
            // calls the fixer classes, so the fixer isn't on the classpath of the tests.
            tasks.named("installForFunctionalTest").configure {
                dependsOn(":kotlin-library-api-watchdog-exempts-fixer:installForFunctionalTest")
            }

            val pluginDevKit = extensions.getByType(GradlePluginConfigExtension::class.java)
            tasks.register("printCiFunctionalTestMatrix") {
                group = "help"
                description = "Prints the versioned functional-test task names as a JSON array for CI."

                val taskNames = pluginDevKit.testAgainst.map { it.testTask.name }.sorted()
                val json = taskNames.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]")

                doLast {
                    logger.quiet(json)
                }
            }

            val agp8Version = catalogVersion("agp8")
            val agp9Version = catalogVersion("agp9")
            val androidSdk = androidSdkDirOrNull(target)
            tasks.withType(Test::class.java).configureEach {
                systemProperty("watchdog.test.agp8Version", agp8Version)
                systemProperty("watchdog.test.agp9Version", agp9Version)
                androidSdk?.let { sdk ->
                    systemProperty("watchdog.test.androidHome", sdk.absolutePath)
                }
            }
        }
    }

    private fun androidSdkDirOrNull(project: Project): File? {
        val localProperties = project.repositoryRoot.file("local.properties").asFile
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
