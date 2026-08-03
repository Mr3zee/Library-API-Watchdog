@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    pluginDevKit("gradle-plugin")
    id("library-api-watchdog.dokka-conventions")
    id("library-api-watchdog.gradle-plugin-conventions")
    id("library-api-watchdog.space-publishing-conventions")
}

kotlin {
    explicitApi()

    abiValidation()
}

pluginDevKit {
    pluginPackage.set("org.jetbrains.kotlin.library.api.watchdog")
}

gradlePlugin {
    plugins {
        create("LibraryApiWatchdog") {
            id = "org.jetbrains.kotlin.library.api-watchdog"
            displayName = "Library API Watchdog"
            description =
                "Warns Kotlin library authors about public API declarations that are hard to evolve"
            implementationClass = "org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSupportPlugin"
        }
    }
}
