package org.jetbrains.kotlinx.library.api.watchdog.conventions

import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import java.util.Properties
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

class DevelocitySettingsConventionPlugin : Plugin<Settings> {
    override fun apply(target: Settings) {
        with(target) {
            pluginManager.apply("com.gradle.develocity")

            val isCi = providers.environmentVariable("CI").map(String::toBoolean).getOrElse(false)
            val buildScanEnabled = providers.fileContents(layout.settingsDirectory.file("local.properties")).asText
                .map { content ->
                    Properties().apply { load(content.reader()) }
                        .getProperty("develocity.buildScan.enabled")?.toBoolean() ?: isCi
                }
                .getOrElse(isCi)

            val develocity = extensions.getByType(DevelocityConfiguration::class.java)
            develocity.server.set("https://develocity.jetbrains.com")
            develocity.buildScan {
                publishing.onlyIf { buildScanEnabled && it.isAuthenticated }
                uploadInBackground.set(!isCi)
            }

            buildCache {
                remote(develocity.buildCache) {
                    isPush = providers.environmentVariable("DEVELOCITY_CACHE_PUSH").map(String::toBoolean).getOrElse(false)
                }
            }
        }
    }
}
