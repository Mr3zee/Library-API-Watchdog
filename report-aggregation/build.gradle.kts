@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    kotlin("jvm")
    id("library-api-watchdog.dokka-conventions")
    `java-gradle-plugin`
    `maven-publish`
    id("library-api-watchdog.space-publishing-conventions")
}

kotlin {
    explicitApi()
    abiValidation()
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
                "Aggregates backwards-compatibility exemption reports from library modules"
            implementationClass =
                "org.jetbrains.kotlinx.library.api.watchdog.WatchdogReportAggregationPlugin"
        }
    }
}
