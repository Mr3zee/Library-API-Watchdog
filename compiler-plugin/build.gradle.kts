import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

plugins {
    id("org.jetbrains.kotlin.compiler.plugin.devkit.compiler-plugin")
    id("library-api-watchdog.devkit-versions-conventions")
    id("library-api-watchdog.benchmark-conventions")
    id("library-api-watchdog.compiler-plugin-conventions")
    id("library-api-watchdog.space-publishing-conventions")
}

pluginDevKit {
    ideaVersions(libs.versions.kotlinIdeMin.get(), includeRc = true, includeEap = true)
    useLatestDev()

    pluginPackage.set("org.jetbrains.kotlinx.library.api.watchdog")
    componentRegistrar.set(
        "org.jetbrains.kotlinx.library.api.watchdog.WatchdogComponentRegistrar",
    )
    commandLineProcessor.set(
        "org.jetbrains.kotlinx.library.api.watchdog.WatchdogCommandLineProcessor",
    )
    generateTestsClass.set("org.jetbrains.kotlinx.library.api.watchdog.GenerateTestsKt")

    versionHierarchy {
        splitDev(2, 4)
        splitDev(2, 5)

        val pathKlibVersion = KotlinToolingVersion("2.4.20-Beta2")
        pre(pathKlibVersion, "legacyKlib")
        post(pathKlibVersion, "pathKlib")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
