@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.compiler.plugin.devkit.BetaAndRc
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

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
        min = "2.3.20",
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

pluginDevKit.testAgainst.configureEach {
    if (version.toKotlinVersion() < KotlinVersion(2, 4)) {
        testTask.configure {
            // The annotations KLIBs are produced by this build's Kotlin 2.4 compiler and cannot
            // be consumed by 2.3 JS/native compilers. Retain the JVM coverage for 2.3; the full
            // multiplatform suite starts with the build compiler's compatibility floor.
            filter.excludeTestsMatching(
                "org.jetbrains.kotlinx.libs.api.watchdog.UpdateBackwardsCompatibilityExemptsTest." +
                    "jsOnlyProjectIsFixedThroughItsRegularCompilation",
            )
            filter.excludeTestsMatching(
                "org.jetbrains.kotlinx.libs.api.watchdog.UpdateBackwardsCompatibilityExemptsTest." +
                    "nativeOnlyProjectIsFixedThroughItsRegularCompilation",
            )
            filter.excludeTestsMatching(
                "org.jetbrains.kotlinx.libs.api.watchdog.WatchdogProjectTest." +
                    "publicTypeFromImplementationDependencyIsAnErrorInMultiplatformProjects",
            )
            filter.excludeTestsMatching(
                "org.jetbrains.kotlinx.libs.api.watchdog.WatchdogProjectTest." +
                    "publicTypeFromApiDependencyIsAcceptedInMultiplatformProjects",
            )
        }
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
