import org.jetbrains.kotlin.compiler.plugin.devkit.BetaAndRc
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    pluginDevKit("compiler-library")
    id("library-api-watchdog.space-publishing-conventions")
}

val compilerPluginProject = project(":kotlin-library-api-watchdog-compiler-plugin")
val annotationsProject = project(":kotlin-library-api-watchdog-plugin-annotations")
val annotationsJvmClasses = files(
    annotationsProject.layout.buildDirectory.dir("classes/kotlin/jvm/main"),
).builtBy(":kotlin-library-api-watchdog-plugin-annotations:compileKotlinJvm")

@OptIn(ExperimentalKotlinGradlePluginApi::class)
pluginDevKit {
    cliVersions(
        min = "2.3.20",
        betaAndRc = BetaAndRc.LATEST,
    )
    ideaVersions(
        min = "262",
        includeRc = true,
        includeEap = true,
    )
    useLatestDev()
    pluginPackage.set("org.jetbrains.kotlin.library.api.watchdog.fixer")

    // The fixer is a compiler-API library, not a compiler plugin. Its caller supplies the
    // kotlin-compiler runtime matching the consuming project's Kotlin version.
    addRuntimeDependency.set(false)

    kotlin.applyPluginDevKitHierarchyTemplate {}
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

    // Exercise the compiler-fixture integration test against every generated Kotlin target.
    targets.configureEach {
        compilations.matching { it.name == "test" }.configureEach {
            val compilerTarget = target.name
            val compilerPluginClasses = files(
                compilerPluginProject.layout.buildDirectory.dir("classes/kotlin/$compilerTarget/main"),
            ).builtBy(
                ":kotlin-library-api-watchdog-compiler-plugin:compileKotlin" +
                        compilerTarget.replaceFirstChar(Char::uppercase),
            )
            defaultSourceSet {
                kotlin.srcDir("src/compilerIntegrationTest/kotlin")
                resources.srcDir(rootProject.file("compiler-plugin/src/test/data"))
                dependencies {
                    implementation(pluginDevKit("compiler-plugin-runtime"))
                    // File dependencies avoid Kotlin version alignment rewriting this project's
                    // org.jetbrains.kotlin modules as external compiler-versioned dependencies.
                    implementation(compilerPluginClasses)
                    implementation(annotationsJvmClasses)
                }
            }
        }
    }
}

// These are regular unit tests; unlike compiler-plugin suites, they do not generate Java runners.
tasks.generateTests { enabled = false }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
