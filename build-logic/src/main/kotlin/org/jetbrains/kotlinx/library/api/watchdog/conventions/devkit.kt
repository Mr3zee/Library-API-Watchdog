package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.compiler.plugin.devkit.BetaAndRc
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitPropertiesDsl
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

/** The Kotlin versions that every dev kit project builds or tests against. */
fun DevKitPropertiesDsl<*>.config() {
    cliVersions(project.catalogVersion("kotlinCliMin"), betaAndRc = BetaAndRc.LATEST)
    // The dev kit reads the default from the annotations klibs when it stores the configuration cache. The
    // plugin-annotations build writes them later, so the default depends on the state of its build directory.
    // The klibs of kotlinMin have the 2.3.0 ABI version, which kotlinCliMin reads.
    companionLibrariesKotlinVersion.set(KotlinToolingVersion(project.catalogVersion("kotlinCliMin")))
}

fun Project.catalogVersion(name: String): String =
    extensions.getByType(VersionCatalogsExtension::class.java).named("libs").findVersion(name).get().requiredVersion
