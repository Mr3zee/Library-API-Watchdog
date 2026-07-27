plugins {
    kotlin("jvm")
    `maven-publish`
    kotlin("compiler.plugin.devkit.functional-test-publishing")
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

tasks.processTestResources {
    from(project(":compiler-plugin").layout.projectDirectory.dir("src/test/data"))
}

tasks.test {
    useJUnitPlatform()
    // Kotlin's standalone PSI application is process-global and can't be initialized by
    // concurrent test instances.
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
