plugins {
    kotlin("jvm")
    `maven-publish`
    kotlin("compiler.plugin.devkit.functional-test-publishing")
    id("library-api-watchdog.exempts-fixer-conventions")
    id("library-api-watchdog.space-publishing-conventions")
}

// A standalone PSI fixer, not a library. The Gradle plugin launches it in a separate JVM whose
// classpath adds kotlin-compiler-embeddable matching the project's Kotlin Gradle plugin. Regular
// KGP compile tasks have already performed all semantic analysis and written the reports.
dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(testFixtures(project(":kotlin-library-api-watchdog-compiler-plugin")))
    testImplementation(project(":kotlin-library-api-watchdog-plugin-annotations"))
}
