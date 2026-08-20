@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.compiler.plugin.devkit.BetaAndRc
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
    cliVersions(
        min = "2.3.0",
        betaAndRc = BetaAndRc.LATEST,
    )
    ideaVersions(
        min = "262",
        includeRc = true,
        includeEap = true,
    )
    useLatestDev()
    pluginPackage.set("org.jetbrains.kotlin.library.api.watchdog")
    companionLibrary(project(":kotlin-library-api-watchdog-plugin-annotations"))
    compilerPlugin = project(":kotlin-library-api-watchdog-compiler-plugin")
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
