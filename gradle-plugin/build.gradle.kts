@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    kotlin("compiler.plugin.devkit.gradle-plugin")
    alias(libs.plugins.dokka)
}

kotlin {
    explicitApi()

    abiValidation()
}

pluginDevKit {
    companionLibrary(project(":plugin-annotations"))
    compilerPlugin = project(":compiler-plugin")
}

// Functional test projects resolve these sibling artifacts from the shared functionalTestRepo.
// Publish the JVM, JS, and root (metadata) variants used by the focused multiplatform functional
// tests without adding an explicit dependency on every native target publication.
tasks.named("installForFunctionalTest") {
    dependsOn(
        ":compiler-plugin:installForFunctionalTest",
        ":exempts-fixer:installForFunctionalTest",
        ":plugin-annotations:publishJsPublicationToFunctionalTestRepository",
        ":plugin-annotations:publishJvmPublicationToFunctionalTestRepository",
        ":plugin-annotations:publishKotlinMultiplatformPublicationToFunctionalTestRepository",
    )
}

gradlePlugin {
    plugins {
        create("LibsApiWatchdog") {
            id = group.toString()
            displayName = "LibsApiWatchdog"
            description =
                "Warns Kotlin library authors about public API declarations that are hard to evolve"
            implementationClass = "org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSupportPlugin"
        }
    }
}
