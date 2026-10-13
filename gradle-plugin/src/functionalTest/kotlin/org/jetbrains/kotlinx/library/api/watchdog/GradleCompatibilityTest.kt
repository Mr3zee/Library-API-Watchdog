package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.gradle.Plugin
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.junit.Test

class GradleCompatibilityTest {
    @Test
    fun configurationCacheIsReused() {
        val project = jvmProject()
        val arguments = arrayOf("help", "--configuration-cache", "--configuration-cache-problems=fail")

        project.build(*arguments).assertOutputHas("Configuration cache entry stored.")
        project.build(*arguments).assertOutputHas("Configuration cache entry reused.")
    }

    @Test
    fun worksInAnIsolatedSubproject() {
        val project = jvmProject {
            plugins()
            subproject("lib") { source("/** A stable public type. */\npublic class StableApi", "StableApi") }
        }

        project.build(":lib:compileKotlin", *PROJECT_ISOLATION).assertOutputHas("BUILD SUCCESSFUL")
    }

    @Test
    fun aggregatesDeclaredProjectReportsWithProjectIsolation() {
        val project = jvmProject {
            plugins(Plugin("org.jetbrains.kotlin.library.api-watchdog-report-aggregation", pluginUnderTestVersion))
            buildScript(
                """
                    dependencies {
                        apiWatchdogReports(project(":openApi"))
                        apiWatchdogReports(project(":dataApi"))
                    }
                """
            )
            subproject("openApi") { source("/** An open API. */\npublic open class OpenApi", "OpenApi") }
            subproject("dataApi") {
                source("/** A data API. */\npublic data class DataApi(public val value: Int)", "DataApi")
            }
        }
        val openApiSource = project.file("openApi/src/main/kotlin/test/OpenApi.kt")
        val dataApiSource = project.file("dataApi/src/main/kotlin/test/DataApi.kt")
        val originalOpenApi = openApiSource.readText()
        val originalDataApi = dataApiSource.readText()

        val result = project.build("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        result.assertTaskOutcome(":openApi:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":dataApi:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":openApi:exemptExistingApi", null)
        result.assertTaskOutcome(":dataApi:exemptExistingApi", null)
        assertEquals(originalOpenApi, openApiSource.readText())
        assertEquals(originalDataApi, dataApiSource.readText())
        val report = project.file("build/reports/api-watchdog/aggregate-report.html")
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
        val project = jvmProject {
            explicitApi = false
            buildScript(
                """
                    kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=strict") } }
                    tasks.named("compileKotlin").configure { println("$compileMarker") }
                    tasks.named("exemptExistingApi").configure {
                        println("$updateMarker")
                    }
                """
            )
        }

        val result = project.build("help")

        result.assertOutputLacks(compileMarker)
        result.assertOutputLacks(updateMarker)
    }

    @Test
    fun realizingEveryTaskKeepsConfiguredSeverities() {
        // `tasks.all { }` realizes the exemption tasks without a schedule.
        val project = jvmProject {
            buildScript("tasks.all { }")
            source("/** An open API. */\npublic open class OpenApi", "OpenApi")
        }

        project.buildAndFail("compileKotlin").assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
    }

    private companion object {
        val PROJECT_ISOLATION = arrayOf("-Dorg.gradle.unsafe.isolated-projects=true", "--configuration-cache-problems=fail")
    }
}
