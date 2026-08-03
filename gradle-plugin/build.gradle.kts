@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    kotlin("compiler.plugin.devkit.gradle-plugin")
    id("libs-watchdog.dokka-conventions")
    id("libs-watchdog.gradle-plugin-conventions")
}

kotlin {
    explicitApi()

    abiValidation()
}

pluginDevKit {
    companionLibrary(project(":plugin-annotations"))
    compilerPlugin = project(":compiler-plugin")
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
