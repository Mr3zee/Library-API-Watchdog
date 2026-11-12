package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Project

/** The settings plugin of the artifacts validator registers its tasks in every project and can't exclude one. */
fun Project.excludeFromArtifactLists() {
    tasks.named { it == "dumpArtifacts" || it == "checkArtifacts" }.configureEach {
        enabled = false
    }
}
