plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    kotlin("compiler.plugin.devkit.functional-test-publishing")
    id("library-api-watchdog.root-conventions")
}
