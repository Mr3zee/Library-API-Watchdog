package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.compiler.plugin.devkit.BetaAndRc
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitVersionsDsl

class DevKitVersionsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val versions = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
            val minCliVersion = versions.findVersion("kotlinCliMin").get().requiredVersion

            extensions.configure(DevKitVersionsDsl::class.java) {
                cliVersions(minCliVersion, betaAndRc = BetaAndRc.LATEST)
            }
        }
    }
}
