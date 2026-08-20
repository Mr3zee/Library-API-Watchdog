import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

plugins {
    pluginDevKit("compiler-plugin")
    id("library-api-watchdog.benchmark-conventions")
    id("library-api-watchdog.compiler-plugin-conventions")
    id("library-api-watchdog.space-publishing-conventions")
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
pluginDevKit {
    pluginPackage.set("org.jetbrains.kotlin.library.api.watchdog")
    componentRegistrar.set(
        "org.jetbrains.kotlinx.libs.api.watchdog.WatchdogComponentRegistrar",
    )
    commandLineProcessor.set(
        "org.jetbrains.kotlinx.libs.api.watchdog.WatchdogCommandLineProcessor",
    )
    generateTestsClass.set("org.jetbrains.kotlinx.libs.api.watchdog.GenerateTestsKt")
    testDataLibraries {
        common(project(":kotlin-library-api-watchdog-plugin-annotations"))
    }

    kotlin.applyPluginDevKitHierarchyTemplate {
        preDev(2, 4, "pre24Dev")
        postDev(2, 4, "post24Dev")

        preDev(2, 5, "pre25Dev")
        postDev(2, 5, "post25Dev")

        val deprecatedKlibVersion = KotlinToolingVersion("2.4.20-dev-6724")
        val pathKlibVersion = KotlinToolingVersion("2.4.20-Beta2")
        groupVersions("legacyKlib", {
            it < pathKlibVersion && it != deprecatedKlibVersion
        })
        groupVersions("deprecatedKlib", { it == deprecatedKlibVersion })
        groupVersions("pathKlib", { it >= pathKlibVersion })
    }
}

tasks.register("ciTests") {
    group = "verification"
    description =
        "Runs compiler diagnostics tests where baselines exist and compiles earlier supported versions."
    dependsOn(
        provider {
            pluginDevKit.testAgainst.map { target ->
                if (target.version.toKotlinVersion() >= KotlinVersion(2, 4)) {
                    target.testTask
                } else {
                    target.mainCompilation.compileTaskProvider
                }
            }
        },
    )
}
