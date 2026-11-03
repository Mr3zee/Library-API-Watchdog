pluginManagement {
    includeBuild("../build-logic-settings")
    includeBuild("../build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap") {
            // Space can challenge a public read on a GitHub runner. With an empty username, Gradle
            // retries the challenge as an anonymous request.
            credentials {
                username = ""
                password = ""
            }
        }
    }
}

plugins { id("devkit-included") }

rootProject.name = "kotlin-library-api-watchdog-plugin-annotations"
