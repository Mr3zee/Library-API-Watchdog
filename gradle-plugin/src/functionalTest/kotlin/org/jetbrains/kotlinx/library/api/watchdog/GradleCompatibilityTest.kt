package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.GradleBuilder.build
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.gradle.Plugin
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion
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
    fun aggregatesDeclaredProjectReportsWithProjectIsolation() {
        val project = object : WatchdogProject() {
            override fun buildGradleProject(): GradleProject =
                newGradleProjectBuilder(GradleProject.DslKind.KOTLIN)
                    .withRootProject {
                        withDevKitSettings()
                        withBuildScript {
                            plugins(
                                Plugin(
                                    "org.jetbrains.kotlin.library.api-watchdog-report-aggregation",
                                    pluginUnderTestVersion,
                                ),
                            )
                            withKotlin(
                                """
                                    dependencies {
                                        backwardsCompatibilityExemptsReports(project(":openApi"))
                                        backwardsCompatibilityExemptsReports(project(":dataApi"))
                                    }
                                """.trimIndent()
                            )
                        }
                    }
                    .withSubproject("openApi") {
                        sources.add(source("/** An open API. */\npublic open class OpenApi", "OpenApi"))
                        withBuildScript { applyDefaultBuildScript() }
                    }
                    .withSubproject("dataApi") {
                        sources.add(
                            source(
                                "/** A data API. */\npublic data class DataApi(public val value: Int)",
                                "DataApi",
                            ),
                        )
                        withBuildScript { applyDefaultBuildScript() }
                    }
                    .write()
        }.gradleProject
        val openApiSource = project.rootDir.resolve("openApi/src/main/kotlin/test/OpenApi.kt")
        val dataApiSource = project.rootDir.resolve("dataApi/src/main/kotlin/test/DataApi.kt")
        val originalOpenApi = openApiSource.readText()
        val originalDataApi = dataApiSource.readText()

        val result = build(
            project.rootDir,
            "aggregateBackwardsCompatibilityExemptsReport",
            "-Dorg.gradle.unsafe.isolated-projects=true",
            "--configuration-cache-problems=fail",
        )

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":openApi:generateBackwardsCompatibilityExemptsReport")?.outcome,
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":dataApi:generateBackwardsCompatibilityExemptsReport")?.outcome,
        )
        assertEquals(null, result.task(":openApi:updateBackwardsCompatibilityExempts"))
        assertEquals(null, result.task(":dataApi:updateBackwardsCompatibilityExempts"))
        assertEquals(originalOpenApi, openApiSource.readText())
        assertEquals(originalDataApi, dataApiSource.readText())
        val report = project.rootDir.resolve(
            "build/reports/api-watchdog/backwards-compatibility-exempts-aggregate.html"
        )
        assertTrue(report.isFile, result.output)
        val html = report.readText()
        assertContains(html, "Aggregate report for 2 project(s)")
        assertContains(html, ":openApi")
        assertContains(html, ":dataApi")
        assertContains(html, "@IntentionallyOpen")
        assertContains(html, "@IntentionallyDataClass")
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
