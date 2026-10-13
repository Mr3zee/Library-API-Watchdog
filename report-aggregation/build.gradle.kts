@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlinx.library.api.watchdog.conventions.GenerateCheckDescriptions

plugins {
    kotlin("jvm")
    id("library-api-watchdog.dokka-conventions")
    `java-gradle-plugin`
    `maven-publish`
    id("library-api-watchdog.space-publishing-conventions")
}

val generateCheckDescriptions = tasks.register<GenerateCheckDescriptions>("generateCheckDescriptions") {
    description = "Generates the WatchdogCheckDescriptions table of the SARIF report from the shared diagnostics.json."
    @Suppress("UnstableApiUsage")
    diagnostics.set(isolated.rootProject.projectDirectory.file("diagnostics.json"))
    packageName.set("org.jetbrains.kotlinx.library.api.watchdog")
    toolVersion.set(project.version.toString())
    outputDirectory.set(layout.buildDirectory.dir("generated/sources/checkDescriptions/main/kotlin"))
}

kotlin {
    explicitApi()
    abiValidation()

    sourceSets.main {
        kotlin.srcDir(generateCheckDescriptions)
    }
}

dependencies {
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("LibraryApiWatchdogReportAggregation") {
            id = "org.jetbrains.kotlin.library.api-watchdog-report-aggregation"
            displayName = "Library API Watchdog report aggregation"
            description =
                "Aggregates the API Watchdog reports of library modules"
            implementationClass =
                "org.jetbrains.kotlinx.library.api.watchdog.WatchdogReportAggregationPlugin"
        }
    }
}
