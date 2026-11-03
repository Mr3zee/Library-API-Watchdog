package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.initialization.Settings
import java.util.Properties

/**
 * Sets the group and the version of every project in this build from the root `gradle.properties`, because an
 * included build doesn't read that file. `LIBRARY_VERSION` overrides the version. `-Pgroup` and `-Pversion` have no
 * effect, because a `-P` value doesn't reach an included build.
 *
 * Don't move the body into the settings script. Gradle copies the `beforeProject` action for each project with the
 * configuration cache serializer. In a script, a lambda that reads a script `val` holds the script object, and the
 * serializer can't copy it.
 */
internal fun Settings.useRootGroupAndVersion() {
    val repositoryRoot = generateSequence(settingsDir) { it.parentFile }
        .first { it.resolve("gradle/libs.versions.toml").isFile }
    val rootProperties = Properties().apply {
        val file = layout.settingsDirectory.file(repositoryRoot.resolve("gradle.properties").absolutePath)
        load(providers.fileContents(file).asText.get().reader())
    }
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
