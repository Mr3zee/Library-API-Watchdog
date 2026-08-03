plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    kotlin("compiler.plugin.devkit.functional-test-publishing")
    id("libs-watchdog.root-conventions")
}
