plugins {
    pluginDevKit("compiler-library")
    id("library-api-watchdog.space-publishing-conventions")
}

pluginDevKit {
    pluginPackage.set("org.jetbrains.kotlin.library.api.watchdog.fixer")
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir("src/main/kotlin")
        }
        commonTest {
            kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
    val compilerIntegrationTest = sourceSets.create("compilerIntegrationTest") {
        dependsOn(sourceSets.commonTest.get())
        resources.srcDir(rootProject.file("compiler-plugin/src/test/data"))
        dependencies {
            implementation(pluginDevKit("compiler-plugin-runtime"))
            implementation(project(":kotlin-library-api-watchdog-compiler-plugin"))
            implementation(project(":kotlin-library-api-watchdog-plugin-annotations"))
        }
    }

    // Exercise the compiler-fixture integration test against every generated Kotlin target.
    pluginDevKit.testAgainst.configureEach { test { dependsOn(compilerIntegrationTest) } }
}

// These are regular unit tests; unlike compiler-plugin suites, they do not generate Java runners.
tasks.generateTests { enabled = false }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
