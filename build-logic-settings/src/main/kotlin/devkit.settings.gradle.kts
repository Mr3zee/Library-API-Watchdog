import org.jetbrains.kotlinx.library.api.watchdog.conventions.useLibraryGroupAndVersion

plugins {
    kotlin("compiler.plugin.devkit")
}

useLibraryGroupAndVersion()

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
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
    }
}
