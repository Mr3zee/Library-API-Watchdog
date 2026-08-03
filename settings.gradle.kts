pluginManagement {
    includeBuild("gradle-conventions")

    repositories {
        maven("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap") {
            // Space can challenge public reads on GitHub-hosted runners. Keep the username
            // explicitly empty so Gradle retries that challenge as an anonymous request.
            credentials {
                username = ""
                password = ""
            }
            content {
                includeGroupByRegex("org\\.jetbrains\\.kotlin\\.compiler\\.plugin\\.devkit(\\..*)?")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://redirector.kotlinlang.org/maven/bootstrap")
        maven("https://redirector.kotlinlang.org/maven/dev/")
        // Publications used by IJ
        // https://kotlinlang.slack.com/archives/C7L3JB43G/p1757001642402909
        maven("https://redirector.kotlinlang.org/maven/intellij-dependencies/")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("org.jetbrains.kotlin.compiler.plugin.devkit.")) {
                useVersion(providers.gradleProperty("compilerPluginDevKitVersion").get())
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap") {
            // See the matching plugin repository above.
            credentials {
                username = ""
                password = ""
            }
            content {
                includeGroupByRegex("org\\.jetbrains\\.kotlin\\.compiler\\.plugin\\.devkit(\\..*)?")
            }
        }
        mavenCentral()
        maven("https://redirector.kotlinlang.org/maven/bootstrap")
        maven("https://redirector.kotlinlang.org/maven/dev/")
        // Publications used by IJ
        // https://kotlinlang.slack.com/archives/C7L3JB43G/p1757001642402909
        maven("https://redirector.kotlinlang.org/maven/intellij-dependencies/")
    }
}

rootProject.name = "libs-api-watchdog"

include("compiler-plugin")

include("exempts-fixer")

include("gradle-plugin")

include("plugin-annotations")
