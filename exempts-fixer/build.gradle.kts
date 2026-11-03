plugins {
    id("devkit.compiler-library")
    id("library-api-watchdog.exempts-fixer-conventions")
    id("library-api-watchdog.space-publishing-conventions")
    id("library-api-watchdog.maven-central-publishing-conventions")
}

pluginDevKit {
    pluginPackage = "org.jetbrains.kotlinx.library.api.watchdog.fixer"
}

kotlin {
    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
    val compilerIntegrationTest = sourceSets.create("compilerIntegrationTest") {
        dependsOn(sourceSets.commonTest.get())
        resources.srcDir(isolated.rootProject.projectDirectory.dir("compiler-plugin/src/commonTest/data"))
        dependencies {
            implementation(pluginDevKit("compiler-plugin-runtime"))
            implementation(project(":kotlin-library-api-watchdog-compiler-plugin"))
            implementation("$group:kotlin-library-api-watchdog-plugin-annotations:$version")
        }
    }

    pluginDevKit.testAgainst.configureEach { test { dependsOn(compilerIntegrationTest) } }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
