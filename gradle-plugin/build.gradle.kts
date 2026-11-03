@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.compiler.plugin.devkit.ProjectOrGav
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    id("devkit.gradle")
    id("library-api-watchdog.dokka-conventions")
    id("library-api-watchdog.gradle-plugin-conventions")
    id("library-api-watchdog.jvm-target-conventions")
    id("library-api-watchdog.space-publishing-conventions")
    id("library-api-watchdog.plugin-portal-publishing-conventions")
    id("library-api-watchdog.maven-central-publishing-conventions")
}

// The dev kit's `gradle-plugin-runtime` is a Java 17 library.
jvmTargetConventions {
    release.set(JvmTarget.JVM_17)
}

kotlin {
    explicitApi()
    compilerOptions.optIn.add("org.jetbrains.kotlinx.library.api.watchdog.InternalWatchdogApi")

    abiValidation()
}

dependencies {
    implementation(pluginDevKit("version-resolution"))
    implementation(project(":kotlin-library-api-watchdog-report-aggregation"))
    testImplementation(kotlin("test-junit"))
}

pluginDevKit {
    pluginPackage = "org.jetbrains.kotlinx.library.api.watchdog"
    companionLibrary("kotlin-library-api-watchdog-plugin-annotations")
    compilerPlugin = ProjectOrGav.Sibling("kotlin-library-api-watchdog-compiler-plugin")
}

// The root build runs `check` of this project only.
tasks.check {
    dependsOn(":kotlin-library-api-watchdog-report-aggregation:check")
}

gradlePlugin {
    plugins {
        create("LibraryApiWatchdog") {
            id = "org.jetbrains.kotlin.library.api-watchdog"
            displayName = "Library API Watchdog"
            description =
                "Warns Kotlin library authors about public API declarations that are hard to evolve"
            implementationClass = "org.jetbrains.kotlinx.library.api.watchdog.WatchdogSupportPlugin"
        }
    }
}
