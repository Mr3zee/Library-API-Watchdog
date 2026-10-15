@file:Suppress("FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.gradle.Plugin
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.junit.Test

class GradleCompatibilityTest {
    @Test
    fun `configuration cache is reused`() {
        val project = jvmProject()
        val arguments = arrayOf("help", "--configuration-cache", "--configuration-cache-problems=fail")

        project.build(*arguments).assertOutputHas("Configuration cache entry stored.")
        project.build(*arguments).assertOutputHas("Configuration cache entry reused.")
    }

    @Test
    fun `works in an isolated subproject`() {
        val project = jvmProject {
            plugins()
            subproject("lib") { source("/** A stable public type. */\npublic class StableApi", "StableApi") }
        }

        project.build(":lib:compileKotlin", *PROJECT_ISOLATION).assertOutputHas("BUILD SUCCESSFUL")
    }

    @Test
    fun `aggregates declared project reports with project isolation`() {
        val project = jvmProject {
            plugins(Plugin("org.jetbrains.kotlin.library.api-watchdog-report-aggregation", pluginUnderTestVersion))
            buildScript(
                """
                    dependencies {
                        apiWatchdogReports(project(":openApi"))
                        apiWatchdogReports(project(":dataApi"))
                    }
                    tasks.aggregateApiWatchdogReports {
                        reports { sarifFile = layout.buildDirectory.file("scan/aggregate.sarif") }
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
        assertContains(html, "Aggregate report for 2 projects")
        assertContains(html, ":openApi")
        assertContains(html, ":dataApi")
        assertReportGroup(html, "IntentionallyOpen", 1, Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
        assertReportGroup(html, "IntentionallyDataClass", 1, Diagnostic.DATA_CLASS_PUBLIC_API)
        assertContains(html, "class=\"fix-status not-fixed\">Wasn't auto-fixed</span>")
        assertContains(html, "<code class=\"language-kotlin\">")
        assertContains(html, "public open class OpenApi")
        assertContains(html, "public data class DataApi")
        assertContains(html, "The class <code>OpenApi</code> can be subclassed outside the library.")
        assertContains(html, "The <code>data class</code> <code>DataApi</code>")
        assertContains(html, "href=\"https://github.com/drewhamilton/Poko\">Poko</a>")
        assertReportedElement(html, "OpenApi", "error")
        assertReportedElement(html, "DataApi", "error")
        val sarif = project.file("build/scan/aggregate.sarif").readText()
        assertContains(sarif, "\"uri\": \"openApi/src/main/kotlin/test/OpenApi.kt\"")
        assertContains(sarif, "\"uri\": \"dataApi/src/main/kotlin/test/DataApi.kt\"")
        assertContains(sarif, "\"ruleId\": \"DATA_CLASS_PUBLIC_API\"")
    }

    @Test
    fun `aggregates every subproject and skips projects without the watchdog`() {
        val project = jvmProject {
            plugins(Plugin("org.jetbrains.kotlin.library.api-watchdog-report-aggregation", pluginUnderTestVersion))
            buildScript(
                """
                    dependencies {
                        subprojects.forEach { apiWatchdogReports(project(it.path)) }
                    }
                """
            )
            subproject("openApi") { source("/** An open API. */\npublic open class OpenApi", "OpenApi") }
            subproject("dataApi") {
                source("/** A data API. */\npublic data class DataApi(public val value: Int)", "DataApi")
            }
            // A Kotlin module without the Watchdog has no report variant.
            subproject("tools") {
                plugins(Plugin("org.jetbrains.kotlin.jvm"))
                source("/** A tool. */\npublic open class Tool", "Tool")
            }
        }

        val result = project.build("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        result.assertTaskOutcome(":openApi:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":dataApi:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":tools:generateApiWatchdogReport", null)
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Aggregate report for 2 projects")
        assertContains(html, ":openApi")
        assertContains(html, ":dataApi")
        assertFalse(html.contains(":tools"), html)
    }

    @Test
    fun `help doesn't realize plugin or compilation tasks`() {
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
    fun `realized tasks keep configured severities`() {
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
