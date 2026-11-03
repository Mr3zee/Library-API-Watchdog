package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitPropertiesDsl

/** The Kotlin versions that every dev kit project builds or tests against. */
fun DevKitPropertiesDsl<*>.config() {
    cliVersions(project.catalogVersion("kotlinCliMin"))
}

fun Project.catalogVersion(name: String): String =
    extensions.getByType(VersionCatalogsExtension::class.java).named("libs").findVersion(name).get().requiredVersion
