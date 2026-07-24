plugins {
    kotlin("jvm")
    `maven-publish`
    kotlin("compiler.plugin.devkit.functional-test-publishing")
}

// A standalone command-line tool, not a library: the Gradle plugin's
// updateBackwardsCompatibilityExempts task launches its main class in a separate JVM whose
// classpath adds kotlin-build-tools-impl of the project's Kotlin version, which provides both the
// Build Tools API implementation and kotlin-compiler-embeddable at runtime.
dependencies {
    compileOnly(libs.kotlin.build.tools.api)
    compileOnly(libs.kotlin.compiler.embeddable)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.build.tools.api)
    testImplementation(libs.kotlin.build.tools.impl)
    testImplementation(libs.kotlin.compiler.embeddable)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
