import org.jetbrains.kotlinx.library.api.watchdog.conventions.config

plugins {
    kotlin("compiler.plugin.devkit.gradle-plugin")
}

pluginDevKit { config() }
