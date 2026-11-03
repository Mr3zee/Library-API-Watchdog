import org.jetbrains.kotlin.compiler.plugin.devkit.ProjectOrGav

plugins {
    id("devkit.gradle")
    id("library-api-watchdog.gradle-plugin-test-conventions")
}

pluginDevKit {
    pluginPackage = "org.jetbrains.kotlinx.library.api.watchdog"
    // The report aggregation plugin is in the same build, and the Gradle plugin publishes it as a dependency.
    includedBuildProjects.add("gradle-plugin")
    includedBuildProjects.add("plugin-annotations")
    companionLibrary("kotlin-library-api-watchdog-plugin-annotations")
    compilerPlugin = ProjectOrGav.LocalProject(project(":kotlin-library-api-watchdog-compiler-plugin").isolated)
}
