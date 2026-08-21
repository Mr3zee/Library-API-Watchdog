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

    kotlin.applyPluginDevKitHierarchyTemplate {
        preDev(2, 4, "pre24Dev")
        postDev(2, 4, "post24Dev")

        preDev(2, 5, "pre25Dev")
        postDev(2, 5, "post25Dev")

        val pathKlibVersion = KotlinToolingVersion("2.4.20-Beta2")
        pre(pathKlibVersion, "legacyKlib")
        post(pathKlibVersion, "pathKlib")
    }
}

pluginDevKit.testAgainst.configureEach {
    if (version.toKotlinVersion() < KotlinVersion(2, 4)) testTask { enabled = false }
}
