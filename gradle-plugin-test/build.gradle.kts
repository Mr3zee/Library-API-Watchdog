import org.jetbrains.kotlin.compiler.plugin.devkit.ProjectOrGav

plugins {
    id("devkit.gradle")
    id("library-api-watchdog.gradle-plugin-test-conventions")
}

// The dev kit publishes the included builds of the functional test classpath for the tests. The Gradle plugin brings
// the report aggregation plugin of the same build.
dependencies {
    functionalTestRuntimeOnly("$group:kotlin-library-api-watchdog-gradle-plugin:$version")
}

pluginDevKit {
    pluginPackage = "org.jetbrains.kotlinx.library.api.watchdog"
    companionLibrary("kotlin-library-api-watchdog-plugin-annotations")
    compilerPlugin = ProjectOrGav.LocalProject(project(":kotlin-library-api-watchdog-compiler-plugin").isolated)
}
