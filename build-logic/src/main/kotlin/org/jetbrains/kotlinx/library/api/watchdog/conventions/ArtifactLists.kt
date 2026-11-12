package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Project

/**
 * Turns off the tasks of the artifacts validator in this project. The artifact lists hold the Maven Central modules
 * only. The settings plugin of the validator registers the tasks in every project, and it has no option that excludes
 * a project.
 */
fun Project.excludeFromArtifactLists() {
    tasks.named { it == "dumpArtifacts" || it == "checkArtifacts" }.configureEach {
        enabled = false
    }
}
