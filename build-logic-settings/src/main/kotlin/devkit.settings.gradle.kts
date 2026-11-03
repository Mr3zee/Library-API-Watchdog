import java.util.Properties

plugins {
    kotlin("compiler.plugin.devkit")
}

// Every build of the repository publishes with the group and the version of the root gradle.properties,
// because an included build doesn't read that file. The root is the directory of the version catalog.
// The values are local, because the configuration cache can't store a reference to this script.
run {
    val repositoryRoot = generateSequence(settingsDir) { it.parentFile }
        .first { it.resolve("gradle/libs.versions.toml").isFile }
    val rootProperties = Properties().apply {
        val file = layout.settingsDirectory.file(repositoryRoot.resolve("gradle.properties").absolutePath)
        load(providers.fileContents(file).asText.get().reader())
    }
    // A -P value doesn't reach an included build, so the script ignores -Pgroup and -Pversion in every build.
    val libraryGroup: String = rootProperties.getProperty("group")
    val libraryVersion: String = providers.environmentVariable("LIBRARY_VERSION").orNull
        ?.also {
            require(it.matches(Regex("\\d+\\.\\d+\\.\\d+(?:[.-][0-9A-Za-z]+)*"))) {
                "Invalid LIBRARY_VERSION. Use MAJOR.MINOR.PATCH with an optional alphanumeric suffix."
            }
        }
        ?: rootProperties.getProperty("version")

    gradle.lifecycle.beforeProject {
        group = libraryGroup
        version = libraryVersion
    }
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
