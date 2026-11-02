@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlinx.library.api.watchdog.conventions.GenerateCheckDescriptions
import org.jetbrains.kotlinx.library.api.watchdog.conventions.registerDiagnosticsGenerator

plugins {
    kotlin("jvm")
    id("library-api-watchdog.dokka-conventions")
    id("library-api-watchdog.jvm-target-conventions")
    `java-gradle-plugin`
    `maven-publish`
    id("library-api-watchdog.space-publishing-conventions")
    id("library-api-watchdog.plugin-portal-publishing-conventions")
    id("library-api-watchdog.maven-central-publishing-conventions")
}

// The Watchdog Gradle plugin depends on this module and needs Java 17.
jvmTargetConventions {
    release.set(JvmTarget.JVM_17)
}

val generateCheckDescriptions = registerDiagnosticsGenerator<GenerateCheckDescriptions>(
    name = "generateCheckDescriptions",
    description = "Generates the report check descriptions and message templates from diagnostics.json.",
    packageName = "org.jetbrains.kotlinx.library.api.watchdog",
    outputDirectory = "checkDescriptions/main/kotlin",
) {
    toolVersion.set(project.version.toString())
}

kotlin {
    explicitApi()
    compilerOptions.optIn.add("org.jetbrains.kotlinx.library.api.watchdog.InternalWatchdogApi")
    abiValidation {
        filters {
            exclude {
                annotatedWith.add("org.jetbrains.kotlinx.library.api.watchdog.InternalWatchdogApi")
            }
        }
    }

    sourceSets.main {
        kotlin.srcDir(generateCheckDescriptions)
    }
}

dependencies {
    implementation(libs.commonmark)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.jsoup)
}

tasks.processResources {
    // The HTML report embeds the repository logo as its favicon.
    from(rootProject.file("assets/logo.svg")) {
        into("org/jetbrains/kotlinx/library/api/watchdog")
        rename { "favicon.svg" }
    }
}

tasks.test {
    useJUnitPlatform()
    val htmlReportFixtures = layout.buildDirectory.dir("html-report-fixtures")
    systemProperty("htmlReportFixtures", htmlReportFixtures.get().asFile.absolutePath)
    outputs.dir(htmlReportFixtures)
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
