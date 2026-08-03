@file:OptIn(ExperimentalAbiValidation::class)

import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    kotlin("compiler.plugin.devkit.gradle-plugin")
    alias(libs.plugins.dokka)
}

kotlin {
    explicitApi()

    abiValidation()
}

pluginDevKit {
    companionLibrary(project(":plugin-annotations"))
    compilerPlugin = project(":compiler-plugin")
}

// Functional test projects resolve these sibling artifacts from the shared functionalTestRepo.
// Publish the JVM, JS, and root (metadata) variants used by the focused multiplatform functional
// tests without adding an explicit dependency on every native target publication.
tasks.named("installForFunctionalTest") {
    dependsOn(
        ":compiler-plugin:installForFunctionalTest",
        ":exempts-fixer:installForFunctionalTest",
        ":plugin-annotations:publishJsPublicationToFunctionalTestRepository",
        ":plugin-annotations:publishJvmPublicationToFunctionalTestRepository",
        ":plugin-annotations:publishKotlinMultiplatformPublicationToFunctionalTestRepository",
    )
}

// The Android functional tests generate real AGP projects, which needs an Android SDK. The SDK is
// resolved the way AGP itself would look for it: local.properties, then the environment, then the
// per-OS default install location. Tests skip themselves when no SDK is found.
fun androidSdkDirOrNull(): File? {
    val localProperties = rootProject.isolated.projectDirectory.file("local.properties").asFile
    if (localProperties.exists()) {
        val properties = Properties()
        localProperties.inputStream().use(properties::load)
        properties.getProperty("sdk.dir")?.let(::File)?.takeIf(File::isDirectory)?.let { return it }
    }
    val home = File(System.getProperty("user.home"))
    return sequenceOf(
        System.getenv("ANDROID_HOME")?.let(::File),
        System.getenv("ANDROID_SDK_ROOT")?.let(::File),
        home.resolve("Library/Android/sdk"),
        home.resolve("Android/Sdk"),
        System.getenv("LOCALAPPDATA")?.let { File(it).resolve("Android/Sdk") },
    ).filterNotNull().firstOrNull(File::isDirectory)
}

tasks.withType<Test>().configureEach {
    systemProperty("watchdog.test.agp8Version", libs.versions.agp8.get())
    systemProperty("watchdog.test.agp9Version", libs.versions.agp9.get())
    androidSdkDirOrNull()?.let { systemProperty("watchdog.test.androidHome", it.absolutePath) }
}

gradlePlugin {
    plugins {
        create("LibsApiWatchdog") {
            id = group.toString()
            displayName = "LibsApiWatchdog"
            description =
                "Warns Kotlin library authors about public API declarations that are hard to evolve"
            implementationClass = "org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSupportPlugin"
        }
    }
}
