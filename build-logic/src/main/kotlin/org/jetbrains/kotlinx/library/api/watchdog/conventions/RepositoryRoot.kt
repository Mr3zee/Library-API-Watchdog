package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Project
import org.gradle.api.file.Directory

/** The directory of the root build. The root project of an included build is in a subdirectory of it. */
@Suppress("UnstableApiUsage")
val Project.repositoryRoot: Directory
    get() = generateSequence(gradle) { it.parent }.last().rootProject.isolated.projectDirectory
