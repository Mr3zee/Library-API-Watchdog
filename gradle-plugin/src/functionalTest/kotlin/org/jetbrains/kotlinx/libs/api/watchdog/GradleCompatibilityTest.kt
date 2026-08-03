package org.jetbrains.kotlinx.libs.api.watchdog

import com.autonomousapps.kit.GradleBuilder.build
import com.autonomousapps.kit.GradleProject
import kotlin.test.assertContains
import kotlin.test.assertFalse
import org.junit.Test

class GradleCompatibilityTest {
    @Test
    fun configurationCacheIsReused() {
        val project = WatchdogProject().gradleProject
        val arguments = arrayOf("help", "--configuration-cache", "--configuration-cache-problems=fail")

        val first = build(project.rootDir, *arguments)
        assertContains(first.output, "Configuration cache entry stored.")

        val second = build(project.rootDir, *arguments)
        assertContains(second.output, "Configuration cache entry reused.")
    }

    @Test
    fun worksInAnIsolatedSubproject() {
        val project = object : WatchdogProject() {
            override fun buildGradleProject(): GradleProject =
                newGradleProjectBuilder(GradleProject.DslKind.KOTLIN)
                    .withRootProject {
                        withDevKitSettings()
                    }
                    .withSubproject("lib") {
                        sources.add(
                            source(
                                "/** A stable public type. */\npublic class StableApi",
                                "StableApi",
                            ),
                        )
                        withBuildScript { applyDefaultBuildScript() }
                    }
                    .write()
        }.gradleProject

        val result = build(
            project.rootDir,
            ":lib:compileKotlin",
            "-Dorg.gradle.unsafe.isolated-projects=true",
            "--configuration-cache-problems=fail",
        )

        assertContains(result.output, "BUILD SUCCESSFUL")
    }

    @Test
    fun helpDoesNotRealizePluginOrCompilationTasks() {
        val compileMarker = "REALIZED_WATCHDOG_COMPILE_TASK"
        val updateMarker = "REALIZED_WATCHDOG_UPDATE_TASK"
        val project = WatchdogProject(
            explicitApi = false,
            extraBuildScript = """
                kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=strict") } }
                tasks.named("compileKotlin").configure { println("$compileMarker") }
                tasks.named("updateBackwardsCompatibilityExempts").configure {
                    println("$updateMarker")
                }
            """.trimIndent(),
        ).gradleProject

        val result = build(project.rootDir, "help")

        assertFalse(result.output.contains(compileMarker), result.output)
        assertFalse(result.output.contains(updateMarker), result.output)
    }
}
