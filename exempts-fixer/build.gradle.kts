plugins {
    id("org.jetbrains.kotlin.compiler.plugin.devkit.compiler-library")
    id("library-api-watchdog.devkit-versions-conventions")
    id("library-api-watchdog.space-publishing-conventions")
}

val devKitVersion = providers.systemProperty("devkitVersion").get()

pluginDevKit {
    pluginPackage.set("org.jetbrains.kotlinx.library.api.watchdog.fixer")
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
            implementation("org.jetbrains.kotlin.compiler.plugin.devkit:compiler-plugin-runtime:$devKitVersion")
            implementation(project(":kotlin-library-api-watchdog-compiler-plugin"))
            implementation(project(":kotlin-library-api-watchdog-plugin-annotations"))
        }
    }

    pluginDevKit.testAgainst.configureEach { test { dependsOn(compilerIntegrationTest) } }
}

// The tests need no generated runners. The `GenerateTestsTask` type of the dev kit is internal,
// so the name selects the tasks: https://github.com/Kotlin/compiler-plugin-dev-kit/issues/62.
tasks.matching { it.name.startsWith("generateTests") }.configureEach { enabled = false }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
