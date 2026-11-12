import java.util.Properties

plugins {
    kotlin("compiler.plugin.devkit")
    id("org.jetbrains.kotlinx.artifacts-validator-plugin")
}

// Sets the group and the version of every project in this build from `library.properties` in the repository root.
// `LIBRARY_VERSION` overrides the version. `-Pgroup` and `-Pversion` have no effect.
// The action reads the file for each project, because the root project of the root build doesn't exist during
// settings evaluation.
gradle.lifecycle.beforeProject {
    @Suppress("UnstableApiUsage")
    val repositoryRoot = generateSequence(gradle) { it.parent }.last().rootProject.isolated.projectDirectory
    val libraryProperties = Properties().apply {
        load(providers.fileContents(repositoryRoot.file("library.properties")).asText.get().reader())
    }
    group = libraryProperties.getProperty("group")
    version = providers.environmentVariable("LIBRARY_VERSION").orNull
        ?.also {
            require(it.matches(Regex("\\d+\\.\\d+\\.\\d+(?:[.-][0-9A-Za-z]+)*"))) {
                "Invalid LIBRARY_VERSION. Use MAJOR.MINOR.PATCH with an optional alphanumeric suffix."
            }
        }
        ?: libraryProperties.getProperty("version")
}

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
