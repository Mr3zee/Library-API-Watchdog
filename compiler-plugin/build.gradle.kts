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
    // The IntelliJ 2027.1 nightly (271.1622) bundles a compiler from the window between Kotlin
    // commit 5a87800b73, which moved `getChild` and `unsubstitutedScope` to other files, and its
    // revert 642f8df9f6. Public dev builds 2285 to 2331 share that layout. The dev kit's IDE
    // mapping table doesn't know this build yet. Remove the line when it does.
    testAgainst("2.5.20-dev-2285", forIde = true)

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
