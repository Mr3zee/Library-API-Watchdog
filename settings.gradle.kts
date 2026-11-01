pluginManagement {
    val devKitVersion = providers.systemProperty("devkitVersion")

    includeBuild("gradle/settings-conventions")

    plugins {
        kotlin("compiler.plugin.devkit") version devKitVersion.get()
    }

    repositories {
        maven("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap") {
            // Space can challenge a public read on a GitHub runner. With an empty username, Gradle
            // retries the challenge as an anonymous request.
            credentials {
                username = ""
                password = ""
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    kotlin("compiler.plugin.devkit")
    id("library-api-watchdog.develocity-conventions")
    // Work around DevKit settings plugin classloader conflicts with the configuration cache.
    // https://github.com/gradle/gradle/issues/32622
    // This version must match libs.versions.kotlin.
    kotlin("multiplatform") version "2.5.0-saturn-677" apply false
}

includeBuild("gradle-conventions")

gradle.lifecycle.beforeProject {
    providers.environmentVariable("LIBRARY_VERSION").orNull?.let { libraryVersion ->
        require(libraryVersion.matches(Regex("\\d+\\.\\d+\\.\\d+(?:[.-][0-9A-Za-z]+)*"))) {
            "Invalid LIBRARY_VERSION. Use MAJOR.MINOR.PATCH with an optional alphanumeric suffix."
        }
        version = libraryVersion
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        maven("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap") {
            // See the matching plugin repository above.
            credentials {
                username = ""
                password = ""
            }
        }
        mavenCentral()
    }
}

rootProject.name = "library-api-watchdog"

include("kotlin-library-api-watchdog-compiler-plugin")
project(":kotlin-library-api-watchdog-compiler-plugin").projectDir = file("compiler-plugin")

include("kotlin-library-api-watchdog-exempts-fixer")
project(":kotlin-library-api-watchdog-exempts-fixer").projectDir = file("exempts-fixer")

include("kotlin-library-api-watchdog-gradle-plugin")
project(":kotlin-library-api-watchdog-gradle-plugin").projectDir = file("gradle-plugin")

include("kotlin-library-api-watchdog-report-aggregation")
project(":kotlin-library-api-watchdog-report-aggregation").projectDir = file("report-aggregation")

include("kotlin-library-api-watchdog-plugin-annotations")
project(":kotlin-library-api-watchdog-plugin-annotations").projectDir = file("plugin-annotations")
