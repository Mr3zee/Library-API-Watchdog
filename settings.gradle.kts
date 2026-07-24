pluginManagement {
    repositories {
        // The compiler-plugin-dev-kit convention plugins are published to mavenLocal.
        mavenLocal()
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
                useVersion("0.0.1-SNAPSHOT")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        // The compiler-plugin-dev-kit runtime artifacts are published to mavenLocal.
        mavenLocal()
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
