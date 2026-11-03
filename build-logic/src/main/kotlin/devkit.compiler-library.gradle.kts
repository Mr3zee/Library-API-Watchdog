import org.jetbrains.kotlinx.library.api.watchdog.conventions.config

plugins {
    kotlin("compiler.plugin.devkit.compiler-library")
}

pluginDevKit { config() }
