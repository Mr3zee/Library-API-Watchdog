package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Project
import org.gradle.api.file.Directory

/**
 * The directory of the version catalog. The root project of an included build is in a subdirectory of it.
 */
val Project.repositoryRoot: Directory
    get() {
        @Suppress("UnstableApiUsage")
        val buildRoot = isolated.rootProject.projectDirectory.asFile
        val root = generateSequence(buildRoot) { it.parentFile }
            .first { it.resolve("gradle/libs.versions.toml").isFile }
        return layout.projectDirectory.dir(root.absolutePath)
    }
