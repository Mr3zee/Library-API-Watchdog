pluginManagement {
    val devKitVersion = providers.systemProperty("devkitVersion")

    includeBuild("gradle-conventions")

    plugins {
        kotlin("compiler.plugin.devkit") version devKitVersion.get()
    }

    repositories {
        maven("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap") {
            // Space can challenge public reads on GitHub-hosted runners. Keep the username
            // explicitly empty so Gradle retries that challenge as an anonymous request.
            credentials {
                username = ""
                password = ""
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins { kotlin("compiler.plugin.devkit") }

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
