import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

plugins {
    id("org.jetbrains.kotlin.compiler.plugin.devkit.compiler-plugin")
    id("library-api-watchdog.devkit-versions-conventions")
    id("library-api-watchdog.benchmark-conventions")
    id("library-api-watchdog.compiler-plugin-conventions")
    id("library-api-watchdog.space-publishing-conventions")
    id("library-api-watchdog.maven-central-publishing-conventions")
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

// The diagnostic test runner of the dev kit doesn't link against the test framework of the
// 2.5.20 dev builds: https://github.com/Kotlin/compiler-plugin-dev-kit/issues/63
pluginDevKit.testAgainst
    .matching { it.version.toString().startsWith("2.5.20-dev") }
    .configureEach { testTask.configure { enabled = false } }
