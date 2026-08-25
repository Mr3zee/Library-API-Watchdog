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

dependencies {
    implementation(pluginDevKit("version-resolution"))
}

pluginDevKit {
    pluginPackage.set("org.jetbrains.kotlin.library.api.watchdog")
}

tasks.register("printCiFunctionalTestMatrix") {
    group = "help"
    description = "Prints the versioned functional-test task names as a JSON array for CI."

    val taskNames = pluginDevKit.testAgainst.map { it.testTask.name }.sorted()
    val json = taskNames.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]")

    doLast {
        logger.quiet(json)
    }
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
