plugins {
    kotlin("jvm")
    `maven-publish`
    kotlin("compiler.plugin.devkit.functional-test-publishing")
    id("libs-watchdog.exempts-fixer-conventions")
}

// A standalone PSI fixer, not a library. The Gradle plugin launches it in a separate JVM whose
// classpath adds kotlin-compiler-embeddable matching the project's Kotlin Gradle plugin. Regular
// KGP compile tasks have already performed all semantic analysis and written the reports.
dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(testFixtures(project(":compiler-plugin")))
    testImplementation(project(":plugin-annotations"))
}
