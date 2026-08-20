import org.jetbrains.kotlin.compiler.plugin.devkit.BetaAndRc
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
        preDev(2, 3, 20, "pre2320Dev")
        postDev(2, 3, 20, "post2320Dev")

        groupVersions("diagnosticReporter2320Dev", {
            val version = it.toKotlinVersion()
            version >= KotlinVersion(2, 3, 20) && version < KotlinVersion(2, 4)
        })

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
