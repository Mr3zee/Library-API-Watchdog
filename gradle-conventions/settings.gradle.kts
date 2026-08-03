pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://redirector.kotlinlang.org/maven/bootstrap")
        maven("https://redirector.kotlinlang.org/maven/dev/")
        maven("https://redirector.kotlinlang.org/maven/intellij-dependencies/")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://redirector.kotlinlang.org/maven/bootstrap")
        maven("https://redirector.kotlinlang.org/maven/dev/")
        maven("https://redirector.kotlinlang.org/maven/intellij-dependencies/")
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "libs-watchdog-gradle-conventions"
