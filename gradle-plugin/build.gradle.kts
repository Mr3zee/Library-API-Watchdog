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
// For :plugin-annotations only the JVM and root (metadata) publications are needed, which avoids
// building every native target just to run JVM functional tests.
tasks.named("installForFunctionalTest") {
    dependsOn(
        ":compiler-plugin:installForFunctionalTest",
        ":exempts-fixer:installForFunctionalTest",
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
