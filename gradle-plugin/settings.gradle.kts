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

dependencyResolutionManagement {
    repositories {
        // The report aggregation tests run in Chrome for Testing. The group is `CHROME_FOR_TESTING_GROUP` of build-logic.
        exclusiveContent {
            forRepository {
                ivy("https://storage.googleapis.com/chrome-for-testing-public") {
                    name = "Chrome for Testing"
                    patternLayout { artifact("[revision]/[classifier]/[artifact]-[classifier].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeGroup("chrome-for-testing") }
        }
    }
}

rootProject.name = "kotlin-library-api-watchdog-gradle-plugin"

include("kotlin-library-api-watchdog-report-aggregation")
project(":kotlin-library-api-watchdog-report-aggregation").projectDir = file("report-aggregation")
